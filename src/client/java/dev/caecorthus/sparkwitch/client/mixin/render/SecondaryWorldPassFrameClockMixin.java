package dev.caecorthus.sparkwitch.client.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.caecorthus.sparkwitch.client.render.entity.SecondaryWorldPass;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Entity render protocol hook (2026-10-25): while a {@link SecondaryWorldPass} runs, the client frame clock reports a
 * near-zero last-frame duration, so frame-time animation (EMF's {@code frame_time}) does not advance twice in one frame.
 * Inside a vanilla world render this value only feeds the entity-outline and Fabulous post processors' time, which
 * then also stop double-advancing. Outside a pass the real value passes through.
 * 实体渲染协议钩子（2026-10-25）：{@link SecondaryWorldPass} 执行期间，客户端帧时钟报告接近 0 的上一帧时长，使按帧时长推进的
 * 动画（EMF 的 {@code frame_time}）不会在一帧内推进两次。原版世界渲染中这个值只用于实体描边与 Fabulous 后处理的时间，
 * 它们也因此不再重复推进。pass 之外原值直接通过。
 */
@Mixin(RenderTickCounter.Dynamic.class)
public abstract class SecondaryWorldPassFrameClockMixin {
    @ModifyReturnValue(method = "getLastFrameDuration()F", at = @At("RETURN"))
    private float sparkwitch$freezeFrameClockInSecondaryPass(float ticks) {
        return SecondaryWorldPass.frameDuration(ticks);
    }
}
