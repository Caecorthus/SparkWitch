#version 150

// The Blind, final pass: black everywhere except white depth-edge line art inside the sound spheres and around
// perceived bodies, plus identity-free ripples and distance-blurred silhouettes for perceived bodies behind walls.
// Colour input is never read, so sky, fog, particles, outlines and every earlier filter are consumed.
// 盲人最终步骤：除声波球内与被感知身体周围的白色深度边缘线稿外全黑；墙后的被感知身体另画无身份的涟漪与随距离
// 模糊的人形轮廓。从不读取颜色，因此天空、雾、粒子、描边以及之前的所有滤镜都会被吞掉。

uniform sampler2D DiffuseSampler;    // blind_mask: r = hidden body coverage, g = distance / 64 / 被遮挡身体遮罩
uniform sampler2D WorldDepthSampler; // captured opaque world depth / 捕获的不透明世界深度
uniform sampler2D SilBlurSampler;    // blind_blur_b: distance-blurred hidden bodies / 随距离模糊后的遮挡身体

uniform mat4 InvViewProj; // inverse(projection * view rotation): camera-relative positions / 相机相对坐标
uniform vec2 InSize;
uniform float PulseCount;
uniform float PulseData[128];  // 16 x {x, y, z, radius, age s, duration s, expand s, kind}, camera-relative
uniform float PlayerCount;
uniform float PlayerData[64];  // 16 x {x, y, z, strength}: perceived body centres, camera-relative
uniform float PlayerHidden[16]; // 0..1 per body: its centre is behind a block (CPU line of sight) / 身体中心被方块挡住的程度
uniform float BlurRange;       // 0 = the blur passes were skipped this frame / 0 表示本帧跳过了模糊 pass
uniform float EchoTime;        // seconds, wraps / 秒，循环

in vec2 texCoord;

out vec4 fragColor;

const int MAX_PULSES = 16;
const int PULSE_STRIDE = 8;
const int MAX_PLAYERS = 16;
const int PLAYER_STRIDE = 4;
const float PLAYER_REVEAL_RADIUS = 1.3;
const float RIPPLE_PERIOD = 0.9;

vec3 viewPos(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// 1 - cos of the turn between the two one-sided tangents: 0 on a plane, high at creases and depth jumps.
// 两个单侧切线夹角的 1 - cos：平面上为 0，折角与深度跳变处很大。
float bend(vec3 a, vec3 c, vec3 b) {
    vec3 t0 = c - a;
    vec3 t1 = b - c;
    float l0 = length(t0);
    float l1 = length(t1);
    if (l0 < 1.0e-6 || l1 < 1.0e-6) {
        return 0.0;
    }
    return 1.0 - dot(t0 / l0, t1 / l1);
}

float edges(vec2 uv, vec3 p, float range) {
    vec2 texel = 1.0 / InSize;
    // Clamp like the sampler does, so a border neighbour is the centre pixel itself (no fake edge on the frame).
    // 与采样器一样夹取坐标，使边框处的邻居就是中心像素本身（画面边缘不会出现假线）。
    vec2 lo = texel * 0.5;
    vec2 hi = 1.0 - texel * 0.5;
    vec2 ul = clamp(uv - vec2(texel.x, 0.0), lo, hi);
    vec2 ur = clamp(uv + vec2(texel.x, 0.0), lo, hi);
    vec2 uu = clamp(uv + vec2(0.0, texel.y), lo, hi);
    vec2 ub = clamp(uv - vec2(0.0, texel.y), lo, hi);
    float dl = texture(WorldDepthSampler, ul).r;
    float dr = texture(WorldDepthSampler, ur).r;
    float du = texture(WorldDepthSampler, uu).r;
    float db = texture(WorldDepthSampler, ub).r;
    if (max(max(dl, dr), max(du, db)) >= 1.0) {
        return 1.0; // outline against the void / 与虚空交界的轮廓
    }
    float b = max(bend(viewPos(ul, dl), p, viewPos(ur, dr)), bend(viewPos(ub, db), p, viewPos(uu, du)));
    // Thresholds rise with distance so far reveals keep only strong edges (depth precision falls off).
    // 阈值随距离升高，远处只保留明显的边（深度精度下降）。
    float low = 0.04 + range * 0.0015;
    return smoothstep(low, low + 0.08 + range * 0.001, b);
}

float pulseReveal(vec3 p, out float wave) {
    float reveal = 0.0;
    wave = 0.0;
    int count = int(PulseCount + 0.5);
    for (int i = 0; i < MAX_PULSES; i++) {
        if (i >= count) {
            break;
        }
        int o = i * PULSE_STRIDE;
        vec3 center = vec3(PulseData[o], PulseData[o + 1], PulseData[o + 2]);
        float radius = PulseData[o + 3];
        float age = PulseData[o + 4];
        float duration = max(PulseData[o + 5], 0.001);
        float expand = max(PulseData[o + 6], 0.001);
        float grow = clamp(age / expand, 0.0, 1.0);
        float front = radius * (1.0 - (1.0 - grow) * (1.0 - grow));
        float dist = length(p - center);
        float inside = 1.0 - smoothstep(front - 0.6, front, dist);
        float fade = 1.0 - smoothstep(max(expand, duration * 0.4), duration, age);
        float rim = mix(1.0, 0.6, smoothstep(radius * 0.6, radius, dist));
        reveal = max(reveal, inside * fade * rim);
        float sweep = 1.0 - smoothstep(expand, expand + 0.3, age);
        wave = max(wave, exp(-abs(dist - front) * 7.0) * sweep * fade);
    }
    return reveal;
}

float playerReveal(vec3 p) {
    float reveal = 0.0;
    int count = int(PlayerCount + 0.5);
    for (int i = 0; i < MAX_PLAYERS; i++) {
        if (i >= count) {
            break;
        }
        int o = i * PLAYER_STRIDE;
        vec3 center = vec3(PlayerData[o], PlayerData[o + 1], PlayerData[o + 2]);
        float near = 1.0 - smoothstep(PLAYER_REVEAL_RADIUS - 0.3, PLAYER_REVEAL_RADIUS, length(p - center));
        reveal = max(reveal, near * PlayerData[o + 3]);
    }
    return reveal;
}

// Identity-free ripple: expanding spherical shells at each perceived body whose centre is hidden; Java decides
// "hidden" once per body (line of sight), so no pixel re-projects or samples depth per body.
// 无身份信息的涟漪：在每个中心被遮挡的被感知身体处扩张的球壳；“被遮挡”由 Java 对每个身体判定一次（视线），
// 因此像素无需逐身体重新投影或采样深度。
float ripples(vec2 uv) {
    int count = int(PlayerCount + 0.5);
    bool anyHidden = false;
    for (int i = 0; i < MAX_PLAYERS; i++) {
        if (i >= count) {
            break;
        }
        anyHidden = anyHidden || PlayerHidden[i] > 0.0;
    }
    if (!anyHidden) {
        return 0.0;
    }
    vec3 dir = normalize(viewPos(uv, 1.0));
    vec3 dirUp = normalize(viewPos(uv + vec2(0.0, 1.0 / InSize.y), 1.0));
    float pixelAngle = max(length(dirUp - dir), 1.0e-5);
    float ripple = 0.0;
    for (int i = 0; i < MAX_PLAYERS; i++) {
        if (i >= count) {
            break;
        }
        float hidden = PlayerHidden[i];
        if (hidden <= 0.0) {
            continue;
        }
        int o = i * PLAYER_STRIDE;
        vec3 center = vec3(PlayerData[o], PlayerData[o + 1], PlayerData[o + 2]);
        float centerDistance = length(center);
        if (centerDistance < 0.75) {
            continue;
        }
        float cosAngle = dot(dir, center / centerDistance);
        if (cosAngle <= 0.0) {
            continue;
        }
        float angle = acos(clamp(cosAngle, -1.0, 1.0));
        float width = 1.3 * pixelAngle;
        for (int k = 0; k < 2; k++) {
            float t = fract(EchoTime / RIPPLE_PERIOD + float(k) * 0.5 + float(i) * 0.37);
            float shell = mix(0.3, 1.6, t);
            float shellAngle = asin(min(shell / centerDistance, 1.0));
            float ring = exp(-abs(angle - shellAngle) / width) * (1.0 - t) * smoothstep(0.0, 0.1, t);
            ripple = max(ripple, ring * hidden * PlayerData[o + 3]);
        }
    }
    return ripple * 0.75;
}

// Hidden bodies: a crisp outline with a faint fill up close, a soft blob far away, fainter with distance (D1).
// 被遮挡的身体：近处为清晰轮廓加淡淡的填充，远处为柔和的一团，随距离变淡（D1）。
float silhouettes(vec2 uv) {
    if (PlayerCount < 0.5) {
        return 0.0;
    }
    vec4 mask = texture(DiffuseSampler, uv);
    // A skipped blur leaves last frame's halo in the target: ignore it. / 跳过模糊时目标里是上一帧的光晕：忽略。
    vec4 blurred = BlurRange > 0.0 ? texture(SilBlurSampler, uv) : vec4(0.0);
    if (mask.r <= 0.0 && blurred.r <= 0.0) {
        return 0.0;
    }
    vec2 texel = 1.0 / InSize;
    float l = texture(DiffuseSampler, uv - vec2(texel.x, 0.0)).r;
    float r = texture(DiffuseSampler, uv + vec2(texel.x, 0.0)).r;
    float u = texture(DiffuseSampler, uv + vec2(0.0, texel.y)).r;
    float b = texture(DiffuseSampler, uv - vec2(0.0, texel.y)).r;
    float outline = max(max(abs(mask.r - l), abs(mask.r - r)), max(abs(mask.r - u), abs(mask.r - b)));
    // Each body pixel keeps its own distance; the blurred halo uses its contributors' distance.
    // 每个身体像素使用自己的距离；模糊光晕使用其来源的距离。
    float range = (mask.r > 0.0 ? mask.g : blurred.g) * 64.0;
    float crisp = max(outline, 0.22 * mask.r);
    float soft = blurred.r * 0.6;
    float body = mix(crisp, soft, smoothstep(3.5, 10.0, range));
    return body * mix(1.0, 0.25, smoothstep(4.0, 40.0, range));
}

void main() {
    float depth = texture(WorldDepthSampler, texCoord).r;
    float line = 0.0;
    if (depth < 1.0) {
        vec3 p = viewPos(texCoord, depth);
        float range = length(p);
        float wave;
        float reveal = max(pulseReveal(p, wave), playerReveal(p));
        if (reveal > 0.003) {
            line = edges(texCoord, p, range) * reveal * mix(1.0, 0.6, smoothstep(30.0, 150.0, range));
        }
        line = max(line, wave * 0.3);
    }
    float value = max(line, max(silhouettes(texCoord), ripples(texCoord)));
    fragColor = vec4(vec3(clamp(value, 0.0, 1.0)), 1.0);
}
