package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** G 键四阶段状态机；服务端去抖避免网络重复包瞬间结束播放。 */
public final class MagicianAbility {
    private static final Map<UUID,Long> LAST = new ConcurrentHashMap<>();
    private MagicianAbility() {}
    public static void handle(ServerPlayerEntity player) {
        handle(player, UseMagicianAbilityC2SPacket.ADVANCE);
    }
    public static void handle(ServerPlayerEntity player, int action) {
        GameWorldComponent game=GameWorldComponent.KEY.get(player.getWorld());
        // Wathe's isRole uses Role object identity.  FactionAPI/NoellesRoles can
        // expose another Role instance with the same id after role assignment,
        // so the active ability must validate the stable identifier instead.
        var role = game.getRole(player);
        if (role == null || !SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())
                || !game.isRunning()
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        // Same server-side skill locks as the Fiend Dash: the payload guards drop the packet, and the inventory
        // buttons send START_/STOP_ actions directly, so the handler re-checks the stun, a SparkTraits silence or Last
        // Escape, and Fear itself. / 与魔人疾驰相同的服务端技能锁：数据包守卫会丢弃该包，而背包按钮直接发送开始/结束动作，
        // 因此处理器自行再次检查眩晕、SparkTraits 沉默或绝境逃生，以及恐惧。
        // The NoellesRoles silence is checked directly too, so it holds without SparkTraits (as for the Time Stealer).
        // 也直接检查 NoellesRoles 沉默，使其在没有 SparkTraits 时同样生效（与窃时者一致）。
        if (ControlExpertStun.isStunned(player)
                || NoellesSilenceBridge.isSilenced(player)
                || SparkTraitsKillerBridge.isRoleSkillBlocked(player)
                || GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        // Server tick count, not world time: it never jumps backwards across a world switch or /time set.
        // 使用服务端刻计数而非世界时间：跨世界切换或 /time set 时不会倒退。
        long tick = player.getServer().getTicks();
        UUID playerId = player.getUuid();
        Long lastAcceptedTick = LAST.get(playerId);
        // 首次请求没有历史 tick，必须直接允许通过；不能用 Long.MIN_VALUE 作为缺省值参与相减，
        // 否则 long 溢出会让差值变成负数，首次录制请求就会被永久误判为去抖窗口内的重复请求。
        if (shouldRejectDebouncedRequest(tick, lastAcceptedTick)) {
            return;
        }
        LAST.put(playerId, tick);
        MagicianPlayerComponent c=MagicianPlayerComponent.KEY.get(player);
        // A press that would start a recording or a playback while any cooldown remains (a forced one included) is
        // refused with the shared action-bar line (owner decision 2026-10-09); stopping one never waits.
        // 冷却未结束（含强制冷却）时，本应开始录制或播放的按键会被拒绝并显示通用动作栏提示（所有者 2026-10-09 决定）；
        // 结束录制或播放从不需要等待。
        if (c.cooldownTicks() > 0
                && startsRecordingOrPlayback(c.stage(), action, MagicianPlaybackManager.hasCachedRecording(player))) {
            player.sendMessage(Text.translatable("message.sparkwitch.skill.cooldown", (c.cooldownTicks() + 19) / 20),
                    true);
            return;
        }
        if (c.stage() == MagicianStage.IDLE && c.cooldownTicks() > 0) return;
        switch (action) {
            case UseMagicianAbilityC2SPacket.START_RECORDING -> {
                if (c.stage() == MagicianStage.IDLE) c.startRecording();
            }
            case UseMagicianAbilityC2SPacket.STOP_RECORDING -> {
                if (c.stage() == MagicianStage.RECORDING) c.stopRecordingEarly();
            }
            case UseMagicianAbilityC2SPacket.START_PLAYBACK -> {
                // 组件阶段可能因 CCA 读写被重置，但服务端仍保留录制快照；此时也必须允许播放。
                if (c.stage() == MagicianStage.READY_PLAYBACK
                        || MagicianPlaybackManager.hasCachedRecording(player)) c.startPlayback();
            }
            case UseMagicianAbilityC2SPacket.STOP_PLAYBACK -> {
                if (c.stage() == MagicianStage.PLAYING) MagicianPlaybackManager.stopPlaybackEarly(player);
            }
            default -> {
                switch (c.stage()) {
                    case RECORDING -> c.stopRecordingEarly();
                    case READY_PLAYBACK -> c.startPlayback();
                    case PLAYING -> MagicianPlaybackManager.stopPlaybackEarly(player);
                    case IDLE -> {
                        // 自改版 NoellesRoles 在组件阶段丢失时优先恢复缓存播放，避免重新覆盖录制数据。
                        if (MagicianPlaybackManager.hasCachedRecording(player)) c.startPlayback();
                        else c.startRecording();
                    }
                }
            }
        }
    }

    /**
     * Whether {@code action} in {@code stage} would start a recording or a playback, mirroring the switch in
     * {@link #handle(ServerPlayerEntity, int)}; only those wait for the cooldown.
     * 该动作在该阶段是否会开始录制或播放，与 {@link #handle(ServerPlayerEntity, int)} 中的分支一致；只有这些需要等待冷却。
     */
    static boolean startsRecordingOrPlayback(MagicianStage stage, int action, boolean cachedRecording) {
        return switch (action) {
            case UseMagicianAbilityC2SPacket.START_RECORDING -> stage == MagicianStage.IDLE;
            case UseMagicianAbilityC2SPacket.START_PLAYBACK -> stage == MagicianStage.READY_PLAYBACK || cachedRecording;
            case UseMagicianAbilityC2SPacket.STOP_RECORDING, UseMagicianAbilityC2SPacket.STOP_PLAYBACK -> false;
            default -> stage == MagicianStage.IDLE || stage == MagicianStage.READY_PLAYBACK;
        };
    }

    /** Drops the debounce entry (disconnect, reset). / 移除去抖记录（断线、重置）。 */
    static void forget(UUID playerId) {
        LAST.remove(playerId);
    }

    /**
     * 判断本次请求是否仍处于上一次请求的去抖窗口内。
     * 上次 tick 为 null 表示玩家尚未成功通过去抖判断，应允许首次技能请求。
     */
    static boolean shouldRejectDebouncedRequest(long currentTick, Long lastAcceptedTick) {
        return lastAcceptedTick != null
                && currentTick - lastAcceptedTick < MagicianConstants.ABILITY_DEBOUNCE_TICKS;
    }
}
