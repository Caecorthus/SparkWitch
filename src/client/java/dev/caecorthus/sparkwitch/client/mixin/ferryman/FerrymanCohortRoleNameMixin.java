package dev.caecorthus.sparkwitch.client.mixin.ferryman;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeTargetOcclusion;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetNameTags;
import dev.caecorthus.sparkwitch.client.render.WraithNameTagPassThrough;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanRules;
import dev.doctor4t.wathe.api.Role;
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
 * Draws "亡魂队友" in the Lost Souls' cyan under the name tag of a fellow Lost Soul, the Soul Moment's sibling of
 * {@code WitchCohortRoleNameMixin} and {@code InsiderCohortRoleNameMixin}: TAIL of Wathe's {@code renderHud}, so the
 * darkness early return hides it too; the same 2-block (8 for spectators) crosshair raycast with the Wraith name-tag
 * pass-through, the Magician puppet stand-in and the BEAR smoke filter; the name tag's fade alpha; and Wathe's cohort
 * offset. Wathe's own red "杀手同伙" never shows for this pair, because neither side holds a killer role.
 * 在同类迷失的灵魂的名牌下以迷失灵魂的青色绘制「亡魂队友」，是 {@code WitchCohortRoleNameMixin} 与
 * {@code InsiderCohortRoleNameMixin} 在亡魂时刻里的同构实现：注入 Wathe {@code renderHud} 的 TAIL，因此黑暗提前返回同样会隐藏
 * 本标签；相同的 2 格（旁观者 8 格）准星射线与冤魂名牌穿透、魔术师皮套替身与 BEAR 烟雾过滤；名牌淡出透明度；以及 Wathe 同伙
 * 提示的偏移。该配对双方都没有杀手指定的职业，所以 Wathe 自带的红色「杀手同伙」永远不会出现。
 */
@Mixin(RoleNameRenderer.class)
public abstract class FerrymanCohortRoleNameMixin {
    @Shadow
    private static float nametagAlpha;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sparkwitch$renderLostSoulCohort(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        if (!SparkWitchServerConnection.isConfirmedServer()
                || nametagAlpha <= 0.05f
                || !GameWorldComponent.KEY.get(player.getWorld()).isRunning()
                // The viewer must be a Lost Soul, so every other role skips the per-frame raycast.
                // 观察者本身必须是迷失的灵魂，其他职业因此跳过每帧的射线检测。
                || !isLostSoul(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }

        float range = WatheClient.canSeeSpectatorInformation() ? 8f : 2f;
        Predicate<Entity> nameTarget = WraithNameTagPassThrough.filterNameTarget(
                player,
                entity -> entity instanceof PlayerEntity
        );
        if (!(MagicianPuppetNameTags.asCopiedPlayer(ProjectileUtil.getCollision(player,
                SmokeTargetOcclusion.filterLabelTarget(player, MagicianPuppetNameTags.stopAtPuppets(nameTarget), range),
                range)) instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof PlayerEntity target)
                || !isLostSoul(GameWorldComponent.KEY.get(player.getWorld()).getRole(target))) {
            return;
        }

        Text cohortText = Text.translatable(FerrymanRules.LOST_SOUL_COHORT_KEY);
        int alpha = (int) (nametagAlpha * 255.0f) << 24;
        int color = FerrymanRules.SOUL_TEAM_COLOR | alpha;

        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2f, context.getScaledWindowHeight() / 2f + 6, 0);
        context.getMatrices().scale(0.6f, 0.6f, 1f);
        context.getMatrices().translate(0, 20 + renderer.fontHeight, 0);
        context.drawTextWithShadow(renderer, cohortText, -renderer.getWidth(cohortText) / 2, 0, color);
        context.getMatrices().pop();
    }

    private static boolean isLostSoul(Role role) {
        return role != null && FerrymanRules.LOST_SOUL_ID.equals(role.identifier());
    }
}
