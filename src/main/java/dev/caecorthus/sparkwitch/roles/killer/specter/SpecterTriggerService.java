package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
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
 *   <li>Drains 30 sanity from a victim with an effective real bar ({@link SpecterRules#hasRealSanity}). It is a plain
 *       subtraction like {@code AbyssSuppression.drainSanity}: a bar already below 0 is never raised, and reaching
 *       Wathe's -100% floor is a normal (unattributed) mental breakdown.</li>
 *   <li>If the placer is still a living Specter who could cast skill one now and it is ready (no cooldown, no active
 *       mark), marks the victim for the normal 10 s through the shriek target gate and SparkFactionAPI. Pressing G
 *       then shrieks exactly as after a manual mark.</li>
 *   <li>Grants cowardice to living, non-Wraith participants outside the killer team (non-Conscience killers +
 *       Impostors) that have no effective real bar, and to the Savior, who otherwise never sits below 25 sanity.</li>
 * </ul>
 * 鬼魂触发器效果，服务端权威：对拥有有效真实理智条（{@link SpecterRules#hasRealSanity}）的踩中者扣除 30 理智，与
 * {@code AbyssSuppression.drainSanity} 一样是单纯相减：已低于 0 的理智绝不会被抬高，降至 Wathe 的 -100% 下限即为正常的
 * （不记功）精神崩溃。若放置者仍是存活且此刻能施放技能一的幽魂，并且技能一可用（不在冷却、没有标记），经尖啸目标门槛与
 * SparkFactionAPI 按正常 10 秒标记该玩家，之后按 G 即正常尖啸。杀手队（非善良杀手 + 内鬼）以外、没有有效真实理智条的存活
 * 非冤魂参与者获得怯懦，救世主同样获得怯懦。
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
        if (!SpecterRules.hasRealSanity(victim)) {
            return;
        }
        // Never raises a negative bar (Wathe allows -100%). / 绝不抬高已为负的理智（Wathe 允许 -100%）。
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(victim);
        mood.setMood(mood.getMood() - SpecterRules.TRIGGER_SANITY_DRAIN / 100.0F);
    }

    private static void applyMark(ServerPlayerEntity victim, @Nullable UUID ownerUuid) {
        // The Specter stepping on their own trigger never marks themselves. / 幽魂踩自己的触发器不标记自己。
        if (ownerUuid == null || ownerUuid.equals(victim.getUuid()) || victim.getServer() == null) {
            return;
        }
        ServerPlayerEntity owner = victim.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner != null) {
            SpecterSkillService.markFromTrigger(owner, victim);
        }
    }

    private static void applyCowardice(ServerPlayerEntity victim) {
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getServerWorld());
        Role role = game.getRole(victim);
        if (role == null
                || !GameFunctions.isPlayerPlayingAndAlive(victim)
                || WraithStateService.isActive(victim)
                || SpecterRules.isEffectiveKiller(victim)) {
            return;
        }
        if (!SpecterRules.hasRealSanity(victim) || isSaviorRole(role)) {
            SpecterCowardiceComponent.KEY.get(victim).grant();
        }
    }

    /**
     * Fail-closed role-id check for the Savior ({@code sparkwitch:savior}), which only exists in the still-open PR #200
     * (feat/savior-role). Matching by id keeps the Specter module free of the Savior class; while that role is not
     * registered the id never matches and nobody gains cowardice through this branch.
     * 救世主（{@code sparkwitch:savior}）的失败关闭职业 ID 判定，该职业目前只存在于仍未合并的 PR #200（feat/savior-role）。
     * 按 ID 匹配使幽魂模块不依赖救世主类；该职业未注册时 ID 永不匹配，没有人会经此分支获得怯懦。
     */
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
        // Spawned at the player's feet height; the visage keeps gravity and block collision, so it stands on the floor.
        // 在玩家脚部高度生成；幻影保留重力与方块碰撞，因此会站在地面上。
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
