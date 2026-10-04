package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkFactionSecondRowCompat;
import dev.caecorthus.sparkwitch.compat.SparkStrengthTabletCompat;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.console.SeekerConsoleDevices;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerMarkService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Frozen contract: the only place where Seeker device state transitions happen (deploy, recall, remote recall,
 * battery depletion, place, break, swallow, return, sweep). Server only. Cameras are unlimited; each is tracked by its
 * own entity id. Every transition that can close a session first calls {@code SeekerRemoteSessionService.end} (only
 * for the session that shows the affected device, for a camera that very camera), then changes the world, then hands
 * the pure transition to {@code SeekerStatusComponent#apply} (the only cooldown writer), then records replay and
 * messages the owner. Owner messages never name a breaker or a Taotie.
 * 冻结契约：搜寻者设备状态转移的唯一入口（部署、回收、远程回收、电量耗尽、放置、损坏、吞噬、归还、清扫）。仅服务端。
 * 摄像头数量不限，每台按自身实体 id 追踪。每个可能结束会话的转移都先调用 {@code SeekerRemoteSessionService.end}
 * （仅针对正在显示该设备的会话，摄像头即正在显示那一台的会话），
 * 再改动世界，再把纯状态转移交给 {@code SeekerStatusComponent#apply}（唯一的冷却写入方），最后记录回放并通知拥有者。
 * 给拥有者的消息从不说出损坏者或饕餮的名字。
 */
public final class SeekerDeviceService {
    static final String DEVICE_BLOCKED = "message.sparkwitch.seeker.device.blocked";
    static final String CAR_USE_TABLET = "message.sparkwitch.seeker.car.use_tablet";
    static final String CAR_NO_SPACE = "message.sparkwitch.seeker.car.no_space";
    static final String CAR_DEPLOYED = "message.sparkwitch.seeker.car.deployed";
    static final String CAR_RECALLED = "message.sparkwitch.seeker.car.recalled";
    static final String CAR_BROKEN = "message.sparkwitch.seeker.car.broken";
    static final String CAR_DEPLETED = "message.sparkwitch.seeker.car.depleted";
    static final String CAR_SWALLOWED = "message.sparkwitch.seeker.car.swallowed";
    static final String CAR_RETURNED = "message.sparkwitch.seeker.car.returned";
    static final String CAR_INVENTORY_FULL = "message.sparkwitch.seeker.car.inventory_full";
    static final String CAR_NOT_DEPLOYED = "message.sparkwitch.seeker.car.not_deployed";
    static final String CAMERA_PLACED = "message.sparkwitch.seeker.camera.placed";
    static final String CAMERA_INVALID_SPOT = "message.sparkwitch.seeker.camera.invalid_spot";
    static final String CAMERA_BROKEN = "message.sparkwitch.seeker.camera.broken";
    static final String TAOTIE_CAR_SWALLOWED = "message.sparkwitch.seeker.taotie.car_swallowed";
    static final String REMOTE_DENIED_PREFIX = "message.sparkwitch.seeker.remote.denied.";
    static final String REMOTE_DENIED_NO_TABLET = "message.sparkwitch.seeker.remote.denied.no_tablet";
    /** Inventory index of the offhand slot in {@link PlayerInventory#getStack(int)}. / 副手槽的背包索引。 */
    static final int OFFHAND_INDEX = PlayerInventory.OFF_HAND_SLOT;

    /**
     * Server-thread only, never saved: the inventory index the swallowed car item came from, so it returns there.
     * 仅服务端线程，从不存盘：被吞小车物品原先所在的背包索引，归还时优先放回该处。
     */
    private static final Map<UUID, Integer> SWALLOWED_SLOTS = new HashMap<>();
    private static boolean registered;

    private SeekerDeviceService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SWALLOWED_SLOTS.clear());
    }

    // ---- Car: deploy, recall, deplete ----

    /** READY car item use. / READY 状态下使用小车物品。 */
    public static TypedActionResult<ItemStack> deployCar(ServerPlayerEntity owner, Hand hand) {
        ItemStack stack = owner.getStackInHand(hand);
        if (!(stack.getItem() instanceof SeekerCarItem)) {
            return TypedActionResult.pass(stack);
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || status.carState() != SeekerCarState.READY
                || status.sessionMode() != SeekerSessionMode.NONE
                || owner.getItemCooldownManager().isCoolingDown(stack.getItem())
                || !SeekerTargeting.canUseDevice(owner, stack)) {
            actionBar(owner, DEVICE_BLOCKED);
            return TypedActionResult.fail(stack);
        }
        ServerWorld world = owner.getServerWorld();
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        Vec3d spot = findCarSpawn(world, owner, playArea);
        if (spot == null) {
            actionBar(owner, CAR_NO_SPACE);
            return TypedActionResult.fail(stack);
        }
        SeekerCarEntity car = SeekerEntities.car().create(world);
        if (car == null) {
            return TypedActionResult.fail(stack);
        }
        car.refreshPositionAndAngles(spot.x, spot.y, spot.z, owner.getYaw(), 0.0F);
        car.setOwner(owner.getUuid(), SeekerTargeting.currentMatchId(world));
        if (!world.spawnEntity(car)) {
            return TypedActionResult.fail(stack);
        }
        status.apply(status.state().deploy(car.getId()));
        if (!isReferenced(status, car)) {
            car.discard();
            actionBar(owner, DEVICE_BLOCKED);
            return TypedActionResult.fail(stack);
        }
        recordCarAction(owner, SeekerRules.REPLAY_DEPLOY_ACTION);
        actionBar(owner, CAR_DEPLOYED);
        // CONSUME: no swing; the car item stays in its slot as the "garage". / 不挥手；小车物品留在槽位中作为“车库”。
        return TypedActionResult.consume(stack);
    }

    /**
     * DEPLOYED car item use on the server. With SparkStrength the owner is pointed at the tablet; without it the
     * client-side console opener (WP-11) handles the car item as the console, so the server only refuses.
     * 服务端处理 DEPLOYED 时的小车物品使用。装有 SparkStrength 时提示改用平板；未装时由客户端控制台打开器（WP-11）
     * 把小车物品当作控制台处理，因此服务端只拒绝。
     */
    public static TypedActionResult<ItemStack> useDeployedCarItem(ServerPlayerEntity owner, Hand hand) {
        ItemStack stack = owner.getStackInHand(hand);
        if (SparkStrengthTabletCompat.isAvailable()) {
            actionBar(owner, CAR_USE_TABLET);
        }
        return TypedActionResult.fail(stack);
    }

    /** Physical right-click recall on the car; 180 s cooldown, no mark. / 右键实体回收；冷却 180 秒，不标记。 */
    public static ActionResult recallCar(ServerPlayerEntity owner, SeekerCarEntity car) {
        if (car.isRemoved() || !car.isOwnedBy(owner.getUuid())) {
            return ActionResult.PASS;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || !isReferenced(status, car)) {
            return ActionResult.PASS;
        }
        if (status.sessionMode() != SeekerSessionMode.NONE || !SeekerTargeting.isActiveParticipant(owner)) {
            actionBar(owner, DEVICE_BLOCKED);
            return ActionResult.FAIL;
        }
        recall(owner, status, car, SeekerRules.REPLAY_RECALL_ACTION);
        return ActionResult.SUCCESS;
    }

    /**
     * Console / {@code seeker_car_recall}: recall from anywhere; 180 s cooldown, no mark. Performs the shared open
     * gate and the console-device check itself.
     * 控制台 / {@code seeker_car_recall}：任意位置远程回收；冷却 180 秒，不标记。自行执行公共门槛与控制台设备检查。
     */
    public static boolean remoteRecallCar(ServerPlayerEntity owner) {
        if (owner == null) {
            return false;
        }
        String denied = SeekerTargeting.commonDenyReason(owner);
        if (denied != null) {
            actionBar(owner, REMOTE_DENIED_PREFIX + denied);
            return false;
        }
        if (!SeekerConsoleDevices.hasConsoleDevice(owner)) {
            actionBar(owner, REMOTE_DENIED_NO_TABLET);
            return false;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || status.carState() != SeekerCarState.DEPLOYED) {
            actionBar(owner, CAR_NOT_DEPLOYED);
            return false;
        }
        recall(owner, status, findCar(owner), SeekerRules.REPLAY_REMOTE_RECALL_ACTION);
        return true;
    }

    private static void recall(ServerPlayerEntity owner, SeekerStatusComponent status, @Nullable SeekerCarEntity car,
                               String replayAction) {
        endSessionShowing(owner, status, SeekerDeviceKind.CAR, status.carEntityId(), SeekerExitReason.CAR_RECALLED);
        if (car != null) {
            car.discard();
        }
        status.apply(status.state().recall());
        recordCarAction(owner, replayAction);
        actionBar(owner, CAR_RECALLED);
    }

    /**
     * Battery reached 0%: end the CAR session (BATTERY_DEPLETED), remove the car, 180 s DEPLETED cooldown, power-down
     * sound; no mark and no replay breaker.
     * 电量归零：结束小车会话（BATTERY_DEPLETED）、移除小车、写入 180 秒 DEPLETED 冷却、播放断电音；不标记、回放无损坏者。
     */
    public static boolean depleteCar(ServerPlayerEntity owner) {
        SeekerStatusComponent status = owner == null ? null : SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || status.carState() != SeekerCarState.DEPLOYED) {
            return false;
        }
        SeekerCarEntity car = findCar(owner);
        endSessionShowing(owner, status, SeekerDeviceKind.CAR, status.carEntityId(), SeekerExitReason.BATTERY_DEPLETED);
        if (car != null) {
            SeekerDeviceSounds.playBatteryDead(car);
            car.discard();
        }
        status.apply(status.state().deplete());
        GameRecordManager.recordGlobalEvent(owner.getServerWorld(), SeekerRules.REPLAY_CAR_DEPLETED_EVENT, owner,
                ownerData(owner.getUuid()));
        chat(owner, CAR_DEPLETED);
        return true;
    }

    // ---- Camera ----

    public static ActionResult placeCamera(ServerPlayerEntity owner, ItemUsageContext context) {
        ItemStack stack = context.getStack();
        if (!(stack.getItem() instanceof SeekerCameraItem)) {
            return ActionResult.PASS;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || status.sessionMode() != SeekerSessionMode.NONE
                || !SeekerTargeting.canUseDevice(owner, stack)) {
            actionBar(owner, DEVICE_BLOCKED);
            return ActionResult.FAIL;
        }
        ServerWorld world = owner.getServerWorld();
        Direction face = context.getSide();
        BlockPos support = context.getBlockPos();
        Vec3d position = SeekerPlacementRules.cameraEntityPos(context.getHitPos(), support, face);
        if (!isValidCameraSpot(world, owner, context.getHitPos(), support, face, position)) {
            actionBar(owner, CAMERA_INVALID_SPOT);
            return ActionResult.FAIL;
        }
        SeekerCameraEntity camera = SeekerEntities.camera().create(world);
        if (camera == null) {
            return ActionResult.FAIL;
        }
        float yaw = SeekerPlacementRules.cameraMountYaw(face, owner.getYaw());
        camera.refreshPositionAndAngles(position.x, position.y, position.z, yaw, 0.0F);
        camera.setMount(face, yaw);
        camera.setOwner(owner.getUuid(), SeekerTargeting.currentMatchId(world));
        if (!world.spawnEntity(camera)) {
            return ActionResult.FAIL;
        }
        status.apply(status.state().placeCamera(camera.getId()));
        if (!isReferenced(status, camera)) {
            camera.discard();
            actionBar(owner, DEVICE_BLOCKED);
            return ActionResult.FAIL;
        }
        stack.decrementUnlessCreative(1, owner);
        NbtCompound extra = new NbtCompound();
        extra.putString(SeekerRules.REPLAY_ACTION_KEY, SeekerRules.REPLAY_PLACE_ACTION);
        GameRecordManager.recordItemUse(owner, SeekerRules.CAMERA_ITEM_ID, null, extra);
        actionBar(owner, CAMERA_PLACED);
        return ActionResult.CONSUME;
    }

    private static boolean isValidCameraSpot(ServerWorld world, ServerPlayerEntity owner, Vec3d hit, BlockPos support,
                                             Direction face, Vec3d position) {
        Vec3d eye = owner.getEyePos();
        if (!SeekerPlacementRules.withinReach(eye, hit, SeekerRules.CAMERA_PLACE_REACH)
                || !SeekerPlacementRules.isOutwardOf(eye, support, face)
                || !seesFace(world, owner, eye, hit, support, face)) {
            return false;
        }
        if (!Block.isFaceFullSquare(world.getBlockState(support).getCollisionShape(world, support), face)) {
            return false;
        }
        Box box = SeekerPlacementRules.cameraBox(position);
        Box playArea = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        if (!SeekerPlacementRules.withinPlayArea(playArea, box.getCenter())
                || !world.isSpaceEmpty(box.contract(SeekerPlacementRules.CONTACT_EPSILON))
                || !world.getEntitiesByClass(SeekerDeviceEntity.class, box, Entity::isAlive).isEmpty()) {
            return false;
        }
        for (BlockPos near : SeekerPlacementRules.forbiddenScan(BlockPos.ofFloored(box.getCenter()))) {
            if (SeekerPlacementRules.isForbiddenNeighbour(world.getBlockState(near))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Server replay of the client crosshair (vanilla only checks reach, not which face is visible): an OUTLINE ray
     * from the eye through the reported hit must first meet {@code support} on {@code face}. This stops a modified
     * client from mounting a camera on the far side of a wall.
     * 服务端复现客户端准星（原版只校验距离，不校验可见面）：从眼睛穿过上报命中点的 OUTLINE 射线必须首先命中
     * {@code support} 的 {@code face} 面，防止改过的客户端把摄像头装到墙的另一侧。
     */
    private static boolean seesFace(ServerWorld world, ServerPlayerEntity owner, Vec3d eye, Vec3d hit,
                                    BlockPos support, Direction face) {
        BlockHitResult sight = world.raycast(new RaycastContext(eye, SeekerPlacementRules.sightProbeEnd(eye, hit),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, owner));
        return sight.getType() == HitResult.Type.BLOCK && sight.getBlockPos().equals(support)
                && sight.getSide() == face;
    }

    // ---- Break ----

    /**
     * The single break sink for every source, in order: {@code SeekerRemoteSessionService.end(owner, CAR_BROKEN |
     * CAMERA_BROKEN)} (a camera session only when it shows this camera) → discard +
     * {@code SeekerDeviceSounds.playBreak} → {@code component.apply(breakCar() | destroyCamera(id))} (180 s BROKEN
     * written by apply) → replay {@code seeker_device_broken} (keys in
     * {@code SeekerRules.REPLAY_*_KEY}; breaker only when {@code source.attributable()}) →
     * {@code SeekerMarkService.onDeviceBroken(owner, breaker, kind)} when attributable → owner message
     * {@code car.broken}/{@code camera.broken} (never names the breaker). Idempotent for an already-removed device;
     * a device its owner no longer references (orphan) is only removed.
     * 所有损坏来源的唯一收口，顺序：结束会话（摄像头会话仅在显示这台摄像头时）→ 移除并播放损坏音 →
     * 应用状态转移（apply 写入 180 秒）→ 记录回放 → 可归属时调用标记服务 → 通知拥有者（不说出损坏者）。对已移除的设备幂等；拥有者不再引用的孤儿设备只会被移除。
     */
    public static void breakDevice(SeekerDeviceEntity device, SeekerBreakSource source,
                                   @Nullable ServerPlayerEntity breaker) {
        if (device == null || source == null || device.isRemoved()
                || !(device.getWorld() instanceof ServerWorld world)) {
            return;
        }
        SeekerDeviceKind kind = device.kind();
        ServerPlayerEntity owner = findOwner(device);
        SeekerStatusComponent status = owner == null ? null : SeekerStatusComponent.KEY.getNullable(owner);
        boolean referenced = status != null && isReferenced(status, device);
        if (referenced) {
            endSessionShowing(owner, status, kind, device.getId(), breakExitReason(kind));
        }
        SeekerDeviceSounds.playBreak(device);
        device.discard();
        if (!referenced) {
            return;
        }
        status.apply(kind == SeekerDeviceKind.CAR
                ? status.state().breakCar()
                : status.state().destroyCamera(device.getId()));
        ServerPlayerEntity recordedBreaker = source.attributable() ? breaker : null;
        GameRecordManager.recordGlobalEvent(world, SeekerRules.REPLAY_DEVICE_BROKEN_EVENT, recordedBreaker,
                brokenReplayData(owner.getUuid(), kind, source,
                        recordedBreaker == null ? null : recordedBreaker.getUuid()));
        if (source.attributable()) {
            SeekerMarkService.onDeviceBroken(owner, recordedBreaker, kind);
        }
        chat(owner, brokenMessageKey(kind));
    }

    // ---- Taotie ----

    /** Device side of a Taotie swallow (no mark, no 180 s). / 饕餮吞车的设备侧处理（不标记、不计 180 秒）。 */
    public static boolean swallowCar(SeekerCarEntity car, ServerPlayerEntity taotie) {
        if (car == null || taotie == null || car.isRemoved()) {
            return false;
        }
        ServerPlayerEntity owner = findOwner(car);
        SeekerStatusComponent status = owner == null ? null : SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || !isReferenced(status, car)) {
            return false;
        }
        endSessionShowing(owner, status, SeekerDeviceKind.CAR, car.getId(), SeekerExitReason.CAR_SWALLOWED);
        SeekerDeviceSounds.playSwallow(car);
        car.discard();
        int slot = removeCarItems(owner);
        if (slot >= 0) {
            SWALLOWED_SLOTS.put(owner.getUuid(), slot);
        } else {
            SWALLOWED_SLOTS.remove(owner.getUuid());
        }
        status.apply(status.state().swallow(taotie.getUuid()));
        NbtCompound data = ownerData(owner.getUuid());
        data.putUuid(SeekerRules.REPLAY_TAOTIE_KEY, taotie.getUuid());
        GameRecordManager.recordGlobalEvent(owner.getServerWorld(), SeekerRules.REPLAY_CAR_SWALLOWED_EVENT, taotie,
                data);
        chat(owner, CAR_SWALLOWED);
        actionBar(taotie, TAOTIE_CAR_SWALLOWED);
        return true;
    }

    /**
     * Returns a swallowed car item (60 s RETURNED cooldown): original slot → first empty hotbar slot → first empty
     * shown second-row slot → first empty main slot ({@link #chooseReturnSlot}); never dropped. With no room the car
     * stays swallowed with PendingReturn (one message) and WP-06's tick retries.
     * 归还被吞的小车物品（60 秒 RETURNED 冷却）：原槽位 → 快捷栏首个空位 → 显示中的第二行首个空位 → 主背包首个空位
     * （{@link #chooseReturnSlot}）；从不掉落。
     * 没有空位时保持被吞并设置 PendingReturn（只提示一次），由 WP-06 的刻重试。
     */
    public static boolean returnSwallowedCar(ServerPlayerEntity owner) {
        SeekerStatusComponent status = owner == null ? null : SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || (status.carState() != SeekerCarState.SWALLOWED && !status.pendingReturn())) {
            return false;
        }
        if (!hasCarItem(owner)) {
            PlayerInventory inventory = owner.getInventory();
            Integer original = SWALLOWED_SLOTS.get(owner.getUuid());
            int originalSlot = original == null ? -1 : original;
            boolean originalEmpty = originalSlot >= 0 && isReturnableIndex(inventory, originalSlot)
                    && inventory.getStack(originalSlot).isEmpty();
            boolean[] mainEmpty = new boolean[inventory.main.size()];
            for (int index = 0; index < mainEmpty.length; index++) {
                mainEmpty[index] = inventory.main.get(index).isEmpty();
            }
            int slot = chooseReturnSlot(originalSlot, originalEmpty, mainEmpty, PlayerInventory.getHotbarSize(),
                    SparkFactionSecondRowCompat.isShown());
            if (slot < 0) {
                if (!status.pendingReturn()) {
                    status.apply(status.state().setPendingReturn(true));
                    chat(owner, CAR_INVENTORY_FULL);
                }
                return false;
            }
            inventory.setStack(slot, new ItemStack(SparkWitchItems.seekerCar()));
            inventory.markDirty();
        }
        SWALLOWED_SLOTS.remove(owner.getUuid());
        status.apply(status.state().returnCar());
        GameRecordManager.recordGlobalEvent(owner.getServerWorld(), SeekerRules.REPLAY_CAR_RETURNED_EVENT, owner,
                ownerData(owner.getUuid()));
        chat(owner, CAR_RETURNED);
        return true;
    }

    /** Removes every car item (main, hotbar, offhand, cursor); returns the first inventory index, or -1. */
    private static int removeCarItems(ServerPlayerEntity owner) {
        PlayerInventory inventory = owner.getInventory();
        int first = -1;
        for (int index = 0; index < inventory.main.size(); index++) {
            if (inventory.main.get(index).getItem() instanceof SeekerCarItem) {
                inventory.main.set(index, ItemStack.EMPTY);
                first = first < 0 ? index : first;
            }
        }
        if (inventory.getStack(OFFHAND_INDEX).getItem() instanceof SeekerCarItem) {
            inventory.setStack(OFFHAND_INDEX, ItemStack.EMPTY);
            first = first < 0 ? OFFHAND_INDEX : first;
        }
        if (owner.currentScreenHandler != null
                && owner.currentScreenHandler.getCursorStack().getItem() instanceof SeekerCarItem) {
            owner.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            owner.currentScreenHandler.syncState();
        }
        inventory.markDirty();
        return first;
    }

    private static boolean hasCarItem(ServerPlayerEntity owner) {
        PlayerInventory inventory = owner.getInventory();
        for (ItemStack stack : inventory.main) {
            if (stack.getItem() instanceof SeekerCarItem) {
                return true;
            }
        }
        return inventory.getStack(OFFHAND_INDEX).getItem() instanceof SeekerCarItem;
    }

    private static boolean isReturnableIndex(PlayerInventory inventory, int index) {
        return (index >= 0 && index < inventory.main.size()) || index == OFFHAND_INDEX;
    }

    // ---- Sweeps and finders ----

    /** Silent removal of every device of {@code owner} (state is cleared by the lifecycle). / 静默移除该拥有者的所有设备。 */
    public static void discardAllFor(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) {
            return;
        }
        SWALLOWED_SLOTS.remove(owner);
        for (ServerWorld world : server.getWorlds()) {
            List<? extends SeekerDeviceEntity> devices = world.getEntitiesByType(
                    TypeFilter.instanceOf(SeekerDeviceEntity.class), device -> device.isOwnedBy(owner));
            for (SeekerDeviceEntity device : devices) {
                device.discard();
            }
        }
    }

    public static void sweepAll(MinecraftServer server) {
        SWALLOWED_SLOTS.clear();
        if (server == null) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            SeekerDeviceEntity.discardAll(world);
        }
    }

    @Nullable
    public static SeekerCarEntity findCar(ServerPlayerEntity owner) {
        SeekerStatusComponent status = owner == null ? null : SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || status.carEntityId() < 0) {
            return null;
        }
        return findOwned(owner, status.carEntityId(), SeekerCarEntity.class);
    }

    /**
     * One of the owner's cameras by entity id: it must be listed in the owner's component and be a live owned
     * camera entity; otherwise null.
     * 按实体 id 查找拥有者的某台摄像头：必须登记在拥有者组件中且为存活的本人摄像头实体，否则为 null。
     */
    @Nullable
    public static SeekerCameraEntity findCamera(ServerPlayerEntity owner, int entityId) {
        SeekerStatusComponent status = owner == null ? null : SeekerStatusComponent.KEY.getNullable(owner);
        if (status == null || entityId < 0 || !status.hasCamera(entityId)) {
            return null;
        }
        return findOwned(owner, entityId, SeekerCameraEntity.class);
    }

    @Nullable
    public static ServerPlayerEntity findOwner(SeekerDeviceEntity device) {
        if (device == null || device.ownerUuid() == null || device.getServer() == null) {
            return null;
        }
        return device.getServer().getPlayerManager().getPlayer(device.ownerUuid());
    }

    /**
     * Whether the owner's component still points at this device (car: DEPLOYED with this id; camera: this id is one
     * of the owner's cameras).
     * 拥有者组件是否仍指向该设备（小车：DEPLOYED 且 id 一致；摄像头：该 id 属于拥有者的摄像头之一）。
     */
    public static boolean isReferenced(SeekerStatusComponent status, SeekerDeviceEntity device) {
        return switch (device.kind()) {
            case CAR -> status.carState() == SeekerCarState.DEPLOYED && status.carEntityId() == device.getId();
            case CAMERA -> status.hasCamera(device.getId());
        };
    }

    @Nullable
    private static <T extends SeekerDeviceEntity> T findOwned(ServerPlayerEntity owner, int entityId, Class<T> type) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return null;
        }
        // Entity ids are global across worlds; start with the owner's own world. / 实体 id 跨世界唯一；先查拥有者所在世界。
        Entity entity = owner.getServerWorld().getEntityById(entityId);
        if (entity == null) {
            for (ServerWorld world : server.getWorlds()) {
                entity = world.getEntityById(entityId);
                if (entity != null) {
                    break;
                }
            }
        }
        if (type.isInstance(entity) && !entity.isRemoved() && type.cast(entity).isOwnedBy(owner.getUuid())) {
            return type.cast(entity);
        }
        return null;
    }

    // ---- Car spawn search (plan §3.8) ----

    /**
     * A top face hit within {@link SeekerRules#DEPLOY_REACH}; otherwise the feet moved {@link SeekerRules#DEPLOY_FORWARD}
     * along the horizontal look (then the feet themselves), dropped at most {@link SeekerRules#DEPLOY_DROP}. Every
     * spot needs an empty 0.4 x 0.6 traversal box, support below and the play area.
     * 先尝试部署距离内命中的顶面；否则取沿水平视线前移后的脚下位置（再退回脚下），向下贴地至多 1.5 格。
     * 每个位置都要求通行箱为空、下方有支撑，并位于游戏区域之内。
     */
    @Nullable
    private static Vec3d findCarSpawn(ServerWorld world, ServerPlayerEntity owner, Box playArea) {
        Vec3d eye = owner.getEyePos();
        Vec3d reachEnd = eye.add(owner.getRotationVec(1.0F).multiply(SeekerRules.DEPLOY_REACH));
        BlockHitResult hit = world.raycast(new RaycastContext(eye, reachEnd, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, owner));
        if (hit.getType() == HitResult.Type.BLOCK
                && SeekerPlacementRules.isDirectCarSpot(hit.getSide(), eye, hit.getPos())
                && isValidCarSpot(world, hit.getPos(), playArea)) {
            return hit.getPos();
        }
        for (Vec3d origin : List.of(SeekerPlacementRules.fallbackCarOrigin(owner.getPos(), owner.getYaw()),
                owner.getPos())) {
            Vec3d[] drop = SeekerPlacementRules.fallbackDropSegment(origin);
            BlockHitResult sight = world.raycast(new RaycastContext(eye, drop[0], RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, owner));
            if (sight.getType() != HitResult.Type.MISS) {
                continue;
            }
            BlockHitResult ground = world.raycast(new RaycastContext(drop[0], drop[1],
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner));
            if (ground.getType() == HitResult.Type.BLOCK && ground.getSide() == Direction.UP
                    && isValidCarSpot(world, ground.getPos(), playArea)) {
                return ground.getPos();
            }
        }
        return null;
    }

    private static boolean isValidCarSpot(ServerWorld world, Vec3d feet, Box playArea) {
        return SeekerPlacementRules.withinPlayArea(playArea, feet)
                && world.isSpaceEmpty(SeekerPlacementRules.carClearanceBox(feet))
                && !world.isSpaceEmpty(SeekerPlacementRules.carSupportProbe(feet));
    }

    // ---- Pure helpers (tested) ----

    /**
     * Only the session that shows the affected device ends: any CAR session for the single car, and a CAMERA session
     * only when its focus is that very camera (breaking another camera leaves the view alone).
     * 只结束正在显示该设备的会话：唯一的小车对应任意小车会话；摄像头会话仅当焦点正是这台摄像头时结束（打坏另一台不影响画面）。
     */
    static boolean sessionShows(SeekerSessionMode mode, int focusEntityId, SeekerDeviceKind kind, int deviceId) {
        return switch (kind) {
            case CAR -> mode == SeekerSessionMode.CAR;
            case CAMERA -> mode == SeekerSessionMode.CAMERA && deviceId >= 0 && focusEntityId == deviceId;
        };
    }

    static SeekerExitReason breakExitReason(SeekerDeviceKind kind) {
        return kind == SeekerDeviceKind.CAR ? SeekerExitReason.CAR_BROKEN : SeekerExitReason.CAMERA_BROKEN;
    }

    static String brokenMessageKey(SeekerDeviceKind kind) {
        return kind == SeekerDeviceKind.CAR ? CAR_BROKEN : CAMERA_BROKEN;
    }

    /** {@code seeker_device_broken} data (contract §4b): owner, device, source, and breaker only when given. */
    static NbtCompound brokenReplayData(UUID owner, SeekerDeviceKind kind, SeekerBreakSource source,
                                        @Nullable UUID breaker) {
        NbtCompound data = ownerData(owner);
        data.putString(SeekerRules.REPLAY_DEVICE_KEY, kind.id());
        data.putString(SeekerRules.REPLAY_SOURCE_KEY, source.id());
        if (breaker != null && source.attributable()) {
            data.putUuid(SeekerRules.REPLAY_BREAKER_KEY, breaker);
        }
        return data;
    }

    /**
     * Return slot: the original index when still empty, else the first empty hotbar slot, else (when SparkFactionAPI
     * 0.1.5.13+ shows it) the first empty slot of the second row 27-35, so the car stays reachable, else the first
     * empty main slot; -1 when the inventory is full.
     * 归还槽位：原索引仍为空时优先；否则快捷栏首个空位；否则（SparkFactionAPI 0.1.5.13+ 显示时）第二行 27-35 的首个
     * 空位，使小车仍可取用；否则主背包首个空位；背包已满时返回 -1。
     */
    static int chooseReturnSlot(int originalSlot, boolean originalEmpty, boolean[] mainEmpty, int hotbarSize,
                                boolean secondRowShown) {
        if (originalSlot >= 0 && originalEmpty) {
            return originalSlot;
        }
        int hotbar = Math.min(hotbarSize, mainEmpty.length);
        for (int index = 0; index < hotbar; index++) {
            if (mainEmpty[index]) {
                return index;
            }
        }
        if (secondRowShown) {
            int end = Math.min(SparkFactionSecondRowCompat.SECOND_ROW_END, mainEmpty.length);
            for (int index = SparkFactionSecondRowCompat.SECOND_ROW_START; index < end; index++) {
                if (mainEmpty[index]) {
                    return index;
                }
            }
        }
        for (int index = hotbar; index < mainEmpty.length; index++) {
            if (mainEmpty[index]) {
                return index;
            }
        }
        return -1;
    }

    // ---- Side effects ----

    private static void endSessionShowing(ServerPlayerEntity owner, SeekerStatusComponent status,
                                          SeekerDeviceKind kind, int deviceId, SeekerExitReason reason) {
        if (sessionShows(status.sessionMode(), status.state().sessionFocusEntityId(), kind, deviceId)) {
            SeekerRemoteSessionService.end(owner, reason);
        }
    }

    private static NbtCompound ownerData(UUID owner) {
        NbtCompound data = new NbtCompound();
        data.putUuid(SeekerRules.REPLAY_OWNER_KEY, owner);
        return data;
    }

    private static void recordCarAction(ServerPlayerEntity owner, String action) {
        NbtCompound extra = new NbtCompound();
        extra.putString(SeekerRules.REPLAY_ACTION_KEY, action);
        GameRecordManager.recordItemUse(owner, SeekerRules.CAR_ITEM_ID, null, extra);
    }

    /** Direct feedback to the owner's own action. / 对拥有者自身操作的直接反馈。 */
    private static void actionBar(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), true);
    }

    /** Something happened to the owner's device while they may be looking elsewhere. / 设备发生的事件。 */
    private static void chat(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), false);
    }
}
