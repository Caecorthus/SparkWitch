package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.text.WitchShopClientTexts;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen$StoreItemWidget")
public abstract class WitchShopPriceMixin {
    @Shadow
    @Final
    public ShopEntry entry;

    // Keep Yarn: intermediary selectors crash runClient; remapJar emits the intermediary form verifyClientMixinSelectors checks.
    // 保持 Yarn：intermediary 选择器会使 runClient 崩溃；remapJar 生成的 intermediary 形式由 verifyClientMixinSelectors 校验。
    @Redirect(
            method = "renderWidget(Lnet/minecraft/client/gui/DrawContext;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/Text;literal(Ljava/lang/String;)Lnet/minecraft/text/MutableText;"
            )
    )
    private MutableText sparkwitch$renderManaPrice(String fallback) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return Text.literal(fallback);
        }
        return WitchShopClientTexts.price(entry, fallback);
    }
}
