#version 150

// BEAR smoke, pass 2 of 2: depth-aware, grain-removing upsample of the half-resolution march onto the world target
// (a 3x3 tent within one surface, nearest-depth weights across depth edges). Blended with
// (ONE, SRC_ALPHA): result = smoke colour + scene * transmittance. The scene depth is sampled from a texture that is
// not attached to the bound framebuffer (SmokeViewTargets wraps only the colour texture), so there is no feedback loop.
// BEAR 烟雾第 2 步（共 2 步）：把半分辨率步进结果按深度感知、去颗粒的方式放大到世界目标上（同一表面内做 3x3 帐篷滤波，
// 跨深度边缘按最近深度加权）。混合方式 (ONE, SRC_ALPHA)：
// 结果 = 烟雾颜色 + 场景 * 透射率。场景深度来自一张未挂在当前帧缓冲上的纹理（SmokeViewTargets 只包装颜色纹理），因此没有反馈回路。

uniform sampler2D SceneDepth;
uniform sampler2D SmokeColor;
uniform sampler2D SmokeDepth;
uniform mat4 InvViewProj;
uniform vec2 FullSize;
uniform vec2 LowSize;

out vec4 OutColor;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    vec2 lowUv = (vec2(px) + 0.5) * 0.5 / LowSize;
    // A 3x3 tent over the half-resolution march (four bilinear taps half a texel out) removes the jitter grain.
    // 对半分辨率步进结果做 3x3 帐篷滤波（四个偏移半纹素的双线性采样），去掉抖动颗粒。
    vec2 halfTexel = 0.5 / LowSize;
    vec2 uv0 = lowUv + vec2(-halfTexel.x, -halfTexel.y);
    vec2 uv1 = lowUv + vec2(halfTexel.x, -halfTexel.y);
    vec2 uv2 = lowUv + vec2(-halfTexel.x, halfTexel.y);
    vec2 uv3 = lowUv + vec2(halfTexel.x, halfTexel.y);
    vec4 s0 = textureLod(SmokeColor, uv0, 0.0);
    vec4 s1 = textureLod(SmokeColor, uv1, 0.0);
    vec4 s2 = textureLod(SmokeColor, uv2, 0.0);
    vec4 s3 = textureLod(SmokeColor, uv3, 0.0);
    vec4 blurred = (s0 + s1 + s2 + s3) * 0.25;
    // All clear, or all fully opaque: no depth edge can show, so skip the scene depth.
    // 全透明或全不透明：深度边缘不可能显现，跳过场景深度。
    if (min(min(s0.a, s1.a), min(s2.a, s3.a)) >= CLEAR_TRANSMITTANCE) {
        discard;
    }
    if (max(max(s0.a, s1.a), max(s2.a, s3.a)) <= 0.0) {
        OutColor = blurred;
        return;
    }
    float depth = texelFetch(SceneDepth, px, 0).r;
    vec2 ndc = (vec2(px) + 0.5) / FullSize * 2.0 - 1.0;
    vec4 h = InvViewProj * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    float dist = length(h.xyz / h.w);
    float inv = 1.0 / max(dist, 1.0e-3);
    // The blur may only run over one surface: the march depths under its four taps must all match this pixel.
    // 模糊只能在同一表面内进行：四个采样处的步进深度都必须与本像素一致。
    float worst = max(
            max(abs(dist - textureLod(SmokeDepth, uv0, 0.0).r), abs(dist - textureLod(SmokeDepth, uv1, 0.0).r)),
            max(abs(dist - textureLod(SmokeDepth, uv2, 0.0).r), abs(dist - textureLod(SmokeDepth, uv3, 0.0).r))) * inv;
    vec4 smoke;
    if (worst < UPSAMPLE_FLAT) {
        smoke = blurred;
    } else {
        // Depth edge: the four nearest texels, weighted by depth match. Mirrors SmokeShaderReference.upsampleWeights (tests).
        // 深度边缘：取最近的四个纹素，按深度匹配加权。镜像 SmokeShaderReference.upsampleWeights（测试）。
        vec2 lowPos = (vec2(px) + 0.5) * 0.5 - 0.5;
        ivec2 base = ivec2(floor(lowPos));
        vec2 f = lowPos - vec2(base);
        ivec2 last = ivec2(LowSize) - 1;
        vec4 sum = vec4(0.0);
        float weightSum = 0.0;
        for (int k = 0; k < 4; k++) {
            ivec2 o = ivec2(k & 1, k >> 1);
            ivec2 tap = clamp(base + o, ivec2(0), last);
            float lowDist = texelFetch(SmokeDepth, tap, 0).r;
            float bilinear = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
            float w = (bilinear + UPSAMPLE_BILINEAR_FLOOR)
                    / (UPSAMPLE_DEPTH_EPSILON + abs(dist - lowDist) / max(dist, 1.0e-3));
            sum += texelFetch(SmokeColor, tap, 0) * w;
            weightSum += w;
        }
        smoke = sum / weightSum;
    }
    if (smoke.a >= CLEAR_TRANSMITTANCE) {
        discard;
    }
    OutColor = smoke;
}
