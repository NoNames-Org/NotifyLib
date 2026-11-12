#version 330

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:toast.glsl>

uniform sampler2D Sampler0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#endif
in vec4 vertexColor;
in vec2 texCoord0;

flat in int toastMode;
flat in vec2 toastTiming;
in float toastU;

out vec4 fragColor;

void main() {
#ifdef IS_GRAYSCALE
    vec4 color = texture(Sampler0, texCoord0).rrrr * vertexColor;
#else
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
#endif
    if (toastMode == T_MODE_TIMER && toastU > toastTimerLeft(toastTiming, GameTime * T_DAY)) {
        // Прошедшая часть полосы: та же картинка, тёмная и полупрозрачная.
        color.rgb *= 0.3;
        color.a *= 0.5;
    }
    if (color.a < 0.1) {
        discard;
    }
#ifdef IS_SEE_THROUGH
    fragColor = color * ColorModulator;
#elif defined(IS_GUI)
    fragColor = color * ColorModulator;
#else
    color *= ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}
