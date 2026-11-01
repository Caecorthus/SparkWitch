package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.client.knight.CrossSwordClientStab;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Puts the cross sword through Wathe's own knife crosshair branch: the branch is selected as soon as the duel target is
 * within the 3-block knife reach ({@link CrossSwordClientStab#aimedDuelTarget}), not only after the hold threshold, and
 * the shared knife-cooldown read is forced off because the cross sword never cools down. Wathe then draws its knife
 * target reticle (five-dot X) exactly as it does for the vanilla knife. Client-only presentation; the server decides.
 * 十字剑直接走 Wathe 自己的小刀准星分支：决斗目标进入 3 格刀距时（{@link CrossSwordClientStab#aimedDuelTarget}）就选中
 * 该分支，而不是只在举够时长后；同时强制关闭共用的刀冷却读取，因为十字剑永不冷却。随后 Wathe 与原版小刀一样绘制五点 X
 * 目标准星。仅为客户端表现，判定由服务端负责。
 */
@Mixin(CrosshairRenderer.class)
public abstract class CrossSwordWatheCrosshairMixin {
    /**
     * Ordinal 2 is the {@code mainHandStack.isOf(WatheItems.KNIFE)} branch selector (after the revolver and derringer).
     * 第 2 个 isOf 是 {@code mainHandStack.isOf(WatheItems.KNIFE)} 分支选择（排在左轮与德林加之后）。
     */
    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z", ordinal = 2))
    private static boolean sparkwitch$crossSwordKnifeCrosshair(ItemStack stack, Item item,
                                                                Operation<Boolean> original) {
        if (item == WatheItems.KNIFE && stack.isOf(SparkWitchItems.crossSword())) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            return player != null && CrossSwordClientStab.aimedDuelTarget(player) != null;
        }
        return original.call(stack, item);
    }

    /**
     * The knife branch shows its cooldown bar while {@code ItemCooldownManager.isCoolingDown(KNIFE)} is true. The cross
     * sword has no cooldown of its own, so a foreign knife cooldown must not replace its target reticle.
     * 小刀分支在 {@code ItemCooldownManager.isCoolingDown(KNIFE)} 为真时显示冷却条。十字剑自身没有冷却，不能因为他人的刀
     * 冷却而顶掉目标准星。
     */
    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager;isCoolingDown(Lnet/minecraft/item/Item;)Z"))
    private static boolean sparkwitch$crossSwordIgnoresKnifeCooldown(ItemCooldownManager manager, Item item,
                                                                     Operation<Boolean> original) {
        if (item == WatheItems.KNIFE) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null && player.getMainHandStack().isOf(SparkWitchItems.crossSword())) {
                return false;
            }
        }
        return original.call(manager, item);
    }
}
