package dev.caecorthus.sparkwitch.client.mixin.bear;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeTargetOcclusion;
import dev.doctor4t.wathe.client.gui.CrosshairRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * External Wathe seam (pinned in {@code watheClientMixinContracts}; owner 2026-11-10): Wathe's crosshair target hints
 * do not light on a target hidden by BEAR smoke ({@code SmokeTargetOcclusion}, the name label's rule). The three
 * target reads of {@code renderCrosshair} are filtered where the HUD consumes them: the gun pip (revolver 30,
 * derringer 7, and the Savior pistol that shares the branch), the knife glyph (also the Knight's cross sword) and the
 * bat glyph (also the Savior lightsaber). Each value arrives after every wrapper on its selector (SparkTraits Marksman
 * range, Magician puppet, Raven Form crow, Seeker device, Wraith pass-through), in any mixin order. Presentation only:
 * {@code KnifeItem.getKnifeTarget}, the gun selectors and vanilla's crosshair target themselves are untouched, so what
 * a shot, stab or swing sends and what the server resolves stay exactly as before.
 * 外部 Wathe 接缝（已固定在 {@code watheClientMixinContracts}；所有者 2026-11-10）：Wathe 的准星目标提示不会在被 BEAR 烟雾
 * 挡住的目标上亮起（{@code SmokeTargetOcclusion}，与名字标签同一规则）。{@code renderCrosshair} 的三处目标读取在 HUD 使用处
 * 过滤：枪械准星（左轮 30、德林加 7，以及共用该分支的救世主手枪）、刀图标（含骑士十字剑）与球棒图标（含救世主光剑）。
 * 每个值都已经过其选靶方法上的全部包装（SparkTraits 神射手射程、魔术师皮套、化鸦乌鸦、搜寻者设备、冤魂穿透），与 mixin
 * 顺序无关。仅用于展示：{@code KnifeItem.getKnifeTarget}、枪械选靶与原版准星目标本身都不改动，因此开枪、刺击、挥击发送的
 * 内容以及服务端的判定与之前完全一致。
 */
@Mixin(CrosshairRenderer.class)
public abstract class SmokeCrosshairHintMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/client/gui/CrosshairRenderer;getVisibleGunTarget(Lnet/minecraft/entity/player/PlayerEntity;D)Lnet/minecraft/util/hit/HitResult;"
            )
    )
    private static HitResult sparkwitch$gunHintSkipsSmokeHiddenTarget(
            HitResult hit,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return SmokeTargetOcclusion.visibleHit(player, hit);
    }

    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/item/KnifeItem;getKnifeTarget(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/util/hit/HitResult;"
            )
    )
    private static HitResult sparkwitch$knifeHintSkipsSmokeHiddenTarget(
            HitResult hit,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return SmokeTargetOcclusion.visibleHit(player, hit);
    }

    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;"
            )
    )
    private static HitResult sparkwitch$batHintSkipsSmokeHiddenTarget(
            HitResult hit,
            MinecraftClient client,
            ClientPlayerEntity player,
            DrawContext context,
            RenderTickCounter tickCounter
    ) {
        return SmokeTargetOcclusion.visibleHit(player, hit);
    }
}
