package com.solar.launcher.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.TypedValue;

import com.solar.launcher.theme.ThemeManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 2026-07-18 — Y1/Y2 hardware button prompts as theme-tinted glyphs.
 * Was: plain text "Back" / "OK" / "Wheel" in hints. Now: monochrome PNGs from assets/y1
 * tinted to match decorative theme font color (PorterDuff SRC_IN).
 * Reversal: delete this class + btn_*.png; restore setText(R.string.*) call sites.
 * Layman: little button pictures that pick up the same ink colour as the menu text.
 * Technical: cache raw bitmaps; tint per paint; ImageSpan for inline prompts on API 17.
 */
public final class HardwareButtonGlyph {

    /** Hardware control shown as a glyph. */
    public enum Button {
        BACK("y1/btn_back.png"),
        OK("y1/btn_ok.png"),
        WHEEL("y1/btn_wheel.png"),
        PREV("y1/btn_prev.png"),
        NEXT("y1/btn_next.png"),
        PLAY_PAUSE("y1/btn_play_pause.png");

        final String assetPath;

        Button(String assetPath) {
            this.assetPath = assetPath;
        }
    }

    /** Placeholder char ImageSpan replaces (must be a single code unit). */
    private static final char GLYPH_PLACEHOLDER = '\uFFFC';

    private static final Map<String, Bitmap> RAW_CACHE = new HashMap<String, Bitmap>();

    private HardwareButtonGlyph() {}

    /** 2026-07-18 — Default hint-line size (~14dp) for 480×360; scales with density. */
    public static int defaultSizePx(Context ctx) {
        if (ctx == null) return 14;
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 14f, ctx.getResources().getDisplayMetrics()));
    }

    /** 2026-07-18 — Slightly larger for section headers / tutorial bodies (~16dp). */
    public static int bodySizePx(Context ctx) {
        if (ctx == null) return 16;
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, ctx.getResources().getDisplayMetrics()));
    }

    /**
     * 2026-07-18 — Loads and caches the monochrome source PNG (black ink, alpha mask).
     * Fail-open: missing asset returns null so callers keep plain text.
     */
    public static Bitmap loadRaw(Context ctx, Button button) {
        if (ctx == null || button == null) return null;
        Bitmap cached = RAW_CACHE.get(button.assetPath);
        if (cached != null && !cached.isRecycled()) return cached;
        AssetManager am = ctx.getAssets();
        InputStream in = null;
        try {
            in = am.open(button.assetPath);
            Bitmap decoded = BitmapFactory.decodeStream(in);
            if (decoded != null) {
                RAW_CACHE.put(button.assetPath, decoded);
            }
            return decoded;
        } catch (Exception e) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("path", button.assetPath);
                d.put("err", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                com.solar.launcher.Debug0f5debLog.log(ctx, "HardwareButtonGlyph.loadRaw",
                        "asset miss", "KB-H3", d);
            } catch (Exception ignored) {}
            // #endregion
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 2026-07-18 — Clears bitmap cache (tests / theme asset hot-reload). */
    public static void clearCacheForTest() {
        RAW_CACHE.clear();
    }

    /**
     * 2026-07-18 — Tinted drawable sized to font height; width keeps bitmap aspect.
     * Was: square bounds (w=h=sizePx) which squashed wide Prev/Next/Play glyphs.
     * 2026-07-18 — Also: setTargetDensity rewrote draw size on API 17 → empty/cropped
     * “rectangles”. Now: scale bitmap to exact px then bounds=pixels (no density rewrite).
     * Layman: icons match letter height but stay their natural shape (not squeezed).
     * Reversal: setBounds(0,0,sizePx,sizePx) + setTargetDensity again.
     */
    public static Drawable tintedDrawable(Context ctx, Button button, int color, int sizePx) {
        Bitmap raw = loadRaw(ctx, button);
        if (raw == null) return null;
        int h = sizePx > 0 ? sizePx : defaultSizePx(ctx);
        int[] wh = boundsForFontHeight(raw.getWidth(), raw.getHeight(), h);
        Bitmap pixels = raw;
        if (raw.getWidth() != wh[0] || raw.getHeight() != wh[1]) {
            // Pixel-exact size for ImageSpan — aspect preserved by boundsForFontHeight.
            pixels = Bitmap.createScaledBitmap(raw, wh[0], wh[1], true);
        }
        BitmapDrawable d = new BitmapDrawable(ctx.getResources(), pixels);
        d.setFilterBitmap(true);
        // Bounds are already screen pixels — skip setTargetDensity (API 17 density rewrite
        // was drawing empty/cropped rectangles for wide Prev/Next/Play glyphs).
        d.setBounds(0, 0, wh[0], wh[1]);
        d.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        return d;
    }

    /**
     * 2026-07-18 — Map bitmap size to height=fontH with aspect preserved (min 1px).
     * Wide glyphs get width &gt; height; tall glyphs get width &lt; height.
     */
    public static int[] boundsForFontHeight(int bitmapW, int bitmapH, int fontHeightPx) {
        int h = fontHeightPx > 0 ? fontHeightPx : 1;
        if (bitmapW <= 0 || bitmapH <= 0) {
            return new int[] { h, h };
        }
        int w = Math.round(h * (bitmapW / (float) bitmapH));
        if (w < 1) w = 1;
        return new int[] { w, h };
    }

    /**
     * 2026-07-18 — Appends a tinted glyph into a spannable.
     * Was: stock ImageSpan ALIGN_BASELINE — last-line Back/cancel clipped to a thin strip
     * (only the chevron’s middle bar showed). Now: CenteredImageSpan grows line metrics
     * and draws the full bounds, aspect preserved.
     * Layman: the whole button picture shows, not a sliced streak.
     * Reversal: new ImageSpan(d, 1) again.
     */
    public static void appendGlyph(SpannableStringBuilder out, Context ctx, Button button,
            int color, int sizePx) {
        if (out == null || ctx == null || button == null) return;
        Drawable d = tintedDrawable(ctx, button, color, sizePx);
        if (d == null) return;
        int start = out.length();
        out.append(GLYPH_PLACEHOLDER);
        out.setSpan(new CenteredImageSpan(d), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 2026-07-18 — ImageSpan that keeps the full glyph visible inside TextView lines.
     * Stock ALIGN_BASELINE hangs the drawable above the baseline; TextView often clips
     * the top on the last keyboard-hint row — Back looked like a white dash.
     * This span expands FontMetrics to the drawable height and centres it in the line.
     */
    static final class CenteredImageSpan extends ImageSpan {
        CenteredImageSpan(Drawable drawable) {
            // ALIGN_BOTTOM = 0 — we ignore stock vertical align and draw ourselves.
            super(drawable, 0);
        }

        /** Reports drawable width and grows ascent/descent so the line fits the full icon. */
        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end,
                Paint.FontMetricsInt fm) {
            Drawable d = getDrawable();
            if (d == null) return 0;
            int dw = d.getBounds().width();
            int dh = d.getBounds().height();
            if (fm != null) {
                // Centre glyph on the paint’s mid-line; expand so nothing is cropped.
                Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
                int fontH = pfm.descent - pfm.ascent;
                if (fontH < 1) fontH = dh > 0 ? dh : 1;
                int mid = pfm.ascent + fontH / 2;
                int half = dh / 2;
                fm.ascent = Math.min(pfm.ascent, mid - half);
                fm.descent = Math.max(pfm.descent, mid + (dh - half));
                fm.top = Math.min(pfm.top, fm.ascent);
                fm.bottom = Math.max(pfm.bottom, fm.descent);
            }
            return dw > 0 ? dw : 0;
        }

        /** Draws the full drawable centred between line top and bottom. */
        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, Paint paint) {
            Drawable d = getDrawable();
            if (d == null) return;
            canvas.save();
            int dh = d.getBounds().height();
            // Mid of the laid-out line — not baseline — so tall Back chevrons stay whole.
            int transY = top + ((bottom - top) - dh) / 2;
            canvas.translate(x, transY);
            d.draw(canvas);
            canvas.restore();
        }
    }

    /** 2026-07-18 — Appends glyph then a short label with a thin space between. */
    public static void appendGlyphLabel(SpannableStringBuilder out, Context ctx, Button button,
            int color, int sizePx, CharSequence label) {
        appendGlyph(out, ctx, button, color, sizePx);
        if (label != null && label.length() > 0) {
            // Non-breaking thin space — glyph stays glued to its verb (legend cells).
            out.append('\u202F');
            out.append(label);
        }
    }

    /**
     * 2026-07-18 — Legend separator between glyph+label cells (centred middot with room).
     * Hallmark: one visual rhythm — never trailing separators.
     */
    private static void appendLegendSep(SpannableStringBuilder out) {
        out.append(" \u00B7 ");
    }

    /** 2026-07-18 — Hint-tint colour matching decorative theme fonts. */
    public static int hintTint() {
        return ThemeManager.getHintTextColor();
    }

    /** 2026-07-18 — Primary/item text tint for headers and list chrome. */
    public static int itemTint() {
        return ThemeManager.getItemTextColorNormal();
    }

    /**
     * 2026-07-18 — Wheel keyboard legend: glyph always left of its action (Hallmark legend).
     * Was: "hold [pp] Aa/#" put words before the glyph; dense wrap clipped Back.
     * Layman: each tip reads like a keycaps chart — picture then what it does.
     * Technical: three rows of appendGlyphLabel cells; never leading prose before ImageSpan.
     * Reversal: restore "hold " + appendGlyph + " Aa/#" on row 2.
     */
    public static CharSequence keyboardHint(Context ctx) {
        int color = hintTint();
        int size = Math.max(hintSizePx(ctx), 1);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        // Row 1 — edit
        appendGlyphLabel(sb, ctx, Button.PREV, color, size, "delete");
        appendLegendSep(sb);
        appendGlyphLabel(sb, ctx, Button.PLAY_PAUSE, color, size, "OK");
        appendLegendSep(sb);
        appendGlyphLabel(sb, ctx, Button.NEXT, color, size, "space");
        sb.append('\n');
        // Row 2 — hold charset + pick + type (glyph still leads each cell)
        appendGlyphLabel(sb, ctx, Button.PLAY_PAUSE, color, size, "hold Aa/#");
        appendLegendSep(sb);
        appendGlyphLabel(sb, ctx, Button.WHEEL, color, size, "pick");
        appendLegendSep(sb);
        appendGlyphLabel(sb, ctx, Button.OK, color, size, "type");
        sb.append('\n');
        // Row 3 — leave
        appendGlyphLabel(sb, ctx, Button.BACK, color, size, "cancel");
        return sb;
    }

    /**
     * 2026-07-18 — Compact glyph height for multi-line keyboard legends (~13sp).
     * Was: 12sp — Back chevron’s thin strokes vanished when line-clipped.
     * Layman: slightly larger button pictures so cancel’s Back icon stays whole.
     */
    private static int hintSizePx(Context ctx) {
        if (ctx == null) return 16;
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f,
                ctx.getResources().getDisplayMetrics()));
    }

    /**
     * 2026-07-18 — Now Playing Options tip — glyph left of action (legend).
     * Was: "Hold [Back] Options". Now: [Back] hold Options (tip-sized glyph).
     */
    public static CharSequence holdBackOptionsHint(Context ctx, boolean y2PowerAlso) {
        int color = hintTint();
        int size = tipSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyph(sb, ctx, Button.BACK, color, size);
        if (y2PowerAlso) {
            sb.append("\u202Fhold / Power Options");
        } else {
            sb.append("\u202Fhold Options");
        }
        return sb;
    }

    /**
     * 2026-07-18 — Queue / playlist hold-OK tutorial — legend: glyph left of action.
     * Was: "Hold [OK] — pick up…". Now: [OK] hold — pick up…
     */
    public static CharSequence queueHoldTutorial(Context ctx) {
        int color = hintTint();
        int size = bodySizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyphLabel(sb, ctx, Button.OK, color, size, "hold — pick up the track, then scroll to move it.");
        return sb;
    }

    /**
     * 2026-07-18 — Place track — legend: [OK] place.
     * Was: "Press [OK] again to place."
     */
    public static CharSequence pressOkPlace(Context ctx) {
        int color = hintTint();
        int size = bodySizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyphLabel(sb, ctx, Button.OK, color, size, "place");
        return sb;
    }

    /**
     * 2026-07-18 — Playlist move tutorial as stacked legend rows (glyph · action).
     * Was: prose with Hold/Press/Scroll before each glyph.
     * Reversal: restore Hold/Scroll/Press sentence order before ImageSpans.
     */
    public static CharSequence playlistMoveTutorialBody(Context ctx) {
        int color = hintTint();
        int size = bodySizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyphLabel(sb, ctx, Button.OK, color, size, "hold — pick up a track");
        sb.append("\n\n");
        appendGlyphLabel(sb, ctx, Button.WHEEL, color, size, "move — neighbours show where it lands");
        sb.append("\n\n");
        appendGlyphLabel(sb, ctx, Button.OK, color, size, "place");
        sb.append("\n\n");
        appendGlyphLabel(sb, ctx, Button.BACK, color, size, "cancel");
        return sb;
    }

    /** 2026-07-18 — Home editor move-mode header: wheel glyph + "move". */
    public static CharSequence wheelToMove(Context ctx) {
        int color = itemTint();
        int size = bodySizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyphLabel(sb, ctx, Button.WHEEL, color, size, "move");
        return sb;
    }

    /** 2026-07-18 — Home editor (touch): OK glyph + "confirm". */
    public static CharSequence okToConfirm(Context ctx) {
        int color = itemTint();
        int size = bodySizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyphLabel(sb, ctx, Button.OK, color, size, "confirm");
        return sb;
    }

    /** 2026-07-18 — Brightness overlay hint. */
    public static CharSequence wheelBrightness(Context ctx) {
        int color = hintTint();
        int size = defaultSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyphLabel(sb, ctx, Button.WHEEL, color, size, "brightness");
        return sb;
    }

    /** 2026-07-18 — FM tune row / NP status: wheel adjusts MHz. */
    public static CharSequence wheelAdjustsMhz(Context ctx) {
        int color = hintTint();
        int size = defaultSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyphLabel(sb, ctx, Button.WHEEL, color, size, "MHz");
        return sb;
    }

    /**
     * 2026-07-18 — FM tuning legend: [OK] save (status prefix stays text).
     * Was: "Tuning — [OK] save" with glyph mid-phrase after em dash — still glyph-before-verb.
     */
    public static CharSequence tuningPressOkSave(Context ctx) {
        int color = hintTint();
        int size = defaultSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("Tuning — ");
        appendGlyphLabel(sb, ctx, Button.OK, color, size, "save");
        return sb;
    }

    /**
     * 2026-07-18 — Favourites empty — legend cell mid sentence after setup copy.
     * Was: "Hold [Back] for the menu…". Now: … [Back] menu …
     */
    public static CharSequence favoritesEmptyHint(Context ctx) {
        int color = hintTint();
        int size = defaultSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("No favorites yet. ");
        appendGlyphLabel(sb, ctx, Button.BACK, color, size, "menu — then Add to favorites.");
        return sb;
    }

    /**
     * 2026-07-18 — NP Flow tip — legend: [PP] + open-Flow verb (string supplies the action words).
     * Was: "Hold [PP] to open Flow". Now: [PP] hold / string without leading Hold.
     */
    public static CharSequence holdPlayPauseOpenFlow(Context ctx) {
        int color = hintTint();
        int size = tipSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyph(sb, ctx, Button.PLAY_PAUSE, color, size);
        sb.append('\u202F');
        if (ctx != null) {
            String action = ctx.getString(com.solar.launcher.R.string.flow_hold_play_pause_hint);
            if (action != null && action.length() > 0) {
                sb.append(action);
            } else {
                sb.append("hold — open Flow");
            }
        } else {
            sb.append("hold — open Flow");
        }
        return sb;
    }

    /**
     * 2026-07-18 — Settings → Flow preview: [PP] glyph + hold action (was plain “Hold Play/Pause…”).
     * Layman: shows the real Play/Pause button picture next to what holding it does.
     * Technical: body-sized ImageSpan + settings_flow_hold_play_pause_hint action words.
     * Reversal: setText(R.string.settings_flow_hold_play_pause_hint) only.
     */
    public static CharSequence settingsHoldPlayPauseOpenFlow(Context ctx) {
        int color = hintTint();
        int size = bodySizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyph(sb, ctx, Button.PLAY_PAUSE, color, size);
        sb.append('\u202F');
        if (ctx != null) {
            String action = ctx.getString(
                    com.solar.launcher.R.string.settings_flow_hold_play_pause_hint);
            if (action != null && action.length() > 0) {
                sb.append(action);
            } else {
                sb.append("hold — open Flow view");
            }
        } else {
            sb.append("hold — open Flow view");
        }
        return sb;
    }

    /**
     * 2026-07-18 — Live NP Options tip — legend: glyph left, short hold action.
     * Was: bodySizePx glyphs + long “keep holding for Options” clipped in 48dp transport.
     * Now: tip-sized glyph + “hold — Options” (fits 480px + taller transport band).
     * Reversal: bodySizePx + longer string before appendGlyph.
     */
    public static CharSequence keepHoldingForOptions(Context ctx, boolean y2PowerAlso) {
        int color = hintTint();
        int size = tipSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyph(sb, ctx, Button.BACK, color, size);
        if (y2PowerAlso) {
            sb.append("\u202F/ Power");
        }
        sb.append('\u202F');
        if (ctx != null) {
            sb.append(ctx.getString(com.solar.launcher.R.string.np_keep_holding_for_options));
        } else {
            sb.append("hold — Options");
        }
        return sb;
    }

    /**
     * 2026-07-18 — Live NP Flow tip — legend: [PP] + short hold action.
     * Was: wide PP glyph at bodySize + long string → half cut off in transport tip band.
     * Reversal: bodySizePx + “keep holding for Flow”.
     */
    public static CharSequence keepHoldingForFlow(Context ctx) {
        int color = hintTint();
        int size = tipSizePx(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendGlyph(sb, ctx, Button.PLAY_PAUSE, color, size);
        sb.append('\u202F');
        if (ctx != null) {
            sb.append(ctx.getString(com.solar.launcher.R.string.np_keep_holding_for_flow));
        } else {
            sb.append("hold — Flow");
        }
        return sb;
    }

    /**
     * 2026-07-18 — Glyph height matched to transport tip text (~17sp).
     * Layman: button pictures match the tip letters so nothing sticks out and gets cropped.
     */
    private static int tipSizePx(Context ctx) {
        if (ctx == null) return 18;
        try {
            return Math.max(1, Math.round(ctx.getResources()
                    .getDimension(com.solar.launcher.R.dimen.y1_transport_hint_text_size)));
        } catch (Exception e) {
            return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f,
                    ctx.getResources().getDisplayMetrics()));
        }
    }

    /**
     * 2026-07-18 — ARGB → #RRGGBB for tests / logging (documents ink→theme contract).
     */
    static String colorHex(int argb) {
        return String.format("#%06X", (argb & 0xFFFFFF));
    }
}
