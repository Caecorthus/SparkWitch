#version 150

// Adapted from SparkTraits depression grayscale, itself adapted from StarRailExpress insanity.fsh.
// License: GPL-3.0-only. SparkWitch keeps only the desaturation pass at a fixed 50 percent.
// 来源：SparkTraits 抑郁灰阶效果与 StarRailExpress insanity.fsh；这里只保留固定 50% 去饱和。

uniform sampler2D DiffuseSampler;
uniform float DesaturateFactor;

in vec2 texCoord;

out vec4 fragColor;

vec3 desaturate(vec3 color, float factor)
{
    vec3 luma = vec3(0.299, 0.587, 0.114);
    vec3 gray = vec3(dot(luma, color));
    return vec3(mix(color, gray, factor));
}

void main()
{
    vec4 color = texture(DiffuseSampler, texCoord);
    color.rgb = desaturate(color.rgb, DesaturateFactor);
    fragColor = color;
}
