package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Ghost trigger effect, server-authoritative:
 * <ul>
 *   <li>Drains 30 sanity (clamped at 0) from whoever steps on it.</li>
 *   <li>If the placer is still a living Specter whose skill one is ready (no cooldown, no active mark), marks the
 *       victim for the normal 10 s. Pressing G then shrieks exactly as after a manual mark.</li>
 *   <li>Grants cowardice unless the victim is on the killer team (non-conscience killers + impostors). Cowardice goes
 *       to fake-mood roles and to the Savior, who otherwise never sits below 25 sanity.</li>
 * </ul>
 * 鬼魂触发器效果，服务端权威：扣除踩中者 30 理智（最低 0）；若放置者仍是存活幽魂且技能一可用（不在冷却、没有标记），
 * 按正常 10 秒标记该玩家，之后按 G 即正常尖啸；除杀手队（非善良杀手 + 内鬼）外，虚假理智职业与救世主获得怯懦。
 */
public final class SpecterTriggerService {
    private SpecterTriggerService() {
    }

    public static void applyTrigger(ServerPlayerEntity victim, @Nullable UUID ownerUuid) {
        drainSanity(victim);
        applyMark(victim, ownerUuid);
        applyCowardice(victim);
        applyHallucination(victim);
    }

    private static void drainSanity(ServerPlayerEntity victim) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(victim);
        float sanity = mood.getMood() * 100.0F;
        mood.setMood(Math.max(0.0F, sanity - SpecterRules.TRIGGER_SANITY_DRAIN) / 100.0F);
    }

    private static void applyMark(ServerPlayerEntity victim, @Nullable UUID ownerUuid) {
        // The Specter stepping on their own trigger never marks themselves. / 幽魂踩自己的触发器不标记自己。
        if (ownerUuid == null || ownerUuid.equals(victim.getUuid()) || victim.getServer() == null) {
            return;
        }
        ServerPlayerEntity owner = victim.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner == null) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getServerWorld());
        if (!SpecterRules.isSpecter(game.getRole(owner))
                || !GameFunctions.isPlayerPlayingAndAlive(owner)
                || GameFunctions.isPlayerSpectatingOrCreative(owner)) {
            return;
        }
        SpecterPlayerComponent component = SpecterPlayerComponent.KEY.get(owner);
        if (component.isOnCooldown() || component.hasActiveMark()) {
            return;
        }
        component.mark(victim.getUuid());
        GameRecordManager.recordSkillUse(owner, SpecterRules.MARK_SKILL_ID, victim, null);
    }

    private static void applyCowardice(ServerPlayerEntity victim) {
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getServerWorld());
        Role role = game.getRole(victim);
        if (role == null
                || FactionIds.KILLER.equals(SparkFactionApi.resolveEffectiveFaction(victim, game))) {
            return;
        }
        if (role.getMoodType() != Role.MoodType.REAL || isSaviorRole(role)) {
            SpecterCowardiceComponent.KEY.get(victim).grant();
        }
    }

    /** Role-id check keeps the Specter module independent of the Savior class. / 用职业 ID 判断，避免依赖救世主类。 */
    private static boolean isSaviorRole(@Nullable Role role) {
        return role != null && Identifier.of("sparkwitch", "savior").equals(role.identifier());
    }

    /** Two seconds of blindness and Slowness II, a private cave sound, and the floating visage in front. / 2 秒失明与缓慢 II、仅本人可闻的洞穴音、面前浮空幻影。 */
    private static void applyHallucination(ServerPlayerEntity victim) {
        victim.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS,
                GhostVisageEntity.LIFETIME_TICKS,
                0,
                true,
                false,
                true
        ));
        victim.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                GhostVisageEntity.LIFETIME_TICKS,
                1,
                true,
                false,
                true
        ));
        // AMBIENT_CAVE picks one of the vanilla cave sounds at random. Player-only packet. / 随机洞穴音，仅本人收包。
        victim.playSoundToPlayer(SoundEvents.AMBIENT_CAVE.value(), SoundCategory.AMBIENT, 1.0F, 1.0F);
        spawnVisage(victim);
    }

    private static void spawnVisage(ServerPlayerEntity victim) {
        ServerWorld world = victim.getServerWorld();
        float yaw = victim.getYaw();
        double x = victim.getX() - Math.sin(Math.toRadians(yaw)) * 2.0D;
        double z = victim.getZ() + Math.cos(Math.toRadians(yaw)) * 2.0D;
        GhostVisageEntity visage = new GhostVisageEntity(SpecterEntities.ghostVisage(), world);
        visage.setViewer(victim);
        // Same height as the player; the entity itself has no gravity, so it stays in place without flying up.
        // 与玩家同高；实体本身无重力，所以停在原地而不是真的飞起来。
        visage.refreshPositionAndAngles(
                x,
                victim.getY(),
                z,
                GhostVisageEntity.facingYaw(yaw),
                0.0F
        );
        world.spawnEntity(visage);
    }
}
