package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
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
                || !GameFunctions.isPlayerAliveAndSurvival(player)) {
            return;
        }
        long tick = player.getServerWorld().getTime();
        UUID playerId = player.getUuid();
        Long lastAcceptedTick = LAST.get(playerId);
        // 首次请求没有历史 tick，必须直接允许通过；不能用 Long.MIN_VALUE 作为缺省值参与相减，
        // 否则 long 溢出会让差值变成负数，首次录制请求就会被永久误判为去抖窗口内的重复请求。
        if (shouldRejectDebouncedRequest(tick, lastAcceptedTick)) {
            return;
        }
        LAST.put(playerId, tick);
        MagicianPlayerComponent c=MagicianPlayerComponent.KEY.get(player);
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
     * 判断本次请求是否仍处于上一次请求的去抖窗口内。
     * 上次 tick 为 null 表示玩家尚未成功通过去抖判断，应允许首次技能请求。
     */
    static boolean shouldRejectDebouncedRequest(long currentTick, Long lastAcceptedTick) {
        return lastAcceptedTick != null
                && currentTick - lastAcceptedTick < MagicianConstants.ABILITY_DEBOUNCE_TICKS;
    }
}
