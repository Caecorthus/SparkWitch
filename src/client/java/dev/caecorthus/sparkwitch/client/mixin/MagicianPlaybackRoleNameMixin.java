package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.WraithNameTagPassThrough;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * 将魔术师皮套接入 Wathe 的屏幕中央准心名字显示。
 *
 * <p>Wathe 原版 RoleNameRenderer 的射线过滤器只接受 PlayerEntity，
 * 而皮套是自定义 LivingEntity，所以不能只依赖 Wathe 原逻辑；这里沿用
 * 相同的距离、光照、缩放和淡入淡出规则，读取皮套同步的伪装玩家名称。</p>
 */
@Mixin(RoleNameRenderer.class)
public abstract class MagicianPlaybackRoleNameMixin {
    private static float sparkwitch$playbackNameAlpha;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sparkwitch$renderPlaybackName(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci
    ) {
        BlockPos eyeBlock = BlockPos.ofFloored(player.getEyePos());
        boolean dark = player.getWorld().getLightLevel(LightType.BLOCK, eyeBlock) < 3
                && player.getWorld().getLightLevel(LightType.SKY, eyeBlock) < 10;
        float range = WatheClient.canSeeSpectatorInformation() ? 8.0F : 2.0F;
        MagicianPlaybackEntity target = null;
        // Raycast players and puppets together so a real player in front hides the puppet label, as Wathe's own
        // name tag picks the nearest player; the Wraith pass-through keeps hidden Wraiths from blocking it.
        // 玩家与皮套一起参与射线，身前的真实玩家会遮住皮套名字（与 Wathe 名牌只选最近玩家一致）；
        // 冤魂穿透规则让隐藏的冤魂不会挡住它。
        Predicate<Entity> nameTarget = WraithNameTagPassThrough.filterNameTarget(
                player, entity -> entity instanceof PlayerEntity || entity instanceof MagicianPlaybackEntity);
        if (!dark
                && ProjectileUtil.getCollision(player, nameTarget, range)
                instanceof EntityHitResult hit
                && hit.getEntity() instanceof MagicianPlaybackEntity playback
                && playback.disguiseName() != null
                && !playback.disguiseName().isBlank()) {
            target = playback;
        }

        float delta = tickCounter.getTickDelta(true) / 4.0F;
        sparkwitch$playbackNameAlpha = MathHelper.lerp(delta, sparkwitch$playbackNameAlpha, target == null ? 0.0F : 1.0F);
        if (sparkwitch$playbackNameAlpha <= 0.05F || target == null) return;

        Text name = Text.literal(target.disguiseName());
        int alpha = (int) (sparkwitch$playbackNameAlpha * 255.0F) << 24;
        int color = MathHelper.packRgb(1.0F, 1.0F, 1.0F) | alpha;
        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() / 2.0F,
                context.getScaledWindowHeight() / 2.0F + 6.0F, 0.0F);
        context.getMatrices().scale(0.6F, 0.6F, 1.0F);
        context.drawTextWithShadow(renderer, name, -renderer.getWidth(name) / 2, 16, color);
        context.getMatrices().pop();
    }
}
