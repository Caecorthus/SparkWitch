package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.curser.CurserClientHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projects Wathe's low-sanity psycho skin for a confused viewer without changing the target's psycho component.
 * 对混乱观察者投射 Wathe 低理智疯魔皮肤，不修改目标的疯魔组件。
 */
@Mixin(value = AbstractClientPlayerEntity.class, priority = 2100)
public abstract class CurserConfusionSkinMixin {
    private static final Identifier WATHE_PSYCHO_TEXTURE = Identifier.of("wathe", "textures/entity/psycho.png");
    /**
     * Every input is constant and the record is immutable, so one instance serves every call (as Black Raven's Steve).
     * 所有输入均为常量且该记录不可变，因此所有调用共用同一实例（与黑羽鸦的史蒂夫皮肤相同）。
     */
    private static final SkinTextures SPARKWITCH$PSYCHO_SKIN = new SkinTextures(
            WATHE_PSYCHO_TEXTURE,
            null,
            null,
            null,
            SkinTextures.Model.WIDE,
            true
    );

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$projectConfusedPsychoSkin(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity target = (AbstractClientPlayerEntity) (Object) this;
        if (!CurserClientHooks.isLocallyConfused()
                || target == MinecraftClient.getInstance().player) {
            return;
        }
        cir.setReturnValue(SPARKWITCH$PSYCHO_SKIN);
    }
}
