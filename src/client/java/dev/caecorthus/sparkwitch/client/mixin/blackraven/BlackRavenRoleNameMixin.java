package dev.caecorthus.sparkwitch.client.mixin.blackraven;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenRoleNameRenderer;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thin Wathe adapter for Black Raven's nearby sensed-role renderer. / Black Raven 近身感知身份渲染的 Wathe 薄适配器。 */
@Mixin(RoleNameRenderer.class)
public abstract class BlackRavenRoleNameMixin {
    @Shadow
    private static Text nametag;

    @Shadow
    private static float nametagAlpha;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sparkwitch$renderSensedRole(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        BlackRavenRoleNameRenderer.render(renderer, player, context, nametag, nametagAlpha);
    }
}
