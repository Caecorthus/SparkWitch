package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.mixin.seeker.SeekerFenceGateBlockAccessor;
import dev.caecorthus.sparkwitch.mixin.seeker.SeekerTrapdoorBlockAccessor;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowUseC2SPayload;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
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
 * Receiver of {@code sparkwitch:black_raven_crow_use} (a right-click while flying), a Raven-owned copy of
 * {@code SeekerCarUseService}: the crow, not the body, right-clicks a whitelisted Wathe door, vanilla door, trapdoor,
 * fence gate, button or lever. Admission: the sender's own live session names this crow, the owner is playing and
 * alive, a per-player throttle, finite values, a loaded and modifiable block, vanilla's hit tolerance, the hit inside
 * the crow's look cone, reach from the crow's eye with a latency slack, and line of sight from that eye onto the same
 * block (or the other half of the same door). The crow has no hands, so the body's held item and inventory are never
 * read: Wathe doors replay Wathe's empty-hand path through {@code DoorInteraction.EVENT} with {@code ItemStack.EMPTY} and
 * the body as the acting player (locked, key and jammed doors refuse with Wathe's own feedback), vanilla doors,
 * trapdoors and fence gates toggle with the crow as the sound source, buttons press with no acting player and levers
 * run their item-independent vanilla use. No item use, no entity interaction, no attack. Invalid or refused requests
 * are dropped silently. Server thread only.
 * {@code black_raven_crow_use}（飞行中右键）的接收方，是 {@code SeekerCarUseService} 的黑羽鸦自有副本：由乌鸦（而非本体）右键白名单中的
 * Wathe 门、原版门、活板门、栅栏门、按钮或拉杆。准入：发送者自己的有效会话指向此乌鸦、主人存活参与、按玩家节流、数值有限、方块已加载
 * 且可修改、原版命中容差、命中点位于乌鸦视线锥内、从乌鸦眼睛算起带延迟余量的触及距离，以及从该眼睛到同一方块（或同一扇门另一半）的视线。
 * 乌鸦没有手，因此从不读取本体的手持物品或物品栏：Wathe 门以 {@code ItemStack.EMPTY} 与本体作为行动玩家经 {@code DoorInteraction.EVENT}
 * 重走空手路径（上锁、需钥匙与被卡住的门以 Wathe 自己的反馈拒绝）；原版门、活板门与栅栏门以乌鸦为声源切换；按钮不指定行动玩家；拉杆执行
 * 本就不依赖物品的原版逻辑。不使用物品、不与实体交互、不攻击。无效或被拒的请求静默丢弃。仅服务端线程。
 */
public final class BlackRavenCrowUseService {
    /** Server-only throttle bookkeeping; weak so it never pins a player. / 仅服务端的节流记录，弱引用不会滞留玩家。 */
    private static final Map<PlayerEntity, Long> LAST_USE_TICK = new WeakHashMap<>();

    private BlackRavenCrowUseService() {
    }

    public static void handleUse(@Nullable ServerPlayerEntity player, @Nullable BlackRavenCrowUseC2SPayload payload) {
        if (player == null || payload == null || payload.hit() == null) {
            return;
        }
        BlackRavenCrowEntity crow = BlackRavenCrowSessions.crowOf(player);
        if (crow == null || crow.getId() != payload.crowEntityId() || !admits(player, crow)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        Long lastUse = LAST_USE_TICK.get(player);
        if (BlackRavenCrowUseRules.isThrottled(lastUse == null ? -1L : lastUse, now)) {
            return;
        }
        // Every admitted request spends the window, so a flood costs at most one raycast per interval.
        // 每个被准入的请求都会占用节流窗口，因此刷包每个间隔最多只消耗一次射线检测。
        LAST_USE_TICK.put(player, now);
        BlockHitResult hit = validatedHit(world, player, crow, payload.hit());
        if (hit == null) {
            return;
        }
        BlockState state = world.getBlockState(hit.getBlockPos());
        BlackRavenCrowUseRules.Target target = BlackRavenCrowUseRules.target(state);
        if (target != null) {
            use(world, player, crow, state, hit, target);
        }
    }

    private static boolean admits(ServerPlayerEntity player, BlackRavenCrowEntity crow) {
        return !crow.isRemoved()
                && crow.getWorld() == player.getWorld()
                && player.isAlive()
                && !player.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && GameWorldComponent.KEY.get(player.getServerWorld()).getGameStatus()
                == GameWorldComponent.GameStatus.ACTIVE;
    }

    /** The claimed hit if the crow can really reach and see that block, otherwise null. / 乌鸦确实可触及且可见时返回命中。 */
    private static @Nullable BlockHitResult validatedHit(ServerWorld world, ServerPlayerEntity player,
                                                         BlackRavenCrowEntity crow, BlockHitResult claimed) {
        BlockPos pos = claimed.getBlockPos();
        Vec3d point = claimed.getPos();
        if (pos == null || claimed.getSide() == null || !BlackRavenCrowUseRules.isFinite(point)
                || !BlackRavenCrowUseRules.withinHitTolerance(point, pos) || !world.isInBuildLimit(pos)
                || !world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()),
                ChunkSectionPos.getSectionCoord(pos.getZ()))
                || !world.canPlayerModifyAt(player, pos)) {
            return null;
        }
        BlockState state = world.getBlockState(pos);
        if (BlackRavenCrowUseRules.target(state) == null) {
            return null;
        }
        Vec3d eye = crow.getEyePos();
        // The crow only uses what it looks at, never a block behind it. / 乌鸦只能交互视线前方的方块，绝不包括身后的方块。
        if (!BlackRavenCrowUseRules.withinLookCone(eye, crow.getYaw(), crow.getPitch(), point)) {
            return null;
        }
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.of(crow));
        if (outline.isEmpty()
                || !BlackRavenCrowUseRules.withinServerReach(outline.getBoundingBox().offset(pos).squaredMagnitude(eye))
                || !BlackRavenCrowUseRules.withinServerReach(eye.squaredDistanceTo(point))) {
            return null;
        }
        BlockHitResult sight = world.raycast(new RaycastContext(eye, BlackRavenCrowUseRules.rayEnd(eye, point),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, crow));
        if (sight.getType() != HitResult.Type.BLOCK
                || !BlackRavenCrowUseRules.sameTarget(world, sight.getBlockPos(), pos)) {
            return null;
        }
        return new BlockHitResult(point, claimed.getSide(), pos, false);
    }

    private static void use(ServerWorld world, ServerPlayerEntity player, BlackRavenCrowEntity crow, BlockState state,
                            BlockHitResult hit, BlackRavenCrowUseRules.Target target) {
        BlockPos pos = hit.getBlockPos();
        switch (target) {
            case WATHE_DOOR -> useWatheDoor(world, player, state, pos, false);
            case WATHE_TRAIN_DOOR -> useWatheDoor(world, player, state, pos, true);
            case DOOR -> {
                // The crow is the sound source, so no nearby listener is excluded. / 乌鸦为声源，不排除任何附近听者。
                if (state.getBlock() instanceof DoorBlock door) {
                    door.setOpen(crow, world, state, pos, !door.isOpen(state));
                }
            }
            case TRAPDOOR -> flipTrapdoor(world, crow, state, pos);
            case FENCE_GATE -> swingFenceGate(world, crow, state, pos);
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

    /** Vanilla {@code TrapdoorBlock#flip}, sourced by the crow. / 以乌鸦为源的原版活板门翻转。 */
    private static void flipTrapdoor(ServerWorld world, BlackRavenCrowEntity crow, BlockState state, BlockPos pos) {
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
        toggled(world, crow, pos, flipped.get(TrapdoorBlock.OPEN), type.trapdoorOpen(), type.trapdoorClose());
    }

    /** Vanilla {@code FenceGateBlock#onUse}, turned by the crow's heading. / 原版栅栏门使用逻辑，按乌鸦朝向转动。 */
    private static void swingFenceGate(ServerWorld world, BlackRavenCrowEntity crow, BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof SeekerFenceGateBlockAccessor gate)) {
            return;
        }
        BlockState next;
        if (state.get(FenceGateBlock.OPEN)) {
            next = state.with(FenceGateBlock.OPEN, false);
        } else {
            Direction heading = crow.getHorizontalFacing();
            next = state.get(HorizontalFacingBlock.FACING) == heading.getOpposite()
                    ? state.with(HorizontalFacingBlock.FACING, heading) : state;
            next = next.with(FenceGateBlock.OPEN, true);
        }
        world.setBlockState(pos, next, Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD);
        WoodType wood = gate.sparkwitch$getWoodType();
        toggled(world, crow, pos, next.get(FenceGateBlock.OPEN), wood.fenceGateOpen(), wood.fenceGateClose());
    }

    /** Vanilla's toggle sound and game event, with no excluded listener. / 原版开关音效与游戏事件，不排除任何听者。 */
    private static void toggled(ServerWorld world, BlackRavenCrowEntity crow, BlockPos pos, boolean open,
                                SoundEvent openSound, SoundEvent closeSound) {
        world.playSound(null, pos, open ? openSound : closeSound, SoundCategory.BLOCKS, 1.0F,
                world.getRandom().nextFloat() * 0.1F + 0.9F);
        world.emitGameEvent(crow, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    /**
     * Wathe's {@code onUse} for an empty hand, re-run through its public API (calling the jar's {@code onUse} would read
     * the body's main-hand key, lockpick or crowbar).
     * 以空手重走 Wathe 的 {@code onUse}（直接调用 jar 中的 {@code onUse} 会读取本体主手的钥匙、撬锁器或撬棍）。
     */
    private static void useWatheDoor(ServerWorld world, ServerPlayerEntity player, BlockState state, BlockPos pos,
                                     boolean train) {
        BlockPos lowerPos = BlackRavenCrowUseRules.lowerHalf(pos,
                state.get(SmallDoorBlock.HALF) == DoubleBlockHalf.UPPER);
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
                .interactionType(BlackRavenCrowUseRules.emptyHandInteraction(train, door.isBlasted(), door.isOpen(),
                        requiresKey))
                .doorType(train ? DoorInteraction.DoorType.TRAIN_DOOR : DoorInteraction.DoorType.SMALL_DOOR)
                .build();
        DoorInteraction.DoorInteractionResult event = DoorInteraction.EVENT.invoker().onInteract(context);
        boolean gameInactive = GameWorldComponent.KEY.get(world).getGameStatus()
                == GameWorldComponent.GameStatus.INACTIVE;
        BlackRavenCrowUseRules.DoorOutcome outcome = BlackRavenCrowUseRules.doorOutcome(event, train,
                player.isCreative(), gameInactive, door.isBlasted(), door.isOpen(), requiresKey, door.isJammed());
        if (outcome == BlackRavenCrowUseRules.DoorOutcome.TOGGLE) {
            SmallDoorBlock.toggleDoor(state, world, door, lowerPos);
        } else if (outcome.messageKey() != null) {
            world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                    WatheSounds.BLOCK_DOOR_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.sendMessage(Text.translatable(outcome.messageKey()), true);
        }
    }
}
