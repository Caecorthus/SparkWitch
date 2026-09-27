package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe replay lines for Seeker item uses and global events. Wathe drops item uses without a registered formatter and
 * prints a raw key for unformatted global events, so every id the Seeker records ({@code SeekerDeviceService}, WP-03)
 * is registered here. The data keys are the frozen {@link SeekerRules} {@code REPLAY_*_KEY} constants (contract §4b).
 * The breaker is named only in the post-round replay (never live); remote sessions and marks are never recorded.
 * The break source is recorded for tooling but not rendered. Malformed records return {@code null}, which Wathe skips.
 * 搜寻者道具使用与全局事件的 Wathe 回放文本。Wathe 会丢弃未注册格式化器的道具使用，并对未注册的全局事件直接输出原始键，
 * 因此搜寻者记录的每个 id（由 WP-03 的 {@code SeekerDeviceService} 写入）都在此注册。数据键为 {@link SeekerRules}
 * 中冻结的 {@code REPLAY_*_KEY} 常量（契约 §4b）。损坏者只在赛后回放中写出（从不实时公开）；遥控会话与标记从不记录。
 * 损坏来源会被记录但不渲染。数据不完整的记录返回 {@code null}，Wathe 会跳过该行。
 */
public final class SeekerReplayFormatters {
    static final String CAR_DEPLOY_KEY = "replay.item_use.sparkwitch.seeker_car.deploy";
    static final String CAR_RECALL_KEY = "replay.item_use.sparkwitch.seeker_car.recall";
    static final String CAR_REMOTE_RECALL_KEY = "replay.item_use.sparkwitch.seeker_car.remote_recall";
    static final String CAMERA_PLACE_KEY = "replay.item_use.sparkwitch.seeker_camera.place";
    static final String CAR_BROKEN_KEY = "replay.global.sparkwitch.seeker_device_broken.car";
    static final String CAMERA_BROKEN_KEY = "replay.global.sparkwitch.seeker_device_broken.camera";
    static final String CAR_BROKEN_UNKNOWN_KEY = "replay.global.sparkwitch.seeker_device_broken.car.unknown";
    static final String CAMERA_BROKEN_UNKNOWN_KEY = "replay.global.sparkwitch.seeker_device_broken.camera.unknown";
    static final String CAR_SWALLOWED_KEY = "replay.global.sparkwitch.seeker_car_swallowed";
    static final String CAR_RETURNED_KEY = "replay.global.sparkwitch.seeker_car_returned";
    static final String CAR_DEPLETED_KEY = "replay.global.sparkwitch.seeker_car_depleted";
    /**
     * Wathe's own key for the recording player ({@code GameRecordManager#addEvent}); only item uses rely on it.
     * Wathe 自身写入的记录玩家键；只有道具使用依赖它。
     */
    static final String WATHE_ACTOR_KEY = "actor";

    private static boolean registered;

    private SeekerReplayFormatters() {
    }

    /**
     * Registers every Seeker formatter once; called only from {@link SeekerFeatureService#register()}.
     * 只注册一次全部搜寻者格式化器；仅由 {@link SeekerFeatureService#register()} 调用。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(SeekerRules.CAR_ITEM_ID, SeekerReplayFormatters::formatCar);
        ReplayRegistry.registerItemUseFormatter(SeekerRules.CAMERA_ITEM_ID, SeekerReplayFormatters::formatCamera);
        ReplayRegistry.registerGlobalEventFormatter(SeekerRules.REPLAY_DEVICE_BROKEN_EVENT,
                SeekerReplayFormatters::formatDeviceBroken);
        ReplayRegistry.registerGlobalEventFormatter(SeekerRules.REPLAY_CAR_SWALLOWED_EVENT,
                SeekerReplayFormatters::formatCarSwallowed);
        ReplayRegistry.registerGlobalEventFormatter(SeekerRules.REPLAY_CAR_RETURNED_EVENT,
                SeekerReplayFormatters::formatCarReturned);
        ReplayRegistry.registerGlobalEventFormatter(SeekerRules.REPLAY_CAR_DEPLETED_EVENT,
                SeekerReplayFormatters::formatCarDepleted);
    }

    /** {@code seeker_car}: deploy / recall / remote_recall, actor = owner. / 小车：部署、回收、远程回收。 */
    static @Nullable Text formatCar(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                    @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        String key = switch (data.getString(SeekerRules.REPLAY_ACTION_KEY)) {
            case SeekerRules.REPLAY_DEPLOY_ACTION -> CAR_DEPLOY_KEY;
            case SeekerRules.REPLAY_RECALL_ACTION -> CAR_RECALL_KEY;
            case SeekerRules.REPLAY_REMOTE_RECALL_ACTION -> CAR_REMOTE_RECALL_KEY;
            default -> null;
        };
        return translateOne(key, data, WATHE_ACTOR_KEY, match);
    }

    /** {@code seeker_camera}: place, actor = owner. / 摄像头：安装。 */
    static @Nullable Text formatCamera(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                       @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        String key = SeekerRules.REPLAY_PLACE_ACTION.equals(data.getString(SeekerRules.REPLAY_ACTION_KEY))
                ? CAMERA_PLACE_KEY
                : null;
        return translateOne(key, data, WATHE_ACTOR_KEY, match);
    }

    /**
     * Named form (breaker, owner) when an attributable breaker was recorded, otherwise the unknown form (owner).
     * 记录了可归属的损坏者时使用具名形式（损坏者、拥有者），否则使用未知形式（拥有者）。
     */
    static @Nullable Text formatDeviceBroken(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                             @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid(SeekerRules.REPLAY_OWNER_KEY)) {
            return null;
        }
        String device = data.getString(SeekerRules.REPLAY_DEVICE_KEY);
        boolean car = SeekerDeviceKind.CAR.id().equals(device);
        if (!car && !SeekerDeviceKind.CAMERA.id().equals(device)) {
            return null;
        }
        Map<UUID, ReplayGenerator.PlayerInfo> players = ReplayGenerator.getPlayerInfoCache(match);
        Text owner = ReplayGenerator.formatPlayerName(data.getUuid(SeekerRules.REPLAY_OWNER_KEY), players);
        if (data.containsUuid(SeekerRules.REPLAY_BREAKER_KEY)) {
            Text breaker = ReplayGenerator.formatPlayerName(data.getUuid(SeekerRules.REPLAY_BREAKER_KEY), players);
            return Text.translatable(car ? CAR_BROKEN_KEY : CAMERA_BROKEN_KEY, breaker, owner);
        }
        return Text.translatable(car ? CAR_BROKEN_UNKNOWN_KEY : CAMERA_BROKEN_UNKNOWN_KEY, owner);
    }

    /** (owner, taotie); both are required. / （拥有者、饕餮）；两者都必须存在。 */
    static @Nullable Text formatCarSwallowed(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                             @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid(SeekerRules.REPLAY_OWNER_KEY) || !data.containsUuid(SeekerRules.REPLAY_TAOTIE_KEY)) {
            return null;
        }
        Map<UUID, ReplayGenerator.PlayerInfo> players = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(CAR_SWALLOWED_KEY,
                ReplayGenerator.formatPlayerName(data.getUuid(SeekerRules.REPLAY_OWNER_KEY), players),
                ReplayGenerator.formatPlayerName(data.getUuid(SeekerRules.REPLAY_TAOTIE_KEY), players));
    }

    /** (owner). / （拥有者）。 */
    static @Nullable Text formatCarReturned(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                            @Nullable ServerWorld world) {
        return translateOne(CAR_RETURNED_KEY, event.data(), SeekerRules.REPLAY_OWNER_KEY, match);
    }

    /** (owner); battery depletion names no breaker. / （拥有者）；电量耗尽不写出任何损坏者。 */
    static @Nullable Text formatCarDepleted(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                            @Nullable ServerWorld world) {
        return translateOne(CAR_DEPLETED_KEY, event.data(), SeekerRules.REPLAY_OWNER_KEY, match);
    }

    private static @Nullable Text translateOne(@Nullable String key, NbtCompound data, String playerKey,
                                               GameRecordManager.MatchRecord match) {
        if (key == null || !data.containsUuid(playerKey)) {
            return null;
        }
        Text player = ReplayGenerator.formatPlayerName(data.getUuid(playerKey),
                ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(key, player);
    }
}
