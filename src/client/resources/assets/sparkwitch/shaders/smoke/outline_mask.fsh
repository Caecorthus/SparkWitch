#version 150

// Per-pixel smoke mask for the post-processed entity-outline target (SmokeOutlineMask). Each outline pixel takes the
// eye distance of its silhouette: the depth captured before the sobel/blur chain at the pixel itself or, for edge pixels
// the chain spread outside the body, the nearest Chebyshev ring within SEARCH_RADIUS that holds any silhouette, taking
// that ring's FARTHEST depth (fail closed). No silhouette nearby counts as the far plane, so an outline drawn without
// depth is hidden by any smoke on its ray. Alpha is scaled by 1 - smoothstep(R, G, d), interpolated bilinearly in
// visibility space across the occlusion texels.
// 后处理后实体描边目标的逐像素烟雾遮罩（SmokeOutlineMask）。每个描边像素取其剪影的视距：像素本身在 sobel/模糊之前捕获的
// 深度；对于被后处理扩散到身体外侧的边缘像素，取 SEARCH_RADIUS 内最近一圈有剪影的切比雪夫环，并取该环中最远的深度
// （失败即遮挡）。附近没有剪影按远平面处理，因此没有深度的描边只要视线上有烟就被遮住。alpha 乘以
// 1 - smoothstep(R, G, d)，在遮挡纹素之间按可见度做双线性插值。

#define SEARCH_RADIUS 3

uniform sampler2D OutlineColor;
uniform sampler2D SilhouetteDepth;
uniform sampler2D Occlusion;
uniform mat4 InverseViewProjection;
uniform vec2 PassSize;
uniform vec2 OcclusionSize;
uniform float Never;

out vec4 fragColor;

float silhouetteDepth(ivec2 pixel) {
    float centre = texelFetch(SilhouetteDepth, pixel, 0).r;
    if (centre < 1.0) {
        return centre;
    }
    ivec2 top = ivec2(PassSize) - 1;
    for (int r = 1; r <= SEARCH_RADIUS; r++) {
        float farthest = -1.0;
        for (int i = -r; i < r; i++) {
            float a = texelFetch(SilhouetteDepth, clamp(pixel + ivec2(i, -r), ivec2(0), top), 0).r;
            float b = texelFetch(SilhouetteDepth, clamp(pixel + ivec2(r, i), ivec2(0), top), 0).r;
            float c = texelFetch(SilhouetteDepth, clamp(pixel + ivec2(-i, r), ivec2(0), top), 0).r;
            float d = texelFetch(SilhouetteDepth, clamp(pixel + ivec2(-r, -i), ivec2(0), top), 0).r;
            if (a < 1.0) farthest = max(farthest, a);
            if (b < 1.0) farthest = max(farthest, b);
            if (c < 1.0) farthest = max(farthest, c);
            if (d < 1.0) farthest = max(farthest, d);
        }
        if (farthest >= 0.0) {
            return farthest;
        }
    }
    return 1.0;
}

float visibility(vec2 range, float distance) {
    if (range.x >= Never) {
        return 1.0;
    }
    if (range.y <= range.x) {
        return distance >= range.y ? 0.0 : 1.0;
    }
    return 1.0 - smoothstep(range.x, range.y, distance);
}

float occlusionAt(ivec2 texel, float distance) {
    ivec2 top = ivec2(OcclusionSize) - 1;
    return visibility(texelFetch(Occlusion, clamp(texel, ivec2(0), top), 0).rg, distance);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 color = texelFetch(OutlineColor, pixel, 0);
    if (color.a <= 0.0) {
        discard;
    }
    vec2 uv = gl_FragCoord.xy / PassSize;
    float depth = silhouetteDepth(pixel);
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
    if (v >= 1.0) {
        discard;
    }
    fragColor = vec4(color.rgb, color.a * v);
}
