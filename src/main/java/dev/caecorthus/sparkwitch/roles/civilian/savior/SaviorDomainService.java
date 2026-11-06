package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.net.SaviorDomainS2CPayload;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Savior domain skill. G opens a 15-block aura around the living Savior for 10 s: everyone inside
 * gets night vision and has blindness removed every tick, non-Saviors also get Slowness I. The S2C domain packet lets
 * clients draw keybind-free outlines (white for others, the Savior's role color for the Savior). The 60 s cooldown and
 * 10 s duration are shared with the synced {@link SaviorPlayerComponent} HUD.
 * 救世主 G 技能的服务端权威实现。按 G 在存活救世主周围展开 15 格、持续 10 秒的领域：领域内所有人获得夜视并每刻清除失明，
 * 救世主以外的人获得缓慢 I。S2C 领域包让客户端绘制无需本能键的描边（他人白色，救世主职业色）。60 秒冷却与 10 秒
 * 持续时间由同步的 {@link SaviorPlayerComponent} 供 HUD 读取。
 */
public final class SaviorDomainService {
    private static boolean registered;

    private SaviorDomainService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(SaviorRules.ROLE_ID, SaviorDomainService::formatReplay);
        PayloadTypeRegistry.playC2S().register(UseSaviorSkillC2SPacket.ID, UseSaviorSkillC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SaviorDomainS2CPayload.ID, SaviorDomainS2CPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseSaviorSkillC2SPacket.ID,
                (payload, context) -> use(context.player()));
        ServerTickEvents.END_SERVER_TICK.register(SaviorDomainService::tick);
        KillPlayer.AFTER.register((victim, killer, reason) -> clearPlayer(victim));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                clearPlayer(player);
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                clearPlayer(serverPlayer);
            }
        });
    }

    /** Server only: role lifecycle reset. / 仅服务端：身份生命周期重置。 */
    public static void assignForRole(ServerPlayerEntity player, @Nullable Role role) {
        SaviorPlayerComponent.KEY.get(player).assignForRole(role);
    }

    /** Server only: death/reset cleanup. / 仅服务端：死亡/重置清理。 */
    public static void clearPlayer(ServerPlayerEntity player) {
        SaviorPlayerComponent.KEY.get(player).clear();
    }

    /**
     * G request. The client only sends an empty intent; role, round, liveness, cooldown and the active-domain guard are
     * all decided here. Feared Saviors are refused by the same shared check as the other role skills.
     * G 请求。客户端只发空意图；职业、回合、存活、冷却与领域是否已展开全部在此判定。被大魔女恐惧时与其他职业技能一样拒绝。
     */
    private static void use(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        SaviorPlayerComponent component = SaviorPlayerComponent.KEY.get(caster);
        if (!game.isRunning()
                || !SaviorRules.isSavior(game.getRole(caster))
                || !GameFunctions.isPlayerPlayingAndAlive(caster)
                || component.getCooldownTicks() > 0
                || component.isDomainActive()) {
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
            return;
        }

        component.startDomain();
        GameRecordManager.recordSkillUse(caster, SaviorRules.ROLE_ID, null, null);
        SaviorDomainS2CPayload payload = new SaviorDomainS2CPayload(
                caster.getId(),
                SaviorRules.DOMAIN_DURATION_TICKS
        );
        for (ServerPlayerEntity viewer : caster.getServerWorld().getPlayers()) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            for (ServerPlayerEntity savior : world.getPlayers()) {
                SaviorPlayerComponent component = SaviorPlayerComponent.KEY.get(savior);
                if (!component.isDomainActive()) {
                    continue;
                }
                // A dead, reassigned or round-ended Savior cannot keep the domain alive.
                // 死亡、身份变更或回合结束的救世主不能继续维持领域。
                if (!game.isRunning()
                        || !SaviorRules.isSavior(game.getRole(savior))
                        || !GameFunctions.isPlayerPlayingAndAlive(savior)) {
                    component.stopDomain();
                    continue;
                }
                applyDomain(world, savior, component.getDomainTicks());
            }
        }
    }

    private static void applyDomain(ServerWorld world, ServerPlayerEntity savior, int remainingDomainTicks) {
        // Short refresh while the domain runs, capped by its remainder so the effects end with it.
        // 领域持续期间短刷新，并以剩余时间为上限，使效果与领域同时结束。
        int effectTicks = Math.max(1, Math.min(remainingDomainTicks, SaviorRules.DOMAIN_EFFECT_REFRESH_TICKS));
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target.isSpectator()
                    || !GameFunctions.isPlayerPlayingAndAlive(target)
                    || target.squaredDistanceTo(savior) > SaviorRules.DOMAIN_RADIUS_SQUARED) {
                continue;
            }
            // "Real-time" removal: blackout and any other blindness source are stripped every server tick.
            // “实时”清除：关灯或任何其他失明来源都会在每个服务端刻被移除。
            target.removeStatusEffect(StatusEffects.BLINDNESS);
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    effectTicks,
                    0,
                    false,
                    false,
                    true
            ));
            if (target != savior) {
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS,
                        effectTicks,
                        0,
                        false,
                        false,
                        true
                ));
            }
        }
    }

    /** Replay line for a successfully expanded domain; the skill has no target. / 成功展开领域的回放行；技能没有目标。 */
    private static @Nullable Text formatReplay(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        if (!event.data().containsUuid("actor")) {
            return null;
        }
        return Text.translatable(
                "replay.skill.sparkwitch.savior.domain",
                ReplayGenerator.formatPlayerName(
                        event.data().getUuid("actor"),
                        ReplayGenerator.getPlayerInfoCache(match)
                )
        );
    }
}
