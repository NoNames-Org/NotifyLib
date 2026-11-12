// NotifyLib: toasts (corner notifications) drawn from boss bar names.
// Server side: ru.fell.notifylib.core.protocol.Protocol and ToastRenderer. The constants below are
// generated from Protocol.java when the pack is built; never edit them by hand.
//
// A toast is the name of a white boss bar:
//   * The name has zero width: the pen returns with notifylib:toast space glyphs, so the game puts the
//     line start exactly at floor(guiWidth / 2).
//   * x band: every element is shifted right by (T_BAND_BASE + code) * T_X_STEP px. Ordinary text never
//     gets that far right of the screen center, so code >= 0 marks a toast element. code bits:
//       corner     (code >> T_SHIFT_CORNER) & 3       0 top left, 1 top right, 2 bottom left, 3 bottom right
//       phase      (code >> T_SHIFT_PHASE) & 3        0 stay, 1 slide in, 2 slide out, 3 shift in stack
//       tick       (code >> T_SHIFT_TICK) & 15        game time mod 16 when the movement started
//       slot       (code >> T_SHIFT_SLOT) & 3         position in the stack, 0 at the corner
//       tall       (code >> T_SHIFT_TALL_BEFORE) & 3  two-line toasts between this one and the corner
//       away       (code >> T_SHIFT_SHIFT_AWAY) & 1   shift: moving away from the corner
//       shiftTall  (code >> T_SHIFT_SHIFT_TALL) & 1   shift: the step crosses a two-line toast
//   * y band: glyphs of the notifylib fonts may be shifted down by flags * T_Y_STEP via their ascent:
//       T_Y_FLAG_TIMER  the timer bar; its RGB = (start tick mod T_DAY) << 9 | duration / 2
//       T_Y_FLAG_ALT    the extra line of a two-line toast: moved by +T_TALL_EXTRA (top corners) or
//                       -T_TALL_EXTRA (bottom corners) after the boss bar row is found
//   * Panel, icon and text are ordinary glyphs with their real colors.
// Every vertex of a toast stays inside one 19 px boss bar row before the alternate-line move, so the
// row (and thus the line origin) is recovered from y alone.
//
// Shader merge: a foreign rendertype_text needs only the toastVertex(...) call in the .vsh and the
// toastTimerLeft(...) check in the .fsh; see the NotifyLib README.

// Generated from ru.fell.notifylib.core.protocol.Protocol. Do not edit.
const float T_X_STEP = 256.0;
const float T_X_SLACK = 32.0;
const int T_BAND_BASE = 32;
const int T_CODES = 16384;
const float T_Y_STEP = 4096.0;
const int T_Y_FLAG_TIMER = 1;
const int T_Y_FLAG_ALT = 2;
const int T_SHIFT_CORNER = 0;
const int T_SHIFT_PHASE = 2;
const int T_SHIFT_TICK = 4;
const int T_SHIFT_SLOT = 8;
const int T_SHIFT_TALL_BEFORE = 10;
const int T_SHIFT_SHIFT_AWAY = 12;
const int T_SHIFT_SHIFT_TALL = 13;
const float T_WIDTH = 160.0;
const float T_HEIGHT = 18.0;
const float T_TALL_EXTRA = 10.0;
const float T_TOP = 5.0;
const float T_GAP = 3.0;
const float T_MARGIN = 4.0;
const float T_BOSSBAR_FIRST = 3.0;
const float T_BOSSBAR_STEP = 19.0;
const float T_ANIM_TICKS = 6.0;
const float T_ANIM_EARLY = 13.0;
const float T_TICK_MOD = 16.0;
const float T_HIDE = 172.0;
const float T_DAY = 24000.0;
const int T_DURATION_BITS = 9;
const float T_DURATION_UNIT = 2.0;
const float T_FUTURE_GUARD = 200.0;
const int T_PHASE_STAY = 0;
const int T_PHASE_IN = 1;
const int T_PHASE_OUT = 2;
const int T_PHASE_SHIFT = 3;
const int T_DEBUG_MARKER_R = 254;
const int T_DEBUG_MARKER_G = 1;
const int T_DEBUG_MARKER_B = 254;


const int T_MODE_NONE = 0;
const int T_MODE_TIMER = 1;

// Ticks since the timer start (both are ticks of the day), across the day boundary. A start slightly
// in the future (client clock behind the server) counts as zero.
float toastTicksSince(float start, float now) {
    float elapsed = mod(now - start + T_DAY, T_DAY);
    return elapsed > T_DAY - T_FUTURE_GUARD ? 0.0 : elapsed;
}

// Ease-out cubic: 0 off screen, 1 in place.
float toastEase(float shown) {
    float rest = 1.0 - clamp(shown, 0.0, 1.0);
    return 1.0 - rest * rest * rest;
}

// Progress 0..1 of a movement that started at `tick` (mod 16); a timestamp from the future is 0.
float toastProgress(float tick, float now) {
    float since = mod(now - tick, T_TICK_MOD);
    return since >= T_ANIM_EARLY ? 0.0 : clamp(since / T_ANIM_TICKS, 0.0, 1.0);
}

// Classifies a GUI text vertex. Returns true for a toast element: pos is then moved to its place on
// screen, alpha is the opacity factor and mode/timing describe the timer bar.
bool toastVertex(inout vec3 pos, vec2 screen, vec3 color, float now,
                 out float alpha, out int mode, out vec2 timing) {
    alpha = 1.0;
    mode = T_MODE_NONE;
    timing = vec2(0.0);

    float yBand = floor(pos.y / T_Y_STEP + 0.5);
    if (yBand < 0.0 || yBand > 3.0) {
        return false;
    }
    int yFlags = int(yBand);
    float y = pos.y - yBand * T_Y_STEP;
    // Boss bar names live in the top third of the screen only.
    if (y >= screen.y / 3.0) {
        return false;
    }
    float line = floor((y - T_BOSSBAR_FIRST + T_TOP) / T_BOSSBAR_STEP);
    vec2 origin = vec2(floor(screen.x * 0.5), T_BOSSBAR_FIRST + T_BOSSBAR_STEP * line);

    float rel = pos.x - origin.x + T_X_SLACK;
    int band = int(floor(rel / T_X_STEP));
    int code = band - T_BAND_BASE;
    if (code < 0 || code >= T_CODES) {
        return false;
    }

    int corner = (code >> T_SHIFT_CORNER) & 3;
    int phase = (code >> T_SHIFT_PHASE) & 3;
    float tick = float((code >> T_SHIFT_TICK) & 15);
    float slot = float((code >> T_SHIFT_SLOT) & 3);
    float tallBefore = float((code >> T_SHIFT_TALL_BEFORE) & 3);
    bool away = ((code >> T_SHIFT_SHIFT_AWAY) & 1) == 1;
    float shiftTall = float((code >> T_SHIFT_SHIFT_TALL) & 1);

    bool left = (corner & 1) == 0;
    bool top = corner < 2;

    vec2 local = vec2(rel - float(band) * T_X_STEP - T_X_SLACK, y - origin.y + T_TOP);
    if ((yFlags & T_Y_FLAG_ALT) != 0) {
        local.y += top ? T_TALL_EXTRA : -T_TALL_EXTRA;
    }

    float rowStep = T_HEIGHT + T_GAP;
    float offset = slot * rowStep + tallBefore * T_TALL_EXTRA;
    float shown = 1.0;
    float progress = phase == T_PHASE_STAY ? 1.0 : toastProgress(tick, now);
    if (phase == T_PHASE_IN) {
        shown = progress;
    } else if (phase == T_PHASE_OUT) {
        shown = 1.0 - progress;
    } else if (phase == T_PHASE_SHIFT) {
        float from = offset + (away ? -1.0 : 1.0) * (rowStep + shiftTall * T_TALL_EXTRA);
        offset = mix(from, offset, toastEase(progress));
    }
    float eased = toastEase(shown);

    vec2 base = vec2(
        left ? T_MARGIN : screen.x - T_MARGIN - T_WIDTH,
        top ? T_MARGIN + offset : screen.y - T_MARGIN - T_HEIGHT - offset);
    pos.xy = base + local;
    pos.x += (left ? -T_HIDE : T_HIDE) * (1.0 - eased);
    alpha = eased;

    if ((yFlags & T_Y_FLAG_TIMER) != 0) {
        ivec3 rgb = ivec3(round(color * 255.0));
        int bits = (rgb.r << 16) | (rgb.g << 8) | rgb.b;
        int unitMask = (1 << T_DURATION_BITS) - 1;
        mode = T_MODE_TIMER;
        timing = vec2(float(bits >> T_DURATION_BITS), float(bits & unitMask) * T_DURATION_UNIT);
    }
    return true;
}

// Share of the timer left (1 = full bar, 0 = empty).
float toastTimerLeft(vec2 timing, float now) {
    return clamp(1.0 - toastTicksSince(timing.x, now) / max(timing.y, 1.0), 0.0, 1.0);
}

// /notify debug: text in the marker color turns green when this shader is active.
bool toastIsDebugMarker(vec3 color) {
    return ivec3(round(color * 255.0)) == ivec3(T_DEBUG_MARKER_R, T_DEBUG_MARKER_G, T_DEBUG_MARKER_B);
}
