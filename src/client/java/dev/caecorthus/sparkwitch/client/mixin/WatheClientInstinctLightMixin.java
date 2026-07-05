package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Extends Wathe's instinct lightmap gate for SparkWitch roles only.
 * 仅为 SparkWitch 指定角色扩展 wathe 本能亮度入口。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class WatheClientInstinctLightMixin {
    @Redirect(
            method = "lambda$onInitializeClient$15",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/client/WatheClient;isInstinctEnabledAndIsKiller()Z"),
            require = 0
    )
    private static boolean sparkwitch$allowWitchInstinctLight() {
        return WatheClient.isInstinctEnabledAndIsKiller()
                || WitchInstinctClientHooks.usesKillerStyleInstinctLight();
    }
}
