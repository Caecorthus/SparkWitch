#version 150

// SparkWitch scope, PICTURE_IN_PICTURE composite. Outside the lens: the sharp, unblurred 1x main view, with only a
// short dark tube rim right at the lens edge. Inside a circle of LensRadius x the short screen side (aspect corrected):
// the second, magnified world render (LensSampler, a square covering LensMargin lens radii), with the same lens look as
// ZOOM_BLUR: slight barrel distortion near the edge, edge transmittance loss, a faint coating tint, very subtle smudges
// and dust fixed to the glass, a soft reflection streak, and the scope shadow. Fade blends from the plain picture while
// scoping in. Java sets LensCenter, LensRadius, LensMargin, ShadowOffset and Fade. The two "shared lens look" blocks
// are kept identical to sparkwitch_scope_lens.fsh (post shaders cannot #moj_import; a unit test compares them).
// SparkWitch 开镜画中画合成。镜外：清晰、不模糊的 1 倍主画面，只在镜片边缘有一圈短的暗色镜筒边。在半径为 LensRadius x 屏幕
// 短边的圆内（已做宽高比校正）：第二次放大的世界渲染（LensSampler，覆盖 LensMargin 个镜片半径的正方形），镜片外观与全画面
// 放大相同：边缘轻微桶形畸变、透光衰减、淡淡的镀膜色调、附着在镜片上的极淡污渍与灰尘、柔和的反光条纹以及镜内阴影。开镜时
// Fade 从原画面过渡。Java 设置 LensCenter、LensRadius、LensMargin、ShadowOffset 与 Fade。两段 “shared lens look” 与
// sparkwitch_scope_lens.fsh 保持一致（后处理着色器不支持 #moj_import；由单元测试比对）。

uniform sampler2D DiffuseSampler; // sharp 1x main / 清晰的 1 倍主画面
uniform sampler2D LensSampler;    // magnified lens render / 放大的镜内渲染

uniform vec2 OutSize;
uniform vec2 LensCenter;   // texture space / 纹理空间
uniform float LensRadius;  // share of the short side / 占短边的比例
uniform float LensMargin;  // lens radii the lens render covers from its centre / 镜内渲染从中心覆盖的镜片半径数
uniform vec2 ShadowOffset; // lens radii, texture axes (+y up) / 镜片半径，纹理坐标轴（+y 向上）
uniform float Fade;        // 0..1

in vec2 texCoord;

out vec4 fragColor;

// BEGIN shared lens look: constants and noise / 共享镜片外观：常量与噪声
const float DISTORTION = 0.06;            // barrel strength at the rim / 镜缘桶形畸变强度
const float EDGE_DARKEN = 0.38;           // transmittance lost at the rim / 镜缘透光损失
const vec3 COATING_TINT = vec3(0.955, 1.0, 0.972);
const float SMUDGE = 0.35;
const float DUST = 0.12;
const float STREAK = 0.05;
const float PUPIL_RADIUS = 1.07;          // clear exit pupil, lens radii / 清晰出瞳半径（镜片半径）
const float SHADOW_SOFTNESS = 0.07;
const float SHADOW_STRENGTH = 0.92;
const float RIM_WIDTH = 0.07;             // tube rim, lens radii / 镜筒边宽度（镜片半径）
const float RIM_SHADE = 0.16;
const float RIM_FLOOR = 0.022;            // keeps the rim off pure black / 使镜筒边不至于全黑

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), u.x),
               mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        value += amplitude * valueNoise(p);
        p = p * 2.03 + vec2(11.7, 5.3);
        amplitude *= 0.5;
    }
    return value / 0.875;
}
// END shared lens look

void main() {
    vec2 size = max(OutSize, vec2(1.0));
    float radiusPx = max(LensRadius * min(size.x, size.y), 1.0);
    vec2 p = (texCoord - LensCenter) * size / radiusPx; // lens radii, aspect corrected / 镜片半径，已校正宽高比
    float r = length(p);
    float aa = 1.25 / radiusPx;

    vec3 original = texture(DiffuseSampler, texCoord).rgb;

    // Periphery: the sharp 1x view, with a short dark tube rim right outside the lens.
    // 镜外：清晰的 1 倍画面，镜片外侧紧挨一圈短的暗色镜筒边。
    float open = smoothstep(RIM_WIDTH * 0.4, RIM_WIDTH * 3.0, max(r - 1.0, 0.0));
    vec3 periphery = original * mix(RIM_SHADE, 1.0, open) + vec3(RIM_FLOOR * (1.0 - open));

    // Lens: barrel distortion grows with r^4, so the centre (and the reticle's ticks) stays exact. The lens render
    // spans [-LensMargin, LensMargin] lens radii on both axes.
    // 镜片：桶形畸变随 r^4 增长，中心（以及分划刻度）保持准确。镜内渲染在两个轴上都覆盖 [-LensMargin, LensMargin] 个镜片半径。
    float r2 = r * r;
    vec2 sampleP = p * (1.0 + DISTORTION * r2 * r2);
    vec3 lens = texture(LensSampler, 0.5 + 0.5 * sampleP / max(LensMargin, 1.0)).rgb;
    // BEGIN shared lens look: glass / 共享镜片外观：镜片
    lens *= COATING_TINT;
    float edge = smoothstep(0.5, 1.0, r);
    lens *= 1.0 - EDGE_DARKEN * edge * edge;

    // Smudges and dust stay fixed to the glass. / 污渍与灰尘固定在镜片上。
    float smudge = smoothstep(0.58, 0.9, fbm(p * 2.2 + vec2(3.7, 9.1)));
    lens = mix(lens, lens * 0.9 + vec3(0.04), smudge * SMUDGE);
    float speck = step(0.9994, hash(floor(p * 220.0) + vec2(17.0, 31.0)));
    lens *= 1.0 - speck * DUST;

    // Reflection streak across the upper left, sliding slightly against the swing.
    // 左上方的反光条纹，随摆动略向反方向滑动。
    vec2 sp = p + ShadowOffset * 0.25;
    float across = dot(sp, vec2(-0.70710678, 0.70710678));
    // x * x, not pow(x, 2.0): GLSL pow is undefined for a negative base. / GLSL 的 pow 对负底数未定义。
    float wide = (across - 0.48) / 0.11;
    float thin = (across - 0.66) / 0.035;
    float streak = exp(-wide * wide) + 0.6 * exp(-thin * thin);
    lens += vec3(STREAK * streak * (1.0 - smoothstep(0.55, 0.97, length(sp))));

    // Scope shadow: everything outside the shifted exit pupil darkens. / 镜内阴影：偏移后的出瞳之外变暗。
    float shadow = smoothstep(PUPIL_RADIUS - SHADOW_SOFTNESS, PUPIL_RADIUS + SHADOW_SOFTNESS,
                              length(p - ShadowOffset));
    lens *= 1.0 - SHADOW_STRENGTH * shadow;
    // END shared lens look

    float inside = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, r);
    vec3 scoped = mix(periphery, lens, inside);
    fragColor = vec4(mix(original, scoped, clamp(Fade, 0.0, 1.0)), 1.0);
}
