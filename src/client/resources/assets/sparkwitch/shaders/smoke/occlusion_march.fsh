#version 150

// Screen-space smoke occlusion (SmokeOcclusion): marches every view ray through the raw density volumes, IGNORING
// world depth (outlines are drawn through walls), and writes one RGBA16F texel: per checkpoint the eye distance where
// the accumulated optical depth reaches it, -(ray total) in the first unreached one after a reached one, else Never.
// Clouds beyond the sampled volumes come in as sealed boxes: from a sealed box's entry on, every checkpoint counts as
// reached (fail closed). A ray that runs out of steps is sealed where it stopped. Everything is camera-relative, so
// large world coordinates keep their precision. Bullet tunnels (BEAR WP5) scale the density analytically, exactly
// like the visible march, so an outline shows through a tunnel where the smoke does.
// 屏幕空间烟雾遮挡（SmokeOcclusion）：每条视线穿过原始浓度体步进，忽略世界深度（描边本就穿墙），写出一个 RGBA16F
// 纹素：每个检查点存累积光学厚度达到它时的视距；已达到检查点之后的第一个未达到检查点存 -(视线总量)；其余为 Never。
// 超出可采样数量的烟团作为封口盒传入：从封口盒入口起所有检查点都算已达到（失败即遮挡）。步数用尽的视线在停止处封口。
// 全部以相机为原点，大世界坐标也不丢精度。子弹通道（BEAR WP5）与可见步进完全一样解析地缩放浓度，因此烟在通道处透光时
// 描边也会透出。

#define MAX_VOLUMES 8
#define MAX_SEALED 24
#define CHECKPOINTS 4
#define MAX_STEPS 512
#define MAX_TUNNELS 16
#define TUNNEL_EDGE 0.2
#define TUNNEL_CULL_SLACK 0.05

uniform sampler3D Volume0;
uniform sampler3D Volume1;
uniform sampler3D Volume2;
uniform sampler3D Volume3;
uniform sampler3D Volume4;
uniform sampler3D Volume5;
uniform sampler3D Volume6;
uniform sampler3D Volume7;

uniform int VolumeCount;
uniform vec3 VolumeMin[MAX_VOLUMES];
uniform vec3 VolumeSize[MAX_VOLUMES];
uniform int SealedCount;
uniform vec3 SealedMin[MAX_SEALED];
uniform vec3 SealedMax[MAX_SEALED];
uniform mat4 InverseViewProjection;
uniform vec2 TargetSize;
uniform float Extinction;
uniform float StepLength;
uniform float Checkpoints[CHECKPOINTS];
uniform float Never;

// BEAR WP5 bullet tunnels (SmokeVolumes.tunnels), evaluated analytically because they are thinner than a cell.
// TunnelStart: camera-relative axis start (xyz) and current radius (w); TunnelAxis: end - start. Mirrors
// SmokeTunnels.segmentSegmentDistanceSquared and SmokeTimeline.edgeFactor.
// BEAR WP5 子弹通道（SmokeVolumes.tunnels），因为比一格还细，所以解析计算。TunnelStart：相对相机的轴线起点（xyz）与
// 当前半径（w）；TunnelAxis：终点 - 起点。镜像 SmokeTunnels.segmentSegmentDistanceSquared 与 SmokeTimeline.edgeFactor。
uniform int TunnelCount;
uniform vec4 TunnelStart[MAX_TUNNELS];
uniform vec3 TunnelAxis[MAX_TUNNELS];

// Squared closest distance between the segments p0 + d1 * s and q0 + d2 * t, s and t in [0, 1] (Ericson 5.1.9).
// 线段 p0 + d1 * s 与 q0 + d2 * t（s、t 属于 [0, 1]）之间最近距离的平方（Ericson 5.1.9）。
float segmentDistanceSquared(vec3 p0, vec3 d1, vec3 q0, vec3 d2) {
    vec3 r = p0 - q0;
    float a = dot(d1, d1);
    float e = dot(d2, d2);
    float f = dot(d2, r);
    float s = 0.0;
    float t = 0.0;
    if (a <= 1.0e-12) {
        t = e <= 1.0e-12 ? 0.0 : clamp(f / e, 0.0, 1.0);
    } else {
        float c = dot(d1, r);
        if (e <= 1.0e-12) {
            s = clamp(-c / a, 0.0, 1.0);
        } else {
            float b = dot(d1, d2);
            float denominator = a * e - b * b;
            s = denominator > 1.0e-12 ? clamp((b * f - c * e) / denominator, 0.0, 1.0) : 0.0;
            t = (b * s + f) / e;
            if (t < 0.0) {
                t = 0.0;
                s = clamp(-c / a, 0.0, 1.0);
            } else if (t > 1.0) {
                t = 1.0;
                s = clamp((b - c) / a, 0.0, 1.0);
            }
        }
    }
    vec3 d = p0 + d1 * s - (q0 + d2 * t);
    return dot(d, d);
}

// Per-pixel cull: bit i is set when tunnel i comes within its radius of the ray segment rayStart + raySpan * s.
// 逐像素剔除：通道 i 进入视线线段 rayStart + raySpan * s 的半径范围内时置第 i 位。
int tunnelMask(vec3 rayStart, vec3 raySpan) {
    int mask = 0;
    for (int i = 0; i < MAX_TUNNELS; i++) {
        if (i >= TunnelCount) {
            break;
        }
        float reach = TunnelStart[i].w + TUNNEL_CULL_SLACK;
        if (segmentDistanceSquared(rayStart, raySpan, TunnelStart[i].xyz, TunnelAxis[i]) < reach * reach) {
            mask |= 1 << i;
        }
    }
    return mask;
}

// Density multiplier the masked tunnels leave at p: SmokeTimeline.edgeFactor(radius, TUNNEL_EDGE, distance to axis).
// 被选中的通道在 p 处留下的浓度系数：SmokeTimeline.edgeFactor(半径, TUNNEL_EDGE, 到轴线的距离)。
float tunnelFactor(vec3 p, int mask) {
    float factor = 1.0;
    for (int i = 0; i < MAX_TUNNELS; i++) {
        if (i >= TunnelCount) {
            break;
        }
        if ((mask & (1 << i)) == 0) {
            continue;
        }
        vec3 axis = TunnelAxis[i];
        vec3 rel = p - TunnelStart[i].xyz;
        float lengthSquared = dot(axis, axis);
        float u = lengthSquared > 0.0 ? clamp(dot(rel, axis) / lengthSquared, 0.0, 1.0) : 0.0;
        float radius = TunnelStart[i].w;
        factor *= smoothstep(radius - TUNNEL_EDGE, radius, length(rel - axis * u));
    }
    return factor;
}

out vec4 fragColor;

float enterT[MAX_VOLUMES];
float exitT[MAX_VOLUMES];

// Sampler arrays may only take constant indices in GLSL 1.50, so each volume is its own line.
// GLSL 1.50 的采样器数组只能用常量下标，因此每个浓度体单独一行。
#define SAMPLE_VOLUME(I, SAMPLER) \
    if (I < VolumeCount && mid >= enterT[I] && mid <= exitT[I]) { \
        inside = true; \
        density += texture(SAMPLER, (point - VolumeMin[I]) / VolumeSize[I]).r; \
    }

void main() {
    vec2 ndc = gl_FragCoord.xy / TargetSize * 2.0 - 1.0;
    vec4 nearPoint = InverseViewProjection * vec4(ndc, -1.0, 1.0);
    vec4 farPoint = InverseViewProjection * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(farPoint.xyz / farPoint.w - nearPoint.xyz / nearPoint.w);
    // Keep the slab test finite on axis-aligned rays. / 让与坐标轴平行的视线的平板相交仍为有限值。
    vec3 safeDir = vec3(abs(dir.x) < 1.0e-8 ? 1.0e-8 : dir.x, abs(dir.y) < 1.0e-8 ? 1.0e-8 : dir.y,
                        abs(dir.z) < 1.0e-8 ? 1.0e-8 : dir.z);
    vec3 inverseDir = 1.0 / safeDir;

    float start = 1.0e30;
    float end = -1.0;
    for (int i = 0; i < MAX_VOLUMES; i++) {
        enterT[i] = 1.0e30;
        exitT[i] = -1.0;
        if (i >= VolumeCount) {
            continue;
        }
        vec3 a = VolumeMin[i] * inverseDir;
        vec3 b = (VolumeMin[i] + VolumeSize[i]) * inverseDir;
        vec3 lo = min(a, b);
        vec3 hi = max(a, b);
        float t0 = max(max(max(lo.x, lo.y), lo.z), 0.0);
        float t1 = min(min(hi.x, hi.y), hi.z);
        if (t1 > t0) {
            enterT[i] = t0;
            exitT[i] = t1;
            start = min(start, t0);
            end = max(end, t1);
        }
    }

    float crossing[CHECKPOINTS] = float[CHECKPOINTS](Never, Never, Never, Never);
    int reached = 0;
    float depth = 0.0;
    float sealAt = Never;
    if (end > start) {
        int tunnels = TunnelCount > 0 ? tunnelMask(dir * start, dir * (end - start)) : 0;
        float t = start;
        int step = 0;
        for (; step < MAX_STEPS && t < end; step++) {
            float mid = t + 0.5 * StepLength;
            vec3 point = dir * mid;
            float density = 0.0;
            bool inside = false;
            SAMPLE_VOLUME(0, Volume0)
            SAMPLE_VOLUME(1, Volume1)
            SAMPLE_VOLUME(2, Volume2)
            SAMPLE_VOLUME(3, Volume3)
            SAMPLE_VOLUME(4, Volume4)
            SAMPLE_VOLUME(5, Volume5)
            SAMPLE_VOLUME(6, Volume6)
            SAMPLE_VOLUME(7, Volume7)
            if (!inside) {
                // Skip the empty gap to the next box on this ray. / 跳过本视线上到下一个盒子之间的空隙。
                float next = 1.0e30;
                for (int i = 0; i < MAX_VOLUMES; i++) {
                    if (enterT[i] > mid) {
                        next = min(next, enterT[i]);
                    }
                }
                if (next >= 1.0e29) {
                    break;
                }
                t = next;
                continue;
            }
            if (tunnels != 0 && density > 0.0) {
                density *= tunnelFactor(point, tunnels);
            }
            float added = density * Extinction * StepLength;
            if (added > 0.0) {
                float before = depth;
                depth += added;
                // Midpoint rule: this sample stands for [t, t + StepLength]; crossings are interpolated inside it.
                // 中点法：该采样代表 [t, t + StepLength]，穿越点在其中线性插值。
                while (reached < CHECKPOINTS && depth >= Checkpoints[reached]) {
                    crossing[reached] = t + StepLength * (Checkpoints[reached] - before) / added;
                    reached++;
                }
                if (reached == CHECKPOINTS) {
                    break;
                }
            }
            t += StepLength;
        }
        if (step >= MAX_STEPS && t < end && reached < CHECKPOINTS) {
            sealAt = t;
        }
    }

    for (int i = 0; i < SealedCount; i++) {
        vec3 a = SealedMin[i] * inverseDir;
        vec3 b = SealedMax[i] * inverseDir;
        vec3 lo = min(a, b);
        vec3 hi = max(a, b);
        float t0 = max(max(max(lo.x, lo.y), lo.z), 0.0);
        float t1 = min(min(hi.x, hi.y), hi.z);
        if (t1 > t0) {
            sealAt = min(sealAt, t0);
        }
    }

    float texel[CHECKPOINTS];
    for (int i = 0; i < CHECKPOINTS; i++) {
        texel[i] = i < reached ? crossing[i] : (i == reached && i > 0 ? -depth : Never);
        if (sealAt < Never) {
            texel[i] = min(texel[i] < 0.0 ? Never : texel[i], sealAt);
        }
    }
    fragColor = vec4(texel[0], texel[1], texel[2], texel[3]);
}
