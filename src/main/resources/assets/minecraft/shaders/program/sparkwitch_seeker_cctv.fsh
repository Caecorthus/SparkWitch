#version 150

// SparkWitch Seeker remote view (car / camera CCTV look): tint, desaturation, scanlines, vignette, film grain.
// Interference adds rolling band jitter and extra static; SignalLost blanks the feed to near-black static.
// Uniform values come from shaders/post/seeker_car.json and seeker_camera.json; Java sets only Interference/SignalLost.
// 搜寻者遥控视角（小车 / 摄像头监控）：色调、去饱和、扫描线、暗角与胶片噪点。Interference 增加滚动条带抖动与噪声；
// SignalLost 把画面遮成近黑的雪花。参数来自两个 post json，Java 只设置 Interference 与 SignalLost。

uniform sampler2D DiffuseSampler;

uniform vec2 InSize;
uniform float Time;
uniform vec3 Tint;
uniform float Saturation;
uniform float Contrast;
uniform float Brightness;
uniform float ScanlineStrength;
uniform float VignetteStrength;
uniform float GrainStrength;
uniform float Interference;
uniform float SignalLost;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 uv = texCoord;
    float frame = floor(Time * 60.0);

    // Rolling horizontal tear bands while the link is weak. / 信号弱时的水平撕裂条带。
    if (Interference > 0.0) {
        float band = hash(vec2(floor(uv.y * 40.0), frame));
        float tear = step(1.0 - 0.18 * Interference, band);
        uv.x += (band - 0.5) * 0.03 * Interference * tear;
    }

    vec3 color = texture(DiffuseSampler, clamp(uv, 0.0, 1.0)).rgb;

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, Saturation);
    color = (color - 0.5) * Contrast + 0.5;
    color *= Tint * Brightness;

    // One dark line every two physical pixels. / 每两个物理像素一条暗线。
    float scan = 0.5 + 0.5 * cos(texCoord.y * InSize.y * 3.14159265);
    color *= 1.0 - ScanlineStrength * scan;

    vec2 centered = texCoord - 0.5;
    centered.x *= InSize.x / max(InSize.y, 1.0);
    float vignette = 1.0 - smoothstep(0.35, 0.95, length(centered));
    color *= mix(1.0, vignette, VignetteStrength);

    float noise = hash(texCoord * InSize + vec2(frame * 1.37, frame * 2.11)) - 0.5;
    color += noise * (GrainStrength + 0.25 * Interference);

    if (SignalLost > 0.5) {
        float snow = hash(floor(texCoord * InSize * 0.5) + vec2(frame, frame * 0.7));
        color = vec3(0.04 + 0.12 * snow);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
