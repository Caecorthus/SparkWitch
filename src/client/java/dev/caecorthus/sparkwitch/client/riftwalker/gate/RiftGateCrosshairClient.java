package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftGateUsers;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRemoverItem;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Client side of the melee pass-through ({@link RiftGateCrosshairRules}), fed by {@code RiftGateCrosshairMixin}
 * (crosshair predicate) and {@code RiftGateAttackMixin} (left-click). Presentation/input only: the server never
 * re-checks melee targets by raycast, so retargeting here is enough, and gate entry stays server-validated. The local
 * player is classified by RAW role through {@link RiftGateUsers#classify} (the same helper the server uses); any
 * failure reads as "not a gate user", so gates are then simply ignored. Client render/main thread only.
 * 近战穿门的客户端部分（{@link RiftGateCrosshairRules}），由 {@code RiftGateCrosshairMixin}（准星判定）与
 * {@code RiftGateAttackMixin}（左键）调用。只负责表现与输入：服务端从不以射线复核近战目标，因此在此改选目标即可，进门仍由
 * 服务端校验。本地玩家按原始职业经 {@link RiftGateUsers#classify}（与服务端相同的辅助方法）分类；任何失败都视为「非门使用者」，
 * 此时准星直接忽略门。仅客户端渲染/主线程。
 */
public final class RiftGateCrosshairClient {
    /** Depth of the gate-free re-pick (render thread only). / 忽略门重选的嵌套深度（仅渲染线程）。 */
    private static int ignoringGatesDepth;

    private RiftGateCrosshairClient() {
    }

    /** Wraps vanilla's crosshair entity predicate; gates are dropped unless the rules keep them. / 包装原版准星实体判定。 */
    public static Predicate<Entity> filterCrosshair(Entity camera, Predicate<Entity> predicate) {
        PlayerEntity local = MinecraftClient.getInstance().player;
        boolean cameraIsLocal = camera != null && camera == local;
        boolean ignoring = ignoringGatesDepth > 0;
        // Classify only when it can matter (own view, not re-picking). A Rift Gate Remover ready in the clicking hand
        // also keeps gates targetable, so an operator's right-click lands on the gate rather than on a door or block
        // behind it.
        // 仅在可能有影响时才分类。传送门清除工具在右键所用的手里时也保留门，使管理员的右键落在门上，而不是门后的门或方块上。
        boolean user = cameraIsLocal && !ignoring
                && (RiftGateRemoverItem.isReadyIn(local) || isLocalGateUser(local));
        if (RiftGateCrosshairRules.keepsGatesTargetable(cameraIsLocal, ignoring, user)) {
            return predicate;
        }
        return candidate -> !(candidate instanceof RiftGateEntity) && predicate.test(candidate);
    }

    /**
     * The entity a left-click should hit, or null to swallow it: a non-gate target is returned unchanged; a gate is
     * replaced by the gate-free re-pick's entity (never a gate), or null when nothing stands behind it.
     * 左键应攻击的实体，返回 null 表示吞掉：非门目标原样返回；门会被替换为忽略门重选到的实体（绝不是门），门后无实体时为 null。
     */
    @Nullable
    public static Entity attackTarget(MinecraftClient client, Entity target) {
        boolean aimedAtGate = target instanceof RiftGateEntity;
        Entity behind = null;
        if (aimedAtGate && client.gameRenderer instanceof RiftGateCrosshairAccess access) {
            HitResult repick = access.sparkwitch$pickIgnoringGates();
            behind = repick instanceof EntityHitResult hit ? hit.getEntity() : null;
        }
        return switch (RiftGateCrosshairRules.attackOutcome(aimedAtGate, behind != null,
                behind instanceof RiftGateEntity)) {
            case ATTACK_AIMED -> target;
            case ATTACK_BEHIND -> behind;
            case SWALLOW -> null;
        };
    }

    /** Runs {@code pick} with gates removed from the crosshair predicate. / 在准星判定忽略门的情况下执行 {@code pick}。 */
    @Nullable
    public static HitResult ignoringGates(Supplier<HitResult> pick) {
        ignoringGatesDepth++;
        try {
            return pick.get();
        } finally {
            ignoringGatesDepth--;
        }
    }

    private static boolean isLocalGateUser(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        try {
            return RiftGateCrosshairRules.isLocalGateUser(RiftGateUsers.classify(player),
                    GameFunctions.isPlayerPlayingAndAlive(player), GameFunctions.isPlayerAliveAndSurvival(player),
                    WraithClientState.isActive(player));
        } catch (RuntimeException | LinkageError failure) {
            // Fail safe: an unknown viewer never keeps gates in the crosshair. / 失败即安全：未知观察者的准星忽略门。
            return false;
        }
    }
}
