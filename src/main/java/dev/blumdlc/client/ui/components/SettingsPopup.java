package dev.blumdlc.client.ui.components;

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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Right-side settings popup component.
 *
 * <p>Owns its own animation, scroll state, dropdown state and per-row drag
 * targets (slider knob, colour-hue knob). Exposes the standard
 * mouse / keyboard entry points used by {@code ClickGuiScreen}; each one
 * returns {@code true} to indicate the input was consumed by the popup.
 *
 * <p>Geometry: the popup hangs to the right of the main panel, separated by
 * {@link #GAP} pixels and using the same height as the panel. If the right
 * side overflows the screen (high GUI scale), the popup flips to the left
 * of the panel. Settings content is scissor-clipped so it never bleeds past
 * the popup body, and the dropdown overlay flips above the trigger when
 * there is no room below it.
 */
public final class SettingsPopup {

	/** Width of the popup in scaled pixels. */
	public static final float WIDTH = 200.0f;
	/** Gap between the main panel and the popup's left edge. */
	public static final float GAP = 7.0f;

	private static final float PAD_X = 12.0f;
	private static final float HEADER_H = 32.0f;
	private static final float ROW_GAP = 7.0f;
	private static final float CLOSE_SIZE = 14.0f;

	// --- Animation ---
	private final Animation openAnim = new Animation(0.0f, 320, Easing.EASE_OUT_EXPO);

	// --- What the popup is showing ---
	private Module module;

	// --- Drag targets (null when not dragging) ---
	private NumberSetting draggingSlider;
	private ColorSetting draggingColor;

	// --- Open ModeSetting dropdown overlay ---
	private ModeSetting openDropdown;
	private float dropdownX, dropdownY, dropdownW, dropdownH;
	/** Final Y where the dropdown list was rendered (after flip / clamp). */
	private float dropdownListY;
	/** Final height of the dropdown list (clamped to popup body if needed). */
	private float dropdownListH;

	// --- Vertical scroll ---
	private final Animation scroll = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
	private float scrollTarget = 0.0f;
	private float maxScroll = 0.0f;

	// --- Cached settings height (invalidated on module change) ---
	private float cachedSettingsHeight = -1.0f;
	private Module cachedHeightModule = null;

	// --- Last rendered popup bounds (used to keep input + dropdown overlay in
	//     sync with the rendered geometry, especially after flip-to-left). ---
	private float lastX, lastY, lastW, lastH;

	// =========================================================================
	//  Lifecycle
	// =========================================================================

	/** True while the popup is at all visible (any non-zero alpha). */
	public boolean isVisible() {
		return module != null && openAnim.getValue() > 0.001f;
	}

	/** True when the popup is settled enough to take input. */
	public boolean isInteractive() {
		return module != null && openAnim.getValue() > 0.5f;
	}

	public Module getModule() {
		return module;
	}

	/**
	 * Toggles the popup for {@code m}. Opening the same module again closes
	 * it. Modules without any settings are ignored.
	 */
	public void toggle(Module m) {
		if (m == null) return;
		if (m == module) {
			close();
			return;
		}
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
	}

	/** Animates the popup out without forgetting which module it was showing. */
	public void close() {
		this.openAnim.setTarget(0.0f);
		this.openDropdown = null;
		this.draggingSlider = null;
		this.draggingColor = null;
		this.cachedSettingsHeight = -1.0f;
	}

	/** Forgets the current module immediately and zeros all animations. */
	public void detach() {
		this.module = null;
		this.openAnim.setImmediate(0.0f);
		this.scrollTarget = 0.0f;
		this.scroll.setImmediate(0.0f);
		this.openDropdown = null;
		this.draggingSlider = null;
		this.draggingColor = null;
	}

	// =========================================================================
	//  Layout helpers
	// =========================================================================

	/**
	 * Picks the popup's X position. Default: to the right of the panel.
	 * If that overflows the screen, flip to the left of the panel.
	 * If both overflow, pin to the right edge.
	 */
	private static float computeX(float panelX, float panelW) {
		float screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
		float right = panelX + panelW + GAP;
		if (right + WIDTH <= screenW - 4.0f) {
			return right;
		}
		float left = panelX - WIDTH - GAP;
		if (left >= 4.0f) {
			return left;
		}
		// Neither side fits cleanly — pin to whichever edge keeps it most visible.
		float pinned = screenW - WIDTH - 4.0f;
		return Math.max(4.0f, pinned);
	}

	// =========================================================================
	//  Render
	// =========================================================================

	public void render(DrawContext ctx, Matrix4f matrix, MsdfFont font,
			float panelX, float panelY, float panelW, float panelH,
			float panelOpen, int mouseX, int mouseY) {
		if (module == null) return;
		float t = openAnim.getValue();
		if (t < 0.001f) {
			// Animation finished closing — release the module reference
			if (openAnim.getTarget() == 0.0f) {
				module = null;
			}
			return;
		}

		float baseX = computeX(panelX, panelW);
		// Slide-in direction follows the side the popup ended up on, so the
		// popup always travels inward from the panel rather than off-screen.
		boolean leftSide = baseX < panelX;
		float startX = baseX + (leftSide ? +16.0f : -16.0f);
		float x = startX + (baseX - startX) * t;
		float y = panelY;
		float w = WIDTH;
		float h = panelH;
		float a = panelOpen * t;

		// Stash for input methods + dropdown overlay
		this.lastX = x;
		this.lastY = y;
		this.lastW = w;
		this.lastH = h;

		// Body — soft drop shadow + vertical gradient + inner hairline.
		UIRender.rect(matrix, x - 5.0f, y - 3.0f, w + 10.0f, h + 10.0f,
			18.0f, ColorUtil.multiplyAlpha(0x55000000, a * 0.85f));
		UIRender.rectGradientV(matrix, x, y, w, h, 14.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BG_TOP, a),
			ColorUtil.multiplyAlpha(Theme.PANEL_BG_BOT, a));
		UIRender.border(matrix, x, y, w, h, 14.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, a));
		UIRender.border(matrix, x + 1.5f, y + 1.5f, w - 3.0f, h - 3.0f, 12.5f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_INNER, a));

		// Header — title and description, both ellipsized so very long names
		// can't bleed past the close button or the body edge.
		float headerTextW = w - PAD_X * 2.0f - CLOSE_SIZE - 6.0f;
		String title = UIRender.ellipsize(font, module.name, 11.0f, headerTextW);
		String desc  = UIRender.ellipsize(font, module.description, 6.5f, w - PAD_X * 2.0f);
		UIRender.text(matrix, font, title, x + PAD_X, y + 12.0f,
			11.0f, ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.06f);
		UIRender.text(matrix, font, desc, x + PAD_X, y + 26.0f,
			6.5f, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a), 0.04f);

		// Close X (top-right)
		float closeX = x + w - PAD_X - CLOSE_SIZE;
		float closeY = y + 12.0f;
		boolean closeHover = inside(mouseX, mouseY, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE);
		int closeColor = closeHover ? Theme.DANGER : 0xFFA0A0B0;
		UIRender.rect(matrix, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE, 4.0f,
			ColorUtil.multiplyAlpha(closeHover ? Theme.DANGER : 0x10FFFFFF,
				(closeHover ? 0.18f : 1.0f) * a));
		UIRender.text(matrix, font, "x", closeX + 4.0f, closeY + 3.0f, 8.0f,
			ColorUtil.multiplyAlpha(closeColor, a));

		// Divider under header
		UIRender.rect(matrix, x + PAD_X, y + HEADER_H, w - PAD_X * 2, 1.0f,
			0.0f, ColorUtil.multiplyAlpha(Theme.DIVIDER, a));

		// Scrollable settings list
		float contentTop = y + HEADER_H + 10.0f;
		float contentBottom = y + h - 10.0f;
		float contentX = x + PAD_X;
		float contentW = w - PAD_X * 2;

		float totalH = getSettingsHeight(module);
		float visibleH = contentBottom - contentTop;
		maxScroll = Math.max(0.0f, totalH - visibleH);
		scrollTarget = Math.max(0.0f, Math.min(scrollTarget, maxScroll));
		scroll.setTarget(scrollTarget);

		// Scissor settings rows so they don't bleed into the header / past the
		// body edge while scrolling.
		ctx.enableScissor(
			(int) Math.floor(x + 1.0f),
			(int) Math.floor(contentTop),
			(int) Math.ceil(x + w - 1.0f),
			(int) Math.ceil(contentBottom));

		float scrollOff = scroll.getValue();
		float cy = contentTop - scrollOff;
		for (Setting<?> s : module.settings) {
			if (!isSettingVisible(s)) continue;
			cy = drawSetting(matrix, font, s, contentX, cy, contentW, a, mouseX, mouseY,
				contentTop, contentBottom);
			cy += ROW_GAP;
		}

		ctx.disableScissor();

		// Scrollbar
		if (maxScroll > 0.5f) {
			float trackX = x + w - 5.0f;
			float trackY = contentTop;
			float trackH = visibleH;
			float thumbH = Math.max(16.0f, trackH * (trackH / (trackH + maxScroll)));
			float thumbY = trackY + (scrollOff / maxScroll) * (trackH - thumbH);
			UIRender.rect(matrix, trackX, trackY, 2.5f, trackH, 1.5f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_BG, a));
			UIRender.rect(matrix, trackX, thumbY, 2.5f, thumbH, 1.5f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_FG, a));
		}

		// Dropdown overlay last, with its own scissor so it can't escape the
		// popup body even if it has to flip.
		drawDropdownOverlay(ctx, matrix, font, a, mouseX, mouseY);
	}

	// =========================================================================
	//  Input
	// =========================================================================

	/**
	 * @return {@code true} if the click hit the popup (consuming it). The
	 *         caller should NOT process the click further when this returns
	 *         true even if no inner element handled it (clicks inside the
	 *         popup body never leak to the cards).
	 */
	public boolean mouseClicked(double mx, double my,
			float panelX, float panelY, float panelW, float panelH, int button) {
		if (!isInteractive()) return false;

		float popupX = computeX(panelX, panelW);
		if (!inside(mx, my, popupX, panelY, WIDTH, panelH)) return false;

		// Close X
		float closeX = popupX + WIDTH - PAD_X - CLOSE_SIZE;
		float closeY = panelY + 12.0f;
		if (button == 0 && inside(mx, my, closeX, closeY, CLOSE_SIZE, CLOSE_SIZE)) {
			close();
			return true;
		}

		// Setting rows
		if (handleSettingsClick(mx, my, popupX, panelY, button)) return true;

		// Anywhere else inside the popup body: consume so cards don't react
		return true;
	}

	public boolean mouseDragged(double mx, double my, int button, float panelX, float panelW) {
		if (button != 0 || module == null) return false;
		float popupX = computeX(panelX, panelW);
		float contentX = popupX + PAD_X;
		float trackW = WIDTH - PAD_X * 2;

		if (draggingSlider != null) {
			float frac = clamp01((float) ((mx - contentX) / trackW));
			NumberSetting ns = draggingSlider;
			double v = ns.min + (ns.max - ns.min) * frac;
			if (ns.step > 0.0) {
				v = Math.round(v / ns.step) * ns.step;
			}
			v = Math.max(ns.min, Math.min(ns.max, v));
			ns.set(v);
			return true;
		}
		if (draggingColor != null) {
			float frac = clamp01((float) ((mx - contentX) / trackW));
			draggingColor.setHue(frac * 360.0);
			return true;
		}
		return false;
	}

	public boolean mouseReleased(int button) {
		if (button == 0) {
			boolean hadDrag = draggingSlider != null || draggingColor != null;
			draggingSlider = null;
			draggingColor = null;
			return hadDrag;
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double v,
			float panelX, float panelY, float panelW, float panelH) {
		if (!isInteractive()) return false;
		float popupX = computeX(panelX, panelW);
		if (!inside(mx, my, popupX, panelY, WIDTH, panelH)) return false;
		scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget - (float) v * 20.0f));
		scroll.setTarget(scrollTarget);
		return true;
	}

	/**
	 * Handles the popup's keyboard concerns. ESC first closes any open
	 * dropdown, then closes the popup itself.
	 */
	public boolean keyPressed(int keyCode) {
		if (module == null) return false;
		if (keyCode == 256 /* ESC */) {
			if (openDropdown != null) {
				openDropdown = null;
				return true;
			}
			close();
			return true;
		}
		return false;
	}

	// =========================================================================
	//  Internal: per-setting drawing
	// =========================================================================

	private float drawSetting(Matrix4f matrix, MsdfFont font, Setting<?> s,
			float x, float y, float w, float a, int mouseX, int mouseY,
			float clipTop, float clipBottom) {
		boolean visible = (y + 40.0f > clipTop) && (y < clipBottom);

		if (visible) {
			String label = UIRender.ellipsize(font, s.name, 8.0f, w - 28.0f);
			UIRender.text(matrix, font, label, x, y, 8.0f,
				ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);
		}

		float ny = y + 12.0f;

		if (s instanceof BooleanSetting bs) {
			if (visible) ny = drawBoolean(matrix, bs, x, y, w, a);
			else ny = y + 14.0f;
		} else if (s instanceof ColorSetting cs) {
			if (visible) ny = drawColor(matrix, font, cs, x, ny, w, a);
			else ny += 22.0f;
		} else if (s instanceof NumberSetting ns) {
			if (visible) ny = drawSlider(matrix, font, ns, x, ny, w, a);
			else ny += 22.0f;
		} else if (s instanceof ModeSetting ms) {
			if (visible) ny = drawMode(matrix, font, ms, x, ny, w, a, mouseX, mouseY);
			else ny += (ms.expanded ? ms.modes.size() * 17.0f : 18.0f);
		} else if (s instanceof MultiSetting mu) {
			if (visible) ny = drawMulti(matrix, font, mu, x, ny, w, a, mouseX, mouseY);
			else ny += mu.options.size() * 17.0f;
		} else {
			ny += 14.0f;
		}
		return ny;
	}

	private float drawBoolean(Matrix4f matrix, BooleanSetting s,
			float x, float y, float w, float a) {
		float trackW = 22.0f;
		float trackH = 12.0f;
		float tx = x + w - trackW;
		float ty = y - 1.0f;
		boolean on = s.get();
		int trackColor = on ? Theme.CARD_ACTIVE_TO : 0x33FFFFFF;
		UIRender.rect(matrix, tx, ty, trackW, trackH, trackH * 0.5f,
			ColorUtil.multiplyAlpha(trackColor, a));
		float knobR = trackH - 4.0f;
		float knobX = on ? tx + trackW - knobR - 2.0f : tx + 2.0f;
		UIRender.rect(matrix, knobX, ty + 2.0f, knobR, knobR, knobR * 0.5f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, a));
		return y + 14.0f;
	}

	private float drawSlider(Matrix4f matrix, MsdfFont font, NumberSetting s,
			float x, float y, float w, float a) {
		// Reserve a fixed strip on the right for the value label so it never
		// runs into the slider knob, and so the track has predictable width.
		float labelW = 28.0f;
		float trackW = Math.max(20.0f, w - labelW);
		float trackH = 4.0f;
		float trackY = y + 4.0f;
		float frac = clamp01((float) ((s.get() - s.min) / (s.max - s.min)));

		UIRender.rect(matrix, x, trackY, trackW, trackH, 2.0f,
			ColorUtil.multiplyAlpha(0x22FFFFFF, a));
		UIRender.rectGradientH(matrix, x, trackY, trackW * frac, trackH, 2.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   a));

		// Knob — clamped so it can't poke past the track ends.
		float knobX = Math.max(x, Math.min(x + trackW - 8.0f, x + trackW * frac - 4.0f));
		UIRender.rect(matrix, knobX, trackY - 3.0f, 8.0f, 10.0f, 4.0f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, a));

		// Value label — right-aligned within its reserved strip.
		String value = String.format("%.1f", s.get());
		String shown = UIRender.ellipsize(font, value, 7.0f, labelW - 2.0f);
		float vw = UIRender.textWidth(font, shown, 7.0f);
		UIRender.text(matrix, font, shown, x + w - vw, y + 2.5f, 7.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, a));

		return y + 22.0f;
	}

	private float drawColor(Matrix4f matrix, MsdfFont font, ColorSetting s,
			float x, float y, float w, float a) {
		float labelW = 24.0f;
		float trackH = 10.0f;
		float trackY = y + 2.0f;
		float trackW = Math.max(20.0f, w - labelW);

		// 24-segment rainbow gradient with rounded ends
		int segments = 24;
		float segW = trackW / segments;
		for (int i = 0; i < segments; i++) {
			float h1 = i / (float) segments;
			float h2 = (i + 1.0f) / segments;
			int c1 = ColorUtil.multiplyAlpha(hsvColor(h1), a);
			int c2 = ColorUtil.multiplyAlpha(hsvColor(h2), a);
			float sx = x + i * segW;
			float sw = (i == segments - 1) ? (trackW - i * segW) : segW;
			float radius = (i == 0 || i == segments - 1) ? trackH * 0.4f : 0.0f;
			UIRender.rectGradientH(matrix, sx, trackY, sw, trackH, radius, c1, c2);
		}

		UIRender.border(matrix, x, trackY, trackW, trackH, trackH * 0.4f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_BORDER, a));

		// Knob (clamped to track) with inner colour swatch
		float frac = clamp01((float) (s.getHue() / 360.0));
		float knobX = Math.max(x, Math.min(x + trackW - 8.0f, x + trackW * frac - 4.0f));
		float knobY = trackY - 2.0f;
		UIRender.rect(matrix, knobX, knobY, 8.0f, trackH + 4.0f, 3.0f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, a));
		UIRender.rect(matrix, knobX + 2.0f, knobY + 2.0f, 4.0f, trackH, 2.0f,
			ColorUtil.multiplyAlpha(s.toArgb(), a));

		// Hue label — right-aligned within reserved strip.
		String value = String.format("%.0f", s.getHue());
		String shown = UIRender.ellipsize(font, value, 7.0f, labelW - 2.0f);
		float vw = UIRender.textWidth(font, shown, 7.0f);
		UIRender.text(matrix, font, shown, x + w - vw, y + 4.0f, 7.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, a));

		return y + 22.0f;
	}

	private float drawMode(Matrix4f matrix, MsdfFont font, ModeSetting s,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		if (s.expanded) {
			return drawModeExpanded(matrix, font, s, x, y, w, a, mouseX, mouseY);
		}

		float h = 16.0f;
		boolean hovered = inside(mouseX, mouseY, x, y, w, h);
		boolean open = (openDropdown == s);

		UIRender.rectGradientV(matrix, x, y, w, h, 7.0f,
			ColorUtil.multiplyAlpha(open || hovered ? Theme.CARD_HOVER : Theme.CARD_BG_TOP, a),
			ColorUtil.multiplyAlpha(open || hovered ? Theme.CARD_HOVER : Theme.CARD_BG_BOT, a));
		UIRender.border(matrix, x, y, w, h, 7.0f, 1.0f,
			ColorUtil.multiplyAlpha(open ? Theme.ACCENT : Theme.CARD_BORDER, a));

		// Reserve room on the right for the caret indicator (~8 px) so the
		// current value never overlaps it.
		String label = UIRender.ellipsize(font, s.get(), 7.5f, w - 24.0f);
		UIRender.text(matrix, font, label, x + 8.0f, y + 4.5f, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);
		drawCaret(matrix, x + w - 11.0f, y + 6.0f, 5.0f, !open,
			ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a));

		// Stash for the dropdown overlay
		if (open) {
			dropdownX = x;
			dropdownY = y;
			dropdownW = w;
			dropdownH = h;
		}
		return y + h + 2.0f;
	}

	private float drawModeExpanded(Matrix4f matrix, MsdfFont font, ModeSetting s,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		float rowH = 14.0f;
		float rowGap = 3.0f;
		float cy = y;
		String selected = s.get();
		for (String mode : s.modes) {
			boolean isSelected = mode.equals(selected);
			boolean hovered = inside(mouseX, mouseY, x, cy, w, rowH);

			if (isSelected) {
				float p = 0.85f + 0.15f * (float) Math.sin(now() * 2.4);
				UIRender.rectGradientH(matrix, x, cy, w, rowH, 6.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, p * a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   p * a));
			} else {
				UIRender.rectGradientV(matrix, x, cy, w, rowH, 6.0f,
					ColorUtil.multiplyAlpha(hovered ? Theme.CARD_HOVER : Theme.CARD_BG_TOP, a),
					ColorUtil.multiplyAlpha(hovered ? Theme.CARD_HOVER : Theme.CARD_BG_BOT, a));
				UIRender.border(matrix, x, cy, w, rowH, 6.0f, 1.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.5f * a));
			}

			int textColor = isSelected ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			String label = UIRender.ellipsize(font, mode, 7.0f, w - 16.0f);
			UIRender.text(matrix, font, label, x + 8.0f, cy + 3.5f, 7.0f,
				ColorUtil.multiplyAlpha(textColor, a), 0.04f);

			cy += rowH + rowGap;
		}
		return cy;
	}

	private float drawMulti(Matrix4f matrix, MsdfFont font, MultiSetting s,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		float rowH = 14.0f;
		float rowGap = 3.0f;
		float cy = y;
		for (String opt : s.options) {
			boolean selected = s.isSelected(opt);
			boolean hovered = inside(mouseX, mouseY, x, cy, w, rowH);

			if (selected) {
				float p = 0.85f + 0.15f * (float) Math.sin(now() * 2.4);
				UIRender.rectGradientH(matrix, x, cy, w, rowH, 6.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, p * a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   p * a));
			} else {
				UIRender.rectGradientV(matrix, x, cy, w, rowH, 6.0f,
					ColorUtil.multiplyAlpha(hovered ? Theme.CARD_HOVER : Theme.CARD_BG_TOP, a),
					ColorUtil.multiplyAlpha(hovered ? Theme.CARD_HOVER : Theme.CARD_BG_BOT, a));
				UIRender.border(matrix, x, cy, w, rowH, 6.0f, 1.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.5f * a));
			}

			int textColor = selected ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			String label = UIRender.ellipsize(font, opt, 7.0f, w - 16.0f);
			UIRender.text(matrix, font, label, x + 8.0f, cy + 3.5f, 7.0f,
				ColorUtil.multiplyAlpha(textColor, a), 0.04f);

			cy += rowH + rowGap;
		}
		return cy;
	}

	private void drawDropdownOverlay(DrawContext ctx, Matrix4f matrix, MsdfFont font, float a,
			int mouseX, int mouseY) {
		ModeSetting s = openDropdown;
		if (s == null) {
			this.dropdownListY = 0.0f;
			this.dropdownListH = 0.0f;
			return;
		}

		float rowH = 14.0f;
		float listH = s.modes.size() * rowH;

		// Find the best vertical placement: prefer below, fall back to above,
		// then pin to whichever side has the most room. All the while keeping
		// the list inside the popup body.
		float bodyTop = lastY + HEADER_H + 4.0f;
		float bodyBottom = lastY + lastH - 4.0f;

		float yBelow = dropdownY + dropdownH + 2.0f;
		float yAbove = dropdownY - 2.0f - listH;

		float y;
		float drawnH = listH;
		if (yBelow + listH <= bodyBottom) {
			y = yBelow;
		} else if (yAbove >= bodyTop) {
			y = yAbove;
		} else {
			// Neither side fits — pin and clamp height.
			y = Math.max(bodyTop, bodyBottom - listH);
			drawnH = Math.min(listH, bodyBottom - y);
			if (drawnH <= rowH * 0.5f) {
				// Practically nothing to draw. Skip without leaving stale state.
				this.dropdownListY = 0.0f;
				this.dropdownListH = 0.0f;
				return;
			}
		}

		this.dropdownListY = y;
		this.dropdownListH = drawnH;
		float x = dropdownX;
		float w = dropdownW;

		// Scissor to the popup body so the overlay can never escape.
		ctx.enableScissor(
			(int) Math.floor(lastX + 1.0f),
			(int) Math.floor(bodyTop),
			(int) Math.ceil(lastX + lastW - 1.0f),
			(int) Math.ceil(bodyBottom));

		UIRender.rect(matrix, x, y, w, drawnH, 8.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BG, a));
		UIRender.border(matrix, x, y, w, drawnH, 8.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.ACCENT, a));

		float cy = y;
		String selected = s.get();
		for (String mode : s.modes) {
			if (cy >= y + drawnH) break;
			boolean isSelected = mode.equals(selected);
			boolean hovered = inside(mouseX, mouseY, x, cy, w, rowH);

			if (isSelected) {
				UIRender.rectGradientH(matrix, x + 1, cy, w - 2, rowH, 6.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   a));
			} else if (hovered) {
				UIRender.rect(matrix, x + 1, cy, w - 2, rowH, 6.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_HOVER, a));
			}

			int textColor = isSelected ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			String label = UIRender.ellipsize(font, mode, 7.0f, w - 16.0f);
			UIRender.text(matrix, font, label, x + 8.0f, cy + 3.5f, 7.0f,
				ColorUtil.multiplyAlpha(textColor, a), 0.04f);

			cy += rowH;
		}

		ctx.disableScissor();
	}

	// =========================================================================
	//  Internal: per-setting click handling
	// =========================================================================

	private boolean handleSettingsClick(double mouseX, double mouseY,
			float popupX, float popupY, int button) {
		float contentX = popupX + PAD_X;
		float contentW = WIDTH - PAD_X * 2;
		float cy = popupY + HEADER_H + 10.0f - scroll.getValue();

		// 1. Open dropdown (if any) intercepts first — using the rendered
		//    overlay position so flip-up dropdowns hit-test correctly.
		if (openDropdown != null && button == 0) {
			float listY = dropdownListY;
			float listH = dropdownListH;
			if (listH > 0.0f
				&& inside(mouseX, mouseY, dropdownX, listY, dropdownW, listH)) {
				float rowH = 14.0f;
				int idx = (int) ((mouseY - listY) / rowH);
				if (idx >= 0 && idx < openDropdown.modes.size()) {
					openDropdown.set(openDropdown.modes.get(idx));
					cachedSettingsHeight = -1.0f;
				}
				openDropdown = null;
				return true;
			}
			if (inside(mouseX, mouseY, dropdownX, dropdownY, dropdownW, dropdownH)) {
				openDropdown = null;
				return true;
			}
			openDropdown = null; // close, then fall through to row handling
		}

		for (Setting<?> s : module.settings) {
			if (!isSettingVisible(s)) continue;
			float labelY = cy;
			float controlY = cy + 12.0f;

			if (s instanceof BooleanSetting bs) {
				float trackW = 22.0f;
				float trackH = 12.0f;
				float tx = contentX + contentW - trackW;
				float ty = labelY - 1.0f;
				if (inside(mouseX, mouseY, tx, ty, trackW, trackH) && button == 0) {
					bs.toggle();
					cachedSettingsHeight = -1.0f; // visibility may have changed
					return true;
				}
				cy = labelY + 14.0f + ROW_GAP;

			} else if (s instanceof ColorSetting cs) {
				float labelW = 24.0f;
				float trackW = Math.max(20.0f, contentW - labelW);
				float trackY = controlY + 2.0f;
				if (inside(mouseX, mouseY, contentX, trackY - 2.0f, trackW, 14.0f) && button == 0) {
					float frac = clamp01((float) ((mouseX - contentX) / trackW));
					cs.setHue(frac * 360.0);
					draggingColor = cs;
					return true;
				}
				cy = controlY + 22.0f + ROW_GAP;

			} else if (s instanceof NumberSetting ns) {
				float labelW = 28.0f;
				float trackW = Math.max(20.0f, contentW - labelW);
				float trackY = controlY + 4.0f;
				if (inside(mouseX, mouseY, contentX, trackY - 4.0f, trackW, 14.0f) && button == 0) {
					float frac = clamp01((float) ((mouseX - contentX) / trackW));
					double v = ns.min + (ns.max - ns.min) * frac;
					if (ns.step > 0.0) {
						v = Math.round(v / ns.step) * ns.step;
					}
					v = Math.max(ns.min, Math.min(ns.max, v));
					ns.set(v);
					draggingSlider = ns;
					return true;
				}
				cy = controlY + 22.0f + ROW_GAP;

			} else if (s instanceof ModeSetting ms) {
				if (ms.expanded) {
					float rowH = 14.0f;
					float rowGap = 3.0f;
					float ry = controlY;
					for (String mode : ms.modes) {
						if (inside(mouseX, mouseY, contentX, ry, contentW, rowH) && button == 0) {
							ms.set(mode);
							cachedSettingsHeight = -1.0f;
							return true;
						}
						ry += rowH + rowGap;
					}
					cy = ry + ROW_GAP;
				} else {
					float h = 16.0f;
					if (inside(mouseX, mouseY, contentX, controlY, contentW, h) && button == 0) {
						openDropdown = (openDropdown == ms) ? null : ms;
						return true;
					}
					cy = controlY + h + 2.0f + ROW_GAP;
				}

			} else if (s instanceof MultiSetting mu) {
				float rowH = 14.0f;
				float rowGap = 3.0f;
				float ry = controlY;
				for (String opt : mu.options) {
					if (inside(mouseX, mouseY, contentX, ry, contentW, rowH) && button == 0) {
						mu.toggle(opt);
						cachedSettingsHeight = -1.0f;
						return true;
					}
					ry += rowH + rowGap;
				}
				cy = ry + ROW_GAP;

			} else {
				cy = controlY + 14.0f + ROW_GAP;
			}
		}
		return false;
	}

	// =========================================================================
	//  Internal: helpers
	// =========================================================================

	/** Pre-computes total height of all settings (for scroll bounds). */
	private static float computeSettingsHeight(Module module) {
		float h = 0.0f;
		for (Setting<?> s : module.settings) {
			if (!isSettingVisible(s)) continue;
			if (s instanceof BooleanSetting) {
				h += 14.0f;
			} else if (s instanceof ColorSetting) {
				h += 12.0f + 22.0f;
			} else if (s instanceof NumberSetting) {
				h += 12.0f + 22.0f;
			} else if (s instanceof ModeSetting ms) {
				h += 12.0f + (ms.expanded ? ms.modes.size() * 17.0f : 18.0f);
			} else if (s instanceof MultiSetting mu) {
				h += 12.0f + mu.options.size() * 17.0f;
			} else {
				h += 12.0f + 14.0f;
			}
			h += ROW_GAP;
		}
		return h;
	}

	/**
	 * Returns cached settings height, recomputing only when the module changes.
	 * For FoldCraft Launcher optimization: avoids recalculating every frame.
	 */
	private float getSettingsHeight(Module m) {
		if (m != cachedHeightModule || cachedSettingsHeight < 0.0f) {
			cachedSettingsHeight = computeSettingsHeight(m);
			cachedHeightModule = m;
		}
		return cachedSettingsHeight;
	}

	/** Invalidates the cached settings height (call when a setting visibility changes). */
	public void invalidateHeightCache() {
		this.cachedSettingsHeight = -1.0f;
	}

	/**
	 * Checks whether a setting should be visible based on its visibility
	 * predicate. Settings with no predicate are always visible.
	 */
	private static boolean isSettingVisible(Setting<?> s) {
		Supplier<Boolean> vis = s.getVisibility();
		return vis == null || vis.get();
	}

	/** Hue 0..1 -> fully-saturated ARGB. */
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

	/**
	 * Tiny font-free caret indicator drawn from 1-px rects. {@code down=true}
	 * draws ▾, {@code down=false} draws ▴. Avoids depending on glyphs that
	 * may be missing from the MSDF atlas (the bundled {@code biko} font has
	 * no {@code ^}), which previously caused the renderer to hand an empty
	 * vertex buffer to {@code BufferBuilder.end()} and crash on Android.
	 */
	private static void drawCaret(Matrix4f m, float x, float y, float size, boolean down, int color) {
		int rows = 3;
		for (int r = 0; r < rows; r++) {
			float rowW;
			float rowX;
			if (down) {
				rowW = size - r * 2.0f;
				rowX = x + r;
			} else {
				rowW = 1.0f + r * 2.0f;
				rowX = x + (size - rowW) * 0.5f;
			}
			if (rowW < 1.0f) rowW = 1.0f;
			UIRender.rect(m, rowX, y + r, rowW, 1.0f, 0.0f, color);
		}
	}

	private static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	/** Seconds-resolution timer used to drive ambient pulse animations. */
	private static float now() {
		return (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
	}
}
