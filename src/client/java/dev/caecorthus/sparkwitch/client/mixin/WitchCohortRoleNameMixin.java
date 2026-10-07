package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.hooks.WitchCohortClientHooks;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetNameTags;
import dev.caecorthus.sparkwitch.client.render.WraithNameTagPassThrough;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(RoleNameRenderer.class)
public abstract class WitchCohortRoleNameMixin {
    @Shadow
    private static float nametagAlpha;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sparkwitch$renderWitchCohort(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || nametagAlpha <= 0.05f
                || !GameWorldComponent.KEY.get(player.getWorld()).isRunning()) {
            return;
        }

        float range = WatheClient.canSeeSpectatorInformation() ? 8f : 2f;
        // Same Wraith pass-through and Magician puppet stand-in as Wathe's name tag, so the label follows the
        // tagged player. 与 Wathe 名牌相同的冤魂穿透与魔术师皮套替身，使标签跟随被显示名牌的玩家。
        Predicate<Entity> nameTarget = WraithNameTagPassThrough.filterNameTarget(
                player,
                entity -> entity instanceof PlayerEntity
        );
        if (!(MagicianPuppetNameTags.asCopiedPlayer(ProjectileUtil.getCollision(
                player, MagicianPuppetNameTags.stopAtPuppets(nameTarget), range)) instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof PlayerEntity target)
                || !WitchCohortClientHooks.isGrandWitchCohortPair(player, target)) {
            return;
        }

        Text cohortText = Text.translatable("game.tip.sparkwitch.witch_cohort");
        int alpha = (int) (nametagAlpha * 255.0f) << 24;
        int color = WitchCohortClientHooks.WITCH_COHORT_COLOR | alpha;

        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2f, context.getScaledWindowHeight() / 2f + 6, 0);
        context.getMatrices().scale(0.6f, 0.6f, 1f);
        context.getMatrices().translate(0, 20 + renderer.fontHeight, 0);
        context.drawTextWithShadow(renderer, cohortText, -renderer.getWidth(cohortText) / 2, 0, color);
        context.getMatrices().pop();
    }
}
