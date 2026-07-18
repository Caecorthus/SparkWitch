package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server authority for Guardian Angel promotion, targeting, shielding, protection, and replay.
 * 守护天使晋升、瞄准、护盾、保护与回放的服务端权威入口。
 */
public final class GuardianAngelFeatureService {
    public static final Identifier SHIELD_ACTIVATED_EVENT = SparkWitch.id("guardian_shield_activated");

    private static final Map<UUID, UUID> SHIELD_OWNERS_BY_TARGET = new ConcurrentHashMap<>();
    private static boolean registered;

    private GuardianAngelFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackoutEffect.BEFORE.register(GuardianAngelFeatureService::beforeBlackout);
        KillPlayer.BEFORE.register(GuardianAngelFeatureService::beforeKill);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearReconnectedTarget(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearDisconnectedTarget(handler.player));
        ReplayRegistry.registerSkillFormatter(GuardianAngelRules.SKILL_ID, GuardianAngelFeatureService::formatSkillUse);
        ReplayRegistry.registerGlobalEventFormatter(
                SHIELD_ACTIVATED_EVENT,
                GuardianAngelFeatureService::formatShieldActivation
        );
    }

    public static void initializeForPromotion(ServerPlayerEntity player, Role role) {
        detachPlayer(player);
        if (!GuardianAngelRules.isGuardianAngel(role)) {
            return;
        }
        GuardianAngelPlayerComponent.KEY.get(player).initializeForPromotion();
        player.removeStatusEffect(StatusEffects.BLINDNESS);
    }

    /** Restores private ownership after reconnect without restarting either cooldown. / 重连时恢复私有归属，但不重置任一冷却。 */
    public static void resumePlayer(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (!GuardianAngelRules.isGuardianAngel(role)) {
            detachPlayer(player);
            return;
        }
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        GuardianAngelPlayerComponent component = GuardianAngelPlayerComponent.KEY.get(player);
        UUID targetUuid = component.getShieldTargetUuid();
        if (targetUuid != null) {
            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetUuid);
            if (target == null || !target.hasStatusEffect(GuardianAngelEffects.guardianShield())) {
                component.clearShieldTarget(targetUuid);
                SHIELD_OWNERS_BY_TARGET.remove(targetUuid, player.getUuid());
            } else {
                SHIELD_OWNERS_BY_TARGET.put(targetUuid, player.getUuid());
            }
        }
    }

    /** Clears owner state on role exit while an applied target effect finishes independently. / 身份退出时清除持有者状态，但已施加的目标效果独立结束。 */
    public static void detachPlayer(ServerPlayerEntity player) {
        GuardianAngelPlayerComponent component = GuardianAngelPlayerComponent.KEY.get(player);
        UUID targetUuid = component.getShieldTargetUuid();
        if (targetUuid != null) {
            SHIELD_OWNERS_BY_TARGET.put(targetUuid, player.getUuid());
        }
        component.clear();
    }

    /** Round cleanup removes both detached shields and still-owned shields. / 回合清理会移除已脱离和仍由持有者追踪的全部护盾。 */
    public static void clearRoundPlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        GuardianAngelPlayerComponent component = GuardianAngelPlayerComponent.KEY.get(player);
        UUID trackedTargetUuid = component.getShieldTargetUuid();
        if (trackedTargetUuid != null) {
            removeTargetEffect(player, trackedTargetUuid);
        }
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(SHIELD_OWNERS_BY_TARGET.entrySet())) {
            if (playerUuid.equals(entry.getValue())) {
                removeTargetEffect(player, entry.getKey());
                SHIELD_OWNERS_BY_TARGET.remove(entry.getKey(), playerUuid);
            }
        }
        if (player.hasStatusEffect(GuardianAngelEffects.guardianShield())) {
            player.removeStatusEffect(GuardianAngelEffects.guardianShield());
        }
        SHIELD_OWNERS_BY_TARGET.remove(playerUuid);
        component.clear();
    }

    /**
     * Keeps the target mapping while either endpoint is offline; the shield remains a target-owned ten-second effect.
     * 任一端离线时保留目标映射；护盾仍由目标身上的十秒效果独立维持。
     */
    static void validateShieldTarget(
            ServerPlayerEntity owner,
            GuardianAngelPlayerComponent component
    ) {
        UUID targetUuid = component.getShieldTargetUuid();
        if (targetUuid == null) {
            return;
        }
        ServerPlayerEntity target = owner.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null) {
            SHIELD_OWNERS_BY_TARGET.put(targetUuid, owner.getUuid());
            return;
        }
        if (!target.hasStatusEffect(GuardianAngelEffects.guardianShield())) {
            component.clearShieldTarget(targetUuid);
            SHIELD_OWNERS_BY_TARGET.remove(targetUuid, owner.getUuid());
            return;
        }
        SHIELD_OWNERS_BY_TARGET.put(targetUuid, owner.getUuid());
    }

    /** Packet payload is empty; all role, cooldown, and raycast decisions are recomputed here. / 数据包为空；身份、冷却与射线均在此重新判定。 */
    public static void use(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getServerWorld());
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(caster);
        GuardianAngelPlayerComponent component = GuardianAngelPlayerComponent.KEY.get(caster);
        if (!GuardianAngelRules.canUse(
                game.isRunning(),
                wraith.isActive(),
                wraith.isPromoted(),
                game.getRole(caster),
                component.getCooldownTicks()
        ) || component.hasActiveShieldTarget()) {
            return;
        }

        ServerPlayerEntity target = GuardianAngelTargeting.findAimedPlayer(caster);
        if (target == null) {
            return;
        }
        if (target.hasStatusEffect(GuardianAngelEffects.guardianShield())) {
            caster.sendMessage(Text.translatable("message.sparkwitch.guardian_angel.already_shielded"), true);
            return;
        }
        if (!GuardianAngelTargeting.isValidDirectTarget(caster, target)) {
            return;
        }

        target.addStatusEffect(new StatusEffectInstance(
                GuardianAngelEffects.guardianShield(),
                GuardianAngelRules.SHIELD_DURATION_TICKS,
                0,
                false,
                false,
                false
        ));
        if (!component.assignShield(target.getUuid())) {
            target.removeStatusEffect(GuardianAngelEffects.guardianShield());
            return;
        }
        SHIELD_OWNERS_BY_TARGET.put(target.getUuid(), caster.getUuid());
        GameRecordManager.recordSkillUse(caster, GuardianAngelRules.SKILL_ID, target, null);
    }

    private static @Nullable BlackoutEffect.BlackoutResult beforeBlackout(
            ServerPlayerEntity player,
            int durationTicks
    ) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        return GuardianAngelRules.isGuardianAngel(role) ? BlackoutEffect.BlackoutResult.cancel() : null;
    }

    private static @Nullable KillPlayer.KillResult beforeKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (!victim.hasStatusEffect(GuardianAngelEffects.guardianShield())
                || !GuardianAngelRules.shouldBlockDeath(deathReason)) {
            return null;
        }

        victim.removeStatusEffect(GuardianAngelEffects.guardianShield());
        UUID ownerUuid = findOwnerUuid(victim);
        ServerPlayerEntity owner = ownerUuid == null
                ? null
                : victim.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) {
            GuardianAngelPlayerComponent.KEY.get(owner).clearShieldTarget(victim.getUuid());
        }
        SHIELD_OWNERS_BY_TARGET.remove(victim.getUuid());

        victim.getServerWorld().playSound(
                null,
                victim.getBlockPos(),
                WatheSounds.ITEM_PSYCHO_ARMOUR,
                SoundCategory.MASTER,
                5.0F,
                1.0F
        );
        recordShieldActivation(victim, owner, ownerUuid, deathReason);
        return KillPlayer.KillResult.cancel();
    }

    private static @Nullable UUID findOwnerUuid(ServerPlayerEntity victim) {
        UUID mapped = SHIELD_OWNERS_BY_TARGET.get(victim.getUuid());
        if (mapped != null) {
            return mapped;
        }
        for (ServerPlayerEntity candidate : victim.getServer().getPlayerManager().getPlayerList()) {
            if (victim.getUuid().equals(GuardianAngelPlayerComponent.KEY.get(candidate).getShieldTargetUuid())) {
                return candidate.getUuid();
            }
        }
        return null;
    }

    private static void recordShieldActivation(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity owner,
            @Nullable UUID ownerUuid,
            Identifier deathReason
    ) {
        NbtCompound data = new NbtCompound();
        if (ownerUuid != null) {
            data.putUuid("actor", ownerUuid);
        }
        data.putUuid("target", victim.getUuid());
        data.putString("death_reason", deathReason.toString());
        GameRecordManager.recordGlobalEvent(victim.getServerWorld(), SHIELD_ACTIVATED_EVENT, owner, data);
    }

    private static void clearDisconnectedTarget(ServerPlayerEntity player) {
        if (player.hasStatusEffect(GuardianAngelEffects.guardianShield())) {
            clearTargetShield(player);
        }
    }

    private static void clearReconnectedTarget(ServerPlayerEntity player) {
        // Status-effect durations pause while offline; never let reconnect extend a ten-second shield.
        // 状态效果离线时会暂停计时；重连不得延长十秒护盾。
        if (player.hasStatusEffect(GuardianAngelEffects.guardianShield())) {
            clearTargetShield(player);
        }
    }

    private static void clearTargetShield(ServerPlayerEntity target) {
        target.removeStatusEffect(GuardianAngelEffects.guardianShield());
        UUID ownerUuid = SHIELD_OWNERS_BY_TARGET.remove(target.getUuid());
        if (ownerUuid == null) {
            return;
        }
        ServerPlayerEntity owner = target.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) {
            GuardianAngelPlayerComponent.KEY.get(owner).clearShieldTarget(target.getUuid());
        }
    }

    private static void removeTargetEffect(ServerPlayerEntity owner, UUID targetUuid) {
        ServerPlayerEntity target = owner.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            target.removeStatusEffect(GuardianAngelEffects.guardianShield());
        }
    }

    private static @Nullable Text formatSkillUse(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                "replay.skill.sparkwitch.guardian_angel.guardian",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }

    private static @Nullable Text formatShieldActivation(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        Text target = ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo);
        if (!data.containsUuid("actor")) {
            return Text.translatable("replay.global.sparkwitch.guardian_shield_activated.no_owner", target);
        }
        return Text.translatable(
                "replay.global.sparkwitch.guardian_shield_activated",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                target
        );
    }
}
