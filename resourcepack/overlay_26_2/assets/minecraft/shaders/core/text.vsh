#version 330

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:toast.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in ivec2 UV2;
#endif

uniform sampler2D Sampler0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#endif
out vec4 vertexColor;
out vec2 texCoord0;

flat out int toastMode;
flat out vec2 toastTiming;
// 0 у левого края глифа, 1 у правого: по нему полоса таймера делится на «осталось» и «прошло».
out float toastU;

// Край глифа по UV, а не по номеру вершины: игра сдвигает UV внутрь на сотую текселя,
// так что у левого края дробная часть ~0.01, у правого ~0.99.
float glyphRight() {
    return step(0.5, fract(UV0.x * float(textureSize(Sampler0, 0).x)));
}

void main() {
    vec3 pos = Position;
    vertexColor = Color;
    texCoord0 = UV0;
    toastMode = T_MODE_NONE;
    toastTiming = vec2(0.0);
    toastU = 0.0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#endif
#if defined(IS_GUI) && !defined(IS_GRAYSCALE)
    if (ProjMat[3][3] == 1.0) {
        vec2 screen = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
        float alpha;
        int mode;
        vec2 timing;
        if (toastVertex(pos, screen, Color.rgb, GameTime * T_DAY, alpha, mode, timing)) {
            toastMode = mode;
            toastTiming = timing;
            if (mode == T_MODE_TIMER) {
                vertexColor = vec4(1.0, 1.0, 1.0, Color.a);
                toastU = glyphRight();
            }
            vertexColor.a *= alpha;
        } else if (toastIsDebugMarker(Color.rgb)) {
            // Отладочный маркер: если он зелёный, этот шейдер работает.
            vertexColor = vec4(0.3, 1.0, 0.3, Color.a);
        }
    }
#endif
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
#endif
}
