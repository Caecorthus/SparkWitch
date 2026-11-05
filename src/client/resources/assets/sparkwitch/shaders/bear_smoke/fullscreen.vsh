#version 150

// BEAR smoke: one attribute-less triangle that covers the viewport (raw GL program, see SmokeProgram).
// BEAR 烟雾：覆盖整个视口的无顶点属性三角形（原生 GL 程序，见 SmokeProgram）。

void main() {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
