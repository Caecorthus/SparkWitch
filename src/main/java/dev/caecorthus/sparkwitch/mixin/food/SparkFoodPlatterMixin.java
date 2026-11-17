package dev.caecorthus.sparkwitch.mixin.food;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.item.defense.DefenseVialService;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets the defence vial ride on Wathe's food platter / drinks tray: right-click a platter while holding the vial to
 * pour it in, and whoever later takes a serving from that platter gets a shield layer. The tray itself knows nothing
 * about vials, so the pouring is remembered here, keyed by block position.
 * <p>
 * 让防御药剂搭上 Wathe 的餐盘 / 饮料托盘：手持药剂右键餐盘即倒入，之后从该餐盘取用的人得到一层护盾。
 * 餐盘本身对药剂一无所知，因此「倒入」这件事记在这里，以方块坐标作键。
 */
@Mixin(FoodPlatterBlock.class)
public abstract class SparkFoodPlatterMixin {
    /**
     * What each player was holding when the interaction started, so the return hook can tell a real serving apart
     * from a failed click. Wathe returns PASS after handing the food over, so the return value alone says nothing.
     * 每个玩家开始交互时手里拿的东西：返回钩子靠它区分「真的取到了一份」和「点了但没取成」。
     * Wathe 在把食物递出去之后返回的是 PASS，所以只看返回值什么都判断不出来。
     */
    private static final Map<UUID, ItemStack> HELD_BEFORE = new ConcurrentHashMap<>();

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$pourVial(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) {
            return;
        }
        if (!player.getStackInHand(Hand.MAIN_HAND).isOf(SparkWitchItems.defenseVial())) {
            HELD_BEFORE.put(player.getUuid(), player.getStackInHand(Hand.MAIN_HAND).copy());
            return;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof BeveragePlateBlockEntity)) {
            return;
        }
        if (DefenseVialService.isArmed(pos, world.getTime())) {
            return;
        }
        DefenseVialService.armTray(pos, world.getTime());
        player.getStackInHand(Hand.MAIN_HAND).decrement(1);
        player.sendMessage(net.minecraft.text.Text.translatable("message.sparkwitch.defense_vial.poured"), true);
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    /**
     * After vanilla handled the interaction, ask whether this player actually took something off the tray. A serving
     * is consumed inside {@code onUse}, so a successful return with a vetted tray means the drinker is due a layer.
     * 原版处理完交互后再问一句：这名玩家是否真的从托盘上取走了东西。取用发生在 {@code onUse} 内部，所以只要返回值
     * 是成功、且这个托盘记过药剂，喝的人就该得到一层。
     */
    @Inject(method = "onUse", at = @At("RETURN"))
    private void sparkwitch$shieldDrinker(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        ItemStack before = HELD_BEFORE.remove(player.getUuid());
        if (before == null) {
            return;
        }
        // Wathe hands the serving over inside onUse and then returns PASS, so compare hands instead of results.
        // Wathe 在 onUse 内部把食物递到手上，然后返回 PASS，所以要对比手上的东西，而不是返回值。
        ItemStack after = player.getStackInHand(Hand.MAIN_HAND);
        if (ItemStack.areEqual(before, after)) {
            return;
        }
        DefenseVialService.onTrayConsumed(serverPlayer, pos);
    }
}
