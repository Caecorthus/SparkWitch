#version 150

// Schrödinger's Cat, spectre window: drains the colour out of the local view.
// 薛定谔之猫的幽魂窗口：抽掉本地画面的颜色。
uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // Luma weights (Rec.601) keep skin tones readable instead of flattening everything to one grey.
    // 使用 Rec.601 亮度权重，避免把所有颜色压成同一级灰、看不清皮肤色。
    vec4 color = texture(DiffuseSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    // 0.85 keeps a hint of contrast so the screen does not look washed out.
    // 0.85 保留一点对比，画面不会发白。
    fragColor = vec4(vec3(luma * 0.85), color.a);
}
