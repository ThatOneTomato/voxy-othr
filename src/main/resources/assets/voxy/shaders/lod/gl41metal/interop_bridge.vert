#version 410 core

void main() {
  vec2 pos = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1);
  gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}
