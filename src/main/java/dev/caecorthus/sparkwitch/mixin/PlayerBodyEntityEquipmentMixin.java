package dev.caecorthus.sparkwitch.mixin;

import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Keeps Wathe corpses safe for LambDynamicLights and other equipment scanners.
 * 让 wathe 尸体兼容 LambDynamicLights 等装备扫描逻辑，避免返回 null 装备列表。
 */
@Mixin(PlayerBodyEntity.class)
public abstract class PlayerBodyEntityEquipmentMixin {
    @Inject(method = "getArmorItems", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$emptyArmorItemsForBody(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(List.of());
        }
    }
}
