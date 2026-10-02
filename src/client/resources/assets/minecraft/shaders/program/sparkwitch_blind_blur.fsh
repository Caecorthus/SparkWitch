#version 150

// The Blind, passes 2-3: separable blur of the hidden-body mask whose radius grows with each body's distance
// (crisp within a few blocks, a soft blob far away). Every source pixel scatters its own normalised Gaussian.
// 盲人第 2-3 步：对被遮挡身体遮罩做可分离模糊，半径随每个身体的距离增大（几格内清晰，远处只剩一团柔和影子）。
// 每个源像素按自身的归一化高斯核扩散。
// In/out: r = coverage, g = distance / 64. / 输入输出：r = 覆盖度，g = 距离 / 64。

uniform sampler2D DiffuseSampler;

uniform vec2 InSize;
uniform vec2 BlurDir;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_TAPS = 24;

float sigmaFor(float code, float scale) {
    float range = code * 64.0;
    return min(clamp((range - 3.0) * 0.5, 0.0, 8.0) * scale, float(MAX_TAPS) / 3.0);
}

void main() {
    vec2 texel = BlurDir / InSize;
    float scale = clamp(InSize.y / 1080.0, 0.5, 2.0);
    float sum = 0.0;
    float code = 0.0;
    for (int i = -MAX_TAPS; i <= MAX_TAPS; i++) {
        vec4 source = texture(DiffuseSampler, texCoord + texel * float(i));
        if (source.r <= 0.0) {
            continue;
        }
        float sigma = sigmaFor(source.g, scale);
        float x = float(i);
        float weight;
        if (sigma < 0.5) {
            weight = i == 0 ? 1.0 : 0.0;
        } else {
            weight = exp(-0.5 * x * x / (sigma * sigma)) / (2.5066283 * sigma);
        }
        if (weight > 0.0005) {
            sum += source.r * weight;
            code = max(code, source.g);
        }
    }
    fragColor = vec4(min(sum, 1.0), code, 0.0, 1.0);
}
