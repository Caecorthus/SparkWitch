package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: owner-approved Seeker numbers, literal ids and side-neutral pure predicates. Every duration is in
 * ticks. Other Seeker modules read these values; they never redefine them.
 * 稳定契约：已批准的搜寻者数值、字面量 id 与两端通用的纯判定。所有时长均以刻为单位。
 * 其他搜寻者模块只读取这些值，从不重新定义。
 */
public final class SeekerRules {
    // ---- Role ----
    public static final Identifier ROLE_ID = SparkWitch.id("seeker");
    public static final int COLOR = 0x5C9EFF;
    public static final Identifier VIGILANTE_ID = Identifier.of("wathe", "vigilante");

    // ---- Items and entities (item and entity-type ids share a path in different registries) ----
    public static final Identifier CAR_ITEM_ID = SparkWitch.id("seeker_car");
    public static final Identifier CAMERA_ITEM_ID = SparkWitch.id("seeker_camera");
    public static final Identifier CAR_ENTITY_ID = SparkWitch.id("seeker_car");
    public static final Identifier CAMERA_ENTITY_ID = SparkWitch.id("seeker_camera");
    /** Placed-model ids loaded through ModelLoadingPlugin. / 通过 ModelLoadingPlugin 加载的放置模型 id。 */
    public static final Identifier CAR_PLACED_MODEL_ID = SparkWitch.id("item/seeker_car_placed");
    public static final Identifier CAMERA_PLACED_MODEL_ID = SparkWitch.id("item/seeker_camera_placed");
    /**
     * The placed camera is drawn in three parts: the static mount above, a head that turns with the synced look, and
     * an LED that glows only while the owner views that camera.
     * 放置的摄像头分三部分绘制：上面的静态底座、随同步视角转动的机头，以及仅在拥有者观看该摄像头时发光的指示灯。
     */
    public static final Identifier CAMERA_HEAD_MODEL_ID = SparkWitch.id("item/seeker_camera_placed_head");
    public static final Identifier CAMERA_LED_MODEL_ID = SparkWitch.id("item/seeker_camera_placed_led");

    // ---- Payload ids (literal; the payload records alias these) ----
    public static final Identifier REMOTE_OPEN_PAYLOAD_ID = SparkWitch.id("seeker_remote_open");
    public static final Identifier REMOTE_CLOSE_PAYLOAD_ID = SparkWitch.id("seeker_remote_close");
    public static final Identifier CAR_MOVE_PAYLOAD_ID = SparkWitch.id("seeker_car_move");
    public static final Identifier CAR_CORRECT_PAYLOAD_ID = SparkWitch.id("seeker_car_correct");
    public static final Identifier CAR_SWALLOW_PAYLOAD_ID = SparkWitch.id("seeker_car_swallow");
    public static final Identifier CAR_RECALL_PAYLOAD_ID = SparkWitch.id("seeker_car_recall");
    public static final Identifier CAMERA_LOOK_PAYLOAD_ID = SparkWitch.id("seeker_camera_look");

    // ---- Sound ids (sounds.json keys are the paths) ----
    public static final Identifier CAR_MOTOR_SOUND_ID = SparkWitch.id("seeker_car_motor");
    public static final Identifier CAR_BREAK_SOUND_ID = SparkWitch.id("seeker_car_break");
    public static final Identifier CAMERA_BREAK_SOUND_ID = SparkWitch.id("seeker_camera_break");
    public static final Identifier CAR_SWALLOW_SOUND_ID = SparkWitch.id("seeker_car_swallow");
    public static final Identifier BATTERY_LOW_SOUND_ID = SparkWitch.id("seeker_battery_low");
    public static final Identifier BATTERY_DEAD_SOUND_ID = SparkWitch.id("seeker_battery_dead");

    // ---- Replay ids ----
    public static final String REPLAY_DEPLOY_ACTION = "deploy";
    public static final String REPLAY_RECALL_ACTION = "recall";
    public static final String REPLAY_REMOTE_RECALL_ACTION = "remote_recall";
    public static final String REPLAY_PLACE_ACTION = "place";
    public static final Identifier REPLAY_DEVICE_BROKEN_EVENT = SparkWitch.id("seeker_device_broken");
    public static final Identifier REPLAY_CAR_SWALLOWED_EVENT = SparkWitch.id("seeker_car_swallowed");
    public static final Identifier REPLAY_CAR_RETURNED_EVENT = SparkWitch.id("seeker_car_returned");
    public static final Identifier REPLAY_CAR_DEPLETED_EVENT = SparkWitch.id("seeker_car_depleted");
    /**
     * Replay NBT keys shared by the writer ({@code SeekerDeviceService}, WP-03) and the reader
     * ({@code SeekerReplayFormatters}, WP-08). Item uses: {@code recordItemUse(owner, CAR_ITEM_ID|CAMERA_ITEM_ID, null,
     * {action})}. Globals: {@code recordGlobalEvent(world, event, source, data)} with data {@code owner} (always),
     * {@code device} ({@link SeekerDeviceKind#id()}), {@code source} ({@link SeekerBreakSource#id()}), {@code breaker}
     * (only when attributable), {@code taotie} (swallow only).
     * 回放 NBT 键，由写入方（WP-03）与读取方（WP-08）共用。物品使用只带 {@code action}；全局事件的数据见上。
     */
    public static final String REPLAY_ACTION_KEY = "action";
    public static final String REPLAY_OWNER_KEY = "owner";
    public static final String REPLAY_DEVICE_KEY = "device";
    public static final String REPLAY_SOURCE_KEY = "source";
    public static final String REPLAY_BREAKER_KEY = "breaker";
    public static final String REPLAY_TAOTIE_KEY = "taotie";

    // ---- Shop and economy ----
    public static final String CAMERA_ENTRY_ID = "seeker_camera";
    /**
     * SparkStrength's own tablet entry id, reused on purpose so SparkStrength skips its 150 entry for the Seeker.
     * 刻意复用 SparkStrength 自己的平板条目 id，使其不再为搜寻者追加 150 的条目。
     */
    public static final String TABLET_ENTRY_ID = SparkStrengthTabletCompat.SS_TABLET_ENTRY_ID;
    public static final int CAMERA_PRICE = 150;
    public static final int TABLET_PRICE = 50;
    public static final int INITIAL_MONEY = 50;
    public static final int TASK_MONEY_REWARD = 50;

    // ---- Cooldowns on the seeker_car item (exact + max writes) ----
    public static final int INITIAL_COOLDOWN_TICKS = 60 * 20;
    /** Owner decision: any recall (physical or remote) costs the broken cooldown. / 所有者决定：任何回收都按损坏冷却计。 */
    public static final int RECALL_COOLDOWN_TICKS = 180 * 20;
    public static final int BROKEN_COOLDOWN_TICKS = 180 * 20;
    public static final int DEPLETED_COOLDOWN_TICKS = 180 * 20;
    public static final int RETURNED_COOLDOWN_TICKS = 60 * 20;

    // ---- Battery (server-authoritative, owner-only) ----
    public static final int BATTERY_MAX = 100;
    /** 1% every 10 ticks = 2%/s while the car is controlled. / 操控时每 10 刻掉 1%，即每秒 2%。 */
    public static final int BATTERY_DRAIN_INTERVAL_CONTROLLED = 10;
    /** 1% every 60 ticks = 1% per 3 s while deployed but not controlled. / 未操控时每 60 刻掉 1%，即每 3 秒 1%。 */
    public static final int BATTERY_DRAIN_INTERVAL_IDLE = 60;
    public static final int BATTERY_WARNING_PERCENT = 20;
    public static final int BATTERY_CRITICAL_PERCENT = 10;

    // ---- Mark ----
    public static final int MARK_TICKS = 200;
    public static final int MARK_COLOR = 0xFF4D4D;
    /** Above SparkWitch's 90 outlines, below skip (100), Black Raven (101) and suppression (102). / 位于 90 之上、100 之下。 */
    public static final int MARK_OUTLINE_PRIORITY = 95;
    public static final int OWN_DEVICE_COLOR = 0x3A5F99;
    public static final int CAR_INSTINCT_COLOR = 0xFFB02E;
    /**
     * The owner's own body while viewing the car or camera, owner's client only. Bright cyan: outlines are composited
     * again untinted after the view filter, so it must stand out against the green car and grey camera pictures.
     * 遥控/观看时拥有者本体的描边色，仅拥有者客户端可见。亮青色：描边会在滤镜之后以原色重新合成，需在小车绿色与摄像头灰色画面上都醒目。
     */
    public static final int OWN_BODY_COLOR = 0x5CE1FF;

    // ---- Remote view ----
    public static final int CAR_MAX_RADIUS = 32;
    public static final int CAMERA_MAX_RADIUS = 48;
    public static final int OPEN_THROTTLE_TICKS = 10;
    public static final int ATTACH_TIMEOUT_TICKS = 40;
    public static final int MOVE_TIMEOUT_TICKS = 100;
    /** Body may drift at most sqrt(2) blocks from the session anchor. / 本体距锚点最多偏离 √2 格。 */
    public static final double BODY_MOVE_TOLERANCE_SQUARED = 2.0;
    public static final int MAX_MOVES_PER_TICK = 3;
    public static final int CHEAT_REJECT_LIMIT = 20;
    public static final int CHEAT_REJECT_WINDOW_TICKS = 100;

    /**
     * Owner decision Q2: the no-sprint/no-interaction lock applies only while viewing (SESSION).
     * 所有者决定 Q2：禁止疾跑与交互仅在遥控/观看期间生效（SESSION）。
     */
    public static final LockScope LOCK_SCOPE = LockScope.SESSION;
    /**
     * Owner decision Q5-b: with SparkTraits present, an Impostor (or unknown) Seeker may not use devices or earn pay.
     * 所有者决定 Q5-b：装有 SparkTraits 时，内鬼（或无法判定）的搜寻者不能使用设备，也不获得报酬。
     */
    public static final boolean DENY_IMPOSTOR_DEVICES = true;

    // ---- Car body, movement and traversal ----
    public static final float CAR_WIDTH = 0.4F;
    public static final float CAR_HEIGHT = 0.25F;
    public static final float CAR_EYE_HEIGHT = 0.2F;
    /** Movement collision box: the car cannot slip under beds or tables (Q11). / 通行箱：小车无法钻进床底或桌底。 */
    public static final double CAR_TRAVERSAL_WIDTH = 0.4;
    public static final double CAR_TRAVERSAL_HEIGHT = 0.6;
    public static final double CAR_SPEED = 0.15;
    public static final double CAR_STRAFE_FACTOR = 0.5;
    public static final double CAR_STEP_HEIGHT = 0.6;
    public static final double CAR_GRAVITY = 0.08;
    public static final double CAR_DRAG = 0.98;
    public static final float CAR_PITCH_LIMIT = 60.0F;
    public static final double CAR_TARGET_MARGIN = 0.15;
    public static final int CAR_TRACKING_RANGE = 8;
    public static final int CAR_TRACKING_INTERVAL = 2;
    /** Render distance for device outlines (squared). / 设备及其描边的渲染距离（平方）。 */
    public static final double DEVICE_RENDER_DISTANCE_SQUARED = 64.0 * 64.0;

    // ---- Camera body and view cone ----
    public static final float CAMERA_SIZE = 0.3F;
    public static final double CAMERA_TARGET_MARGIN = 0.05;
    public static final int CAMERA_TRACKING_RANGE = 8;
    public static final int CAMERA_TRACKING_INTERVAL = 20;
    public static final double CAMERA_VIEW_OFFSET = 0.35;
    public static final float CAMERA_YAW_CONE = 70.0F;
    public static final float CAMERA_PITCH_CONE = 45.0F;
    public static final float CAMERA_FLOOR_PITCH_CENTER = -60.0F;
    public static final float CAMERA_CEILING_PITCH_CENTER = 60.0F;

    // ---- Placement ----
    public static final double DEPLOY_REACH = 3.0;
    public static final double DEPLOY_FORWARD = 0.8;
    public static final double DEPLOY_DROP = 1.5;
    public static final double CAMERA_PLACE_REACH = 4.5;
    public static final int CAMERA_FORBIDDEN_RADIUS = 1;

    // ---- Counter-play sound (S2) ----
    public static final int MOTOR_SOUND_INTERVAL_TICKS = 8;
    public static final float MOTOR_SOUND_VOLUME = 0.35F;
    /** Fixed broadcast range of the motor sound in blocks. / 电机声的固定广播半径（格）。 */
    public static final float MOTOR_SOUND_RANGE = 6.0F;

    // ---- SparkFactionAPI action ids (canAffectPlayer proxies, owner as the proxy target) ----
    public static final Identifier BREAK_ACTION_ID = SparkWitch.id("seeker_break_device");
    public static final Identifier MARK_ACTION_ID = SparkWitch.id("seeker_mark");
    public static final Identifier SWALLOW_ACTION_ID = SparkWitch.id("seeker_swallow_car");

    /** Lock scope switch (Q2). / 锁定范围开关（Q2）。 */
    public enum LockScope {
        SESSION,
        DEPLOYED
    }

    private SeekerRules() {
    }

    public static boolean isSeekerId(@Nullable Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    public static boolean isSeeker(@Nullable Role role) {
        return role != null && isSeekerId(role.identifier());
    }

    /**
     * Q7: the Seeker is drawn only when the SparkStrength tablet item exists (registry id only).
     * Q7：仅当 SparkStrength 平板物品存在时（只按注册 id）才会抽到搜寻者。
     */
    public static boolean shouldAppear() {
        return SparkStrengthTabletCompat.tabletItem().isPresent();
    }

    /**
     * True only when Wathe's gun path asks about the native Vigilante and the shooter is a Seeker.
     * 仅当 Wathe 枪械路径询问原生义警且射手是搜寻者时为真。
     */
    public static boolean countsAsNativeVigilanteForGun(@Nullable Role queried, @Nullable Role actual) {
        return queried != null && VIGILANTE_ID.equals(queried.identifier()) && isSeeker(actual);
    }

    /** Engine limit: tracking and chunks centre on the body. / 引擎限制：追踪与区块以本体为中心。 */
    public static int effectiveRadius(int maxRadius, int viewDistance) {
        int engineLimit = Math.max(0, 16 * (viewDistance - 1));
        return Math.max(0, Math.min(maxRadius, engineLimit));
    }

    public static int maxRadius(SeekerSessionMode mode) {
        return switch (mode) {
            case CAR -> CAR_MAX_RADIUS;
            case CAMERA -> CAMERA_MAX_RADIUS;
            case NONE -> 0;
        };
    }

    public static boolean withinRadius(double squaredDistance, int radius) {
        return squaredDistance >= 0.0 && radius > 0 && squaredDistance <= (double) radius * radius;
    }

    /** Ticks between 1% battery steps; controlled means the owner is driving (session mode CAR). / 每掉 1% 的间隔刻数。 */
    public static int batteryDrainInterval(boolean controlled) {
        return controlled ? BATTERY_DRAIN_INTERVAL_CONTROLLED : BATTERY_DRAIN_INTERVAL_IDLE;
    }

    public static int clampBattery(int percent) {
        return Math.max(0, Math.min(BATTERY_MAX, percent));
    }

    public static boolean isBatteryWarning(int percent) {
        return percent <= BATTERY_WARNING_PERCENT;
    }

    public static boolean isBatteryCritical(int percent) {
        return percent <= BATTERY_CRITICAL_PERCENT;
    }

    /** Max-merge: a longer existing cooldown (e.g. Saint Karma) is never shortened. / 取大：不缩短更长的既有冷却。 */
    public static int mergeCooldownTicks(int existingTicks, int requestedTicks) {
        return Math.max(Math.max(0, existingTicks), Math.max(0, requestedTicks));
    }

    /**
     * The "during" lock (Q2): SESSION locks only while viewing; DEPLOYED also while the car is out.
     * “期间”锁（Q2）：SESSION 仅在观看时锁定；DEPLOYED 在小车部署期间也锁定。
     */
    public static boolean isLocked(SeekerCarState carState, SeekerSessionMode sessionMode) {
        if (sessionMode != SeekerSessionMode.NONE) {
            return true;
        }
        return LOCK_SCOPE == LockScope.DEPLOYED && carState == SeekerCarState.DEPLOYED;
    }

    /** Q5-b fail-closed gate. / Q5-b 失败即关闭的门槛。 */
    public static boolean devicesDenied(boolean impostorOrUnknown) {
        return DENY_IMPOSTOR_DEVICES && impostorOrUnknown;
    }
}
