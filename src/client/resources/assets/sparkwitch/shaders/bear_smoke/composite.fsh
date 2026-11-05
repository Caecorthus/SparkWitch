#version 150

// BEAR smoke, pass 2 of 2: depth-aware upsample of the half-resolution march onto the world target. Blended with
// (ONE, SRC_ALPHA): result = smoke colour + scene * transmittance. The scene depth is sampled from a texture that is
// not attached to the bound framebuffer (SmokeViewTargets wraps only the colour texture), so there is no feedback loop.
// BEAR 烟雾第 2 步（共 2 步）：把半分辨率步进结果按深度感知方式放大到世界目标上。混合方式 (ONE, SRC_ALPHA)：
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
    vec4 bilinearSmoke = textureLod(SmokeColor, lowUv, 0.0);
    // All four taps clear, or all four fully opaque: no depth edge can show, so skip the scene depth.
    // 四个采样全透明或全不透明：深度边缘不可能显现，跳过场景深度。
    if (bilinearSmoke.a >= CLEAR_TRANSMITTANCE) {
        discard;
    }
    if (bilinearSmoke.a <= 0.0) {
        OutColor = bilinearSmoke;
        return;
    }
    float depth = texelFetch(SceneDepth, px, 0).r;
    vec2 ndc = (vec2(px) + 0.5) / FullSize * 2.0 - 1.0;
    vec4 h = InvViewProj * vec4(ndc, depth * 2.0 - 1.0, 1.0);
    float dist = length(h.xyz / h.w);

    vec2 lowPos = (vec2(px) + 0.5) * 0.5 - 0.5;
    ivec2 base = ivec2(floor(lowPos));
    vec2 f = lowPos - vec2(base);
    ivec2 last = ivec2(LowSize) - 1;
    ivec2 taps[4];
    float lowDist[4];
    float worst = 0.0;
    for (int k = 0; k < 4; k++) {
        taps[k] = clamp(base + ivec2(k & 1, k >> 1), ivec2(0), last);
        lowDist[k] = texelFetch(SmokeDepth, taps[k], 0).r;
        worst = max(worst, abs(dist - lowDist[k]) / max(dist, 1.0e-3));
    }
    vec4 smoke;
    if (worst < UPSAMPLE_FLAT) {
        // One surface under all four taps: plain hardware bilinear (one fetch). / 四个采样都在同一表面：直接硬件双线性（一次采样）。
        smoke = bilinearSmoke;
    } else {
        // Depth edge. Mirrors SmokeMarchRules.upsampleWeights. / 深度边缘。镜像 SmokeMarchRules.upsampleWeights。
        vec4 sum = vec4(0.0);
        float weightSum = 0.0;
        for (int k = 0; k < 4; k++) {
            ivec2 o = ivec2(k & 1, k >> 1);
            float bilinear = (o.x == 1 ? f.x : 1.0 - f.x) * (o.y == 1 ? f.y : 1.0 - f.y);
            float w = (bilinear + UPSAMPLE_BILINEAR_FLOOR)
                    / (UPSAMPLE_DEPTH_EPSILON + abs(dist - lowDist[k]) / max(dist, 1.0e-3));
            sum += texelFetch(SmokeColor, taps[k], 0) * w;
            weightSum += w;
        }
        smoke = sum / weightSum;
    }
    if (smoke.a >= CLEAR_TRANSMITTANCE) {
        discard;
    }
    OutColor = smoke;
}
