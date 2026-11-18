package dev.caecorthus.sparkwitch.client.mixin.m1903a4;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.m1903a4.M1903A4FirstPersonRules;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Vanilla's bow sway keeps sampling {@code sin((useTicks - 0.1) * 1.3)} while the bow stays drawn, and since
 * {@code useTicks} keeps growing past the 20-tick full charge, the first-person bow trembles the whole time. The
 * M1903A4 is a bow-class weapon, so that tremble is dropped here for this item once the charge is full.
 * 原版弓的第一人称摆动在拉满后仍按 {@code sin((useTicks - 0.1) * 1.3)} 持续采样，而 {@code useTicks} 拉满后还在增长，
 * 于是弓会一直轻微抖动。M1903A4 是弓类物品，这里在蓄力拉满后取消这件物品的这段抖动。
 */
@Mixin(HeldItemRenderer.class)
public abstract class M1903A4ChargeWobbleMixin {

    /**
     * Only the bow sway is wrapped: the slice starts at the method's single {@code 20.0F} (the bow case's
     * {@code useTicks / 20.0F} pull), and the sway is the first {@code translate} after it (vanilla 1.21.1: translate
     * ordinal 6, pinned by {@code M1903A4ClientContractTest}). {@code @WrapOperation} chains with other mods' wraps on the
     * same call, and the gate reads the method's own player, hand and stack, so the other hand and every other rendered
     * stack always make the original call.
     * 只包装弓的摆动：切片从方法中唯一的 {@code 20.0F}（弓分支 {@code useTicks / 20.0F} 的拉弓进度）开始，摆动是其后第一个
     * {@code translate}（原版 1.21.1 中为第 6 序号的 translate，由 {@code M1903A4ClientContractTest} 固定）。
     * {@code @WrapOperation} 可与其他模组对同一调用的包装串联，判定读取的是方法自身的玩家、手与物品，因此另一只手以及其他
     * 任何被渲染的物品都会执行原调用。
     */
    @WrapOperation(
            method = "renderFirstPersonItem",
            slice = @Slice(from = @At(value = "CONSTANT", args = "floatValue=20.0F")),
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
                    ordinal = 0
            )
    )
    private void sparkwitch$holdFullChargeSteady(MatrixStack matrices, float x, float y, float z,
                                                 Operation<Void> original, AbstractClientPlayerEntity player,
                                                 float tickDelta, float pitch, Hand hand, float swingProgress,
                                                 ItemStack item) {
        if (M1903A4FirstPersonRules.holdsSteady(item.isOf(SparkWitchItems.m1903a4()), player.isUsingItem(),
                player.getActiveHand() == hand, player.getItemUseTime())) {
            return;
        }
        original.call(matrices, x, y, z);
    }
}
