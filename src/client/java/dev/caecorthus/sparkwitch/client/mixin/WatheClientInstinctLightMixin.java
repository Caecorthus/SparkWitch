package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Extends Wathe's instinct lightmap gate for SparkWitch roles only.
 * 仅为 SparkWitch 指定角色扩展 wathe 本能亮度入口。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class WatheClientInstinctLightMixin {
    @WrapOperation(
            method = "lambda$onInitializeClient$15",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/client/WatheClient;isInstinctEnabledAndIsKiller()Z")
    )
    private static boolean sparkwitch$allowWitchInstinctLight(Operation<Boolean> original) {
        // Preserve the wrapped chain so other mods can extend the same Wathe light gate.
        // 保留包装调用链，让其他模组也能扩展同一个 Wathe 亮度入口。
        boolean originalAllowed = original.call();
        return originalAllowed
                || WitchInstinctClientHooks.usesKillerStyleInstinctLight();
    }
}
