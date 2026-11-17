package dev.caecorthus.sparkwitch.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A trimmed bed with a behaviour attached. Ported from Wathe's {@code TrimmedBedBlock} (same two-block layout, same
 * wake-up search, same occupied handling) so sleeping still fills Wathe's sleep task, then specialised per variant:
 * the grey stained bed refuses sleep outright, the green strange bed lets it happen and bills the sleeper afterwards.
 * <p>
 * 带行为的裁剪床。移植自 Wathe 的 {@code TrimmedBedBlock}（同样的两格布局、同样的起床位置搜索、同样的占用处理），
 * 因此睡觉依然能完成 Wathe 的睡觉任务，再按变体分化：灰色污渍床直接拒绝睡觉，绿色的奇怪床照睡、睡完再向睡眠者收账。
 */
public class SparkBedBlock extends BedBlock {
    public static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 8, 16);

    /** Which of the two beds this block is. / 这个方块是两种床里的哪一种。 */
    public enum Variant {
        /** Grey stained bed: refuses to let anyone sleep. / 灰色污渍床：不让任何人睡。 */
        GREY_DIRTY,
        /** Green strange bed: sleep works, then applies the debuffs. / 绿色奇怪床：能睡，睡完附带负面效果。 */
        GREEN_STRANGE
    }

    private final Variant variant;

    public SparkBedBlock(Variant variant, Settings settings) {
        super(DyeColor.WHITE, settings);
        this.variant = variant;
        this.setDefaultState(this.stateManager.getDefaultState().with(PART, BedPart.FOOT).with(OCCUPIED, false));
    }

    public Variant variant() {
        return variant;
    }

    @Nullable
    public static Direction getDirection(BlockView world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.getBlock() instanceof SparkBedBlock ? blockState.get(FACING) : null;
    }

    private static Direction getDirectionTowardsOtherPart(BedPart part, Direction direction) {
        return part == BedPart.FOOT ? direction : direction.getOpposite();
    }

    private static boolean isBedBelow(BlockView world, BlockPos pos) {
        return world.getBlockState(pos.down()).getBlock() instanceof SparkBedBlock;
    }

    public static Optional<Vec3d> findWakeUpPosition(EntityType<?> type, net.minecraft.world.CollisionView world, BlockPos pos, Direction bedDirection, float spawnAngle) {
        Direction direction = bedDirection.rotateYClockwise();
        Direction direction2 = direction.pointsTo(spawnAngle) ? direction.getOpposite() : direction;
        if (isBedBelow(world, pos)) {
            return findWakeUpPosition(type, world, pos, bedDirection, direction2);
        }
        int[][] offsets = getAroundAndOnBedOffsets(bedDirection, direction2);
        Optional<Vec3d> optional = findWakeUpPosition(type, world, pos, offsets, true);
        if (optional.isPresent()) {
            return optional;
        }
        return findWakeUpPosition(type, world, pos, offsets, false);
    }

    private static Optional<Vec3d> findWakeUpPosition(EntityType<?> type, net.minecraft.world.CollisionView world, BlockPos pos, Direction bedDirection, Direction respawnDirection) {
        int[][] offsets = getAroundBedOffsets(bedDirection, respawnDirection);
        Optional<Vec3d> optional = findWakeUpPosition(type, world, pos, offsets, true);
        if (optional.isPresent()) {
            return optional;
        }
        BlockPos below = pos.down();
        Optional<Vec3d> underBed = findWakeUpPosition(type, world, below, offsets, true);
        if (underBed.isPresent()) {
            return underBed;
        }
        int[][] onBed = getOnBedOffsets(bedDirection);
        Optional<Vec3d> onTop = findWakeUpPosition(type, world, pos, onBed, true);
        if (onTop.isPresent()) {
            return onTop;
        }
        Optional<Vec3d> relaxed = findWakeUpPosition(type, world, pos, offsets, false);
        if (relaxed.isPresent()) {
            return relaxed;
        }
        Optional<Vec3d> relaxedUnder = findWakeUpPosition(type, world, below, offsets, false);
        if (relaxedUnder.isPresent()) {
            return relaxedUnder;
        }
        return findWakeUpPosition(type, world, pos, onBed, false);
    }

    private static Optional<Vec3d> findWakeUpPosition(EntityType<?> type, net.minecraft.world.CollisionView world, BlockPos pos, int[][] possibleOffsets, boolean ignoreInvalidPos) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int[] offset : possibleOffsets) {
            mutable.set(pos.getX() + offset[0], pos.getY(), pos.getZ() + offset[1]);
            Vec3d vec3d = net.minecraft.entity.Dismounting.findRespawnPos(type, world, mutable, ignoreInvalidPos);
            if (vec3d == null) {
                continue;
            }
            return Optional.of(vec3d);
        }
        return Optional.empty();
    }

    private static int[][] getAroundAndOnBedOffsets(Direction bedDirection, Direction respawnDirection) {
        return ArrayUtils.addAll(getAroundBedOffsets(bedDirection, respawnDirection), getOnBedOffsets(bedDirection));
    }

    private static int[][] getAroundBedOffsets(Direction bedDirection, Direction respawnDirection) {
        return new int[][]{{respawnDirection.getOffsetX(), respawnDirection.getOffsetZ()}, {respawnDirection.getOffsetX() - bedDirection.getOffsetX(), respawnDirection.getOffsetZ() - bedDirection.getOffsetZ()}, {respawnDirection.getOffsetX() - bedDirection.getOffsetX() * 2, respawnDirection.getOffsetZ() - bedDirection.getOffsetZ() * 2}, {-bedDirection.getOffsetX() * 2, -bedDirection.getOffsetZ() * 2}, {-respawnDirection.getOffsetX() - bedDirection.getOffsetX() * 2, -respawnDirection.getOffsetZ() - bedDirection.getOffsetZ() * 2}, {-respawnDirection.getOffsetX() - bedDirection.getOffsetX(), -respawnDirection.getOffsetZ() - bedDirection.getOffsetZ()}, {-respawnDirection.getOffsetX(), -respawnDirection.getOffsetZ()}, {-respawnDirection.getOffsetX() + bedDirection.getOffsetX(), -respawnDirection.getOffsetZ() + bedDirection.getOffsetZ()}, {bedDirection.getOffsetX(), bedDirection.getOffsetZ()}, {respawnDirection.getOffsetX() + bedDirection.getOffsetX(), respawnDirection.getOffsetZ() + bedDirection.getOffsetZ()}};
    }

    private static int[][] getOnBedOffsets(Direction bedDirection) {
        return new int[][]{{0, 0}, {-bedDirection.getOffsetX(), -bedDirection.getOffsetZ()}};
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.CONSUME;
        }
        // TODO: 手里的刀/枪是否也要在这里拒绝交互？Wathe 的裁剪床会 PASS，先保持与它一致。
        ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
        if (mainHand.isOf(dev.doctor4t.wathe.index.WatheItems.KNIFE)
                || mainHand.isOf(dev.doctor4t.wathe.index.WatheItems.REVOLVER)
                || mainHand.isOf(dev.doctor4t.wathe.index.WatheItems.DERRINGER)) {
            return ActionResult.PASS;
        }
        if (variant == Variant.GREY_DIRTY) {
            // Owner spec: this bed can never be slept in, it just says why.
            // 所有者规格：这张床永远不能睡，只把原因说出来。
            player.sendMessage(Text.translatable("block.sparkwitch.grey_dirty_bed.too_dirty"), true);
            return ActionResult.SUCCESS;
        }
        if (state.get(PART) != BedPart.HEAD) {
            pos = pos.offset(state.get(FACING));
            state = world.getBlockState(pos);
            if (!state.isOf(this)) {
                return ActionResult.CONSUME;
            }
        }
        if (state.get(OCCUPIED)) {
            if (!wakePlayers(world, pos)) {
                player.sendMessage(Text.translatable("block.minecraft.bed.occupied"), true);
            }
            return ActionResult.SUCCESS;
        }
        player.trySleep(pos).ifLeft(reason -> {
            if (reason.getMessage() != null) {
                player.sendMessage(reason.getMessage(), true);
            }
        });
        return ActionResult.SUCCESS;
    }

    private boolean wakePlayers(World world, BlockPos pos) {
        List<PlayerEntity> list = world.getEntitiesByClass(PlayerEntity.class, new Box(pos), LivingEntity::isSleeping);
        if (list.isEmpty()) {
            return false;
        }
        list.get(0).wakeUp();
        return true;
    }

    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.onLandedUpon(world, state, pos, entity, fallDistance * 0.5f);
    }

    @Override
    public void onEntityLand(BlockView world, Entity entity) {
        if (entity.bypassesLandingEffects()) {
            super.onEntityLand(world, entity);
        } else {
            Vec3d vec3d = entity.getVelocity();
            if (vec3d.y < 0.0) {
                double d = entity instanceof LivingEntity ? 1.0 : 0.8;
                entity.setVelocity(vec3d.x, -vec3d.y * 0.66f * d, vec3d.z);
            }
        }
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == getDirectionTowardsOtherPart(state.get(PART), state.get(FACING))) {
            if (neighborState.isOf(this) && neighborState.get(PART) != state.get(PART)) {
                return state.with(OCCUPIED, neighborState.get(OCCUPIED));
            }
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && player.isCreative()) {
            BedPart bedPart = state.get(PART);
            if (bedPart == BedPart.FOOT) {
                BlockPos headPos = pos.offset(getDirectionTowardsOtherPart(bedPart, state.get(FACING)));
                BlockState headState = world.getBlockState(headPos);
                if (headState.isOf(this) && headState.get(PART) == BedPart.HEAD) {
                    world.setBlockState(headPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                    world.syncWorldEvent(player, net.minecraft.world.WorldEvents.BLOCK_BROKEN, headPos, Block.getRawIdFromState(headState));
                }
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction direction = ctx.getHorizontalPlayerFacing();
        BlockPos footPos = ctx.getBlockPos();
        BlockPos headPos = footPos.offset(direction);
        World world = ctx.getWorld();
        if (world.getBlockState(headPos).canReplace(ctx) && world.getWorldBorder().contains(headPos)) {
            return this.getDefaultState().with(FACING, direction);
        }
        return null;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockPos headPos = pos.offset(state.get(FACING));
            world.setBlockState(headPos, state.with(PART, BedPart.HEAD), Block.NOTIFY_ALL);
            world.updateNeighbors(pos, Blocks.AIR);
            state.updateNeighbors(world, pos, Block.NOTIFY_ALL);
        }
    }

    @Override
    protected boolean canPathfindThrough(BlockState state, NavigationType type) {
        return false;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SparkBedBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
