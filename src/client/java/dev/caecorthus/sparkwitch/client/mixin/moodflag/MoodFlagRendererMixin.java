package dev.caecorthus.sparkwitch.client.mixin.moodflag;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.caecorthus.sparkwitch.client.moodflag.MoodFlags;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client presentation seam for the faction mood flags ({@link MoodFlags}), pinned to Wathe 1.5.6 {@code MoodRenderer}.
 * <ul>
 *     <li>{@code renderKiller} and {@code renderCivilian} each draw the flag with one
 *     {@code drawGuiTexture(Identifier, int, int, int, int)} call; both are {@code @WrapOperation}s (never
 *     {@code @Redirect}) that swap only Wathe's own sprites, at the same 14x17 footprint. They compose in any nesting
 *     order with Natsume's and Noa's wraps of the same civilian call and with SparkTraits' {@code @ModifyArg}s there,
 *     and an icon NoellesRoles' getter hooks already replaced (Stimulation) passes through.</li>
 *     <li>{@code renderHud} dispatches FAKE to {@code renderKiller} and REAL to {@code renderCivilian} after SparkTraits'
 *     effective-mood {@code @Redirect}; NONE draws nothing. Two {@code @Inject}s right before those calls mark the
 *     dispatch in a {@code @Share}d local, and the TAIL {@code @Inject} (the normal end only: the not-playing and Psycho
 *     early returns never reach it) draws the no-sanity flag when neither ran, at {@code renderKiller}'s spot
 *     ({@code 3 * moodOffset}) without a bar.</li>
 * </ul>
 * Client-only and display-only: the server owns roles, traits and sanity, and no value is ever shown.
 * 阵营心情旗帜（{@link MoodFlags}）的客户端表现接缝，锁定 Wathe 1.5.6 {@code MoodRenderer}。
 * <ul>
 *     <li>{@code renderKiller} 与 {@code renderCivilian} 各自用一次 {@code drawGuiTexture(Identifier, int, int, int, int)}
 *     绘制旗帜；两者都使用只替换 Wathe 自有 sprite 的 {@code @WrapOperation}（绝不用 {@code @Redirect}），尺寸同为 14x17。
 *     它们与夏目安安、诺亚在同一平民调用上的包装以及 SparkTraits 在该处的 {@code @ModifyArg} 以任意嵌套顺序共存；
 *     NoellesRoles 取图钩子已替换的图标（亢奋）原样放行。</li>
 *     <li>{@code renderHud} 在 SparkTraits 有效理智类型 {@code @Redirect} 之后，把 FAKE 分派给 {@code renderKiller}、REAL
 *     分派给 {@code renderCivilian}；NONE 什么都不画。两个紧贴这两处调用之前的 {@code @Inject} 在 {@code @Share} 局部变量中
 *     标记已分派，TAIL {@code @Inject}（只在正常结尾：未在局与疯魔的提前返回不会到达）在两者都未执行时于
 *     {@code renderKiller} 的位置（{@code 3 * moodOffset}）绘制无理智旗帜，不画理智条。</li>
 * </ul>
 * 仅客户端、仅展示：职业、词条与理智均由服务端负责，且从不显示任何数值。
 */
@Mixin(MoodRenderer.class)
public abstract class MoodFlagRendererMixin {
    @WrapOperation(
            method = "renderKiller",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"
            ),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$killerMoodFlag(
            DrawContext context,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height,
            Operation<Void> original
    ) {
        original.call(context, MoodFlags.killerSprite(sprite), x, y, width, height);
    }

    @WrapOperation(
            method = "renderCivilian",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"
            ),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$civilianMoodFlag(
            DrawContext context,
            Identifier sprite,
            int x,
            int y,
            int width,
            int height,
            Operation<Void> original
    ) {
        original.call(context, MoodFlags.civilianSprite(sprite), x, y, width, height);
    }

    @Inject(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/gui/MoodRenderer;renderKiller(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/client/gui/DrawContext;)V"
            ),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$markKillerMoodDispatch(
            PlayerEntity player,
            TextRenderer textRenderer,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci,
            @Share("moodIconDispatched") LocalBooleanRef moodIconDispatched
    ) {
        moodIconDispatched.set(true);
    }

    @Inject(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/gui/MoodRenderer;renderCivilian(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/client/gui/DrawContext;F)V"
            ),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$markCivilianMoodDispatch(
            PlayerEntity player,
            TextRenderer textRenderer,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci,
            @Share("moodIconDispatched") LocalBooleanRef moodIconDispatched
    ) {
        moodIconDispatched.set(true);
    }

    @Inject(method = "renderHud", at = @At("TAIL"), require = 1, allow = 1)
    private static void sparkwitch$noSanityMoodFlag(
            PlayerEntity player,
            TextRenderer textRenderer,
            DrawContext context,
            RenderTickCounter tickCounter,
            CallbackInfo ci,
            @Share("moodIconDispatched") LocalBooleanRef moodIconDispatched
    ) {
        if (moodIconDispatched.get()) {
            return;
        }
        Identifier flag = MoodFlags.noMoodSprite(player);
        if (flag == null) {
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(0, 3 * MoodRenderer.moodOffset, 0);
        context.drawGuiTexture(flag, 5, 6, 14, 17);
        context.getMatrices().pop();
    }
}
