package dev.blumdlc.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.modules.impl.Themes;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.ColorSetting;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.settings.Setting;
import dev.blumdlc.client.ui.animation.Animation;
import dev.blumdlc.client.ui.animation.Easing;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.KeyName;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * DropDown ClickGUI — five fixed category panels arranged in a row at the
 * top of the screen.
 *
 * <p><b>Animations</b> drive every visible state change so nothing pops
 * abruptly:
 * <ul>
 *   <li>{@code openAnim} — root fade-in/out for the whole GUI;</li>
 *   <li>per-module {@code toggleAnim} — accent bar + tint when a module
 *       turns on/off;</li>
 *   <li>per-module {@code hoverAnim} — soft hover highlight that follows
 *       the mouse with a small lag;</li>
 *   <li>per-module {@code settingsAnim} — fold-in of the inline settings
 *       sub-panel (settings rendering is clipped to the animated height,
 *       so they can't bleed into the next module row);</li>
 *   <li>per-panel {@code scrollAnim} — wheel scrolling glides instead of
 *       jumping, and the value is clamped between zero and the max scroll
 *       implied by the body height;</li>
 *   <li>{@code dropdownAnim} — overlay drop-down lists fade + slide in,
 *       and reverse-animate when closing.</li>
 * </ul>
 *
 * <p><b>Interaction model</b>:
 * <ul>
 *   <li>Left-click a module row to toggle it.</li>
 *   <li>Right-click a module row to expand its inline settings sub-panel.</li>
 *   <li>Middle-click a module row to start a key bind.</li>
 *   <li>Inside a settings sub-panel: boolean → click box; number →
 *       click+drag slider; mode/multi → click trigger to open dropdown;
 *       color → click ±30° hue.</li>
 * </ul>
 */
public final class ClickGuiScreen extends Screen {

	// =========================================================================
	//  Geometry constants
	// =========================================================================

	private static final float PANEL_W       = 110.0f;
	private static final float HEADER_H      = 18.0f;
	private static final float MODULE_H      = 14.0f;
	private static final float SETTING_H     = 13.0f;
	private static final float CORNER        = 4.0f;
	private static final float GAP           = 3.0f;
	private static final float PANEL_GAP     = 4.0f;
	private static final float DROPDOWN_ROW_H = 12.0f;
	/** Soft cap on body height so panels don't run off-screen on small res. */
	private static final float BODY_MAX_H    = 320.0f;

	/** Wheel sensitivity; one notch ≈ this many pixels of scroll target. */
	private static final float SCROLL_STEP   = 18.0f;

	// =========================================================================
	//  Category panels
	// =========================================================================

	private static final Category[] CATEGORIES = {
		Category.COMBAT, Category.MOVEMENT, Category.RENDER,
		Category.PLAYER, Category.UTIL
	};

	private final List<Panel> panels = new ArrayList<>();
	private final Animation openAnim = new Animation(0.0f, 280, Easing.EASE_OUT_QUINT);

	/**
	 * Currently-open dropdown overlay (single- or multi-select), or
	 * {@code null} when none. Dropdowns live at the screen level so the
	 * list rect can float above sibling panels.
	 *
	 * <p>The overlay's open/close progress is on {@link #dropdownAnim};
	 * even after {@link #openDropdown} is nulled, the animation may still
	 * be playing the close phase (we keep a {@code closingDropdown}
	 * reference so the overlay can fade out gracefully).
	 */
	private OpenDropdown openDropdown;
	private OpenDropdown closingDropdown;
	private final Animation dropdownAnim = new Animation(0.0f, 200, Easing.EASE_OUT_QUART);

	/**
	 * Set every frame by {@link Panel#renderSetting} when it draws the
	 * trigger that owns {@link #openDropdown}. If after the per-panel pass
	 * the flag is still false, the trigger is no longer visible (its
	 * sub-panel was collapsed or scrolled out) and the overlay auto-closes.
	 */
	private boolean sawOpenDropdownThisFrame;

	// =========================================================================
	//  Lifecycle
	// =========================================================================

	public ClickGuiScreen() {
		super(Text.literal("Blum+"));
	}

	@Override
	protected void init() {
		super.init();
		float totalW = CATEGORIES.length * PANEL_W + (CATEGORIES.length - 1) * PANEL_GAP;
		float startX = Math.max(4.0f, (this.width - totalW) * 0.5f);
		float y = 10.0f;

		if (panels.isEmpty()) {
			for (Category cat : CATEGORIES) {
				panels.add(new Panel(cat, startX, y));
				startX += PANEL_W + PANEL_GAP;
			}
		} else {
			for (Panel p : panels) {
				p.x = startX;
				p.y = y;
				startX += PANEL_W + PANEL_GAP;
			}
		}
		openAnim.setImmediate(0.0f);
		openAnim.setTarget(1.0f);
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

	// =========================================================================
	//  Render
	// =========================================================================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		Themes.syncAll();
		Theme.refresh();

		Matrix4f m = context.getMatrices().peek().getPositionMatrix();
		MsdfFont font = Fonts.BIKO.get();
		float open = openAnim.getValue();

		// Dim backdrop — the alpha follows the root open animation so the
		// world fades in/out smoothly when the GUI shows or hides.
		UIRender.rect(m, 0, 0, this.width, this.height, 0,
			ColorUtil.withAlpha(0x000000, 0.45f * open));

		// Pass 1: render all panels (bodies + module rows + inline settings).
		// Each panel updates `sawOpenDropdownThisFrame` if it draws the trigger
		// owning the currently-open dropdown.
		sawOpenDropdownThisFrame = false;
		for (Panel panel : panels) {
			panel.render(m, font, mouseX, mouseY, open);
		}

		// Auto-close dropdown if its trigger was not visible this frame —
		// the overlay fades out via the close animation, never just pops.
		if (openDropdown != null && !sawOpenDropdownThisFrame) {
			beginDropdownClose();
		}

		// Pass 2: render the dropdown overlay so it floats above sibling
		// panels. We render either the active dropdown (opening/open) or
		// the lingering one that's animating closed.
		float dropProgress = dropdownAnim.getValue();
		OpenDropdown overlay = openDropdown != null ? openDropdown : closingDropdown;
		if (overlay != null && dropProgress > 0.001f) {
			renderDropdownOverlay(m, font, open * dropProgress, dropProgress, overlay,
				mouseX, mouseY);
		} else if (closingDropdown != null && dropProgress <= 0.001f) {
			closingDropdown = null;
		}

		super.render(context, mouseX, mouseY, delta);
	}

	private void renderDropdownOverlay(Matrix4f m, MsdfFont font, float overlayAlpha,
			float progress, OpenDropdown dd, int mouseX, int mouseY) {
		float tx = dd.triggerX;
		float ty = dd.triggerY;
		float tw = dd.triggerW;
		float th = dd.triggerH;

		int n = dd.rowCount();
		if (n == 0) {
			return;
		}

		float listH = n * DROPDOWN_ROW_H + 2.0f;

		// Place the list below the trigger, or flip above when the panel
		// would clip. The flip decision is made once and cached for the
		// life of the dropdown so the overlay doesn't jitter mid-animation.
		if (!dd.placementResolved) {
			float yBelow = ty + th + 2.0f;
			dd.flipAbove = (yBelow + listH > this.height - 4.0f);
			dd.placementResolved = true;
		}
		float ly = dd.flipAbove
			? Math.max(4.0f, ty - 2.0f - listH)
			: ty + th + 2.0f;
		float lx = tx;
		float lw = tw;

		// Slide-in: 6px above the resting position when collapsed, easing
		// down to its final spot. Direction inverts when flipped above.
		float slideOffset = (1.0f - progress) * 6.0f * (dd.flipAbove ? +1.0f : -1.0f);
		ly += slideOffset;

		// Stash for click hit-testing (use the *resting* rect so a click on
		// the visible rendered list still registers even mid-animation).
		dd.listX = lx;
		dd.listY = dd.flipAbove
			? Math.max(4.0f, ty - 2.0f - listH)
			: ty + th + 2.0f;
		dd.listW = lw;
		dd.listH = listH;

		// Drop shadow + body + accent border. Alpha follows overlayAlpha so
		// the entire list fades cleanly with progress.
		UIRender.rect(m, lx + 1.0f, ly + 2.0f, lw, listH, 4.0f,
			ColorUtil.withAlpha(0x000000, 0.45f * overlayAlpha));
		UIRender.rectGradientV(m, lx, ly, lw, listH, 4.0f,
			ColorUtil.withAlpha(0x141828, 0.95f * overlayAlpha),
			ColorUtil.withAlpha(0x080B14, 0.95f * overlayAlpha));
		UIRender.border(m, lx, ly, lw, listH, 4.0f, 1.0f,
			ColorUtil.withAlpha(ClientTheme.accent(), 0.8f * overlayAlpha));

		boolean multi = dd.isMulti();
		float cy = ly + 1.0f;
		for (int i = 0; i < n; i++) {
			boolean selected = dd.selected(i);
			boolean hovered = mouseX >= lx && mouseX <= lx + lw
				&& mouseY >= cy && mouseY <= cy + DROPDOWN_ROW_H
				&& progress > 0.4f; // suppress hover during early fade-in
			Animation rowHover = dd.rowHoverAnim(i);
			rowHover.setTarget(hovered ? 1.0f : 0.0f);
			float hoverT = rowHover.getValue();

			if (!multi && selected) {
				UIRender.rectGradientH(m, lx + 1.5f, cy, lw - 3.0f, DROPDOWN_ROW_H, 2.5f,
					ColorUtil.withAlpha(ClientTheme.from(), 0.6f * overlayAlpha),
					ColorUtil.withAlpha(ClientTheme.to(),   0.6f * overlayAlpha));
			}
			if (hoverT > 0.01f) {
				UIRender.rect(m, lx + 1.5f, cy, lw - 3.0f, DROPDOWN_ROW_H, 2.5f,
					ColorUtil.withAlpha(0xFFFFFF, 0.10f * hoverT * overlayAlpha));
			}

			int textColor = (!multi && selected)
				? ColorUtil.withAlpha(0xFFFFFFFF, overlayAlpha)
				: ColorUtil.withAlpha(0xFFCCCCD0, overlayAlpha);

			float labelMaxW = lw - 8.0f - (multi ? 12.0f : 0.0f);
			String label = UIRender.ellipsize(font, dd.label(i), 6.5f, labelMaxW);
			UIRender.text(m, font, label, lx + 5.0f, cy + 3.0f, 6.5f, textColor, 0.05f);

			if (multi) {
				float bxW = 7.0f, bxH = 7.0f;
				float bx = lx + lw - bxW - 4.0f;
				float by = cy + (DROPDOWN_ROW_H - bxH) * 0.5f;
				int boxBg = selected
					? ColorUtil.withAlpha(ClientTheme.accent(), 0.85f * overlayAlpha)
					: ColorUtil.withAlpha(0x222530, 0.85f * overlayAlpha);
				UIRender.rect(m, bx, by, bxW, bxH, 1.5f, boxBg);
				UIRender.border(m, bx, by, bxW, bxH, 1.5f, 0.6f,
					ColorUtil.withAlpha(0xFFFFFF, 0.30f * overlayAlpha));
				if (selected) {
					UIRender.checkmark(m, bx + 0.5f, by + 0.5f, bxW - 1.0f,
						ColorUtil.withAlpha(0xFFFFFF, overlayAlpha));
				}
			}

			cy += DROPDOWN_ROW_H;
		}
	}

	private void beginDropdownClose() {
		if (openDropdown != null) {
			closingDropdown = openDropdown;
			openDropdown = null;
			dropdownAnim.setTarget(0.0f);
		}
	}

	private void beginDropdownOpen(OpenDropdown dd) {
		// If a different dropdown is fading out, drop it instantly so the
		// new one isn't blocked behind a stale ghost.
		closingDropdown = null;
		openDropdown = dd;
		dropdownAnim.setImmediate(0.0f);
		dropdownAnim.setTarget(1.0f);
	}

	// =========================================================================
	//  Input
	// =========================================================================

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		// 1. Dropdown overlay takes priority — its rect floats above panels.
		//    Only the *currently open* dropdown is interactive; a fading-out
		//    closingDropdown ignores clicks so the user can't double-pick.
		if (openDropdown != null) {
			if (mx >= openDropdown.listX && mx <= openDropdown.listX + openDropdown.listW
				&& my >= openDropdown.listY && my <= openDropdown.listY + openDropdown.listH) {
				if (button == 0) {
					int idx = (int) ((my - openDropdown.listY - 1.0f) / DROPDOWN_ROW_H);
					if (idx >= 0 && idx < openDropdown.rowCount()) {
						openDropdown.clickRow(idx);
					}
					if (!openDropdown.isMulti()) {
						beginDropdownClose();
					}
				}
				return true;
			}
			// Click on the trigger again → close (toggle).
			if (mx >= openDropdown.triggerX && mx <= openDropdown.triggerX + openDropdown.triggerW
				&& my >= openDropdown.triggerY && my <= openDropdown.triggerY + openDropdown.triggerH) {
				beginDropdownClose();
				return true;
			}
			// Click anywhere else: close, then keep going so the click can
			// still hit a different control underneath in the same gesture.
			beginDropdownClose();
		}

		// 2. Process panels in reverse (top-most first).
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel p = panels.get(i);
			if (p.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		for (Panel p : panels) p.mouseReleased(button);
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (button == 0) {
			for (Panel p : panels) {
				if (p.mouseDragged(mx)) return true;
			}
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
		// Scroll inside the open dropdown's list scrolls only the list (no
		// inner scroll yet, but consume the event so the panel underneath
		// doesn't scroll too).
		if (openDropdown != null
			&& mx >= openDropdown.listX && mx <= openDropdown.listX + openDropdown.listW
			&& my >= openDropdown.listY && my <= openDropdown.listY + openDropdown.listH) {
			return true;
		}
		for (Panel p : panels) {
			if (mx >= p.x && mx <= p.x + PANEL_W) {
				p.scrollBy(-(float) vert * SCROLL_STEP);
				return true;
			}
		}
		return super.mouseScrolled(mx, my, horiz, vert);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (openDropdown != null && keyCode == 256) { // ESC closes the dropdown first
			beginDropdownClose();
			return true;
		}
		for (Panel p : panels) {
			if (p.keyPressed(keyCode)) return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// =========================================================================
	//  OpenDropdown — works for both ModeSetting and MultiSetting
	// =========================================================================

	private static final class OpenDropdown {
		final ModeSetting modeSetting;     // null if multi
		final MultiSetting multiSetting;   // null if mode
		float triggerX, triggerY, triggerW, triggerH;
		float listX, listY, listW, listH;
		/** Resolved on first render; fixed for the rest of the dropdown's life. */
		boolean placementResolved;
		boolean flipAbove;

		/** Per-row hover animations, lazily allocated. */
		private final Map<Integer, Animation> rowHovers = new HashMap<>();

		OpenDropdown(ModeSetting m, float tx, float ty, float tw, float th) {
			this.modeSetting = m;
			this.multiSetting = null;
			set(tx, ty, tw, th);
		}

		OpenDropdown(MultiSetting mu, float tx, float ty, float tw, float th) {
			this.modeSetting = null;
			this.multiSetting = mu;
			set(tx, ty, tw, th);
		}

		private void set(float tx, float ty, float tw, float th) {
			this.triggerX = tx; this.triggerY = ty;
			this.triggerW = tw; this.triggerH = th;
		}

		boolean isMulti() {
			return multiSetting != null;
		}

		int rowCount() {
			return modeSetting != null
				? modeSetting.modes.size()
				: multiSetting.options.size();
		}

		String label(int i) {
			return modeSetting != null
				? modeSetting.modes.get(i)
				: multiSetting.options.get(i);
		}

		boolean selected(int i) {
			return modeSetting != null
				? modeSetting.modes.get(i).equals(modeSetting.get())
				: multiSetting.isSelected(multiSetting.options.get(i));
		}

		void clickRow(int i) {
			if (modeSetting != null) {
				modeSetting.set(modeSetting.modes.get(i));
			} else {
				multiSetting.toggle(multiSetting.options.get(i));
			}
		}

		Animation rowHoverAnim(int i) {
			Animation a = rowHovers.get(i);
			if (a == null) {
				a = new Animation(0.0f, 140, Easing.EASE_OUT_QUART);
				rowHovers.put(i, a);
			}
			return a;
		}
	}

	// =========================================================================
	//  Panel (one per category)
	// =========================================================================

	private final class Panel {
		final Category category;
		float x, y;

		/** Smoothed scroll position. {@link #scrollTarget} is the wheel sink. */
		float scrollTarget = 0.0f;
		final Animation scrollAnim = new Animation(0.0f, 220, Easing.EASE_OUT_QUART);

		final Map<Module, Animation> moduleAnims = new HashMap<>();
		final Map<Module, Animation> hoverAnims  = new HashMap<>();
		final Map<Module, Boolean>   settingsOpen = new HashMap<>();
		final Map<Module, Animation> settingsAnims = new HashMap<>();
		Module bindingModule = null;

		// Slider drag state — kept on the panel because both click and drag
		// translate mouse-X through the slider's track geometry.
		NumberSetting draggingSlider = null;
		Module draggingModule = null;

		Panel(Category category, float x, float y) {
			this.category = category;
			this.x = x;
			this.y = y;
		}

		List<Module> getModules() {
			return BlumDLC.MODULES.byCategory(category);
		}

		// ---- Scroll handling ----------------------------------------------

		/** Increment the user-facing scroll target, clamping to content. */
		void scrollBy(float delta) {
			scrollTarget = clampScroll(scrollTarget + delta);
			scrollAnim.setTarget(scrollTarget);
		}

		/** Re-clamp the existing target whenever content height shrinks. */
		float clampScroll(float v) {
			float maxScroll = maxScrollFor(getModules());
			if (v < 0.0f) return 0.0f;
			if (v > maxScroll) return maxScroll;
			return v;
		}

		float maxScrollFor(List<Module> modules) {
			float bodyH = computeBodyHeight(modules);
			float content = rawContentHeight(modules);
			return Math.max(0.0f, content - bodyH);
		}

		float rawContentHeight(List<Module> modules) {
			float h = 4.0f;
			for (Module mod : modules) {
				h += MODULE_H + GAP + settingsHeight(mod);
			}
			return h;
		}

		// ---- Render -------------------------------------------------------

		void render(Matrix4f m, MsdfFont font, int mx, int my, float open) {
			float alpha = open;
			List<Module> modules = getModules();

			// Re-clamp the target every frame so settings panels closing /
			// opening can't leave the scroll past the new content end.
			scrollTarget = clampScroll(scrollTarget);
			scrollAnim.setTarget(scrollTarget);
			float scroll = scrollAnim.getValue();

			// Header — gradient with category name centred.
			int hFrom = ColorUtil.withAlpha(ClientTheme.from(), 0.92f * alpha);
			int hTo   = ColorUtil.withAlpha(ClientTheme.to(),   0.92f * alpha);
			UIRender.rectGradientH(m, x, y, PANEL_W, HEADER_H, CORNER, hFrom, hTo);
			UIRender.border(m, x, y, PANEL_W, HEADER_H, CORNER, 0.8f,
				ColorUtil.withAlpha(0xFFFFFF, 0.18f * alpha));

			String catName = category.displayName;
			float tw = UIRender.textWidth(font, catName, 8.0f);
			UIRender.text(m, font, catName, x + (PANEL_W - tw) * 0.5f, y + 5.0f, 8.0f,
				ColorUtil.withAlpha(0xFFFFFF, 0.96f * alpha), 0.07f);

			// Body background.
			float bodyY = y + HEADER_H + 1.0f;
			float bodyH = computeBodyHeight(modules);

			UIRender.blur(m, x, bodyY, PANEL_W, bodyH, CORNER, 8.0f,
				ColorUtil.withAlpha(0x0A0E1A, 0.7f * alpha));
			UIRender.rect(m, x, bodyY, PANEL_W, bodyH, CORNER,
				ColorUtil.withAlpha(0x0D1117, 0.88f * alpha));
			UIRender.border(m, x, bodyY, PANEL_W, bodyH, CORNER, 0.7f,
				ColorUtil.withAlpha(0xFFFFFF, 0.06f * alpha));

			// Module rows + inline settings sub-panels.
			float ry = bodyY + 2.0f - scroll;
			for (Module mod : modules) {
				if (ry - bodyY > bodyH) break;
				if (ry + MODULE_H < bodyY) {
					ry += MODULE_H + GAP + settingsHeight(mod);
					continue;
				}

				Animation togA = moduleAnims.computeIfAbsent(mod,
					k -> new Animation(mod.enabled ? 1.0f : 0.0f, 200, Easing.EASE_OUT_QUART));
				togA.setTarget(mod.enabled ? 1.0f : 0.0f);
				float togT = togA.getValue();

				boolean hovered = mx >= x + 2 && mx <= x + PANEL_W - 2
					&& my >= ry && my <= ry + MODULE_H
					&& my >= bodyY && my <= bodyY + bodyH;
				Animation hovA = hoverAnims.computeIfAbsent(mod,
					k -> new Animation(0.0f, 140, Easing.EASE_OUT_QUART));
				hovA.setTarget(hovered ? 1.0f : 0.0f);
				float hovT = hovA.getValue();

				// Active highlight + accent bar — both ride the toggle anim,
				// so flipping the module on swells the bar and tints the row.
				if (togT > 0.01f) {
					UIRender.rect(m, x + 2, ry, PANEL_W - 4, MODULE_H, 3.0f,
						ColorUtil.withAlpha(ClientTheme.accent(), 0.25f * togT * alpha));
					UIRender.rect(m, x + 2, ry + 2, 1.5f, MODULE_H - 4, 0.75f,
						ColorUtil.withAlpha(ClientTheme.accent(), 0.85f * togT * alpha));
				}
				if (hovT > 0.01f) {
					UIRender.rect(m, x + 2, ry, PANEL_W - 4, MODULE_H, 3.0f,
						ColorUtil.withAlpha(0xFFFFFF, 0.07f * hovT * alpha));
				}

				int nameColor = togT > 0.5f
					? ColorUtil.lerp(0xFFCCCCCC, ClientTheme.accent(), togT * 0.6f)
					: 0xFFBBBBBB;
				UIRender.text(m, font, mod.name, x + 8, ry + 3.5f, 6.5f,
					ColorUtil.withAlpha(nameColor, alpha), 0.05f);

				if (bindingModule == mod) {
					UIRender.text(m, font, "...", x + PANEL_W - 16, ry + 3.5f, 6.5f,
						ColorUtil.withAlpha(ClientTheme.accent(), alpha), 0.05f);
				} else if (KeyName.isBound(mod.keybind)) {
					String kn = KeyName.describe(mod.keybind);
					float knW = UIRender.textWidth(font, kn, 5.5f);
					UIRender.text(m, font, kn, x + PANEL_W - knW - 6, ry + 4.5f, 5.5f,
						ColorUtil.withAlpha(0xFF888888, alpha), 0.04f);
				}

				ry += MODULE_H;

				// Inline settings sub-panel — clipped to the animated height
				// so partially-open panels can't bleed into the next module
				// row below.
				boolean sOpen = settingsOpen.getOrDefault(mod, false);
				Animation sA = settingsAnims.computeIfAbsent(mod,
					k -> new Animation(0.0f, 220, Easing.EASE_OUT_QUART));
				sA.setTarget(sOpen ? 1.0f : 0.0f);
				float sT = sA.getValue();

				if (sT > 0.01f && !mod.settings.isEmpty()) {
					float fullH = settingsContentHeight(mod);
					float sH = fullH * sT;
					UIRender.rect(m, x + 4, ry, PANEL_W - 8, sH, 2.0f,
						ColorUtil.withAlpha(0x000000, 0.30f * alpha));

					float panelStart = ry + 1.0f;
					float panelEnd   = ry + sH;
					float sy = panelStart;
					for (Setting<?> setting : mod.settings) {
						if (setting.getVisibility() != null && !setting.getVisibility().get())
							continue;
						// Stop before any setting that would land outside
						// the visible animated rect; a partially-rendered
						// setting at the end of the fold-in would just
						// overflow into the row below.
						if (sy + SETTING_H - 1.0f > panelEnd) {
							break;
						}
						sy = renderSetting(m, font, setting, x + 6, sy, PANEL_W - 12,
							alpha * sT, mx, my);
					}
					ry += sH + 1.0f;
				}

				ry += GAP;
			}
		}

		float settingsHeight(Module mod) {
			Animation sA = settingsAnims.get(mod);
			float sT = (sA != null) ? sA.getValue() : 0.0f;
			if (sT < 0.01f || mod.settings.isEmpty()) return 0.0f;
			return settingsContentHeight(mod) * sT + 1.0f;
		}

		float settingsContentHeight(Module mod) {
			float h = 0.0f;
			for (Setting<?> s : mod.settings) {
				if (s.getVisibility() != null && !s.getVisibility().get()) continue;
				h += SETTING_H;
			}
			return h + 2.0f;
		}

		float computeBodyHeight(List<Module> modules) {
			return Math.min(rawContentHeight(modules), BODY_MAX_H);
		}

		float renderSetting(Matrix4f m, MsdfFont font, Setting<?> s,
				float sx, float sy, float sw, float alpha, int mx, int my) {

			// ---------------------------------------------------- Boolean
			if (s instanceof BooleanSetting bs) {
				String label = UIRender.ellipsize(font, bs.name, 5.5f, sw - 14.0f);
				UIRender.text(m, font, label, sx, sy + 3.5f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);

				boolean on = bs.get();
				float bxW = 9.0f, bxH = 9.0f;
				float bx = sx + sw - bxW;
				float by = sy + 1.5f;
				int boxBg = on
					? ColorUtil.withAlpha(ClientTheme.accent(), 0.85f * alpha)
					: ColorUtil.withAlpha(0x222530, 0.85f * alpha);
				int boxBorder = on
					? ColorUtil.withAlpha(ClientTheme.accent(), alpha)
					: ColorUtil.withAlpha(0xFF555560, alpha);
				UIRender.rect(m, bx, by, bxW, bxH, 2.0f, boxBg);
				UIRender.border(m, bx, by, bxW, bxH, 2.0f, 0.7f, boxBorder);
				if (on) {
					UIRender.checkmark(m, bx + 0.5f, by + 0.5f, bxW - 1.0f,
						ColorUtil.withAlpha(0xFFFFFFFF, alpha));
				}
				return sy + SETTING_H;
			}

			// ---------------------------------------------------- Number
			if (s instanceof NumberSetting ns) {
				String label = UIRender.ellipsize(font, ns.name, 5.5f, sw * 0.55f);
				UIRender.text(m, font, label, sx, sy + 0.5f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);

				String valStr = formatValue(ns.get(), ns.step);
				float vW = UIRender.textWidth(font, valStr, 5.0f);
				UIRender.text(m, font, valStr, sx + sw - vW, sy + 0.5f, 5.0f,
					ColorUtil.withAlpha(ClientTheme.accent(), alpha), 0.04f);

				float barY = sy + 7.5f;
				float barH = 2.5f;
				float pct = (float) ((ns.get() - ns.min) / (ns.max - ns.min));
				pct = Math.max(0.0f, Math.min(1.0f, pct));
				UIRender.rect(m, sx, barY, sw, barH, 1.25f,
					ColorUtil.withAlpha(0x222530, 0.8f * alpha));
				if (pct > 0.0f) {
					UIRender.rectGradientH(m, sx, barY, sw * pct, barH, 1.25f,
						ColorUtil.withAlpha(ClientTheme.from(), alpha),
						ColorUtil.withAlpha(ClientTheme.to(),   alpha));
				}
				return sy + SETTING_H;
			}

			// ---------------------------------------------------- Mode (dropdown trigger)
			if (s instanceof ModeSetting ms) {
				renderDropdownTrigger(m, font, sx, sy, sw, alpha,
					ms.name, ms.get(),
					openDropdown != null && openDropdown.modeSetting == ms);
				return sy + SETTING_H;
			}

			// ---------------------------------------------------- Multi (dropdown trigger)
			if (s instanceof MultiSetting mu) {
				int onCount = 0;
				for (String o : mu.options) if (mu.isSelected(o)) onCount++;
				renderDropdownTrigger(m, font, sx, sy, sw, alpha,
					mu.name, onCount + "/" + mu.options.size(),
					openDropdown != null && openDropdown.multiSetting == mu);
				return sy + SETTING_H;
			}

			// ---------------------------------------------------- Color
			if (s instanceof ColorSetting cs) {
				String label = UIRender.ellipsize(font, cs.name, 5.5f, sw - 14.0f);
				UIRender.text(m, font, label, sx, sy + 3.5f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
				float swSize = 9.0f;
				float swX = sx + sw - swSize;
				float swY = sy + 1.5f;
				UIRender.rect(m, swX, swY, swSize, swSize, 2.0f,
					ColorUtil.withAlpha(cs.toArgb(), alpha));
				UIRender.border(m, swX, swY, swSize, swSize, 2.0f, 0.7f,
					ColorUtil.withAlpha(0xFFFFFF, 0.25f * alpha));
				return sy + SETTING_H;
			}

			// ---------------------------------------------------- Fallback
			UIRender.text(m, font, s.name, sx, sy + 3.5f, 5.5f,
				ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
			return sy + SETTING_H;
		}

		/**
		 * Shared chrome for {@link ModeSetting} and {@link MultiSetting}
		 * dropdown trigger rows. Also writes back the trigger's screen-space
		 * rect into {@link ClickGuiScreen#openDropdown} so the overlay tracks
		 * it as the panel scrolls or the GUI re-anchors.
		 */
		void renderDropdownTrigger(Matrix4f m, MsdfFont font,
				float sx, float sy, float sw, float alpha,
				String labelName, String labelValue, boolean isOpen) {
			float trX = sx;
			float trY = sy + 0.5f;
			float trW = sw;
			float trH = SETTING_H - 1.5f;

			int trBg = isOpen
				? ColorUtil.withAlpha(ClientTheme.accent(), 0.18f * alpha)
				: ColorUtil.withAlpha(0x14181F, 0.85f * alpha);
			int trBorder = isOpen
				? ColorUtil.withAlpha(ClientTheme.accent(), 0.85f * alpha)
				: ColorUtil.withAlpha(0xFFFFFF, 0.10f * alpha);
			UIRender.rect(m, trX, trY, trW, trH, 2.5f, trBg);
			UIRender.border(m, trX, trY, trW, trH, 2.5f, 0.7f, trBorder);

			String prefix = labelName + ":";
			float pW = UIRender.textWidth(font, prefix, 5.5f);
			float caretSpace = 9.0f;
			float valueAreaW = Math.max(8.0f, trW - pW - 6.0f - caretSpace - 4.0f);
			String value = UIRender.ellipsize(font, labelValue, 5.5f, valueAreaW);

			UIRender.text(m, font, prefix, trX + 4.0f, trY + 3.0f, 5.5f,
				ColorUtil.withAlpha(0xFF888892, alpha), 0.04f);
			UIRender.text(m, font, value, trX + 4.0f + pW + 3.0f, trY + 3.0f, 5.5f,
				ColorUtil.withAlpha(0xFFFFFFFF, alpha), 0.05f);

			float caretSize = 4.0f;
			UIRender.caret(m,
				trX + trW - caretSize - 3.0f,
				trY + (trH - caretSize) * 0.5f,
				caretSize, isOpen,
				ColorUtil.withAlpha(ClientTheme.accent(), alpha));

			if (isOpen) {
				openDropdown.triggerX = trX;
				openDropdown.triggerY = trY;
				openDropdown.triggerW = trW;
				openDropdown.triggerH = trH;
				sawOpenDropdownThisFrame = true;
			}
		}

		// ---- Input ---------------------------------------------------------

		boolean mouseClicked(double mx, double my, int button) {
			List<Module> modules = getModules();
			float bodyY = y + HEADER_H + 1.0f;
			float bodyH = computeBodyHeight(modules);

			if (mx < x || mx > x + PANEL_W || my < bodyY || my > bodyY + bodyH)
				return false;

			float scroll = scrollAnim.getValue();
			float ry = bodyY + 2.0f - scroll;
			for (Module mod : modules) {
				if (my >= ry && my < ry + MODULE_H) {
					if (button == 0) { mod.toggle(); return true; }
					if (button == 1) {
						boolean cur = settingsOpen.getOrDefault(mod, false);
						settingsOpen.put(mod, !cur);
						return true;
					}
					if (button == 2) {
						bindingModule = (bindingModule == mod) ? null : mod;
						return true;
					}
				}
				ry += MODULE_H;

				boolean sOpen = settingsOpen.getOrDefault(mod, false);
				Animation sA = settingsAnims.get(mod);
				float sT = sA != null ? sA.getValue() : 0.0f;
				if (sT > 0.01f && !mod.settings.isEmpty()) {
					float sH = settingsContentHeight(mod) * sT;
					if (my >= ry && my < ry + sH) {
						if (handleSettingClick(mod, mx, my, ry, button, sH)) return true;
					}
					ry += sH + 1.0f;
				}
				ry += GAP;
			}
			return false;
		}

		boolean handleSettingClick(Module mod, double mx, double my, float startY, int button,
				float visibleH) {
			float sx = x + 6.0f, sw = PANEL_W - 12.0f;
			float sy = startY + 1.0f;
			float endY = startY + visibleH;
			for (Setting<?> setting : mod.settings) {
				if (setting.getVisibility() != null && !setting.getVisibility().get())
					continue;
				// Mirror the render-time clip: only settings entirely within
				// the visible rect are interactive; otherwise the user could
				// click on a row that's animating below the visible area.
				if (sy + SETTING_H - 1.0f > endY) {
					break;
				}

				if (my >= sy && my < sy + SETTING_H) {
					if (setting instanceof BooleanSetting bs) {
						if (button == 0) { bs.toggle(); return true; }
					} else if (setting instanceof NumberSetting ns) {
						if (button == 0) {
							setSliderFromMouse(ns, mx, sx, sw);
							draggingSlider = ns;
							draggingModule = mod;
							return true;
						}
					} else if (setting instanceof ModeSetting ms) {
						if (button == 0) {
							float trX = sx, trY = sy + 0.5f, trW = sw, trH = SETTING_H - 1.5f;
							if (openDropdown != null && openDropdown.modeSetting == ms) {
								beginDropdownClose();
							} else {
								beginDropdownOpen(new OpenDropdown(ms, trX, trY, trW, trH));
							}
							return true;
						}
						if (button == 1) {
							ms.cycle();
							return true;
						}
					} else if (setting instanceof MultiSetting mu) {
						if (button == 0) {
							float trX = sx, trY = sy + 0.5f, trW = sw, trH = SETTING_H - 1.5f;
							if (openDropdown != null && openDropdown.multiSetting == mu) {
								beginDropdownClose();
							} else {
								beginDropdownOpen(new OpenDropdown(mu, trX, trY, trW, trH));
							}
							return true;
						}
					} else if (setting instanceof ColorSetting cs) {
						if (button == 0) {
							cs.setHue((cs.getHue() + 30.0) % 360.0);
							return true;
						}
						if (button == 1) {
							cs.setHue((cs.getHue() - 30.0 + 360.0) % 360.0);
							return true;
						}
					}
					return true;
				}
				sy += SETTING_H;
			}
			return false;
		}

		boolean mouseDragged(double mx) {
			if (draggingSlider != null) {
				float sx = x + 6.0f;
				float sw = PANEL_W - 12.0f;
				setSliderFromMouse(draggingSlider, mx, sx, sw);
				return true;
			}
			return false;
		}

		void mouseReleased(int button) {
			if (button == 0) {
				draggingSlider = null;
				draggingModule = null;
			}
		}

		boolean keyPressed(int keyCode) {
			if (bindingModule != null) {
				if (keyCode == 256) {
					bindingModule.keybind = -1;
				} else {
					bindingModule.keybind = keyCode;
				}
				bindingModule = null;
				return true;
			}
			return false;
		}
	}

	// =========================================================================
	//  Helpers
	// =========================================================================

	private static void setSliderFromMouse(NumberSetting ns, double mx, float sx, float sw) {
		float pct = (float) ((mx - sx) / sw);
		pct = Math.max(0.0f, Math.min(1.0f, pct));
		double val = ns.min + (ns.max - ns.min) * pct;
		if (ns.step > 0.0) val = Math.round(val / ns.step) * ns.step;
		val = Math.max(ns.min, Math.min(ns.max, val));
		ns.set(val);
	}

	private static String formatValue(double v, double step) {
		if (step >= 1.0) return Long.toString(Math.round(v));
		if (step >= 0.1) return String.format("%.1f", v);
		return String.format("%.2f", v);
	}
}
