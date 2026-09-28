package dev.caecorthus.sparkwitch.roles.civilian.seeker.remote;

import dev.caecorthus.sparkwitch.mixin.seeker.SeekerFenceGateBlockAccessor;
import dev.caecorthus.sparkwitch.mixin.seeker.SeekerTrapdoorBlockAccessor;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarUseC2SPacket;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server authority for {@code seeker_car_use}: the driven car, not the body, right-clicks a whitelisted door, trapdoor,
 * fence gate, button or lever. The body's session lock is untouched: {@code SeekerInteractionGuards} still fails every
 * Fabric use/attack callback of the locked body, and this service deliberately bypasses {@code UseBlockCallback}
 * because the car acts, not the body. Admission mirrors the car move validator (CAR session, matching and attached
 * session id, the sender's own live deployed car as the focus) plus the Seeker's common gate and body availability,
 * a per-player throttle, finite values, a loaded and modifiable block, vanilla's hit tolerance, reach from the car's
 * eye with a latency slack, and line of sight from that eye onto the same block (or the other half of the same door).
 * The car has no hands, so the body's held item is never read or used and its inventory is never touched: Wathe doors
 * re-run Wathe's empty-hand path through its public API with the body as the acting player
 * ({@code DoorInteraction.EVENT} listeners such as the Pig God still apply; blasted, jammed and locked doors keep
 * Wathe's own feedback); hand-openable vanilla doors and trapdoors and fence gates (turned by the car's heading) toggle
 * with the car as the source, buttons press with no acting player, and levers run their item-independent vanilla use,
 * which already names no player. So no listener is excluded from the sound, unlike vanilla's own use, which leaves the
 * acting player's sound to that player's client; the server still picks listeners by distance from each body, so a
 * body farther than the sound's range (16 blocks) hears nothing, as with every other world sound during the view.
 * {@code seeker_car_use} 的服务端权威：由被驾驶的小车（而不是本体）右键白名单中的门、活板门、栅栏门、按钮或拉杆。
 * 本体的会话锁不变：{@code SeekerInteractionGuards} 仍让被锁定本体的每个 Fabric 使用/攻击回调失败；本服务有意绕过
 * {@code UseBlockCallback}，因为行动的是小车而不是本体。准入与小车移动校验一致（CAR 会话、会话 id 匹配且已挂接、
 * 焦点是发送者自己仍存活且已部署的小车），另加搜寻者公共门槛与本体可用、按玩家节流、数值有限、方块已加载且可修改、
 * 原版命中容差、从小车眼睛算起带延迟余量的触及距离，以及从该眼睛到同一方块（或同一扇门的另一半）的视线。
 * 小车没有手，因此从不读取或使用本体的手持物品，也从不改动其物品栏：Wathe 门通过其公开 API 重走 Wathe 的空手路径，
 * 以本体作为行动玩家（猪神等 {@code DoorInteraction.EVENT} 监听器依然生效；被炸开、被卡住与上锁的门保留 Wathe
 * 自己的反馈）；可徒手开启的原版门、活板门以及栅栏门（按小车朝向转动）以小车为源切换，按钮在没有行动玩家的情况下
 * 按下，拉杆执行本就不指定玩家的原版使用逻辑。因此音效不会排除任何听者（原版自身的使用逻辑会把行动玩家的音效留给其
 * 客户端播放）；服务端仍按每个本体的距离挑选听者，所以本体距离超过音效范围（16 格）时听不到，与观看期间的其他世界
 * 音效相同。
 */
public final class SeekerCarUseService {
    /** Server-only throttle bookkeeping; weak so it never pins a player. / 仅服务端的节流记录，弱引用不会滞留玩家。 */
    private static final Map<PlayerEntity, Long> LAST_USE_TICK = new WeakHashMap<>();

    private SeekerCarUseService() {
    }

    /** Invalid, stale or refused requests are dropped silently. / 无效、过期或被拒绝的请求静默丢弃。 */
    public static void handleUse(ServerPlayerEntity player, SeekerCarUseC2SPacket packet) {
        if (player == null || packet == null || packet.hit() == null) {
            return;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(player);
        if (status == null) {
            return;
        }
        SeekerCarEntity car = SeekerDeviceService.findCar(player);
        if (!admits(player, status, car, packet.sessionId())) {
            return;
        }
        long now = SeekerRemoteSessionService.serverTick(player);
        Long lastUse = LAST_USE_TICK.get(player);
        if (SeekerCarUseRules.isThrottled(lastUse == null ? -1L : lastUse, now)) {
            return;
        }
        // Every admitted request spends the window, so a flood costs at most one raycast per interval.
        // 每个被准入的请求都会占用节流窗口，因此刷包每个间隔最多只消耗一次射线检测。
        LAST_USE_TICK.put(player, now);
        ServerWorld world = player.getServerWorld();
        BlockHitResult hit = validatedHit(world, player, car, packet.hit());
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        SeekerCarUseRules.Target target = SeekerCarUseRules.target(state);
        if (target != null) {
            use(world, player, car, state, hit, target);
        }
    }

    private static boolean admits(ServerPlayerEntity player, SeekerStatusComponent status,
                                  @Nullable SeekerCarEntity car, int packetSessionId) {
        SeekerSessionState session = status.sessionState();
        return session != null
                && status.sessionMode() == SeekerSessionMode.CAR
                && session.mode == SeekerSessionMode.CAR
                && packetSessionId == status.sessionId()
                && session.sessionId == status.sessionId()
                && session.attached
                && status.carState() == SeekerCarState.DEPLOYED
                && car != null
                && !car.isRemoved()
                && car.isAlive()
                && car.getWorld() == player.getWorld()
                && car.getId() == status.carEntityId()
                && car.getId() == session.focusEntityId
                && player.getUuid().equals(car.ownerUuid())
                && player.isAlive()
                && !player.isSpectator()
                && SeekerTargeting.commonDenyReason(player) == null
                && SeekerTargeting.isBodyAvailable(player);
    }

    /** The claimed hit if the car can really reach and see that block, otherwise null. / 小车确实可触及且可见时返回命中。 */
    @Nullable
    private static BlockHitResult validatedHit(ServerWorld world, ServerPlayerEntity player, SeekerCarEntity car,
                                               BlockHitResult claimed) {
        BlockPos pos = claimed.getBlockPos();
        Vec3d point = claimed.getPos();
        if (pos == null || claimed.getSide() == null || !SeekerCarUseRules.isFinite(point)
                || !SeekerCarUseRules.withinHitTolerance(point, pos) || !world.isInBuildLimit(pos)
                || !world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()),
                ChunkSectionPos.getSectionCoord(pos.getZ()))
                || !world.canPlayerModifyAt(player, pos)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (SeekerCarUseRules.target(state) == null) {
            return null;
        }
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(car));
        Vec3d eye = car.getEyePos();
        if (outline.isEmpty()
                || !SeekerCarUseRules.withinServerReach(outline.getBoundingBox().offset(pos).squaredMagnitude(eye))
                || !SeekerCarUseRules.withinServerReach(eye.squaredDistanceTo(point))) {
            return null;
        }
        BlockHitResult sight = world.raycast(new RaycastContext(eye, SeekerCarUseRules.rayEnd(eye, point),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, car));
        if (sight.getType() != HitResult.Type.BLOCK || !SeekerCarUseRules.sameTarget(world, sight.getBlockPos(), pos)) {
            return null;
        }
        return new BlockHitResult(point, claimed.getSide(), pos, false);
    }

    private static void use(ServerWorld world, ServerPlayerEntity player, SeekerCarEntity car, BlockState state,
                            BlockHitResult hit, SeekerCarUseRules.Target target) {
        BlockPos pos = hit.getBlockPos();
        switch (target) {
            case WATHE_DOOR -> useWatheDoor(world, player, state, pos, false);
            case WATHE_TRAIN_DOOR -> useWatheDoor(world, player, state, pos, true);
            case DOOR -> {
                // The car is the sound source, so no nearby listener (the body included) is excluded.
                // 小车为声源，附近的听者（包括本体）都不会被排除。
                if (state.getBlock() instanceof DoorBlock door) {
                    door.setOpen(car, world, state, pos, !door.isOpen(state));
                }
            }
            case TRAPDOOR -> flipTrapdoor(world, car, state, pos);
            case FENCE_GATE -> swingFenceGate(world, car, state, pos);
            case BUTTON -> {
                // No acting player: vanilla would exclude the presser's own click. / 不传玩家：原版会排除按下者自己的点击声。
                if (state.getBlock() instanceof ButtonBlock button && !state.get(ButtonBlock.POWERED)) {
                    button.powerOn(state, world, pos, null);
                }
            }
            // Vanilla's lever use already toggles with no acting player. / 原版拉杆的使用逻辑本就不指定行动玩家。
            case LEVER -> state.onUse(world, player, hit);
        }
    }

    /** Vanilla {@code TrapdoorBlock#flip}, sourced by the car. / 以小车为源的原版活板门翻转。 */
    private static void flipTrapdoor(ServerWorld world, SeekerCarEntity car, BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof SeekerTrapdoorBlockAccessor trapdoor)) {
            return;
        }
        BlockSetType type = trapdoor.sparkwitch$getBlockSetType();
        if (!type.canOpenByHand()) {
            return;
        }
        BlockState flipped = state.cycle(TrapdoorBlock.OPEN);
        world.setBlockState(pos, flipped, Block.NOTIFY_LISTENERS);
        if (flipped.get(TrapdoorBlock.WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        toggled(world, car, pos, flipped.get(TrapdoorBlock.OPEN), type.trapdoorOpen(), type.trapdoorClose());
    }

    /**
     * Vanilla {@code FenceGateBlock#onUse}, turned by the car's heading instead of the body's facing.
     * 原版栅栏门使用逻辑，按小车朝向而不是本体朝向转动。
     */
    private static void swingFenceGate(ServerWorld world, SeekerCarEntity car, BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof SeekerFenceGateBlockAccessor gate)) {
            return;
        }
        BlockState next;
        if (state.get(FenceGateBlock.OPEN)) {
            next = state.with(FenceGateBlock.OPEN, false);
        } else {
            Direction heading = car.getHorizontalFacing();
            next = state.get(HorizontalFacingBlock.FACING) == heading.getOpposite()
                    ? state.with(HorizontalFacingBlock.FACING, heading) : state;
            next = next.with(FenceGateBlock.OPEN, true);
        }
        world.setBlockState(pos, next, Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD);
        WoodType wood = gate.sparkwitch$getWoodType();
        toggled(world, car, pos, next.get(FenceGateBlock.OPEN), wood.fenceGateOpen(), wood.fenceGateClose());
    }

    /** Vanilla's toggle sound and game event, with no excluded listener. / 原版开关音效与游戏事件，不排除任何听者。 */
    private static void toggled(ServerWorld world, SeekerCarEntity car, BlockPos pos, boolean open,
                                SoundEvent openSound, SoundEvent closeSound) {
        world.playSound(null, pos, open ? openSound : closeSound, SoundCategory.BLOCKS, 1.0F,
                world.getRandom().nextFloat() * 0.1F + 0.9F);
        world.emitGameEvent(car, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    /**
     * Wathe's {@code onUse} for an empty hand, re-run through its public API (the jar's {@code onUse} reads the body's
     * main-hand stack, so it cannot be called directly without handing the car the body's key, lockpick or crowbar).
     * 以空手重走 Wathe 的 {@code onUse}（jar 中的 {@code onUse} 会读取本体主手物品，直接调用就等于把本体的钥匙、
     * 撬锁器或撬棍交给小车）。
     */
    private static void useWatheDoor(ServerWorld world, ServerPlayerEntity player, BlockState state, BlockPos pos,
                                     boolean train) {
        BlockPos lowerPos = SeekerCarUseRules.lowerHalf(pos, state.get(SmallDoorBlock.HALF) == DoubleBlockHalf.UPPER);
        if (!(world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity door)) {
            return;
        }
        boolean requiresKey = !door.getKeyName().isEmpty();
        DoorInteraction.DoorInteractionContext context = DoorInteraction.DoorInteractionContext.builder()
                .world(world)
                .pos(pos)
                .lowerPos(lowerPos)
                .state(state)
                .entity(door)
                .player(player)
                .handItem(ItemStack.EMPTY)
                .interactionType(SeekerCarUseRules.emptyHandInteraction(train, door.isBlasted(), door.isOpen(),
                        requiresKey))
                .doorType(train ? DoorInteraction.DoorType.TRAIN_DOOR : DoorInteraction.DoorType.SMALL_DOOR)
                .build();
        DoorInteraction.DoorInteractionResult event = DoorInteraction.EVENT.invoker().onInteract(context);
        boolean gameInactive = GameWorldComponent.KEY.get(world).getGameStatus()
                == GameWorldComponent.GameStatus.INACTIVE;
        SeekerCarUseRules.DoorOutcome outcome = SeekerCarUseRules.doorOutcome(event, train, player.isCreative(),
                gameInactive, door.isBlasted(), door.isOpen(), requiresKey, door.isJammed());
        if (outcome == SeekerCarUseRules.DoorOutcome.TOGGLE) {
            SmallDoorBlock.toggleDoor(state, world, door, lowerPos);
        } else if (outcome.messageKey() != null) {
            world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                    WatheSounds.BLOCK_DOOR_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.sendMessage(Text.translatable(outcome.messageKey()), true);
        }
    }
}
