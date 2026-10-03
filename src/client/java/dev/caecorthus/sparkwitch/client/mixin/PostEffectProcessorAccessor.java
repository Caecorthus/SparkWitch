package dev.caecorthus.sparkwitch.client.mixin;

import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Shared client accessor to a PRIVATE post processor's passes, so a SparkWitch filter can set matrix, vector and array
 * uniforms per pass ({@code pass.getProgram().getUniformByNameOrDummy(name)}) and run passes selectively;
 * {@code PostEffectProcessor#setUniforms} only takes single floats. Read-only: callers never add or remove passes.
 * 共享客户端访问器：读取私有后处理器的 pass 列表，使 SparkWitch 滤镜能逐 pass 设置矩阵、向量与数组 uniform
 * （{@code pass.getProgram().getUniformByNameOrDummy(name)}）并按需运行部分 pass；{@code PostEffectProcessor#setUniforms}
 * 只接受单个浮点数。只读：调用方从不增删 pass。
 */
@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {
    @Accessor("passes")
    List<PostEffectPass> sparkwitch$getPasses();
}
