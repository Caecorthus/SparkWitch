package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssSuppression;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Standing effects of the Deep Dark Zone, server-authoritative. Every {@link AbyssListenerRules#ZONE_CHECK_INTERVAL_TICKS}
 * ticks, in each world with a live zone, a participant whose stepping block is converted gets Speed III (witch-faction
 * ally), or, when {@link AbyssSuppression#canAffect} allows it, Slowness III plus the owner-only exposure mark that
 * shows the pseudo task and boosts the sanity drain ({@link DeepDarkZoneStandingDrain}). Reads the zone only through
 * the {@link DeepDarkZoneService} queries; never touches blocks, Wathe's task map or any cooldown.
 * 深暗领域站立效果，由服务端权威决定。每 {@link AbyssListenerRules#ZONE_CHECK_INTERVAL_TICKS} 刻，在有存活领域的世界里，
 * 脚下方块已被转换的参与者：魔女阵营队友获得速度 III；其他人在 {@link AbyssSuppression#canAffect} 允许时获得缓慢 III，
 * 并被打上仅同步给本人的暴露标记（显示临时任务并加速理智下降，见 {@link DeepDarkZoneStandingDrain}）。
 * 只通过 {@link DeepDarkZoneService} 的查询读取领域；从不改动方块、Wathe 任务表或任何冷却。
 */
public final class DeepDarkZoneStandingService {
    private static boolean registered;

    private DeepDarkZoneStandingService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(DeepDarkZoneStandingService::tick);
        // Exposure counts down by itself; these only end it at once. / 暴露会自行倒计时；这里只是让它立即结束。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> clearExposure(victim));
        ResetPlayer.EVENT.register(DeepDarkZoneStandingService::clearExposure);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearAllExposure(serverWorld);
            }
        });
    }

    /**
     * Ends every exposure in {@code world} at once; each changed component syncs its zero to its owner. Also called by
     * every instant zone restore in {@link DeepDarkZoneService}.
     * 立即结束 {@code world} 中的所有暴露；每个发生变化的组件都会把零值同步给其拥有者。{@link DeepDarkZoneService}
     * 的每次立即恢复也会调用。
     */
    static void clearAllExposure(ServerWorld world) {
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            clearExposure(player);
        }
    }

    private static void tick(ServerWorld world) {
        if (!DeepDarkZoneStandingRules.isCheckTick(world.getTime()) || !DeepDarkZoneService.hasActiveZones(world)) {
            return;
        }
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            BlockPos stepping = player.getSteppingPos();
            DeepDarkZoneStandingRules.Standing standing = DeepDarkZoneStandingRules.classify(
                    AbyssSuppression.isParticipantTarget(player),
                    player.isOnGround(),
                    () -> DeepDarkZoneService.isConverted(world, stepping),
                    () -> AbyssSuppression.isAlly(player));
            switch (standing) {
                case ALLY -> refresh(player, StatusEffects.SPEED, AbyssListenerRules.ZONE_ALLY_SPEED_AMPLIFIER,
                        actingOwner(world, stepping));
                case NON_ALLY -> suppress(world, player, stepping);
                case NONE -> {
                }
            }
        }
    }

    private static void suppress(ServerWorld world, ServerPlayerEntity player, BlockPos stepping) {
        // No living thrower online: no actor, so only the participant rules apply (plan default N19).
        // 没有在线且存活的投掷者：不设施加者，只按参与者规则判定（方案默认 N19）。
        ServerPlayerEntity actor = actingOwner(world, stepping);
        if (!AbyssSuppression.canAffect(actor, player, AbyssListenerRules.ZONE_ACTION_ID)) {
            return;
        }
        refresh(player, StatusEffects.SLOWNESS, AbyssListenerRules.ZONE_SLOWNESS_AMPLIFIER, actor);
        AbyssZoneExposureComponent.KEY.get(player).expose(AbyssListenerRules.ZONE_EXPOSURE_TICKS);
    }

    private static @Nullable ServerPlayerEntity actingOwner(ServerWorld world, BlockPos stepping) {
        return DeepDarkZoneStandingRules.actingOwner(
                DeepDarkZoneService.ownersAt(world, stepping),
                uuid -> {
                    ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(uuid);
                    return owner != null && AbyssSuppression.isParticipantTarget(owner) ? owner : null;
                });
    }

    private static void refresh(
            ServerPlayerEntity player,
            RegistryEntry<StatusEffect> effect,
            int amplifier,
            @Nullable ServerPlayerEntity source
    ) {
        StatusEffectInstance current = player.getStatusEffect(effect);
        boolean present = current != null;
        if (DeepDarkZoneStandingRules.needsRefresh(
                present,
                present ? current.getAmplifier() : 0,
                present ? current.getDuration() : 0,
                present && current.isInfinite(),
                amplifier)) {
            AbyssSuppression.addEffect(player, effect, AbyssListenerRules.ZONE_EFFECT_REFRESH_TICKS, amplifier, source);
        }
    }

    private static void clearExposure(@Nullable ServerPlayerEntity player) {
        if (player != null) {
            AbyssZoneExposureComponent.KEY.get(player).clear();
        }
    }
}
