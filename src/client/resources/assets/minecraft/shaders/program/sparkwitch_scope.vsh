#version 150

// SparkWitch scope passes: a full-screen quad that does not depend on ProjMat. PostEffectPass draws the corners
// (0,0)-(OutSize) of its OWN output, but the processor's shared ortho ProjMat spans the full framebuffer, so a blit
// vertex shader would cover only a quarter of the half-resolution blur targets. Mapping by OutSize works at any size.
// SparkWitch 开镜 pass：不依赖 ProjMat 的全屏四边形。PostEffectPass 按“自己的”输出尺寸绘制 (0,0)-(OutSize) 四角，
// 但处理器共用的正交 ProjMat 覆盖整个帧缓冲，blit 顶点着色器在半分辨率模糊目标上只会覆盖四分之一。按 OutSize 映射则任意尺寸都正确。

in vec4 Position;

uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    vec2 uv = Position.xy / max(OutSize, vec2(1.0));
    gl_Position = vec4(uv * 2.0 - 1.0, 0.2, 1.0);
    texCoord = uv;
}
