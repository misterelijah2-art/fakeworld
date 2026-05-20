#version 150

uniform sampler2D DiffuseSampler;
uniform float DarknessAmount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    // Step 1: darken the entire screen globally
    vec3 darkened = color.rgb * (1.0 - DarknessAmount * 0.7);

    // Step 2: add edge vignette on top
    vec2 uv = texCoord - 0.5;
    float dist = length(uv); // 0.0 center, ~0.707 corner
    float vignette = smoothstep(0.05, 0.45, dist);
    darkened = darkened * (1.0 - vignette * DarknessAmount);

    fragColor = vec4(darkened, color.a);
}
