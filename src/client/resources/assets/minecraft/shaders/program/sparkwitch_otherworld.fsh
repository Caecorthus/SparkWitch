#version 150

// Otherworld screen filter, "Yin realm" look (owner pick 2026-11-11): the world turns ghost-fire teal, only vivid reds
// stay, distance sinks into teal-black fog, the screen edge ghosts and breathes. The switch is a wave that sweeps
// outward through depth when the window opens. Output is opaque and the program blends one/zero: vanilla clears the
// main target with alpha 0, so passing the sampled alpha through would turn sky/clear-colour pixels black.
// 里世界画面滤镜「阴间」（所有者 2026-11-11 选定）：世界转为鬼火青绿，只保留鲜红，远处沉入青黑雾气，屏幕边缘重影并缓慢
// 呼吸。窗口开启时由一道沿纵深向外扫过的波纹完成切换。输出不透明，程序混合模式为 one/zero：原版以 alpha 0 清空主帧缓冲，
// 透传采样的 alpha 会让天空/清屏颜色像素变黑。
uniform sampler2D DiffuseSampler;
uniform sampler2D WorldDepthSampler; // world depth copied during the world pass / 世界 pass 期间拷贝的世界深度
uniform sampler2D HandDepthSampler;  // main depth at filter time: vanilla cleared it for the hand / 滤镜时刻的主深度：原版已为手部清空

uniform vec2 InSize;
uniform float Intensity;    // 1 while the window is open, 1 -> 0 over the closing fade / 窗口开启时为 1，结束渐隐时 1 -> 0
uniform float OpenSeconds;  // seconds since the window opened (large while fading) / 窗口开启以来的秒数（渐隐时为大值）
uniform float LookTime;     // free-running seconds, wraps / 自由运行的秒数，循环
uniform float Near;
uniform float Far;
uniform float HasDepth;     // 0 = no world depth this frame: no wave, no fog / 0 表示本帧没有世界深度：无波纹、无雾
uniform float Distortion;   // vanilla "Distortion Effects" accessibility scale / 原版“扭曲效果”无障碍缩放

in vec2 texCoord;

out vec4 fragColor;

const float MAX_DISTANCE = 44.0;
const vec3 FOG = vec3(0.012, 0.055, 0.06);

float hash(vec2 p)
{
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p)
{
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p)
{
    float amplitude = 0.5;
    float sum = 0.0;
    for (int i = 0; i < 4; i++) {
        sum += amplitude * valueNoise(p);
        p *= 2.03;
        amplitude *= 0.5;
    }
    return sum;
}

// View distance in blocks, capped. The first-person hand/item counts as distance 0: it is whatever was drawn into the
// main depth after vanilla cleared it (when nothing cleared it, main still equals the copy and nothing is a hand).
// 以方块计的视距（有上限）。第一人称手部/物品按距离 0 处理：即原版清空主深度后画进去的内容（若未清空，主深度仍等于拷贝，
// 不会有像素被当作手部）。
float viewDistance(vec2 uv)
{
    if (HasDepth < 0.5) {
        return 0.0;
    }
    float world = texture(WorldDepthSampler, uv).r;
    float hand = texture(HandDepthSampler, uv).r;
    if (hand < 1.0 && abs(hand - world) > 1.0e-6) {
        return 0.0;
    }
    if (world >= 1.0) {
        return MAX_DISTANCE;
    }
    float ndc = world * 2.0 - 1.0;
    return min(2.0 * Near * Far / (Far + Near - ndc * (Far - Near)), MAX_DISTANCE);
}

// Split tone by brightness (teal-black shadows, teal mids, pale bone-green highlights); a colour that is mostly red
// keeps its red. Then the depth fog.
// 按亮度分色（青黑暗部、青绿中间调、惨白偏绿高光）；以红为主的颜色保留红色。之后叠加纵深雾。
vec3 grade(vec3 color, float fog)
{
    float luma = pow(dot(color, vec3(0.299, 0.587, 0.114)), 1.12);
    float warmth = (color.r - max(color.g, color.b)) / max(color.r, 1.0e-3);
    float keep = clamp(warmth * 2.6 - 1.0, 0.0, 1.0);
    vec3 graded = mix(vec3(0.02, 0.065, 0.075), vec3(0.17, 0.44, 0.40), smoothstep(0.0, 0.40, luma));
    graded = mix(graded, vec3(0.78, 0.98, 0.84), smoothstep(0.4, 1.15, luma));
    graded = mix(graded, vec3(0.9, 0.05, 0.06) * (0.22 + luma * 1.7), keep * 0.9);
    return mix(graded, FOG, fog);
}

void main()
{
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    if (Intensity <= 0.0) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec2 centred = texCoord * 2.0 - 1.0;
    float radius = length(centred * vec2(1.0, 0.85));
    float dist = viewDistance(texCoord);

    // Edge breathing warp and double vision. / 边缘呼吸扭曲与重影。
    float breathe = smoothstep(0.35, 1.3, radius) * Distortion;
    vec2 warped = texCoord + centred * 0.006 * breathe * sin(LookTime * 0.8 + radius * 5.0);
    vec2 ghost = centred * 0.014 * breathe * (1.0 + 0.4 * sin(LookTime * 1.3));

    float fog = (1.0 - exp(-max(dist - 4.0, 0.0) * 0.075))
            * (0.7 + 0.3 * fbm(texCoord * 3.0 + vec2(LookTime * 0.05, 0.0))) * 0.9;
    vec3 look = grade(texture(DiffuseSampler, warped).rgb, fog);
    vec3 doubled = 0.5 * (grade(texture(DiffuseSampler, warped + ghost).rgb, fog)
            + grade(texture(DiffuseSampler, warped - ghost).rgb, fog));
    look = mix(look, max(look * 0.75, doubled), 0.6 * breathe);

    // Pale wisps drifting through the distance. / 远处飘动的惨白雾丝。
    float wisp = fbm(vec2(texCoord.x * 3.0 + LookTime * 0.07, texCoord.y * 7.0 - LookTime * 0.11));
    look += vec3(0.25, 0.55, 0.5) * smoothstep(0.52, 0.85, wisp) * 0.22 * smoothstep(3.0, 14.0, dist);

    look *= 1.0 - 0.6 * smoothstep(0.7, 1.5, radius);
    look += (hash(texCoord * InSize + fract(LookTime) * 91.7) - 0.5) * 0.05;

    // Opening wave: starts slow at the viewer, accelerates down the corridor, reaches MAX_DISTANCE in about 1.3 s.
    // 开场波纹：从观察者处慢起，沿走廊加速，约 1.3 秒到达 MAX_DISTANCE。
    float front = OpenSeconds * (5.0 + 26.0 * OpenSeconds);
    float turned = 1.0 - smoothstep(front - 3.0, front, dist);
    float rimWidth = 1.0 + 0.07 * front;
    float rimOffset = (dist - front) / rimWidth;
    float rim = exp(-rimOffset * rimOffset) * step(OpenSeconds, 1.7) * HasDepth * Intensity;
    // The sky and the far distance all sit at the cap: keep the wave leaving from flashing them at full strength.
    // 天空与远处都处于距离上限：避免波纹离开时让它们整片全亮闪一下。
    rim *= 1.0 - 0.6 * smoothstep(30.0, MAX_DISTANCE, dist);

    vec3 result = mix(base + vec3(0.42, 0.78, 0.7) * rim, look, turned * Intensity);
    fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
