package dev.caecorthus.sparkwitch.client.mixin.insider;

import dev.caecorthus.sparkwitch.client.insider.InsiderCohortClientHooks;
import dev.caecorthus.sparkwitch.client.insider.InsiderCohortRules;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetNameTags;
import dev.caecorthus.sparkwitch.client.render.WraithNameTagPassThrough;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
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

/**
 * Draws the "嘉豪同伙" label in the Team Jiahao gold under an Insider <-> Corrupt Cop name tag (D7, C9), a sibling of
 * {@code WitchCohortRoleNameMixin} with the same trigger: TAIL of Wathe's {@code renderHud}, so its darkness early
 * return also hides this label; the same 2-block (8 for spectators) crosshair raycast with the Wraith name-tag
 * pass-through; the name tag's fade alpha; and Wathe's cohort offset. Wathe's red "杀手同伙" never shows for this pair
 * (neither role has killer features), and the witch and Insider pairs never overlap.
 * 在内应与黑警的名牌下以嘉豪阵营金色绘制“嘉豪同伙”（D7、C9），与 {@code WitchCohortRoleNameMixin} 同构、触发条件相同：注入
 * Wathe {@code renderHud} 的 TAIL，因此黑暗提前返回同样会隐藏本标签；相同的 2 格（旁观者 8 格）准星射线与冤魂名牌穿透；
 * 名牌渐隐透明度；以及 Wathe 同伙提示的偏移。该配对不会出现 Wathe 红色“杀手同伙”（双方都没有杀手功能），
 * 魔女配对与内应配对也不会重叠。
 */
@Mixin(RoleNameRenderer.class)
public abstract class InsiderCohortRoleNameMixin {
    @Shadow
    private static float nametagAlpha;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sparkwitch$renderJiahaoCohort(
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
                || !InsiderCohortClientHooks.isJiahaoCohortPair(player, target)) {
            return;
        }

        Text cohortText = Text.translatable(InsiderCohortRules.JIAHAO_COHORT_KEY);
        int alpha = (int) (nametagAlpha * 255.0f) << 24;
        int color = InsiderRules.TEAM_JIAHAO_COLOR | alpha;

        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2f, context.getScaledWindowHeight() / 2f + 6, 0);
        context.getMatrices().scale(0.6f, 0.6f, 1f);
        context.getMatrices().translate(0, 20 + renderer.fontHeight, 0);
        context.drawTextWithShadow(renderer, cohortText, -renderer.getWidth(cohortText) / 2, 0, color);
        context.getMatrices().pop();
    }
}
