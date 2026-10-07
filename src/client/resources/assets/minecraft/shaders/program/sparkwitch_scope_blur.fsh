#version 150

// SparkWitch scope, passes 1-2: one direction of a separable Gaussian, written at half resolution (Java halves both
// blur targets). Pass 1 reads the full-size main target with linear filtering, so each tap also averages a 2x2 block
// (the downsample). Steps are in OUTPUT texels and the radius scales with the target height, so the blur covers the
// same share of the screen at any resolution.
// SparkWitch 开镜第 1-2 步：可分离高斯模糊的一个方向，以半分辨率写出（Java 把两个模糊目标减半）。第 1 步以线性过滤读取
// 全尺寸 main，因此每个采样同时平均 2x2 像素块（即降采样）。步长以输出纹素计，半径随目标高度缩放，因此任意分辨率下模糊
// 覆盖屏幕的比例相同。

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
uniform vec2 BlurDir;

in vec2 texCoord;

out vec4 fragColor;

// Taps per side, and the radius in output texels on a 540-texel-tall target (1080p at half resolution).
// 每侧采样数，以及 540 纹素高目标（1080p 的一半）上以输出纹素计的半径。
const int TAPS = 12;
const float RADIUS_AT_540 = 18.0;

void main() {
    vec2 size = max(OutSize, vec2(1.0));
    float radius = max(RADIUS_AT_540 * size.y / 540.0, 1.0);
    float spacing = max(radius / float(TAPS), 1.0);
    float sigma = radius / 2.5;
    vec2 stepUv = BlurDir / size * spacing;
    vec3 sum = texture(DiffuseSampler, texCoord).rgb;
    float total = 1.0;
    for (int i = 1; i <= TAPS; i++) {
        float x = float(i) * spacing;
        float weight = exp(-0.5 * x * x / (sigma * sigma));
        sum += (texture(DiffuseSampler, texCoord + stepUv * float(i)).rgb
                + texture(DiffuseSampler, texCoord - stepUv * float(i)).rgb) * weight;
        total += 2.0 * weight;
    }
    fragColor = vec4(sum / total, 1.0);
}
