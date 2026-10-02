#version 150

// The Blind, pass 1: keeps only the parts of perceived players' bodies that the captured world hides (through walls).
// 盲人第 1 步：只保留被已捕获世界深度挡住（墙后）的被感知玩家身体部分。
// Output: r = hidden coverage, g = body distance / 64 (drives the distance blur and fade).
// 输出：r = 被遮挡的覆盖度，g = 身体距离 / 64（决定随距离的模糊与淡出）。

uniform sampler2D DiffuseSampler;    // perceived bodies, white, own depth test / 被感知玩家身体（白色，自带深度测试）
uniform sampler2D WorldDepthSampler; // opaque world depth captured before the debug render / 调试渲染前捕获的不透明世界深度
uniform sampler2D SilDepthSampler;   // depth of the nearest perceived body / 最近的被感知身体深度

uniform mat4 InvViewProj;

in vec2 texCoord;

out vec4 fragColor;

vec3 viewPos(vec2 uv, float depth) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

void main() {
    float coverage = texture(DiffuseSampler, texCoord).r;
    float bodyDepth = texture(SilDepthSampler, texCoord).r;
    if (coverage <= 0.0 || bodyDepth >= 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    float bodyDistance = length(viewPos(texCoord, bodyDepth));
    float worldDepth = texture(WorldDepthSampler, texCoord).r;
    float worldDistance = worldDepth >= 1.0 ? 1.0e6 : length(viewPos(texCoord, worldDepth));
    // A body pixel in line of sight is the same surface as the world pixel; only clearly nearer world surfaces hide it.
    // 视线内的身体像素与世界像素是同一表面；只有明显更近的世界表面才算遮挡。
    float hidden = smoothstep(0.08, 0.3, bodyDistance - worldDistance);
    fragColor = vec4(coverage * hidden, clamp(bodyDistance / 64.0, 0.0, 1.0), 0.0, 1.0);
}
