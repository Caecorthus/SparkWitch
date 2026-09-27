package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Frozen contract: the only place where Seeker device state transitions happen (deploy, recall, remote recall,
 * battery depletion, place, break, swallow, return, sweep). Server only.
 * TODO(WP-03): implement every transition. / 待 WP-03 实现所有状态转移。
 * 冻结契约：搜寻者设备状态转移的唯一入口（部署、回收、远程回收、电量耗尽、放置、损坏、吞噬、归还、清扫）。仅服务端。
 */
public final class SeekerDeviceService {
    private SeekerDeviceService() {
    }

    public static void register() {
        // TODO(WP-03) / 待 WP-03 实现
    }

    /** READY car item use. / READY 状态下使用小车物品。 */
    public static TypedActionResult<ItemStack> deployCar(ServerPlayerEntity owner, Hand hand) {
        return TypedActionResult.pass(owner.getStackInHand(hand));
    }

    /** Physical right-click recall on the car; 180 s cooldown, no mark. / 右键实体回收；冷却 180 秒，不标记。 */
    public static ActionResult recallCar(ServerPlayerEntity owner, SeekerCarEntity car) {
        return ActionResult.PASS;
    }

    /**
     * Console / {@code seeker_car_recall}: recall from anywhere; 180 s cooldown, no mark. Performs the shared open
     * gate and the console-device check itself.
     * 控制台 / {@code seeker_car_recall}：任意位置远程回收；冷却 180 秒，不标记。自行执行公共门槛与控制台设备检查。
     */
    public static boolean remoteRecallCar(ServerPlayerEntity owner) {
        return false;
    }

    /**
     * Battery reached 0%: end the CAR session (BATTERY_DEPLETED), remove the car, 180 s DEPLETED cooldown, power-down
     * sound; no mark and no replay breaker.
     * 电量归零：结束小车会话（BATTERY_DEPLETED）、移除小车、写入 180 秒 DEPLETED 冷却、播放断电音；不标记、回放无损坏者。
     */
    public static boolean depleteCar(ServerPlayerEntity owner) {
        return false;
    }

    public static ActionResult placeCamera(ServerPlayerEntity owner, ItemUsageContext context) {
        return ActionResult.PASS;
    }

    /**
     * The single break sink for every source, in order: {@code SeekerRemoteSessionService.end(owner, CAR_BROKEN |
     * CAMERA_BROKEN)} → discard + {@code SeekerDeviceSounds.playBreak} → {@code component.apply(breakCar() |
     * destroyCamera())} (180 s BROKEN written by apply) → replay {@code seeker_device_broken} (keys in
     * {@code SeekerRules.REPLAY_*_KEY}; breaker only when {@code source.attributable()}) →
     * {@code SeekerMarkService.onDeviceBroken(owner, breaker, kind)} when attributable → owner message
     * {@code car.broken}/{@code camera.broken} (never names the breaker). Idempotent for an already-removed device.
     * 所有损坏来源的唯一收口，顺序：结束会话 → 移除并播放损坏音 → 应用状态转移（apply 写入 180 秒）→ 记录回放 →
     * 可归属时调用标记服务 → 通知拥有者（不说出损坏者）。对已移除的设备幂等。
     */
    public static void breakDevice(SeekerDeviceEntity device, SeekerBreakSource source,
                                   @Nullable ServerPlayerEntity breaker) {
    }

    /** Device side of a Taotie swallow (no mark, no 180 s). / 饕餮吞车的设备侧处理（不标记、不计 180 秒）。 */
    public static boolean swallowCar(SeekerCarEntity car, ServerPlayerEntity taotie) {
        return false;
    }

    /** Returns a swallowed car item (60 s RETURNED cooldown). / 归还被吞的小车物品（60 秒 RETURNED 冷却）。 */
    public static boolean returnSwallowedCar(ServerPlayerEntity owner) {
        return false;
    }

    public static void discardAllFor(MinecraftServer server, UUID owner) {
    }

    public static void sweepAll(MinecraftServer server) {
    }

    @Nullable
    public static SeekerCarEntity findCar(ServerPlayerEntity owner) {
        return null;
    }

    @Nullable
    public static SeekerCameraEntity findCamera(ServerPlayerEntity owner) {
        return null;
    }

    @Nullable
    public static ServerPlayerEntity findOwner(SeekerDeviceEntity device) {
        return null;
    }
}
