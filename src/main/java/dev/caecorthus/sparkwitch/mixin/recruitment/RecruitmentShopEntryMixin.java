package dev.caecorthus.sparkwitch.mixin.recruitment;

import dev.caecorthus.sparkwitch.compat.recruitment.RecruitmentShopOutputs;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/** Observes both pinned Wathe constructors, retaining weak physical-output provenance.
 * 观察锁定 Wathe 的两个构造器，以弱引用保留物理产物来源，不修改商品。 */
@Mixin(value = ShopEntry.class, remap = false)
public abstract class RecruitmentShopEntryMixin {
    @Shadow @Final private Predicate<PlayerEntity> customBuyHandler;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void sparkwitch$captureRecruitmentOutput(CallbackInfo ci) {
        RecruitmentShopOutputs.observe((ShopEntry) (Object) this, customBuyHandler != null);
    }
}
