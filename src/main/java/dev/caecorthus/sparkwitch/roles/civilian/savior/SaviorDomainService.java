package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.SaviorDomainS2CPayload;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
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
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative Savior domain skill. G opens a 15-block aura around the living Savior for 10 s: everyone inside
 * keeps night vision and has blindness removed every tick, non-Saviors also keep Slowness I. The S2C domain packet lets
 * clients draw keybind-free outlines (white for others, the Savior's role color for the Savior). The 60 s cooldown and
 * 10 s duration are shared with the synced {@link SaviorPlayerComponent} HUD.
 * 救世主 G 技能的服务端权威实现。按 G 在存活救世主周围展开 15 格、持续 10 秒的领域：领域内所有人保持夜视并每刻清除失明，
 * 救世主以外的人保持缓慢 I。S2C 领域包让客户端绘制无需本能键的描边（他人白色，救世主职业色）。60 秒冷却与 10 秒
 * 持续时间由同步的 {@link SaviorPlayerComponent} 供 HUD 读取。
 */
public final class SaviorDomainService {
    /**
     * A domain effect is re-added only when missing or at most this many ticks remain, so a target receives about one
     * effect packet per effect every ten ticks instead of two every tick.
     * 领域效果只在缺失或剩余不超过该刻数时才重新施加，使目标每种效果约每十刻收到一次效果包，而不是每刻两次。
     */
    static final int EFFECT_REFRESH_MARGIN_TICKS = 10;

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
        // A death SparkTraits intercepted (Last Stand revive) is not a death: the revived Savior keeps its domain state
        // (Dominator precedent). / SparkTraits 拦截的死亡（背水一战复活）不算死亡：复活的救世主保留领域状态（支配者先例）。
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            if (victim != null && !WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                clearPlayer(victim);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && !WitchFactorTraitsBridge.isDeathIntercepted(player)) {
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
     * G request. The client only sends an empty intent; everything is decided here against server state: an ACTIVE
     * round (not STOPPING), the raw Savior role, a present caster (playing and alive, not a spectator, not inside a
     * Taotie), no SparkTraits role-skill block (Last Escape, silenced), no cooldown and no running domain. Feared
     * Saviors are then refused by the same shared check as the other role skills.
     * G 请求。客户端只发空意图；一切都按服务端状态判定：ACTIVE 回合（不含 STOPPING）、真实救世主职业、在场的施放者（存活在局、
     * 非旁观、未被饕餮吞下）、没有 SparkTraits 职业技能封锁（脱险、沉默）、不在冷却且没有进行中的领域。随后被大魔女恐惧时与其他
     * 职业技能一样经共用判定拒绝。
     */
    private static void use(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        SaviorPlayerComponent component = SaviorPlayerComponent.KEY.get(caster);
        if (!mayUse(holdsDomain(game, caster),
                SparkTraitsKillerBridge.isRoleSkillBlocked(caster),
                component.getCooldownTicks() > 0,
                component.isDomainActive())) {
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
                // A dead, reassigned, spectating or swallowed Savior, or a round that is no longer ACTIVE, ends it.
                // 死亡、身份变更、旁观或被饕餮吞下的救世主，或回合不再处于 ACTIVE 时，领域结束。
                if (!holdsDomain(game, savior)) {
                    component.stopDomain();
                    continue;
                }
                applyDomain(world, savior, component.getDomainTicks());
            }
        }
    }

    /**
     * The liveness gate shared by the G press and every domain tick: an ACTIVE round, the raw Savior role, playing and
     * alive, not a spectator and not inside a Taotie.
     * G 键与每个领域刻共用的在场门控：ACTIVE 回合、真实救世主职业、存活在局、非旁观且未被饕餮吞下。
     */
    private static boolean holdsDomain(GameWorldComponent game, ServerPlayerEntity savior) {
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && SaviorRules.isSavior(game.getRole(savior))
                && GameFunctions.isPlayerPlayingAndAlive(savior)
                && !savior.isSpectator()
                && !NoellesTaotieSeekerBridge.isSwallowed(savior);
    }

    /** Pure G-press rule before the Fear check. / 恐惧判定之前的纯 G 键规则。 */
    static boolean mayUse(boolean holdsDomain, boolean roleSkillBlocked, boolean coolingDown, boolean domainActive) {
        return holdsDomain && !roleSkillBlocked && !coolingDown && !domainActive;
    }

    /**
     * Pure: re-add a domain effect only when it is missing or a finite one has at most
     * {@link #EFFECT_REFRESH_MARGIN_TICKS} left. / 纯函数：仅当效果缺失，或有限时长效果剩余不超过
     * {@link #EFFECT_REFRESH_MARGIN_TICKS} 刻时才重新施加。
     */
    static boolean needsRefresh(boolean present, boolean infinite, int remainingTicks) {
        return !present || (!infinite && remainingTicks <= EFFECT_REFRESH_MARGIN_TICKS);
    }

    private static void applyDomain(ServerWorld world, ServerPlayerEntity savior, int remainingDomainTicks) {
        // Short refresh while the domain runs, capped by its remainder so every effect it writes ends no later than the
        // domain. / 领域持续期间短刷新，并以剩余时间为上限，使每次写入的效果都不晚于领域结束。
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
            refresh(target, StatusEffects.NIGHT_VISION, effectTicks);
            if (target != savior) {
                refresh(target, StatusEffects.SLOWNESS, effectTicks);
            }
        }
    }

    private static void refresh(ServerPlayerEntity target, RegistryEntry<StatusEffect> effect, int ticks) {
        StatusEffectInstance current = target.getStatusEffect(effect);
        if (!needsRefresh(current != null, current != null && current.isInfinite(),
                current == null ? 0 : current.getDuration())) {
            return;
        }
        target.addStatusEffect(new StatusEffectInstance(effect, ticks, 0, false, false, true));
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
