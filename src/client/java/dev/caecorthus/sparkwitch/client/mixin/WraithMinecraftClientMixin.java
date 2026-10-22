package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.render.LocalViewerSnapshot;
import dev.caecorthus.sparkwitch.client.render.WraithViewerRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the final Wraith outline veto, and closes the per-pass viewer memo at every frame start.
 * 在所有描边来源返回后应用最终的冤魂否决，并在每帧开始时关闭按 pass 缓存的观察者事实。
 */
@Mixin(value = MinecraftClient.class, priority = 100)
public abstract class WraithMinecraftClientMixin {
    /**
     * Frame start, before this frame drains packets/tasks and runs client ticks: {@link LocalViewerSnapshot} only
     * memoizes inside a world render pass, and this guarantees no pass outlives its frame even if END were skipped.
     * 帧开始、本帧处理数据包/任务与客户端 tick 之前：{@link LocalViewerSnapshot} 只在世界渲染 pass 内缓存，这里保证即使
     * END 被跳过，pass 也不会跨越本帧。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void sparkwitch$closeViewerSnapshotAtFrameStart(boolean tick, CallbackInfo ci) {
        LocalViewerSnapshot.close();
    }

    /**
     * Runs before later {@code hasOutline} RETURN modifiers (Wathe's instinct outline among them) and cancels, so it
     * must also veto when the value here is still false; it is not a value-only filter.
     * 早于后续的 {@code hasOutline} RETURN 修改器（包括 Wathe 的本能描边）执行并取消，因此即使此处的值仍为 false 也必须否决；
     * 它不是只看返回值的过滤器。
     */
    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void sparkwitch$vetoWraithOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (entity instanceof PlayerEntity target
                && WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, target)) {
            cir.setReturnValue(false);
        }
    }
}
