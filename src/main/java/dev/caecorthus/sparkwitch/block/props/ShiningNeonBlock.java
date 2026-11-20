package dev.caecorthus.sparkwitch.block.props;

import dev.caecorthus.sparkwitch.prop.NeonSettings;
import dev.doctor4t.wathe.index.WatheProperties;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * A neon pillar that behaves like Wathe's own (right-click toggles a whole column) but also blinks. Sneak-right-click
 * opens a settings screen where a creative-mode builder picks a blink interval or the random mode; the settings live
 * on the block entity so a column can be tuned individually.
 * <p>
 * 与 Wathe 原版行为一致的霓虹灯柱（右键切换整列），但还会闪烁。潜行右键打开设置界面，创造模式建造者可选闪烁间隔或
 * 随机模式；设置保存在方块实体上，因此每根柱子可以单独调。
 */
public class ShiningNeonBlock extends PillarBlock {
    public static final BooleanProperty LIT = Properties.LIT;
    /**
     * Wathe's own column flag, taken straight from its registry: the copied settings carry a luminance callback that
     * reads {@code WatheProperties.ACTIVE} by identity, so a freshly created property with the same name would still
     * be a different instance and throw while the first state is built.
     * <p>
     * 直接复用 Wathe 自己的整列标记：复制来的 settings 带着一个亮度回调，它按实例身份读 {@code WatheProperties.ACTIVE}，
     * 自己新建一个同名属性仍然是不同实例，构建第一个状态时照样会抛异常。
     */
    public static final BooleanProperty ACTIVE = WatheProperties.ACTIVE;

    public ShiningNeonBlock(Settings settings) {
        super(settings);
        this.setDefaultState(super.getDefaultState().with(LIT, true).with(ACTIVE, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        super.appendProperties(builder);
        // Wathe's neon pillar settings carry a luminance callback that reads LIT; without registering the property
        // the very first default state throws while the block is constructed. / Wathe 的霓虹灯 settings 带了一个读取
        // LIT 的亮度回调，不注册这个属性时方块构建第一个默认状态就会抛异常。
        builder.add(LIT, ACTIVE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction side = ctx.getSide();
        Direction.Axis axis = side.getAxis() == Direction.Axis.Y ? Direction.Axis.Y : side.getAxis();
        return this.getDefaultState().with(AXIS, axis).with(LIT, true).with(ACTIVE, true);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.shouldCancelInteraction()) {
            if (world.isClient) {
                return ActionResult.SUCCESS;
            }
            if (player instanceof ServerPlayerEntity serverPlayer) {
                NeonSettings.openScreen(serverPlayer, world, pos);
            }
            return ActionResult.SUCCESS;
        }
        boolean lit = state.get(LIT);
        if (!world.isClient) {
            Direction.Axis axis = state.get(AXIS);
            Direction direction = switch (axis) {
                case X -> Direction.EAST;
                case Y -> Direction.UP;
                case Z -> Direction.SOUTH;
            };
            BlockPos.Mutable cursor = pos.mutableCopy();
            while (toggle(world, cursor, axis, lit)) {
                cursor.move(direction);
            }
            cursor.set(pos).move(direction.getOpposite());
            while (toggle(world, cursor, axis, lit)) {
                cursor.move(direction.getOpposite());
            }
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6F, lit ? 0.8F : 1.3F);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.get(AXIS);
        if (direction.getAxis() == axis && neighborState.isOf(this) && neighborState.get(AXIS) == axis) {
            return state.with(ACTIVE, neighborState.get(ACTIVE));
        }
        return state;
    }

    private static boolean toggle(World world, BlockPos pos, Direction.Axis axis, boolean lit) {
        BlockState state = world.getBlockState(pos);
        if (state.isOf(state.getBlock()) && state.getBlock() instanceof ShiningNeonBlock
                && state.get(AXIS) == axis && state.get(LIT) == lit) {
            world.setBlockState(pos, state.with(LIT, !lit));
            return true;
        }
        return false;
    }
}
