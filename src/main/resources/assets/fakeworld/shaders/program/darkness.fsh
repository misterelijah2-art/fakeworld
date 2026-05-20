#version 150

uniform sampler2D DiffuseSampler;
uniform float DarknessAmount;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Distance from screen center (0.0 = center, ~0.7 = corner)
    vec2 uv = texCoord - 0.5;
    float dist = length(uv);

    // smoothstep(innerEdge, outerEdge, dist)
    // 0.0 at center (clear), 1.0 at outer edge (fully dark)
    float vignette = smoothstep(0.15, 0.75, dist);

    // Scale intensity by DarknessAmount uniform (driven by FakeworldClient)
    float darkening = vignette * DarknessAmount;

    fragColor = vec4(color.rgb * (1.0 - darkening), color.a);
}
