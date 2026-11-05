#version 150

// BEAR smoke, pass 1 of 2: half-resolution ray march through up to MAX_CLOUDS density volumes (SmokeVolumes).
// Writes premultiplied in-scattered colour + transmittance, and the eye distance of the surface this texel marched to
// (a checkerboard of the nearest and farthest of its 2x2 full-resolution depths) for the depth-aware upsample.
// Every #define comes from SmokeMarchRules (injected by SmokeProgram); the Java side mirrors the step, erosion and
// ray-box rules and its tests pin them.
// BEAR 烟雾第 1 步（共 2 步）：半分辨率光线步进，穿过至多 MAX_CLOUDS 个浓度体（SmokeVolumes）。输出预乘的散射颜色与
// 透射率，以及该纹素步进终点表面的视距（其 2x2 全分辨率深度按棋盘格交替取最近/最远），供深度感知放大使用。
// 所有 #define 都来自 SmokeMarchRules（由 SmokeProgram 注入）；Java 端镜像步长、侵蚀与光线-盒子规则并由测试固定。

uniform sampler2D SceneDepth;
uniform sampler3D Noise;
uniform sampler3D Volume0;
uniform sampler3D Volume1;
uniform sampler3D Volume2;
uniform sampler3D Volume3;
uniform sampler3D Volume4;
uniform sampler3D Volume5;
uniform sampler3D Volume6;
uniform sampler3D Volume7;

// Clip space -> camera-relative world space (inverse of projection * view rotation of this world render).
// 裁剪空间 -> 相对相机的世界空间（本次世界渲染的投影 * 视角旋转之逆）。
uniform mat4 InvViewProj;
uniform vec2 FullSize;
uniform int CloudCount;
// Camera-relative bounds of each volume texture, border included. / 各浓度体纹理（含外圈）相对相机的边界。
uniform vec3 BoxMin[MAX_CLOUDS];
uniform vec3 BoxMax[MAX_CLOUDS];
uniform vec3 BoxInvSize[MAX_CLOUDS];
// Albedo times the world light at each cloud (lightmap colour, sampled on the CPU). / 反照率乘以各烟团处的世界光照（CPU 采样的光照图颜色）。
uniform vec3 CloudLight[MAX_CLOUDS];
// World-anchored noise offsets (camera position and drift folded in on the CPU, in tile units).
// 锚定世界的噪声偏移（相机位置与漂移已在 CPU 上折算为平铺单位）。
uniform vec3 NoiseOffset;
uniform vec3 DetailOffset;
uniform float NoiseFrequency;
uniform float NoiseLodScale;
uniform float NoiseStrength;
uniform float Jitter;
// Edge breathing phase, radians (SmokeMotion). / 边缘呼吸相位（弧度，SmokeMotion）。
uniform float BreathePhase;
uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;
uniform int FogShape;

out vec4 OutSmoke;
out float OutDepth;

float tNear[MAX_CLOUDS];
float tFar[MAX_CLOUDS];

float cloudSample(int i, vec3 p) {
    vec3 uvw = (p - BoxMin[i]) * BoxInvSize[i];
    switch (i) {
        case 0: return textureLod(Volume0, uvw, 0.0).r;
        case 1: return textureLod(Volume1, uvw, 0.0).r;
        case 2: return textureLod(Volume2, uvw, 0.0).r;
        case 3: return textureLod(Volume3, uvw, 0.0).r;
        case 4: return textureLod(Volume4, uvw, 0.0).r;
        case 5: return textureLod(Volume5, uvw, 0.0).r;
        case 6: return textureLod(Volume6, uvw, 0.0).r;
        default: return textureLod(Volume7, uvw, 0.0).r;
    }
}

// Raw density at p from the clouds whose ray interval holds t, plus the light-weighted sum.
// p 处的原始浓度（只取光线区间包含 t 的烟团），以及按浓度加权的光照和。
float densityOnRay(vec3 p, float t, out vec3 light) {
    float sum = 0.0;
    light = vec3(0.0);
    for (int i = 0; i < MAX_CLOUDS; i++) {
        if (i >= CloudCount) {
            break;
        }
        if (t < tNear[i] || t > tFar[i]) {
            continue;
        }
        float d = cloudSample(i, p);
        sum += d;
        light += CloudLight[i] * d;
    }
    return sum;
}

// Raw density at an off-ray point (the self-shadow tap). / 光线外某点的原始浓度（自阴影采样）。
float densityAt(vec3 q) {
    float sum = 0.0;
    for (int i = 0; i < MAX_CLOUDS; i++) {
        if (i >= CloudCount) {
            break;
        }
        if (any(lessThan(q, BoxMin[i])) || any(greaterThan(q, BoxMax[i]))) {
            continue;
        }
        sum += cloudSample(i, q);
    }
    return sum;
}

// Mirrors SmokeMarchRules.erode: noise only ever removes density, and density 1 stays 1.
// 镜像 SmokeMarchRules.erode：噪声只会减少浓度，浓度 1 保持为 1。
float erode(float density, float shapeErosion, float detailErosion) {
    float d = clamp(density, 0.0, 1.0);
    float lo = EROSION_SHAPE * shapeErosion;
    d = clamp((d - lo) / (1.0 - lo), 0.0, 1.0);
    lo = EROSION_DETAIL * detailErosion;
    return clamp((d - lo) / (1.0 - lo), 0.0, 1.0);
}

// Mirrors SmokeMarchRules.stepLength. / 镜像 SmokeMarchRules.stepLength。
float stepLength(float t, float budget, bool empty, float transmittance) {
    float base = max(min(MIN_STEP + STEP_GROWTH * t, NEAR_STEP_CAP), budget)
            * (1.0 + OPACITY_STEP_GROWTH * (1.0 - transmittance));
    return empty ? base * EMPTY_STEP_SCALE : base;
}

// Vanilla linear fog (linear_fog / fog_distance). / 原版线性雾（linear_fog / fog_distance）。
float fogValue(vec3 p) {
    float dist = FogShape == 0 ? length(p) : max(length(p.xz), abs(p.y));
    if (dist <= FogStart) {
        return 0.0;
    }
    return (dist < FogEnd ? smoothstep(FogStart, FogEnd, dist) : 1.0) * FogColor.a;
}

// Interleaved gradient noise (Jimenez 2014). / 交错梯度噪声（Jimenez 2014）。
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

vec3 unproject(vec2 ndc, float z) {
    vec4 p = InvViewProj * vec4(ndc, z, 1.0);
    return p.xyz / p.w;
}

void main() {
    ivec2 low = ivec2(gl_FragCoord.xy);
    ivec2 full = low * 2;
    ivec2 last = ivec2(FullSize) - 1;
    float d00 = texelFetch(SceneDepth, min(full, last), 0).r;
    float d10 = texelFetch(SceneDepth, min(full + ivec2(1, 0), last), 0).r;
    float d01 = texelFetch(SceneDepth, min(full + ivec2(0, 1), last), 0).r;
    float d11 = texelFetch(SceneDepth, min(full + ivec2(1, 1), last), 0).r;
    float depth = ((low.x + low.y) & 1) == 0
            ? min(min(d00, d10), min(d01, d11))
            : max(max(d00, d10), max(d01, d11));
    vec2 ndc = (vec2(full) + 1.0) / FullSize * 2.0 - 1.0;
    vec3 origin = unproject(ndc, -1.0);
    vec3 surface = unproject(ndc, depth * 2.0 - 1.0);
    OutDepth = length(surface);

    vec3 ray = surface - origin;
    float tMax = length(ray);
    vec3 dir = ray / max(tMax, 1.0e-6);
    vec3 safeDir = mix(dir, vec3(1.0e-6), lessThan(abs(dir), vec3(1.0e-6)));
    vec3 invDir = 1.0 / safeDir;

    // Ray-box slabs (mirrors SmokeMarchRules.rayBox). / 光线-盒子板块求交（镜像 SmokeMarchRules.rayBox）。
    float tStart = tMax;
    float tEnd = 0.0;
    for (int i = 0; i < MAX_CLOUDS; i++) {
        // Sentinels: a missed box never holds t and never ends a gap. / 哨兵值：未命中的盒子既不包含 t 也不结束空隙。
        tNear[i] = 1.0e9;
        tFar[i] = -1.0;
        if (i >= CloudCount) {
            continue;
        }
        vec3 a = (BoxMin[i] - origin) * invDir;
        vec3 b = (BoxMax[i] - origin) * invDir;
        vec3 lo = min(a, b);
        vec3 hi = max(a, b);
        float tn = max(max(lo.x, lo.y), max(lo.z, 0.0));
        float tf = min(min(hi.x, hi.y), min(hi.z, tMax));
        if (tf > tn) {
            tNear[i] = tn;
            tFar[i] = tf;
            tStart = min(tStart, tn);
            tEnd = max(tEnd, tf);
        }
    }
    if (tEnd <= tStart) {
        OutSmoke = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float jitter = ign(gl_FragCoord.xy + Jitter);
    float budget = (tEnd - tStart) / float(MAX_STEPS);
    float transmittance = 1.0;
    vec3 color = vec3(0.0);
    float a = tStart;
    bool empty = false;
    float shadow = 1.0;
    float churn = 1.0;
    for (int s = 0; s < MAX_STEPS; s++) {
        // Jump over the gaps between boxes. / 跳过盒子之间的空隙。
        bool inside = false;
        float gapEnd = tEnd;
        for (int i = 0; i < MAX_CLOUDS; i++) {
            if (i >= CloudCount) {
                break;
            }
            if (a >= tNear[i] && a < tFar[i]) {
                inside = true;
            } else if (tNear[i] > a) {
                gapEnd = min(gapEnd, tNear[i]);
            }
        }
        if (!inside) {
            if (gapEnd >= tEnd) {
                break;
            }
            a = gapEnd;
        }
        float b = min(a + stepLength(a, budget, empty, transmittance), tEnd);
        float t = mix(a, b, jitter);
        float len = b - a;
        a = b;
        vec3 p = origin + dir * t;

        vec3 lightSum;
        float raw = densityOnRay(p, t, lightSum);
        if (raw <= EMPTY_DENSITY) {
            empty = true;
            continue;
        }
        empty = false;
        float density = min(raw, 1.0);
        vec3 shadowPoint = p;
        vec4 shapeNoise = vec4(0.5);
        bool haveNoise = false;
        if (raw < FULL_DENSITY) {
            // Edge zone only: full smoke stays full, so the interior needs no erosion (erode(1) == 1).
            // 仅边缘区：满浓度保持为满，因此内部不需要侵蚀（erode(1) == 1）。
            float lod = log2(max(t * NoiseLodScale, 1.0));
            shapeNoise = textureLod(Noise, p * NoiseFrequency + NoiseOffset, lod);
            haveNoise = true;
            // Edges breathe: erosion and warp swing slowly, out of phase across the cloud (render-only).
            // 边缘呼吸：侵蚀与扭曲缓慢摆动，各处相位错开（纯渲染）。
            float breath = 0.5 + 0.5 * sin(BreathePhase + 6.2831853 * shapeNoise.a);
            // Domain warp hides the half-block cell grid. / 域扭曲隐藏半格网格。
            shadowPoint = p + (shapeNoise.bra - 0.5)
                    * (2.0 * WARP_BLOCKS * NoiseStrength * (1.0 - 0.3 * breath));
            raw = densityOnRay(shadowPoint, t, lightSum);
            if (raw <= EMPTY_DENSITY) {
                continue;
            }
            // Detail only matters while the sample can still be seen. / 细节只在样本仍可见时才有意义。
            float detail = transmittance > SHADOW_MIN_TRANSMITTANCE
                    ? textureLod(Noise, p * (NoiseFrequency * DETAIL_SCALE) + DetailOffset, lod + log2(DETAIL_SCALE)).g
                    : 0.5;
            float shapeErosion = (1.0 - shapeNoise.r) * (1.0 - BREATHE_DEPTH + BREATHE_DEPTH * breath);
            density = erode(raw, shapeErosion * NoiseStrength, (1.0 - detail) * NoiseStrength);
            if (density <= 0.0) {
                continue;
            }
        }
        // Cheap self-shadow from the smoke above, modulated by the advected noise so billows of light and shade churn
        // across the face of a formed cloud; once the view is mostly blocked the last values are reused.
        // 廉价自阴影：取上方的烟，并用平流噪声调制，使成形烟团表面的明暗团块不断翻滚；视线大部分被挡住后沿用上一组值。
        if (transmittance > SHADOW_MIN_TRANSMITTANCE) {
            if (!haveNoise) {
                shapeNoise = textureLod(Noise, p * NoiseFrequency + NoiseOffset, log2(max(t * NoiseLodScale, 1.0)));
                // The warped tap keeps the shadow from tracing the cell grid. / 扭曲后的采样点使阴影不沿网格分布。
                shadowPoint = p + (shapeNoise.bra - 0.5) * (2.0 * WARP_BLOCKS * NoiseStrength);
            }
            float billow = mix(1.0, mix(1.0 - BILLOW_SHADE, 1.0 + BILLOW_SHADE, shapeNoise.r), NoiseStrength);
            float above = min(densityAt(shadowPoint + vec3(0.0, SHADOW_DISTANCE, 0.0)), 1.0) * billow;
            shadow = exp(-EXTINCTION * SHADOW_STRENGTH * SHADOW_DISTANCE * above);
            churn = mix(1.0, mix(1.0 - CHURN_SHADE, 1.0 + CHURN_SHADE, shapeNoise.g), NoiseStrength);
        }
        vec3 radiance = (lightSum / raw) * (AMBIENT_SHARE + (1.0 - AMBIENT_SHARE) * shadow) * churn;
        radiance = mix(radiance, FogColor.rgb, fogValue(p));
        float stepTransmittance = exp(-EXTINCTION * density * len);
        color += transmittance * (1.0 - stepTransmittance) * radiance;
        transmittance *= stepTransmittance;
        if (transmittance < OPAQUE_TRANSMITTANCE) {
            color += transmittance * radiance;
            transmittance = 0.0;
            break;
        }
    }
    OutSmoke = vec4(color, transmittance);
}
