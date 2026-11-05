#version 150

// BEAR smoke, pass 1 of 2: half-resolution ray march through up to MAX_CLOUDS density volumes (SmokeVolumes).
// Writes premultiplied in-scattered colour + transmittance, and the eye distance of the surface this texel marched to
// (a checkerboard of the nearest and farthest of its 2x2 full-resolution depths) for the depth-aware upsample.
// Lighting: lightmap ambient (block light indoors, sky outside, dark at night) with sky occlusion, plus single
// scattering of the sun/moon with a 3-tap shadow march. Every #define comes from SmokeMarchRules (injected by
// SmokeProgram); the Java side mirrors the step, erosion and ray-box rules and its tests pin them.
// BEAR 烟雾第 1 步（共 2 步）：半分辨率光线步进，穿过至多 MAX_CLOUDS 个浓度体（SmokeVolumes）。输出预乘的散射颜色与
// 透射率，以及该纹素步进终点表面的视距（其 2x2 全分辨率深度按棋盘格交替取最近/最远），供深度感知放大使用。
// 光照：光照图环境光（室内为方块光、室外为天空光、夜晚变暗）加天空遮蔽，以及日月光单次散射与 3 次阴影步进。
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
// rgb: albedo times the ambient world light at each cloud (lightmap colour, sampled on the CPU); a: how much of the
// cloud sees the sky (0 indoors), which gates the direct sun/moon light.
// rgb：反照率乘以各烟团处的环境世界光照（CPU 采样的光照图颜色）；a：烟团见天的程度（室内为 0），决定是否有日月直射光。
uniform vec4 CloudLight[MAX_CLOUDS];
// Direction toward the sun or moon (whichever is up) and its colour times strength times albedo; skyward ambient tint.
// 指向太阳或月亮（取在地平线上的那个）的方向，及其颜色 * 强度 * 反照率；朝天环境光色调。
uniform vec3 LightDir;
uniform vec3 LightColor;
uniform vec3 SkyTint;
// World-anchored noise offsets (camera position and drift folded in on the CPU, in tile units).
// 锚定世界的噪声偏移（相机位置与漂移已在 CPU 上折算为平铺单位）。
uniform vec3 NoiseOffset;
uniform vec3 DetailOffset;
uniform vec3 BigOffset;
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

// Raw density at p from the clouds whose ray interval holds t, plus the density-weighted cloud light and the cloud
// that holds most of it (the lighting taps sample only that one).
// p 处的原始浓度（只取光线区间包含 t 的烟团），以及按浓度加权的烟团光照与贡献最大的烟团（光照采样只取它）。
float densityOnRay(vec3 p, float t, out vec4 light, out int dominant) {
    float sum = 0.0;
    float best = -1.0;
    dominant = 0;
    light = vec4(0.0);
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
        if (d > best) {
            best = d;
            dominant = i;
        }
    }
    return sum;
}

// Lighting tap in one cloud; outside its box the clamped empty border reads 0. / 单个烟团内的光照采样；盒外读到钳制的空外圈，为 0。
float lightTap(int cloud, vec3 q) {
    return cloudSample(cloud, q);
}

// Mirrors SmokeMarchRules.erode: noise only ever removes density. / 镜像 SmokeMarchRules.erode：噪声只会减少浓度。
float erode(float density, float patchErosion, float shapeErosion, float detailErosion) {
    float d = clamp(density, 0.0, 1.0);
    float lo = EROSION_PATCH * patchErosion;
    d = clamp((d - lo) / (1.0 - lo), 0.0, 1.0);
    lo = EROSION_SHAPE * shapeErosion;
    d = clamp((d - lo) / (1.0 - lo), 0.0, 1.0);
    lo = EROSION_DETAIL * detailErosion;
    d = clamp((d - lo) / (1.0 - lo), 0.0, 1.0);
    return d * (1.0 - INTERIOR_THINNING * patchErosion);
}

// Mirrors SmokeMarchRules.stepLength. / 镜像 SmokeMarchRules.stepLength。
float stepLength(float t, float budget, bool empty, float transmittance, float lastDensity) {
    float base = max(min(MIN_STEP + STEP_GROWTH * t, NEAR_STEP_CAP), budget)
            * (1.0 + OPACITY_STEP_GROWTH * (1.0 - transmittance))
            * (1.0 + THIN_STEP_GROWTH * (1.0 - lastDensity));
    return empty ? base * EMPTY_STEP_SCALE : base;
}

// Henyey-Greenstein phase relative to isotropic, blended with isotropic. / 相对各向同性的 HG 相函数，再与各向同性混合。
float phase(float cosTheta) {
    float g = PHASE_G;
    float hg = (1.0 - g * g) / pow(1.0 + g * g - 2.0 * g * cosTheta, 1.5);
    return mix(1.0, hg, PHASE_SHARE);
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

    // Ray-box slabs (mirrors SmokeMarchRules.rayBox), widened by the warp so outward wisps are reached.
    // 光线-盒子板块求交（镜像 SmokeMarchRules.rayBox），按扭曲幅度外扩，使向外的烟丝也能被步进到。
    float tStart = tMax;
    float tEnd = 0.0;
    for (int i = 0; i < MAX_CLOUDS; i++) {
        // Sentinels: a missed box never holds t and never ends a gap. / 哨兵值：未命中的盒子既不包含 t 也不结束空隙。
        tNear[i] = 1.0e9;
        tFar[i] = -1.0;
        if (i >= CloudCount) {
            continue;
        }
        vec3 a = (BoxMin[i] - WARP_REACH - origin) * invDir;
        vec3 b = (BoxMax[i] + WARP_REACH - origin) * invDir;
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
    float lastDensity = 1.0;
    vec3 radiance = vec3(0.0);
    float scatter = phase(dot(LightDir, dir));
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
        float b = min(a + stepLength(a, budget, empty, transmittance, lastDensity), tEnd);
        float t = mix(a, b, jitter);
        float len = b - a;
        a = b;
        vec3 p = origin + dir * t;
        bool visible = transmittance > VISIBLE_TRANSMITTANCE;

        float lod = log2(max(t * NoiseLodScale, 1.0));
        // Big billows (thick and thin regions), advected (SmokeMotion). / 大团块（浓淡区域），随平流移动（SmokeMotion）。
        vec4 big = textureLod(Noise, p * (NoiseFrequency * BIG_SCALE) + BigOffset, lod + log2(BIG_SCALE));
        vec4 shapeNoise = vec4(0.5);
        vec4 detail = vec4(0.5);
        vec4 light;
        int dominant;
        float raw;
        float density;
        vec3 q = p;
        float breath = 0.5;
        if (!visible) {
            // Mostly hidden already: unwarped density, big-billow thinning only. / 已大多被遮住：未扭曲浓度，只做大团块变稀。
            raw = densityOnRay(p, t, light, dominant);
            if (raw <= EMPTY_DENSITY) {
                empty = true;
                continue;
            }
            density = erode(raw, (1.0 - big.g) * NoiseStrength, 0.0, 0.0);
        } else {
            // Curls from three warp scales hide the half-block grid; erosion then carves ragged, semi-transparent
            // edges and thin patches. / 三种尺度的扭曲卷曲隐藏半格网格；侵蚀再刻出破碎、半透明的边缘与稀薄区域。
            shapeNoise = textureLod(Noise, p * NoiseFrequency + NoiseOffset, lod);
            detail = textureLod(Noise, p * (NoiseFrequency * DETAIL_SCALE) + DetailOffset, lod + log2(DETAIL_SCALE));
            breath = 0.5 + 0.5 * sin(BreathePhase + 6.2831853 * shapeNoise.a);
            vec3 warp = (big.bra - 0.5) * BIG_WARP + (shapeNoise.bra - 0.5) * (WARP_BLOCKS * (1.0 - 0.3 * breath))
                    + (detail.bra - 0.5) * FINE_WARP;
            warp *= 2.0 * NoiseStrength;
            q = p + warp;
            raw = densityOnRay(q, t, light, dominant);
            // The mirrored tap (p - warp) widens the boundary into a semi-transparent band of wisps; it may only add,
            // so a warp into a wall or floor still keeps half the smoke beside it.
            // 镜像采样（p - warp）把边界展宽成半透明的烟丝带；它只能增加浓度，所以即使扭曲进墙或地面，旁边仍保留一半的烟。
            vec4 mirrorLight;
            int mirrorDominant;
            float mirrored = densityOnRay(p - warp, t, mirrorLight, mirrorDominant);
            if (mirrored > raw) {
                light = 0.5 * (light + mirrorLight);
                raw = 0.5 * (raw + mirrored);
                dominant = mirrorDominant;
            }
            if (raw <= EMPTY_DENSITY) {
                empty = true;
                continue;
            }
            // Cellular big-scale erosion tears the boundary band into irregular tendrils and thin patches.
            // 大尺度胞元侵蚀把边界带撕成不规则的烟缕与稀薄区域。
            density = erode(raw, (1.0 - big.g) * NoiseStrength,
                    (1.0 - shapeNoise.r) * (1.0 - BREATHE_DEPTH + BREATHE_DEPTH * breath) * NoiseStrength,
                    (1.0 - detail.g) * NoiseStrength);
        }
        empty = false;
        lastDensity = density;
        if (density <= 0.0) {
            continue;
        }
        if (visible) {
            vec3 cloudAmbient = light.rgb / raw;
            float skyExposure = light.a / raw;
            // Sky occlusion: smoke above this point hides part of the sky dome; the billow term varies it with the
            // advected noise. / 天空遮蔽：上方的烟挡住部分天穹；团块项随平流噪声变化。
            float billow = mix(1.0, mix(1.0 - BILLOW_SHADE, 1.0 + BILLOW_SHADE, shapeNoise.r), NoiseStrength);
            // Every lighting tap starts from the warped point, so shadows never trace the cell grid.
            // 所有光照采样都从扭曲后的点出发，阴影因此不会沿网格分布。
            float above = lightTap(dominant, q + vec3(0.0, AO_DISTANCE, 0.0)) * billow;
            float occlusion = exp(-EXTINCTION * AO_DISTANCE * above);
            vec3 tint = mix(vec3(1.0), SkyTint, SKY_TINT * skyExposure);
            float ambientScale = mix(INDOOR_AMBIENT, OUTDOOR_AMBIENT, skyExposure);
            radiance = cloudAmbient * tint * ambientScale * (AMBIENT_MIN + (1.0 - AMBIENT_MIN) * occlusion);
            if (skyExposure > 0.02 && dot(LightColor, LightColor) > 1.0e-6) {
                // Single scattering of the sun/moon with a short shadow march toward it.
                // 日月光的单次散射，配合朝光源的短阴影步进。
                // Three taps at 0.3 / 1.0 / 2.2 blocks covering 0..3.2 blocks. / 三个采样点覆盖 0..3.2 格。
                float opticalDepth = lightTap(dominant, q + LightDir * 0.3) * 0.6
                        + lightTap(dominant, q + LightDir * 1.0) * 0.9
                        + lightTap(dominant, q + LightDir * 2.2) * 1.7;
                float lit = exp(-EXTINCTION * LIGHT_DENSITY_SCALE * opticalDepth);
                radiance += LightColor * skyExposure * scatter * lit;
            }
            radiance *= mix(1.0, mix(1.0 - CHURN_SHADE, 1.0 + CHURN_SHADE, shapeNoise.g), NoiseStrength);
            radiance = mix(radiance, FogColor.rgb, fogValue(p));
        }
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
