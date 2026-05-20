package dev.blumdlc.client.ui.components;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.ColorSetting;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.settings.Setting;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.animation.Animation;
import dev.blumdlc.client.ui.animation.Easing;
import dev.blumdlc.client.ui.util.ColorUtil;

/**
 * Right-side module settings popup — "Card Stack" style.
 *
 * <p>Each setting is wrapped in its own subtle rounded card so the user can
 * scan them at a glance. The header sticks to the top while the rest scrolls
 * and includes a quick toggle for the module's enabled state plus a close X.
 *
 * <p>Visuals per setting type:
 * <ul>
 *   <li><b>Boolean</b> — animated pill toggle on the right.</li>
 *   <li><b>Number</b>  — full-width gradient track + value chip.</li>
 *   <li><b>Mode</b>    — segmented pill if 2-3 options (collapsed), dropdown
 *                        for longer lists, expanded radio cards otherwise.</li>
 *   <li><b>Color</b>   — preview swatch + rainbow hue strip + hue value.</li>
 *   <li><b>Multi</b>   — chip pills wrapping below the label.</li>
 * </ul>
 */
public final class SettingsPopup {

	public static final float WIDTH = 212.0f;
	public static final float GAP = 7.0f;

	private static final float PAD_X = 12.0f;
	private static final float HEADER_H = 50.0f;
	private static final float ROW_GAP = 5.0f;
	private static final float CARD_PAD_X = 8.0f;
	private static final float CARD_PAD_Y = 7.0f;
	private static final float CARD_RADIUS = 7.0f;
	private static final float CLOSE_SIZE = 14.0f;


	// --- Open / close animation -------------------------------------------------
	private final Animation openAnim = new Animation(0.0f, 320, Easing.EASE_OUT_EXPO);

	// --- Currently displayed module ---------------------------------------------
	private Module module;

	// --- Drag targets (null when no drag active) --------------------------------
	private NumberSetting draggingSlider;
	private ColorSetting draggingColor;

	// --- Open ModeSetting dropdown overlay --------------------------------------
	private ModeSetting openDropdown;
	private float dropdownX, dropdownY, dropdownW, dropdownH;

	// --- Vertical scrolling -----------------------------------------------------
	private final Animation scroll = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
	private float scrollTarget = 0.0f;
	private float maxScroll = 0.0f;

	// --- Per-row staggered entrance --------------------------------------------
	private final Map<Setting<?>, Animation> rowEnter = new HashMap<>();

	// --- Cached layout (invalidated on visibility / mode changes) --------------
	private float cachedSettingsHeight = -1.0f;
	private Module cachedHeightModule = null;

	// =========================================================================
	//  Lifecycle
	// =========================================================================

	public boolean isVisible()      { return module != null && openAnim.getValue() > 0.001f; }
	public boolean isInteractive()  { return module != null && openAnim.getValue() > 0.5f; }
	public Module  getModule()      { return module; }
	public void invalidateHeightCache() { this.cachedSettingsHeight = -1.0f; }

	/** Toggle the popup for {@code m}; tapping the same module twice closes it. */
	public void toggle(Module m) {
		if (m == null) return;
		if (m == module) { close(); return; }
		if (m.settings.isEmpty()) return;
		this.module = m;
		this.scrollTarget = 0.0f;
		this.scroll.setImmediate(0.0f);
		this.openAnim.setImmediate(0.0f);
		this.openAnim.setTarget(1.0f);
		this.openDropdown = null;
		this.draggingSlider = null;
		this.draggingColor = null;
		this.cachedSettingsHeight = -1.0f;
		this.cachedHeightModule = null;
		retriggerEnterAnims(m);
	}


	public void close() {
		this.openAnim.setTarget(0.0f);
		this.openDropdown = null;
		this.draggingSlider = null;
		this.draggingColor = null;
		this.cachedSettingsHeight = -1.0f;
	}

	public void detach() {
		this.module = null;
		this.openAnim.setImmediate(0.0f);
		this.scrollTarget = 0.0f;
		this.scroll.setImmediate(0.0f);
		this.openDropdown = null;
		this.draggingSlider = null;
		this.draggingColor = null;
		this.rowEnter.clear();
	}

	/** (Re)triggers the staggered enter animation for each visible row. */
	private void retriggerEnterAnims(Module m) {
		this.rowEnter.clear();
		int idx = 0;
		for (Setting<?> s : m.settings) {
			if (!isSettingVisible(s)) continue;
			Animation a = new Animation(0.0f, 320, Easing.EASE_OUT_QUINT);
			a.setTarget(1.0f, 25L * idx++);
			rowEnter.put(s, a);
		}
	}

	private float enterAlphaFor(Setting<?> s) {
		Animation a = rowEnter.get(s);
		return a == null ? 1.0f : a.getValue();
	}

	// =========================================================================
	//  Render
	// =========================================================================

	public void render(Matrix4f matrix, MsdfFont font,
			float panelX, float panelY, float panelW, float panelH,
			float panelOpen, int mouseX, int mouseY) {
		if (module == null) return;
		float t = openAnim.getValue();
		if (t < 0.001f) {
			if (openAnim.getTarget() == 0.0f) module = null;
			return;
		}


		float baseX = panelX + panelW + GAP;
		float startX = baseX - 18.0f;
		float x = startX + (baseX - startX) * t;
		float y = panelY;
		float w = WIDTH;
		float h = panelH;
		float a = panelOpen * t;

		// Body
		UIRender.rect(matrix, x, y, w, h, 12.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BG, a));
		UIRender.border(matrix, x, y, w, h, 12.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, a));

		drawHeader(matrix, font, x, y, w, a, mouseX, mouseY);

		// Settings list area
		float contentTop = y + HEADER_H;
		float contentBottom = y + h - 8.0f;
		float contentX = x + PAD_X;
		float contentW = w - PAD_X * 2;

		float totalH = getSettingsHeight(module);
		float visibleH = contentBottom - contentTop;
		maxScroll = Math.max(0.0f, totalH - visibleH);
		scrollTarget = Math.max(0.0f, Math.min(scrollTarget, maxScroll));
		scroll.setTarget(scrollTarget);

		float cy = contentTop + 4.0f - scroll.getValue();
		for (Setting<?> s : module.settings) {
			if (!isSettingVisible(s)) continue;
			cy = drawSetting(matrix, font, s, contentX, cy, contentW,
				a, mouseX, mouseY, contentTop, contentBottom);
			cy += ROW_GAP;
		}

		// Scrollbar
		if (maxScroll > 0.5f) {
			float trackX = x + w - 5.0f;
			float trackH = visibleH;
			float thumbH = Math.max(18.0f, trackH * (trackH / (trackH + maxScroll)));
			float thumbY = contentTop + (scroll.getValue() / maxScroll) * (trackH - thumbH);
			UIRender.rect(matrix, trackX, contentTop, 2.5f, trackH, 1.5f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_BG, a));
			UIRender.rect(matrix, trackX, thumbY, 2.5f, thumbH, 1.5f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_FG, a));
		}

		drawDropdownOverlay(matrix, font, a, mouseX, mouseY);
	}


	/**
	 * Sticky module header: small accent dot, name + description, quick toggle
	 * switch (skipped for HUD-only / themes containers with no toggle), close X.
	 */
	private void drawHeader(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		// Bottom divider
		UIRender.rect(matrix, x + PAD_X, y + HEADER_H - 1.0f, w - PAD_X * 2, 1.0f,
			0.0f, ColorUtil.multiplyAlpha(Theme.DIVIDER, a));

		// Accent dot
		float dotR = 6.0f;
		float dotX = x + PAD_X;
		float dotY = y + 13.0f;
		UIRender.rect(matrix, dotX, dotY, dotR, dotR, dotR * 0.5f,
			ColorUtil.multiplyAlpha(Theme.ACCENT, 0.25f * a));
		UIRender.rect(matrix, dotX + 1, dotY + 1, dotR - 2, dotR - 2, (dotR - 2) * 0.5f,
			ColorUtil.multiplyAlpha(Theme.ACCENT, a));

		// Title + subtitle
		UIRender.text(matrix, font, module.name, x + PAD_X + dotR + 6.0f, y + 11.0f,
			10.0f, ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.06f);
		UIRender.text(matrix, font, module.description,
			x + PAD_X + dotR + 6.0f, y + 24.0f,
			6.0f, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a), 0.04f);

		// Close X
		float closeX = x + w - PAD_X - CLOSE_SIZE;
		float closeY = y + 10.0f;
		boolean closeHover = inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
		UIRender.rect(matrix, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, 5.0f,
			ColorUtil.multiplyAlpha(closeHover ? Theme.DANGER : 0x12FFFFFF,
				(closeHover ? 0.20f : 1.0f) * a));
		int closeColor = closeHover ? Theme.DANGER : 0xFFA0A0B0;
		UIRender.text(matrix, font, "x", closeX + 4.5f, closeY + 3.0f, 7.5f,
			ColorUtil.multiplyAlpha(closeColor, a));

		// Module enable/disable switch (under close)
		float swW = 24.0f, swH = 11.0f;
		float swX = x + w - PAD_X - swW;
		float swY = y + 30.0f;
		boolean on = module.enabled;
		drawSwitch(matrix, swX, swY, swW, swH, on, a);
	}


	/** Generic pill toggle switch used by the header and BooleanSetting rows. */
	private void drawSwitch(Matrix4f m, float x, float y, float w, float h,
			boolean on, float a) {
		int track = on ? Theme.CARD_ACTIVE_TO : 0x33FFFFFF;
		UIRender.rect(m, x, y, w, h, h * 0.5f,
			ColorUtil.multiplyAlpha(track, a));
		// Soft inner glow when on
		if (on) {
			UIRender.rect(m, x, y, w, h, h * 0.5f,
				ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, 0.4f * a));
		}
		float knobR = h - 3.0f;
		float knobX = on ? x + w - knobR - 1.5f : x + 1.5f;
		// Knob shadow
		UIRender.rect(m, knobX + 0.5f, y + 2.0f, knobR, knobR, knobR * 0.5f,
			ColorUtil.multiplyAlpha(0x55000000, a));
		UIRender.rect(m, knobX, y + 1.5f, knobR, knobR, knobR * 0.5f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, a));
	}

	// =========================================================================
	//  Per-setting rendering
	// =========================================================================

	private float drawSetting(Matrix4f matrix, MsdfFont font, Setting<?> s,
			float x, float y, float w, float a, int mouseX, int mouseY,
			float clipTop, float clipBottom) {

		float enterT = enterAlphaFor(s);
		float rowAlpha = enterT * a;
		float ox = (1.0f - enterT) * 8.0f;
		float drawX = x + ox;

		// Compute card height so we can clip-skip drawing when off-screen.
		float cardH = settingHeight(s);
		boolean visible = (y + cardH > clipTop) && (y < clipBottom);

		if (visible) {
			// Card background
			UIRender.rect(matrix, drawX, y, w, cardH, CARD_RADIUS,
				ColorUtil.multiplyAlpha(0xFF14171F, rowAlpha));
			UIRender.border(matrix, drawX, y, w, cardH, CARD_RADIUS, 0.7f,
				ColorUtil.multiplyAlpha(0x18FFFFFF, rowAlpha));
		}

		float ix = drawX + CARD_PAD_X;
		float iy = y + CARD_PAD_Y;
		float iw = w - CARD_PAD_X * 2;


		if (s instanceof BooleanSetting bs) {
			if (visible) drawBooleanRow(matrix, font, bs, ix, iy, iw, rowAlpha);
		} else if (s instanceof NumberSetting ns) {
			if (visible) drawNumberRow(matrix, font, ns, ix, iy, iw, rowAlpha);
		} else if (s instanceof ColorSetting cs) {
			if (visible) drawColorRow(matrix, font, cs, ix, iy, iw, rowAlpha);
		} else if (s instanceof ModeSetting ms) {
			if (visible) drawModeRow(matrix, font, ms, drawX, ix, iy, iw, rowAlpha,
				mouseX, mouseY);
		} else if (s instanceof MultiSetting mu) {
			if (visible) drawMultiRow(matrix, font, mu, ix, iy, iw, rowAlpha,
				mouseX, mouseY);
		} else if (visible) {
			UIRender.text(matrix, font, s.name, ix, iy, 7.5f,
				ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, rowAlpha), 0.05f);
		}
		return y + cardH;
	}

	// --- Boolean -----------------------------------------------------------

	private void drawBooleanRow(Matrix4f m, MsdfFont font, BooleanSetting s,
			float x, float y, float w, float a) {
		UIRender.text(m, font, s.name, x, y + 3.0f, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);
		float swW = 22.0f, swH = 10.0f;
		float swX = x + w - swW;
		float swY = y + 2.0f;
		drawSwitch(m, swX, swY, swW, swH, s.get(), a);
	}

	// --- Number ------------------------------------------------------------

	private void drawNumberRow(Matrix4f m, MsdfFont font, NumberSetting s,
			float x, float y, float w, float a) {
		// Label + value chip
		UIRender.text(m, font, s.name, x, y, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);

		String value = (s.step >= 1.0)
			? String.format("%.0f", s.get())
			: String.format("%.1f", s.get());
		float vw = UIRender.textWidth(font, value, 6.5f);
		UIRender.rect(m, x + w - vw - 8.0f, y - 1.5f, vw + 8.0f, 11.0f, 4.0f,
			ColorUtil.multiplyAlpha(0x18FFFFFF, a));
		UIRender.text(m, font, value, x + w - vw - 4.0f, y + 1.5f, 6.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.04f);


		// Track + gradient fill
		float trackY = y + 14.0f;
		float trackH = 4.0f;
		float frac = clamp01((float) ((s.get() - s.min) / (s.max - s.min)));
		UIRender.rect(m, x, trackY, w, trackH, trackH * 0.5f,
			ColorUtil.multiplyAlpha(0x22FFFFFF, a));
		if (frac > 0.001f) {
			UIRender.rectGradientH(m, x, trackY, w * frac, trackH, trackH * 0.5f,
				ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
				ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, a));
		}
		// Knob with shadow
		float knobX = x + w * frac - 4.0f;
		UIRender.rect(m, knobX + 0.5f, trackY - 2.5f, 8.0f, 9.0f, 4.0f,
			ColorUtil.multiplyAlpha(0x55000000, a));
		UIRender.rect(m, knobX, trackY - 3.0f, 8.0f, 9.0f, 4.0f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, a));
	}

	// --- Color -------------------------------------------------------------

	private void drawColorRow(Matrix4f m, MsdfFont font, ColorSetting s,
			float x, float y, float w, float a) {
		// Label + hue value chip
		UIRender.text(m, font, s.name, x + 14.0f, y + 3.0f, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);

		// Preview circle
		float pr = 9.0f;
		float px = x;
		float py = y;
		UIRender.rect(m, px, py, pr, pr, pr * 0.5f,
			ColorUtil.multiplyAlpha(s.toArgb(), a));
		UIRender.border(m, px, py, pr, pr, pr * 0.5f, 0.8f,
			ColorUtil.multiplyAlpha(0x60FFFFFF, a));

		String hue = String.format("%.0f", s.getHue());
		float hw = UIRender.textWidth(font, hue, 6.5f);
		UIRender.text(m, font, hue, x + w - hw - 2.0f, y + 1.5f, 6.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, a), 0.04f);

		// Hue strip
		float trackY = y + 14.0f;
		float trackH = 7.0f;
		int segs = 18;
		float segW = w / segs;
		for (int i = 0; i < segs; i++) {
			float h1 = i / (float) segs;
			float h2 = (i + 1.0f) / segs;
			int c1 = ColorUtil.multiplyAlpha(hsvColor(h1), a);
			int c2 = ColorUtil.multiplyAlpha(hsvColor(h2), a);
			float sx = x + i * segW;
			float sw = (i == segs - 1) ? (w - i * segW) : segW;
			float radius = (i == 0 || i == segs - 1) ? trackH * 0.5f : 0.0f;
			UIRender.rectGradientH(m, sx, trackY, sw, trackH, radius, c1, c2);
		}


		float frac = clamp01((float) (s.getHue() / 360.0));
		float knobX = x + w * frac - 3.5f;
		UIRender.rect(m, knobX + 0.5f, trackY - 1.5f, 7.0f, trackH + 3.0f, 3.0f,
			ColorUtil.multiplyAlpha(0x55000000, a));
		UIRender.rect(m, knobX, trackY - 2.0f, 7.0f, trackH + 4.0f, 3.0f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, a));
		UIRender.rect(m, knobX + 1.5f, trackY - 0.5f, 4.0f, trackH + 1.0f, 2.0f,
			ColorUtil.multiplyAlpha(s.toArgb(), a));
	}

	// --- Mode --------------------------------------------------------------

	private void drawModeRow(Matrix4f m, MsdfFont font, ModeSetting s,
			float cardX, float x, float y, float w, float a, int mx, int my) {
		if (s.expanded) {
			drawModeExpanded(m, font, s, x, y, w, a, mx, my);
			return;
		}
		// Label
		UIRender.text(m, font, s.name, x, y + 3.0f, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);

		boolean useSegmented = s.modes.size() <= 3;
		if (useSegmented) {
			drawModeSegmented(m, font, s, x, y + 12.0f, w, a, mx, my);
		} else {
			drawModeCompact(m, font, s, cardX, x, y + 12.0f, w, a, mx, my);
		}
	}

	private void drawModeSegmented(Matrix4f m, MsdfFont font, ModeSetting s,
			float x, float y, float w, float a, int mx, int my) {
		float h = 14.0f;
		int n = s.modes.size();
		// Background container
		UIRender.rect(m, x, y, w, h, 5.0f,
			ColorUtil.multiplyAlpha(0x10FFFFFF, a));
		float segW = (w - 4.0f) / n;
		String selected = s.get();
		int idx = 0;
		for (String mode : s.modes) {
			float sx = x + 2.0f + idx * segW;
			boolean isSel = mode.equals(selected);
			boolean hov = inside(mx, my, sx, y + 2.0f, segW, h - 4.0f);
			if (isSel) {
				UIRender.rectGradientH(m, sx, y + 2.0f, segW, h - 4.0f, 4.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, a));
			} else if (hov) {
				UIRender.rect(m, sx, y + 2.0f, segW, h - 4.0f, 4.0f,
					ColorUtil.multiplyAlpha(0x14FFFFFF, a));
			}


			float tw = UIRender.textWidth(font, mode, 6.5f);
			float tx = sx + (segW - tw) * 0.5f;
			int tc = isSel ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			UIRender.text(m, font, mode, tx, y + 5.0f, 6.5f,
				ColorUtil.multiplyAlpha(tc, a));
			idx++;
		}
	}

	private void drawModeCompact(Matrix4f m, MsdfFont font, ModeSetting s,
			float cardX, float x, float y, float w, float a, int mx, int my) {
		float h = 14.0f;
		boolean open = openDropdown == s;
		boolean hov = inside(mx, my, x, y, w, h);
		int bg = open ? Theme.CARD_HOVER : (hov ? Theme.CARD_HOVER : Theme.CARD_BG);
		UIRender.rect(m, x, y, w, h, 5.0f,
			ColorUtil.multiplyAlpha(bg, a));
		UIRender.border(m, x, y, w, h, 5.0f, 0.8f,
			ColorUtil.multiplyAlpha(open ? Theme.ACCENT : Theme.CARD_BORDER, a));
		UIRender.text(m, font, s.get(), x + 7.0f, y + 4.0f, 7.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.04f);
		UIRender.text(m, font, open ? "^" : "v", x + w - 10.0f, y + 4.0f, 7.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a));
		if (open) {
			dropdownX = x;
			dropdownY = y;
			dropdownW = w;
			dropdownH = h;
		}
	}

	private void drawModeExpanded(Matrix4f m, MsdfFont font, ModeSetting s,
			float x, float y, float w, float a, int mx, int my) {
		UIRender.text(m, font, s.name, x, y, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);
		float rowH = 13.0f;
		float gap = 3.0f;
		float cy = y + 12.0f;
		String selected = s.get();
		for (String mode : s.modes) {
			boolean isSel = mode.equals(selected);
			boolean hov = inside(mx, my, x, cy, w, rowH);
			if (isSel) {
				UIRender.rectGradientH(m, x, cy, w, rowH, 4.5f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, a));
			} else {
				UIRender.rect(m, x, cy, w, rowH, 4.5f,
					ColorUtil.multiplyAlpha(hov ? Theme.CARD_HOVER : 0x10FFFFFF, a));
			}


			int tc = isSel ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			UIRender.text(m, font, mode, x + 7.0f, cy + 3.5f, 6.5f,
				ColorUtil.multiplyAlpha(tc, a), 0.04f);
			cy += rowH + gap;
		}
	}

	// --- Multi (chip pills) ------------------------------------------------

	private void drawMultiRow(Matrix4f m, MsdfFont font, MultiSetting s,
			float x, float y, float w, float a, int mx, int my) {
		UIRender.text(m, font, s.name, x, y, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);

		float chipH = 12.0f;
		float chipPadX = 6.0f;
		float gap = 4.0f;
		float cx = x;
		float cy = y + 12.0f;
		for (String opt : s.options) {
			float tw = UIRender.textWidth(font, opt, 6.5f);
			float chipW = tw + chipPadX * 2;
			if (cx + chipW > x + w) {
				cx = x;
				cy += chipH + gap;
			}
			boolean sel = s.isSelected(opt);
			boolean hov = inside(mx, my, cx, cy, chipW, chipH);
			if (sel) {
				UIRender.rectGradientH(m, cx, cy, chipW, chipH, chipH * 0.5f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, a));
			} else {
				UIRender.rect(m, cx, cy, chipW, chipH, chipH * 0.5f,
					ColorUtil.multiplyAlpha(hov ? 0x22FFFFFF : 0x12FFFFFF, a));
				UIRender.border(m, cx, cy, chipW, chipH, chipH * 0.5f, 0.7f,
					ColorUtil.multiplyAlpha(0x22FFFFFF, a));
			}
			int tc = sel ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			UIRender.text(m, font, opt, cx + chipPadX, cy + 3.0f, 6.5f,
				ColorUtil.multiplyAlpha(tc, a), 0.04f);
			cx += chipW + gap;
		}
	}


	private void drawDropdownOverlay(Matrix4f m, MsdfFont font, float a,
			int mx, int my) {
		ModeSetting s = openDropdown;
		if (s == null) return;
		float rowH = 13.0f;
		float listH = s.modes.size() * rowH + 4.0f;
		float x = dropdownX;
		float y = dropdownY + dropdownH + 2.0f;
		float w = dropdownW;

		UIRender.rect(m, x, y, w, listH, 6.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BG, a));
		UIRender.border(m, x, y, w, listH, 6.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.ACCENT, a));

		float cy = y + 2.0f;
		String selected = s.get();
		for (String mode : s.modes) {
			boolean isSel = mode.equals(selected);
			boolean hov = inside(mx, my, x, cy, w, rowH);
			if (isSel) {
				UIRender.rectGradientH(m, x + 2, cy, w - 4, rowH, 4.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, a));
			} else if (hov) {
				UIRender.rect(m, x + 2, cy, w - 4, rowH, 4.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_HOVER, a));
			}
			int tc = isSel ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			UIRender.text(m, font, mode, x + 7.0f, cy + 3.5f, 6.5f,
				ColorUtil.multiplyAlpha(tc, a), 0.04f);
			cy += rowH;
		}
	}

	// =========================================================================
	//  Input
	// =========================================================================

	public boolean mouseClicked(double mx, double my,
			float panelX, float panelY, float panelW, float panelH, int button) {
		if (!isInteractive()) return false;
		float popupX = panelX + panelW + GAP;
		if (!inside(mx, my, popupX, panelY, WIDTH, panelH)) return false;


		// Header X
		float closeX = popupX + WIDTH - PAD_X - CLOSE_SIZE;
		float closeY = panelY + 10.0f;
		if (button == 0 && inside(mx, my, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
			close(); return true;
		}
		// Header switch (toggle module)
		float swW = 24.0f, swH = 11.0f;
		float swX = popupX + WIDTH - PAD_X - swW;
		float swY = panelY + 30.0f;
		if (button == 0 && inside(mx, my, swX, swY, swW, swH)) {
			module.toggle(); return true;
		}

		if (handleSettingsClick(mx, my, popupX, panelY, button)) return true;
		return true; // consume so cards underneath don't fire
	}

	private boolean handleSettingsClick(double mx, double my,
			float popupX, float popupY, int button) {
		float contentX = popupX + PAD_X;
		float contentW = WIDTH - PAD_X * 2;
		float ix = contentX + CARD_PAD_X;
		float iw = contentW - CARD_PAD_X * 2;

		// Open dropdown intercepts first
		if (openDropdown != null && button == 0) {
			float listY = dropdownY + dropdownH + 2.0f;
			float rowH = 13.0f;
			float listH = openDropdown.modes.size() * rowH + 4.0f;
			if (inside(mx, my, dropdownX, listY, dropdownW, listH)) {
				int idx = (int) ((my - (listY + 2.0f)) / rowH);
				if (idx >= 0 && idx < openDropdown.modes.size()) {
					openDropdown.set(openDropdown.modes.get(idx));
					cachedSettingsHeight = -1.0f;
				}
				openDropdown = null;
				return true;
			}
			if (inside(mx, my, dropdownX, dropdownY, dropdownW, dropdownH)) {
				openDropdown = null;
				return true;
			}
			openDropdown = null;
		}

		float cy = popupY + HEADER_H + 4.0f - scroll.getValue();


		for (Setting<?> s : module.settings) {
			if (!isSettingVisible(s)) continue;
			float cardH = settingHeight(s);
			float iy = cy + CARD_PAD_Y;
			float iyTop = iy;

			if (s instanceof BooleanSetting bs) {
				float swW = 22.0f, swH = 10.0f;
				float swX = ix + iw - swW;
				if (inside(mx, my, swX, iy + 2.0f, swW, swH) && button == 0) {
					bs.toggle();
					cachedSettingsHeight = -1.0f;
					return true;
				}
			} else if (s instanceof NumberSetting ns) {
				float trackY = iy + 14.0f;
				if (inside(mx, my, ix, trackY - 4.0f, iw, 12.0f) && button == 0) {
					float frac = clamp01((float) ((mx - ix) / iw));
					applySliderValue(ns, frac);
					draggingSlider = ns;
					return true;
				}
			} else if (s instanceof ColorSetting cs) {
				float trackY = iy + 14.0f;
				if (inside(mx, my, ix, trackY - 2.0f, iw, 12.0f) && button == 0) {
					float frac = clamp01((float) ((mx - ix) / iw));
					cs.setHue(frac * 360.0);
					draggingColor = cs;
					return true;
				}
			} else if (s instanceof ModeSetting ms) {
				if (ms.expanded) {
					float rowH = 13.0f;
					float gap = 3.0f;
					float ry = iyTop + 12.0f;
					for (String mode : ms.modes) {
						if (inside(mx, my, ix, ry, iw, rowH) && button == 0) {
							ms.set(mode);
							cachedSettingsHeight = -1.0f;
							return true;
						}
						ry += rowH + gap;
					}
				} else if (ms.modes.size() <= 3) {
					float segY = iyTop + 12.0f;
					float segH = 14.0f;
					int n = ms.modes.size();
					float segW = (iw - 4.0f) / n;
					int idx = 0;
					for (String mode : ms.modes) {
						float sx = ix + 2.0f + idx * segW;
						if (inside(mx, my, sx, segY + 2.0f, segW, segH - 4.0f) && button == 0) {
							ms.set(mode);
							cachedSettingsHeight = -1.0f;
							return true;
						}
						idx++;
					}
				} else {


					float ddY = iyTop + 12.0f;
					float ddH = 14.0f;
					if (inside(mx, my, ix, ddY, iw, ddH) && button == 0) {
						openDropdown = (openDropdown == ms) ? null : ms;
						dropdownX = ix; dropdownY = ddY; dropdownW = iw; dropdownH = ddH;
						return true;
					}
				}
			} else if (s instanceof MultiSetting mu) {
				float chipH = 12.0f, chipPadX = 6.0f, gap = 4.0f;
				float cx2 = ix;
				float cy2 = iyTop + 12.0f;
				MsdfFont font = dev.blumdlc.client.ui.Fonts.BIKO.get();
				for (String opt : mu.options) {
					float tw = UIRender.textWidth(font, opt, 6.5f);
					float chipW = tw + chipPadX * 2;
					if (cx2 + chipW > ix + iw) {
						cx2 = ix;
						cy2 += chipH + gap;
					}
					if (inside(mx, my, cx2, cy2, chipW, chipH) && button == 0) {
						mu.toggle(opt);
						cachedSettingsHeight = -1.0f;
						return true;
					}
					cx2 += chipW + gap;
				}
			}
			cy += cardH + ROW_GAP;
		}
		return false;
	}

	private void applySliderValue(NumberSetting ns, float frac) {
		double v = ns.min + (ns.max - ns.min) * frac;
		if (ns.step > 0.0) v = Math.round(v / ns.step) * ns.step;
		v = Math.max(ns.min, Math.min(ns.max, v));
		ns.set(v);
	}


	public boolean mouseDragged(double mx, double my, int button,
			float panelX, float panelW) {
		if (button != 0 || module == null) return false;
		float popupX = panelX + panelW + GAP;
		float ix = popupX + PAD_X + CARD_PAD_X;
		float iw = WIDTH - PAD_X * 2 - CARD_PAD_X * 2;
		if (draggingSlider != null) {
			float frac = clamp01((float) ((mx - ix) / iw));
			applySliderValue(draggingSlider, frac);
			return true;
		}
		if (draggingColor != null) {
			float frac = clamp01((float) ((mx - ix) / iw));
			draggingColor.setHue(frac * 360.0);
			return true;
		}
		return false;
	}

	public boolean mouseReleased(int button) {
		if (button == 0) {
			boolean had = draggingSlider != null || draggingColor != null;
			draggingSlider = null;
			draggingColor = null;
			return had;
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double v,
			float panelX, float panelY, float panelW, float panelH) {
		if (!isInteractive()) return false;
		float popupX = panelX + panelW + GAP;
		if (!inside(mx, my, popupX, panelY, WIDTH, panelH)) return false;
		scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget - (float) v * 22.0f));
		scroll.setTarget(scrollTarget);
		return true;
	}

	public boolean keyPressed(int keyCode) {
		if (module == null) return false;
		if (keyCode == 256) {
			if (openDropdown != null) { openDropdown = null; return true; }
			close();
			return true;
		}
		return false;
	}


	// =========================================================================
	//  Geometry helpers
	// =========================================================================

	/** Per-setting card height (pixels). */
	private static float settingHeight(Setting<?> s) {
		float pad = CARD_PAD_Y * 2;
		if (s instanceof BooleanSetting) {
			return pad + 13.0f;
		}
		if (s instanceof NumberSetting) {
			return pad + 22.0f;
		}
		if (s instanceof ColorSetting) {
			return pad + 23.0f;
		}
		if (s instanceof ModeSetting ms) {
			if (ms.expanded) return pad + 12.0f + ms.modes.size() * 16.0f - 3.0f;
			return pad + 12.0f + 14.0f;
		}
		if (s instanceof MultiSetting mu) {
			// Estimate row count for chip wrapping (4 chars/option estimate)
			int approxRows = Math.max(1, (int) Math.ceil(mu.options.size() / 3.0));
			return pad + 12.0f + approxRows * 16.0f - 4.0f;
		}
		return pad + 13.0f;
	}

	private float getSettingsHeight(Module m) {
		if (m != cachedHeightModule || cachedSettingsHeight < 0.0f) {
			float h = 0.0f;
			for (Setting<?> s : m.settings) {
				if (!isSettingVisible(s)) continue;
				h += settingHeight(s) + ROW_GAP;
			}
			cachedSettingsHeight = h;
			cachedHeightModule = m;
		}
		return cachedSettingsHeight;
	}

	private static boolean isSettingVisible(Setting<?> s) {
		Supplier<Boolean> vis = s.getVisibility();
		return vis == null || vis.get();
	}


	private static int hsvColor(float h) {
		float r, g, b;
		float i = (float) Math.floor(h * 6.0f);
		float f = h * 6.0f - i;
		float q = 1.0f - f;
		switch (((int) i) % 6) {
			case 0:  r = 1; g = f; b = 0; break;
			case 1:  r = q; g = 1; b = 0; break;
			case 2:  r = 0; g = 1; b = f; break;
			case 3:  r = 0; g = q; b = 1; break;
			case 4:  r = f; g = 0; b = 1; break;
			default: r = 1; g = 0; b = q; break;
		}
		int ri = Math.round(r * 255.0f);
		int gi = Math.round(g * 255.0f);
		int bi = Math.round(b * 255.0f);
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}
}
