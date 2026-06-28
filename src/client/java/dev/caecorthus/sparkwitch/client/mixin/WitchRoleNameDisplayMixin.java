package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.WitchRoleDisplayTexts;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.text.MutableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps Wathe spectator/body role labels readable when a role translation key is missing.
 * 当 wathe 旁观者/尸体职业名缺翻译时，避免显示原始 translation key。
 */
@Mixin(RoleNameRenderer.class)
public abstract class WitchRoleNameDisplayMixin {
    @Redirect(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;)Lnet/minecraft/text/MutableText;",
                    ordinal = 0
            )
    )
    private static MutableText sparkwitch$renderSpectatorRoleName(String translationKey) {
        return WitchRoleDisplayTexts.roleName(translationKey);
    }

    @Redirect(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;)Lnet/minecraft/text/MutableText;",
                    ordinal = 2
            )
    )
    private static MutableText sparkwitch$renderBodyRoleName(String translationKey) {
        return WitchRoleDisplayTexts.roleName(translationKey);
    }
}
