package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.knight.CrossSwordClientStab;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reuses Wathe's own knife crosshair path for the cross sword: when a release would send a stab
 * ({@link CrossSwordClientStab#canStabNow}, the same selector as the release sender), the knife check in
 * {@link CrosshairRenderer#renderCrosshair} is made to pass, so Wathe draws its knife target reticle (five-dot X) and
 * the knife attack indicator exactly as it does for the vanilla knife. Client-only presentation; the server decides.
 * 复用 Wathe 自己的小刀准星路径：松手会发送刺击时（{@link CrossSwordClientStab#canStabNow}，与松手发送使用同一选靶），
 * 让 {@link CrosshairRenderer#renderCrosshair} 里的小刀判断通过，Wathe 就会像原版小刀一样画五点 X 目标准星和刀攻击提示。
 * 仅为客户端表现，判定由服务端负责。
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
        if (item == WatheItems.KNIFE && CrossSwordClientStab.canStabNow(stack)) {
            return true;
        }
        return original.call(stack, item);
    }
}
