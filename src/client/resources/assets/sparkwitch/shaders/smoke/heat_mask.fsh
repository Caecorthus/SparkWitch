#version 150

// BEAR smoke in another mod's heat image (SmokeHeatMask, BearSmokeViewApi; SparkStrength's Undercover thermal, S4).
// Each heat pixel takes the eye distance of its depth (the world depth the caller copied in, nearer wherever it wrote
// body depth; a cleared depth counts as infinitely far) and the smoke's optical depth up to there, decoded from the
// SmokeOcclusion checkpoint texel and interpolated bilinearly in visibility space exactly like outline_mask.fsh.
// Without HAZE the pass writes that visibility, blended as dst * src.a, so heat behind smoke fades like an outline.
// With HAZE it writes Haze * (1 - visibility), blended with MAX, so smoke in front of the depth glows warm and is
// limited by the world depth (it never shows through a wall).
// 另一模组热图中的 BEAR 烟雾（SmokeHeatMask、BearSmokeViewApi；SparkStrength 卧底热成像 S4）。每个热图像素取其深度的视距
// （调用方拷入的世界深度，写入了人体深度之处更近；清除值视为无限远），以及到该处的烟雾光学厚度：从 SmokeOcclusion 检查点
// 纹素解出，并与 outline_mask.fsh 完全一样在可见度空间做双线性插值。未定义 HAZE 时写出该可见度，按 dst * src.a 混合，
// 烟后的热像描边一样变淡。定义 HAZE 时写出 Haze * (1 - 可见度)，按 MAX 混合，使深度之前的烟发出暖色，并受世界深度限制
// （绝不透墙显示）。

#define CHECKPOINTS 4

uniform sampler2D HeatDepth;
uniform sampler2D Occlusion;
uniform mat4 InverseViewProjection;
uniform vec2 TargetSize;
uniform vec2 OcclusionSize;
uniform float Checkpoints[CHECKPOINTS];
uniform float Never;
uniform float Haze;

out vec4 fragColor;

// Mirrors SmokeOcclusionMath.opticalDepthAt (as outline_mask.fsh). / 对应 SmokeOcclusionMath.opticalDepthAt（同 outline_mask.fsh）。
float opticalDepthAt(vec4 texel, float distance) {
    float value[CHECKPOINTS] = float[CHECKPOINTS](texel.x, texel.y, texel.z, texel.w);
    if (value[0] < 0.0 || value[0] >= Never || distance < value[0]) {
        return 0.0;
    }
    float previousDistance = value[0];
    float previousDepth = Checkpoints[0];
    for (int i = 1; i < CHECKPOINTS; i++) {
        if (value[i] < 0.0) {
            return -value[i];
        }
        if (value[i] >= Never) {
            return Checkpoints[i];
        }
        if (distance < value[i]) {
            float span = max(value[i] - previousDistance, 1.0e-4);
            return mix(previousDepth, Checkpoints[i], (distance - previousDistance) / span);
        }
        previousDistance = value[i];
        previousDepth = Checkpoints[i];
    }
    return Checkpoints[CHECKPOINTS - 1];
}

float visibility(vec4 texel, float distance) {
    float fade = Checkpoints[0];
    return 1.0 - clamp((opticalDepthAt(texel, distance) - fade) / (Checkpoints[CHECKPOINTS - 1] - fade), 0.0, 1.0);
}

float occlusionAt(ivec2 texel, float distance) {
    ivec2 top = ivec2(OcclusionSize) - 1;
    return visibility(texelFetch(Occlusion, clamp(texel, ivec2(0), top), 0), distance);
}

void main() {
    ivec2 pixel = clamp(ivec2(gl_FragCoord.xy), ivec2(0), ivec2(TargetSize) - 1);
    vec2 uv = gl_FragCoord.xy / TargetSize;
    float depth = texelFetch(HeatDepth, pixel, 0).r;
    float distance = 1.0e30;
    if (depth < 1.0) {
        vec4 point = InverseViewProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        distance = length(point.xyz / point.w);
    }
    vec2 cell = uv * OcclusionSize - 0.5;
    vec2 base = floor(cell);
    vec2 f = cell - base;
    ivec2 b = ivec2(base);
    float v = mix(mix(occlusionAt(b, distance), occlusionAt(b + ivec2(1, 0), distance), f.x),
                  mix(occlusionAt(b + ivec2(0, 1), distance), occlusionAt(b + ivec2(1, 1), distance), f.x), f.y);
#ifdef HAZE
    float heat = Haze * (1.0 - v);
    if (heat <= 0.0) {
        discard;
    }
    fragColor = vec4(heat);
#else
    if (v >= 1.0) {
        discard;
    }
    fragColor = vec4(v);
#endif
}
