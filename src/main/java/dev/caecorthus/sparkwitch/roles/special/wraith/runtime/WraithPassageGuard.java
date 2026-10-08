package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.block.OrnamentBlock;
import dev.doctor4t.wathe.block.PrivacyBlock;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import dev.doctor4t.wathe.block.VentHatchBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Keeps promoted Wraiths from toggling doors, train windows, door buttons and vent hatches (owner decision
 * 2026-10-04). Only the Wathe lockpick keeps its jam and unlock uses on Wathe doors. It also refuses an occupied bed,
 * which would wake the sleeper; restricted Wraiths share that bed check (owner decision 2026-10-07).
 * 阻止晋升冤魂开关门、车窗、门边按钮与通风口盖（所有者 2026-10-04 决定），仅开锁器在 Wathe 门上保留卡门与撬锁用途；
 * 也拒绝有人的床，以免叫醒床上的人，未晋升冤魂共用这一床判断（所有者 2026-10-07 决定）。
 *
 * <p>Runs inside the Wraith {@code UseBlockCallback} on both sides, so it reads only synced state: block states and
 * the Wathe door entity's open and key fields. FAIL ends the whole right-click, just as a successful toggle would.
 * A click on a Wathe ornament is judged by the block behind it, because {@code OrnamentBlock} forwards the use there,
 * through further ornaments too.
 * 在双端的冤魂 UseBlockCallback 内运行，只读取已同步的方块状态与 Wathe 门实体的开关、钥匙字段。FAIL 会结束整次右键，
 * 与成功开关门时一样。点击 Wathe 装饰物时按其背后的方块判断，因为 OrnamentBlock 会把使用转发过去，并可经过多层装饰物。</p>
 */
final class WraithPassageGuard {
    private static final int MAX_ORNAMENT_HOPS = 8;

    private WraithPassageGuard() {
    }

    static ActionResult verdict(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        for (int hops = 0; state.getBlock() instanceof OrnamentBlock; hops++) {
            if (hops == MAX_ORNAMENT_HOPS) {
                return ActionResult.FAIL;
            }
            pos = pos.offset(state.get(FacingBlock.FACING).getOpposite());
            state = world.getBlockState(pos);
        }
        if (state.getBlock() instanceof BedBlock) {
            return mayUseBed(player, world, pos, state) ? ActionResult.PASS : ActionResult.FAIL;
        }
        if (!isPassage(world, pos, state)) {
            return ActionResult.PASS;
        }
        if (WraithParticipationRules.mayUseItemOnPassage(
                blockUseSkipped(player), player.getStackInHand(hand).isOf(WatheItems.CROWBAR))) {
            return ActionResult.PASS;
        }
        return mayUseLockpick(player, world, hand, pos, state) ? ActionResult.PASS : ActionResult.FAIL;
    }

    /**
     * Shared bed check for a Wraith's click already resolved to {@code pos}, which must hold a {@link BedBlock}.
     * 冤魂点击已解析到 pos 的床（须为 BedBlock）时共用的判断。
     */
    static boolean mayUseBed(PlayerEntity player, World world, BlockPos pos, BlockState state) {
        return WraithParticipationRules.mayUseBed(blockUseSkipped(player), isHeadOccupied(world, pos, state));
    }

    // 与原版 interactBlock 相同的跳过条件：任一手有物品且潜行时，方块自身的使用不会运行。
    // Vanilla's own skip condition: with an item in either hand, sneaking skips the block's own use.
    private static boolean blockUseSkipped(PlayerEntity player) {
        return player.shouldCancelInteraction()
                && (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty());
    }

    /**
     * Mirrors Wathe's bed: a foot click is redirected to the head, whose sleeper is woken. If the head is missing,
     * the clicked half's own OCCUPIED decides.
     * 与 Wathe 的床一致：点击床尾会转到床头并叫醒床头的人。床头缺失时按被点击半格自身的 OCCUPIED 判断。
     */
    private static boolean isHeadOccupied(World world, BlockPos pos, BlockState state) {
        if (state.get(BedBlock.PART) != BedPart.HEAD) {
            BlockState head = world.getBlockState(pos.offset(state.get(HorizontalFacingBlock.FACING)));
            if (head.getBlock() instanceof BedBlock) {
                return head.get(BedBlock.OCCUPIED);
            }
        }
        return state.get(BedBlock.OCCUPIED);
    }

    private static boolean isPassage(World world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return isDoorFamily(block)
                || block instanceof PrivacyBlock
                || block instanceof VentHatchBlock
                || block instanceof ButtonBlock && opensDoor(world, pos);
    }

    private static boolean isDoorFamily(Block block) {
        return block instanceof DoorPartBlock
                || block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof FenceGateBlock;
    }

    /**
     * Wathe buttons toggle a Wathe door in the surrounding 3x3x3 cube, and any button's redstone reaches adjacent
     * vanilla doors, so a button counts as a door button when a door-family block is in that cube.
     * Wathe 按钮会切换周围 3x3x3 内的 Wathe 门，任何按钮的红石也能驱动相邻原版门，因此该范围内有门类方块即视为门边按钮。
     */
    private static boolean opensDoor(World world, BlockPos pos) {
        for (BlockPos near : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1))) {
            if (!near.equals(pos) && isDoorFamily(world.getBlockState(near).getBlock())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mirrors Wathe: a train door is always locked while closed, and a small door is locked when it has a key name.
     * The sneak jam reaches {@code LockpickItem#useOnBlock}; the unlock reaches the door's main-hand {@code onUse}.
     * 与 Wathe 一致：关闭的列车门总是上锁，小门有钥匙名即上锁。潜行卡门走 LockpickItem#useOnBlock，撬锁走门的主手 onUse。
     */
    private static boolean mayUseLockpick(
            PlayerEntity player,
            World world,
            Hand hand,
            BlockPos pos,
            BlockState state
    ) {
        if (!player.getStackInHand(hand).isOf(WatheItems.LOCKPICK)
                || !(state.getBlock() instanceof SmallDoorBlock)) {
            return false;
        }
        BlockPos lowerPos = state.get(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
        if (!(world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity door)) {
            return false;
        }
        boolean locked = state.getBlock() instanceof TrainDoorBlock || !door.getKeyName().isEmpty();
        return WraithParticipationRules.mayUsePassageBlock(true, player.isSneaking(), door.isOpen(), locked);
    }
}
