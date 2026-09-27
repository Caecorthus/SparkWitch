package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: the two Seeker predicates. "Owner of record" (online, exactly Seeker, game running, match id bound)
 * drives cleanup; "active participant" (plus alive, not spectator/creative, not an active Wraith) gates every action.
 * Living-spectator states (swallowed, Last Stand) only end sessions, they never clear state.
 * TODO(WP-02): implement. / 待 WP-02 实现。
 * 冻结契约：搜寻者的两个谓词。“记录中的拥有者”用于清理；“可行动的参与者”作为所有行动的门槛。
 * 活着的旁观状态（被吞噬、最后一搏）只结束会话，从不清空状态。
 */
public final class SeekerTargeting {
    private SeekerTargeting() {
    }

    public static boolean isOwnerOfRecord(@Nullable PlayerEntity player) {
        return false;
    }

    public static boolean isActiveParticipant(@Nullable PlayerEntity player) {
        return false;
    }

    /** Deploy/place gate (includes the Q5-b impostor rule). / 部署与放置门槛（含 Q5-b 内鬼规则）。 */
    public static boolean canUseDevice(ServerPlayerEntity player, ItemStack stack) {
        return false;
    }

    /** Body not swallowed, not Last-Stand pending, not kidnapper-controlled. / 本体未被吞噬、非最后一搏、未被绑架控制。 */
    public static boolean isBodyAvailable(ServerPlayerEntity player) {
        return false;
    }

    /**
     * Shared gate of {@code seeker_remote_open} and {@code seeker_car_recall} (game running, exact role, participant,
     * not feared, not skill-blocked, not stunned, not kidnapped, not impostor). Returns null when allowed, otherwise the
     * suffix of {@code message.sparkwitch.seeker.remote.denied.<suffix>}.
     * {@code seeker_remote_open} 与 {@code seeker_car_recall} 的公共门槛。允许时返回 null，
     * 否则返回 {@code message.sparkwitch.seeker.remote.denied.<suffix>} 的后缀。
     */
    @Nullable
    public static String commonDenyReason(ServerPlayerEntity player) {
        return "blocked";
    }

    /** Current match binding id, or null when no round is running. / 当前对局绑定 id；无对局时为 null。 */
    @Nullable
    public static String currentMatchId(World world) {
        return null;
    }
}
