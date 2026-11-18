package dev.caecorthus.sparkwitch.block;

import dev.doctor4t.wathe.block.TrimmedBedBlock;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A Wathe trimmed bed with a behaviour attached. Both beds ARE {@link TrimmedBedBlock}s backed by Wathe's own
 * {@code wathe:trimmed_bed} block entity (see {@code SparkWitchBlocks}), so every Wathe and add-on seam that keys off
 * trimmed beds (sleep task, scorpion poison and its particles, bed traps) treats them as ordinary trimmed beds.
 * The grey stained bed refuses every use except Wathe's knife and guns (kept on PASS like Wathe's own beds), so it
 * can never be slept in or trapped; the green strange bed behaves exactly like Wathe's bed and bills the sleeper
 * afterwards in {@code SparkBedService}.
 * <p>
 * 带行为的 Wathe 裁剪床。两张床都是 {@link TrimmedBedBlock}，并由 Wathe 自己的 {@code wathe:trimmed_bed} 方块实体
 * 承载（见 {@code SparkWitchBlocks}），因此 Wathe 与各附属模组所有以裁剪床为准的接缝（睡觉任务、蝎子毒及其粒子、
 * 床陷阱）都把它们当作普通裁剪床。灰色污渍床拒绝除 Wathe 匕首和枪械之外的一切使用（与 Wathe 原床一样返回 PASS），
 * 因此永远无法入睡或被布置陷阱；绿色奇怪床的行为与 Wathe 原床完全一致，睡完后由 {@code SparkBedService} 结算。
 */
public class SparkBedBlock extends TrimmedBedBlock {
    /** Which of the two beds this block is. / 这个方块是两种床里的哪一种。 */
    public enum Variant {
        /** Grey stained bed: nobody can sleep in it. / 灰色污渍床：谁都不能睡。 */
        GREY_DIRTY,
        /** Green strange bed: sleep works, finishing the sleep task in it costs debuffs. / 绿色奇怪床：能睡，在其中完成睡觉任务会附带负面效果。 */
        GREEN_STRANGE
    }

    private final Variant variant;

    public SparkBedBlock(Variant variant, Settings settings) {
        super(settings);
        this.variant = variant;
    }

    public Variant variant() {
        return variant;
    }

    /**
     * The grey bed answers before Wathe's {@code onUse} (and any add-on hook inside it) runs, so it never starts a
     * sleep and never takes a scorpion or trap item. The green bed is Wathe's bed unchanged.
     * 灰床在 Wathe 的 {@code onUse}（及其中任何附属钩子）之前作答，因此既不会入睡，也不会收下蝎子或陷阱道具。
     * 绿床完全沿用 Wathe 原床。
     */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (variant != Variant.GREY_DIRTY) {
            return super.onUse(state, world, pos, player, hit);
        }
        if (world.isClient) {
            return ActionResult.CONSUME;
        }
        // Same weapon pass-through as Wathe's trimmed bed. / 与 Wathe 裁剪床相同的武器放行。
        ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
        if (mainHand.isOf(WatheItems.KNIFE) || mainHand.isOf(WatheItems.REVOLVER) || mainHand.isOf(WatheItems.DERRINGER)) {
            return ActionResult.PASS;
        }
        player.sendMessage(Text.translatable("block.sparkwitch.grey_dirty_bed.too_dirty"), true);
        return ActionResult.SUCCESS;
    }
}
