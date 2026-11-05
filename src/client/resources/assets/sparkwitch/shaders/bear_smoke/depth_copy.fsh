#version 150

// BEAR smoke, Fabulous! only: copies the world depth into a private R32F texture at the head of the particle draw,
// before the Fabulous! composite clears the main depth. A shader copy needs no depth-format match.
// BEAR 烟雾，仅 Fabulous!：在粒子绘制开头把世界深度拷到私有 R32F 纹理，早于 Fabulous! 合成清掉主深度。
// 着色器拷贝不需要深度格式一致。

uniform sampler2D SourceDepth;

out vec4 OutDepth;

void main() {
    OutDepth = vec4(texelFetch(SourceDepth, ivec2(gl_FragCoord.xy), 0).r);
}
