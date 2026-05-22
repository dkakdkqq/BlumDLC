package dev.blumdlc.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.modules.impl.Themes;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.ui.animation.Animation;
import dev.blumdlc.client.ui.animation.Easing;
import dev.blumdlc.client.ui.components.SettingsPopup;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.KeyName;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * "Square" ClickGUI redesign — a single rectangular dark-glass panel inspired
 * by the FeverVisual {@code SecondGui} layout:
 *
 * <ul>
 *   <li>Single 500&times;340 panel with sharp 3&nbsp;px corners.</li>
 *   <li>Header bar with a logo plate, the {@code "Blum+"} title, a brownish
 *       {@code v1.0-1.21.4} version label and a search input.</li>
 *   <li>Vertical category sidebar on the left (Combat / Movement / Visual /
 *       Player / Util / Themes). The selected category is tinted blue with a
 *       cyan accent strip down its left edge.</li>
 *   <li>Module list on the right, one card per module. Each card has a
 *       see-saw toggle pill (a white vertical divider in the centre, right
 *       half lights up blue when ON), an optional bind chip and an optional
 *       three-dot affordance for opening the settings popup.</li>
 *   <li>Settings popup is anchored to the right of the panel, with an
 *       automatic flip-to-left fallback if it would overflow the screen at
 *       high GUI scales (handled by {@link SettingsPopup}).</li>
 * </ul>
 *
 * <p>Bind capture is initiated with middle-click on a card. The next key
 * press is captured (ESC clears the bind). Right-click on a card opens the
 * settings popup; left-click on the card body toggles the module, left-click
 * on the three-dot affordance opens the popup.
 */
public final class ClickGuiScreen extends Screen {

	// =========================================================================
	//  Geometry
	// =========================================================================

	private static final float PANEL_W     = 500.0f;
	private static final float PANEL_H     = 340.0f;
	private static final float CORNER      = 8.0f;          // softer rounding
	private static final float CARD_CORNER = 5.0f;

	private static final float HEADER_H    = 50.0f;
	private static final float SIDEBAR_W   = 110.0f;
	private static final float CONTENT_PAD = 8.0f;

	private static final float CAT_H       = 32.0f;
	private static final float CAT_GAP     = 3.0f;

	private static final float ROW_H       = 24.0f;
	private static final float ROW_GAP     = 3.0f;

	private static final float SEARCH_W    = 160.0f;
	private static final float SEARCH_H    = 20.0f;

	private static final float TOGGLE_W    = 24.0f;
	private static final float TOGGLE_H    = 11.0f;

	// =========================================================================
	//  Palette — fixed chrome (FeverVisual-inspired). Accent is themeable
	//  through Theme.ACCENT / CARD_ACTIVE_*.
	// =========================================================================

	/** Logo plate background — same translucent black as the panel. */
	private static final int CLR_LOGO      = 0xCC080B14;
	/** Brownish red used for the version label. */
	private static final int CLR_VERSION   = 0xFFA65252;
	/** Selected category tint — translucent blue. */
	private static final int CLR_CAT_ON    = 0xC8005DFF;
	/** Module-row tint when its module is enabled. */
	private static final int CLR_ROW_ON    = 0x66005DFF;
	/** Toggle pill ON-side fill. */
	private static final int CLR_TOGGLE_ON = 0xCC005DFF;
	/** White vertical divider down the centre of the toggle pill. */
	private static final int CLR_DIVIDER   = 0xD0FFFFFF;

	// =========================================================================
	//  Branding
	// =========================================================================

	private static final String TITLE       = "Blum+";
	private static final String VERSION     = "v1.0-1.21.4";
	private static final Identifier LOGO_TEX = Identifier.of("blumdlc", "logo.png");

	// =========================================================================
	//  Categories
	// =========================================================================

	private static final Category[] CATEGORIES = {
		Category.COMBAT,
		Category.MOVEMENT,
		Category.RENDER,
		Category.PLAYER,
		Category.UTIL,
		Category.THEMES,
	};

	// =========================================================================
	//  Animations
	// =========================================================================

	private final Animation openAnim  = new Animation(0.0f, 320, Easing.EASE_OUT_QUINT);
	private final Animation slideAnim = new Animation(20.0f, 380, Easing.EASE_OUT_EXPO);

	private int selectedCategory = 0;
	private final Animation[] catHover  = new Animation[CATEGORIES.length];
	private final Animation[] catSelect = new Animation[CATEGORIES.length];

	// Module list state — rebuilt on category / search change
	private List<Module>     visibleModules = new ArrayList<>();
	private final List<Animation> rowHover  = new ArrayList<>();
	private final List<Animation> rowEnter  = new ArrayList<>();
	private final List<Animation> rowToggle = new ArrayList<>();
	private final List<Animation> rowFlash  = new ArrayList<>();

	// Vertical scroll on the module list
	private final Animation scrollAnim = new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC);
	private float scrollTarget = 0.0f;
	private float maxScroll    = 0.0f;

	// Search box
	private String  searchText      = "";
	private boolean searchActive    = false;
	private final Animation searchFocus = new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC);
	private long    lastBlinkSwap   = System.currentTimeMillis();
	private boolean caretVisible    = true;

	// Shared settings popup + bind capture
	private final SettingsPopup popup = new SettingsPopup();
	private Module bindingModule;

	// =========================================================================
	//  Lifecycle
	// =========================================================================

	public ClickGuiScreen() {
		super(Text.literal("Blum+"));
		for (int i = 0; i < CATEGORIES.length; i++) {
			catHover[i]  = new Animation(0.0f, 140, Easing.EASE_OUT_CUBIC);
			catSelect[i] = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
		}
		catSelect[0].setImmediate(1.0f);
	}

	@Override
	protected void init() {
		super.init();
		openAnim.setTarget(1.0f);
		slideAnim.setTarget(0.0f);
		rebuildModules(true);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Vanilla blur disabled — we paint our own dim layer in render().
	}

	@Override
	public void close() {
		openAnim.setTarget(0.0f);
		slideAnim.setTarget(20.0f);
		bindingModule = null;
		popup.close();
		super.close();
	}

	// =========================================================================
	//  Build / rebuild
	// =========================================================================

	private void rebuildModules(boolean stagger) {
		Category cat = CATEGORIES[selectedCategory];
		String low = searchText.toLowerCase();
		List<Module> list = new ArrayList<>();
		for (Module m : BlumDLC.MODULES.byCategory(cat)) {
			if (low.isEmpty() || m.name.toLowerCase().contains(low)) {
				list.add(m);
			}
		}
		list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		visibleModules = list;

		int n = list.size();
		sync(rowHover,  n, () -> new Animation(0.0f, 140, Easing.EASE_OUT_CUBIC));
		sync(rowEnter,  n, () -> new Animation(0.0f, 320, Easing.EASE_OUT_QUINT));
		sync(rowToggle, n, () -> new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC));
		sync(rowFlash,  n, () -> new Animation(0.0f, 260, Easing.EASE_OUT_CUBIC));

		for (int i = 0; i < n; i++) {
			rowToggle.get(i).setTarget(list.get(i).enabled ? 1.0f : 0.0f);
			if (stagger) {
				Animation e = rowEnter.get(i);
				e.setImmediate(0.0f);
				e.setTarget(1.0f, 14L * i);
			} else {
				rowEnter.get(i).setImmediate(1.0f);
			}
		}

		// Reset scroll on rebuild
		scrollTarget = 0.0f;
		scrollAnim.setImmediate(0.0f);

		// Drop the popup if its module disappeared (filtered out / category change)
		Module pm = popup.getModule();
		if (pm != null && !list.contains(pm)) {
			popup.detach();
		}

		clampScroll();
	}

	private static <T> void sync(List<T> list, int n, Supplier<T> factory) {
		while (list.size() > n) list.remove(list.size() - 1);
		while (list.size() < n) list.add(factory.get());
	}

	private void clampScroll() {
		int n = visibleModules.size();
		float content = n == 0 ? 0.0f : n * (ROW_H + ROW_GAP) - ROW_GAP;
		float lh = listHeight();
		maxScroll = Math.max(0.0f, content - lh);
		scrollTarget = Math.max(0.0f, Math.min(scrollTarget, maxScroll));
		scrollAnim.setTarget(scrollTarget);
	}

	private static float listHeight() {
		return PANEL_H - HEADER_H - CONTENT_PAD * 2.0f;
	}

	// =========================================================================
	//  Render
	// =========================================================================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		Themes.syncAll();
		Theme.refresh();

		Matrix4f m = context.getMatrices().peek().getPositionMatrix();
		MsdfFont font = Fonts.BIKO.get();
		float open  = openAnim.getValue();
		float slide = slideAnim.getValue();

		// Backdrop dim
		UIRender.rect(m, 0.0f, 0.0f, this.width, this.height, 0.0f,
			ColorUtil.multiplyAlpha(Theme.DIM, open));

		// Panel position — centred, but clamped so the panel itself is never
		// rendered fully off-screen at extreme GUI scales.
		float px = (this.width - PANEL_W) * 0.5f;
		px = Math.max(2.0f, Math.min(px, this.width - PANEL_W - 2.0f));
		float py = (this.height - PANEL_H) * 0.5f + slide;
		py = Math.max(2.0f, Math.min(py, this.height - PANEL_H - 2.0f));

		drawPanel(context, m, font, px, py, open, mouseX, mouseY);

		// Settings popup is anchored to the right edge of the panel, with an
		// automatic flip-to-left fallback if it would overflow the screen.
		popup.render(context, m, font, px, py, PANEL_W, PANEL_H, open, mouseX, mouseY);

		super.render(context, mouseX, mouseY, delta);
	}

	private void drawPanel(DrawContext ctx, Matrix4f m, MsdfFont font,
			float px, float py, float open, int mouseX, int mouseY) {
		// Soft drop shadow — a slightly larger, very dim rect behind the
		// panel that gives it visual lift over the world.
		UIRender.rect(m, px - 6.0f, py - 4.0f, PANEL_W + 12.0f, PANEL_H + 12.0f,
			CORNER + 6.0f, ColorUtil.multiplyAlpha(0x55000000, open * 0.85f));

		// Subtle blur underneath so the world is dimmed but readable.
		UIRender.blur(m, px, py, PANEL_W, PANEL_H, CORNER, 5.0f, 0xFF000000);

		// Main plate — vertical slate gradient instead of a flat fill.
		UIRender.rectGradientV(m, px, py, PANEL_W, PANEL_H, CORNER,
			ColorUtil.multiplyAlpha(Theme.PANEL_BG_TOP, open),
			ColorUtil.multiplyAlpha(Theme.PANEL_BG_BOT, open));
		// Outer crisp border + inner hairline for "glass depth".
		UIRender.border(m, px, py, PANEL_W, PANEL_H, CORNER, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, open));
		UIRender.border(m, px + 1.5f, py + 1.5f, PANEL_W - 3.0f, PANEL_H - 3.0f,
			Math.max(0.0f, CORNER - 1.5f), 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_INNER, open));

		drawHeader(ctx, m, font, px, py, open, mouseX, mouseY);

		// Header / body divider
		UIRender.rect(m, px + 12.0f, py + HEADER_H, PANEL_W - 24.0f, 1.0f, 0.0f,
			ColorUtil.multiplyAlpha(Theme.DIVIDER, open));

		drawSidebar(m, font, px, py + HEADER_H, open, mouseX, mouseY);

		// Sidebar / module-list divider
		UIRender.rect(m, px + SIDEBAR_W, py + HEADER_H + 4.0f,
			1.0f, PANEL_H - HEADER_H - 8.0f, 0.0f,
			ColorUtil.multiplyAlpha(Theme.DIVIDER, open));

		drawModules(ctx, m, font, px + SIDEBAR_W, py + HEADER_H, open, mouseX, mouseY);
	}

	// ---------------------------------------------------------------------
	//  Header
	// ---------------------------------------------------------------------

	private void drawHeader(DrawContext ctx, Matrix4f m, MsdfFont font,
			float px, float py, float open, int mouseX, int mouseY) {
		// Logo plate
		float logoSz = 36.0f;
		float logoX  = px + 8.0f;
		float logoY  = py + (HEADER_H - logoSz) * 0.5f;
		UIRender.rect(m, logoX, logoY, logoSz, logoSz, CARD_CORNER,
			ColorUtil.multiplyAlpha(CLR_LOGO, open));
		UIRender.border(m, logoX, logoY, logoSz, logoSz, CARD_CORNER, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, open));

		// Logo image (tinted by alpha so it fades with the panel)
		UIRender.texture(m, LOGO_TEX,
			logoX + 6.0f, logoY + 6.0f, logoSz - 12.0f, logoSz - 12.0f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, open));

		// Title + version — clipped to the strip between the logo and the
		// search bar so they can't bleed into either neighbour.
		float titleX  = logoX + logoSz + 10.0f;
		float searchX = px + PANEL_W - SEARCH_W - 10.0f;
		float titleW  = Math.max(0.0f, searchX - 6.0f - titleX);
		String title = UIRender.ellipsize(font, TITLE, 13.0f, titleW);
		String version = UIRender.ellipsize(font, VERSION, 6.5f, titleW);
		UIRender.text(m, font, title, titleX, py + 10.0f, 13.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, open), 0.07f);
		UIRender.text(m, font, version, titleX, py + 28.0f, 6.5f,
			ColorUtil.multiplyAlpha(CLR_VERSION, open), 0.05f);

		// Search bar
		float sw = SEARCH_W;
		float sh = SEARCH_H;
		float sx = searchX;
		float sy = py + (HEADER_H - sh) * 0.5f;

		float focus = searchFocus.getValue();
		int border  = ColorUtil.lerp(Theme.PANEL_BORDER, Theme.ACCENT, focus);

		// Soft outer halo when focused — drawn before the box so the box
		// can sit cleanly on top of it.
		if (focus > 0.001f) {
			UIRender.border(m, sx - 1.5f, sy - 1.5f, sw + 3.0f, sh + 3.0f,
				CARD_CORNER + 2.0f, 1.0f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, focus * 0.35f * open));
		}

		UIRender.rect(m, sx, sy, sw, sh, CARD_CORNER,
			ColorUtil.multiplyAlpha(Theme.SEARCH_BG, open));
		UIRender.border(m, sx, sy, sw, sh, CARD_CORNER, 1.0f,
			ColorUtil.multiplyAlpha(border, open));

		// Search text + caret are scissor-clipped to the input box, and when
		// the typed text is wider than the box (and the field is focused),
		// the text scrolls horizontally so the caret stays visible at the
		// right edge.
		String disp = searchText.isEmpty() ? "Search..." : searchText;
		int dispC   = searchText.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT_PRIMARY;
		float padL = 8.0f;
		float padR = 8.0f;
		float available = sw - padL - padR;
		float dispW = UIRender.textWidth(font, disp, 7.0f);
		float scroll = (searchActive && dispW > available) ? (dispW - available) : 0.0f;

		ctx.enableScissor(
			(int) Math.floor(sx + 1.0f),
			(int) Math.floor(sy + 1.0f),
			(int) Math.ceil(sx + sw - 1.0f),
			(int) Math.ceil(sy + sh - 1.0f));

		UIRender.text(m, font, disp,
			sx + padL - scroll, sy + (sh - 7.0f) * 0.5f - 0.5f, 7.0f,
			ColorUtil.multiplyAlpha(dispC, open));

		// Caret
		long now = System.currentTimeMillis();
		if (now - lastBlinkSwap > 520L) {
			lastBlinkSwap = now;
			caretVisible = !caretVisible;
		}
		if (searchActive && caretVisible) {
			float caretX = sx + padL
				+ UIRender.textWidth(font, searchText, 7.0f) - scroll + 1.0f;
			UIRender.rect(m, caretX, sy + 4.0f, 1.0f, sh - 8.0f, 0.0f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, open));
		}

		ctx.disableScissor();
	}

	// ---------------------------------------------------------------------
	//  Sidebar
	// ---------------------------------------------------------------------

	private void drawSidebar(Matrix4f m, MsdfFont font,
			float sx, float sy, float open, int mouseX, int mouseY) {
		float startY = sy + CONTENT_PAD;
		for (int i = 0; i < CATEGORIES.length; i++) {
			float cy = startY + i * (CAT_H + CAT_GAP);
			float cx = sx + CONTENT_PAD;
			float cw = SIDEBAR_W - CONTENT_PAD * 2.0f;

			boolean hov = inside(mouseX, mouseY, cx, cy, cw, CAT_H);
			catHover[i].setTarget(hov ? 1.0f : 0.0f);
			catSelect[i].setTarget(i == selectedCategory ? 1.0f : 0.0f);

			float hovT = catHover[i].getValue();
			float selT = catSelect[i].getValue();

			// Off plate — gradient for richer feel
			UIRender.rectGradientV(m, cx, cy, cw, CAT_H, CARD_CORNER,
				ColorUtil.multiplyAlpha(Theme.CARD_BG_TOP, open),
				ColorUtil.multiplyAlpha(Theme.CARD_BG_BOT, open));

			// Hover lift (only when not selected)
			if (hovT > 0.001f && selT < 0.999f) {
				UIRender.rect(m, cx, cy, cw, CAT_H, CARD_CORNER,
					ColorUtil.multiplyAlpha(0x18FFFFFF, hovT * (1.0f - selT) * open));
			}

			// Selected blue tint + cyan accent strip on the left edge.
			// The strip's alpha breathes gently so the active item feels alive.
			if (selT > 0.001f) {
				UIRender.rect(m, cx, cy, cw, CAT_H, CARD_CORNER,
					ColorUtil.multiplyAlpha(CLR_CAT_ON, selT * open));
				float stripPulse = 0.85f + 0.15f * (float) Math.sin(now() * 2.0);
				UIRender.rect(m, cx + 1.0f, cy + 5.0f, 2.5f, CAT_H - 10.0f, 1.5f,
					ColorUtil.multiplyAlpha(Theme.ACCENT, selT * stripPulse * open));
			}

			UIRender.border(m, cx, cy, cw, CAT_H, CARD_CORNER, 1.0f,
				ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, open));

			// Label — ellipsized so very long category names can never spill
			// past the sidebar's right edge.
			float fs = 8.5f;
			float labelX = cx + 12.0f;
			float labelMaxW = cx + cw - labelX - 4.0f;
			String name = UIRender.ellipsize(font, CATEGORIES[i].displayName, fs, labelMaxW);
			int tc = ColorUtil.lerp(Theme.TEXT_SECONDARY, 0xFFFFFFFF,
				selT * 0.7f + hovT * 0.3f);
			UIRender.text(m, font, name, labelX,
				cy + (CAT_H - fs) * 0.5f - 0.5f, fs,
				ColorUtil.multiplyAlpha(tc, open), 0.06f);
		}
	}

	// ---------------------------------------------------------------------
	//  Module list
	// ---------------------------------------------------------------------

	private void drawModules(DrawContext ctx, Matrix4f m, MsdfFont font,
			float ox, float oy, float open, int mouseX, int mouseY) {
		float listX = ox + CONTENT_PAD;
		float listY = oy + CONTENT_PAD;
		float listW = PANEL_W - SIDEBAR_W - CONTENT_PAD * 2.0f;
		float listH = listHeight();

		ctx.enableScissor(
			(int) Math.floor(listX),
			(int) Math.floor(listY),
			(int) Math.ceil(listX + listW),
			(int) Math.ceil(listY + listH));

		float scrollOff = scrollAnim.getValue();
		float ry = listY - scrollOff;
		for (int i = 0; i < visibleModules.size(); i++) {
			drawRow(m, font, i, listX, ry, listW, open, mouseX, mouseY,
				listY, listY + listH);
			ry += ROW_H + ROW_GAP;
		}

		if (visibleModules.isEmpty()) {
			String msg = searchText.isEmpty() ? "No modules" : "No matches";
			float w = UIRender.textWidth(font, msg, 8.5f);
			UIRender.text(m, font, msg,
				listX + (listW - w) * 0.5f,
				listY + listH * 0.45f, 8.5f,
				ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, open), 0.05f);
		}

		ctx.disableScissor();

		// Scrollbar
		if (maxScroll > 0.5f) {
			float trackX = listX + listW + 1.0f;
			float trackY = listY;
			float trackH = listH;
			float thumbH = Math.max(18.0f, trackH * (trackH / (trackH + maxScroll)));
			float thumbY = trackY + (scrollOff / maxScroll) * (trackH - thumbH);
			UIRender.rect(m, trackX, trackY, 2.0f, trackH, 1.0f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_BG, 0.7f * open));
			UIRender.rect(m, trackX, thumbY, 2.0f, thumbH, 1.0f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_FG, open));
		}
	}

	private void drawRow(Matrix4f m, MsdfFont font, int idx,
			float rx, float ry, float rw, float open,
			int mouseX, int mouseY, float clipTop, float clipBot) {
		if (ry + ROW_H < clipTop - 4.0f || ry > clipBot + 4.0f) return;

		Module mod = visibleModules.get(idx);
		Animation enter = rowEnter.get(idx);
		Animation hover = rowHover.get(idx);
		Animation tog   = rowToggle.get(idx);
		Animation flash = rowFlash.get(idx);

		float enterT = enter.getValue();
		float rxOff  = rx + (1.0f - enterT) * 8.0f;
		float alpha  = enterT * open;

		boolean hov = mouseX >= rxOff && mouseX < rxOff + rw
			&& mouseY >= ry && mouseY < ry + ROW_H
			&& mouseY >= clipTop && mouseY <= clipBot;
		hover.setTarget(hov ? 1.0f : 0.0f);
		tog.setTarget(mod.enabled ? 1.0f : 0.0f);

		float hovT = hover.getValue();
		float togT = tog.getValue();
		float flT  = flash.getValue();

		// Base card — gradient
		UIRender.rectGradientV(m, rxOff, ry, rw, ROW_H, CARD_CORNER,
			ColorUtil.multiplyAlpha(Theme.CARD_BG_TOP, alpha),
			ColorUtil.multiplyAlpha(Theme.CARD_BG_BOT, alpha));

		// Hover diagonal sheen
		if (hovT > 0.001f) {
			UIRender.rectGradientH(m, rxOff, ry, rw, ROW_H, CARD_CORNER,
				ColorUtil.multiplyAlpha(0x10FFFFFF, hovT * 0.5f * alpha),
				ColorUtil.multiplyAlpha(0x22FFFFFF, hovT * 0.9f * alpha));
		}

		// ON tint — pulses subtly so enabled rows feel alive
		if (togT > 0.001f) {
			float p = 0.85f + 0.15f * (float) Math.sin(now() * 2.4);
			UIRender.rectGradientH(m, rxOff, ry, rw, ROW_H, CARD_CORNER,
				ColorUtil.multiplyAlpha(CLR_ROW_ON, togT * p * alpha),
				ColorUtil.multiplyAlpha(0x99005DFF, togT * p * 0.6f * alpha));
		}

		UIRender.border(m, rxOff, ry, rw, ROW_H, CARD_CORNER, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, alpha));

		// Click flash ring — ripple outward, scissor of the module list
		// keeps it inside the panel.
		if (flT > 0.001f && flT < 0.999f) {
			float ripple = flT * 2.0f;
			UIRender.border(m, rxOff - ripple, ry - ripple,
				rw + ripple * 2.0f, ROW_H + ripple * 2.0f,
				CARD_CORNER + ripple, Math.max(0.4f, 1.4f - flT),
				ColorUtil.multiplyAlpha(Theme.ACCENT, (1.0f - flT) * 0.85f * alpha));
		}

		// Highlight if this row owns the popup
		if (popup.getModule() == mod) {
			UIRender.border(m, rxOff, ry, rw, ROW_H, CARD_CORNER, 1.0f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, 0.7f * alpha));
		}

		// Right-side controls (laid out RTL)
		float rightX = rxOff + rw - 6.0f;

		// Toggle pill
		float tgX = rightX - TOGGLE_W;
		float tgY = ry + (ROW_H - TOGGLE_H) * 0.5f;
		drawTogglePill(m, tgX, tgY, TOGGLE_W, TOGGLE_H, togT, alpha);
		rightX = tgX - 6.0f;

		// Bind chip
		boolean awaiting = (bindingModule == mod);
		if (awaiting || KeyName.isBound(mod.keybind)) {
			String label = awaiting ? "..." : KeyName.describe(mod.keybind);
			float fs = 5.5f;
			// Cap chip width so a really long keybind label can't push the
			// dots / module name into negative width.
			float chipMaxW = Math.max(12.0f, rightX - rxOff - 60.0f);
			String chipLabel = UIRender.ellipsize(font, label, fs, chipMaxW - 6.0f);
			float lw = UIRender.textWidth(font, chipLabel, fs) + 6.0f;
			float lh = fs + 4.0f;
			float lx = rightX - lw;
			float ly = ry + (ROW_H - lh) * 0.5f;
			int chipBg    = awaiting ? Theme.ACCENT : 0x66000000;
			int chipColor = awaiting ? 0xFF000000   : Theme.TEXT_SECONDARY;
			UIRender.rect(m, lx, ly, lw, lh, CARD_CORNER,
				ColorUtil.multiplyAlpha(chipBg, alpha));
			UIRender.text(m, font, chipLabel, lx + 3.0f, ly + 2.0f, fs,
				ColorUtil.multiplyAlpha(chipColor, alpha), 0.05f);
			rightX = lx - 4.0f;
		}

		// "..." dots — only when the module has settings
		if (!mod.settings.isEmpty()) {
			float dotR = 1.5f;
			float dotGap = 2.0f;
			float dotsW = dotR * 6.0f + dotGap * 2.0f;
			float dotsX = rightX - dotsW;
			float dotsY = ry + (ROW_H - dotR * 2.0f) * 0.5f;
			int dotColor = ColorUtil.multiplyAlpha(0xFFFFFFFF,
				(0.5f + 0.5f * Math.max(hovT, togT)) * alpha);
			for (int d = 0; d < 3; d++) {
				float dx = dotsX + d * (dotR * 2.0f + dotGap);
				UIRender.rect(m, dx, dotsY, dotR * 2.0f, dotR * 2.0f, dotR, dotColor);
			}
			rightX = dotsX - 4.0f;
		}

		// Module name
		int nameColor = ColorUtil.lerp(Theme.TEXT_SECONDARY, 0xFFFFFFFF,
			togT * 0.7f + hovT * 0.3f);
		float nameSize = 8.0f;
		float maxNameW = rightX - rxOff - 10.0f;
		String name = UIRender.ellipsize(font, mod.name, nameSize, maxNameW);
		UIRender.text(m, font, name, rxOff + 10.0f,
			ry + (ROW_H - nameSize) * 0.5f - 0.5f, nameSize,
			ColorUtil.multiplyAlpha(nameColor, alpha), 0.05f);
	}

	/**
	 * The signature "see-saw" toggle: a rounded pill with a white vertical
	 * divider down the middle, and a blue right-half fill that fades in as
	 * the module becomes enabled. ON state pulses subtly + grows a soft
	 * accent halo so it reads as "active" at a glance.
	 */
	private void drawTogglePill(Matrix4f m, float x, float y, float w, float h,
			float t, float alpha) {
		float r = h * 0.5f;

		// Outer accent halo — only really visible when fully ON
		if (t > 0.55f) {
			float halo = (t - 0.55f) / 0.45f;
			float p = 0.6f + 0.4f * (float) Math.sin(now() * 3.0);
			UIRender.border(m, x - 1.0f, y - 1.0f, w + 2.0f, h + 2.0f, r + 1.0f, 1.0f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, halo * p * 0.45f * alpha));
		}

		// Dark base
		UIRender.rect(m, x, y, w, h, r,
			ColorUtil.multiplyAlpha(0xA0000000, alpha));

		// ON-state fill with gentle breathing
		if (t > 0.001f) {
			float p = 0.88f + 0.12f * (float) Math.sin(now() * 3.0);
			UIRender.rect(m, x, y, w, h, r,
				ColorUtil.multiplyAlpha(CLR_TOGGLE_ON, t * p * alpha));
		}

		// Centre divider — always visible, slightly thicker for clarity.
		UIRender.rect(m, x + w * 0.5f - 0.5f, y + 1.5f, 1.0f, h - 3.0f, 0.0f,
			ColorUtil.multiplyAlpha(CLR_DIVIDER, 0.85f * alpha));

		// Border
		UIRender.border(m, x, y, w, h, r, 1.0f,
			ColorUtil.multiplyAlpha(0x55FFFFFF, alpha));
	}

	// =========================================================================
	//  Input
	// =========================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float px = (this.width - PANEL_W) * 0.5f;
		px = Math.max(2.0f, Math.min(px, this.width - PANEL_W - 2.0f));
		float py = (this.height - PANEL_H) * 0.5f;
		py = Math.max(2.0f, Math.min(py, this.height - PANEL_H - 2.0f));

		// Settings popup eats first
		if (popup.mouseClicked(mouseX, mouseY, px, py, PANEL_W, PANEL_H, button)) {
			return true;
		}

		// Search box focus
		float sw = SEARCH_W;
		float sh = SEARCH_H;
		float sx = px + PANEL_W - sw - 10.0f;
		float sy = py + (HEADER_H - sh) * 0.5f;
		boolean inSearch = inside(mouseX, mouseY, sx, sy, sw, sh);
		searchActive = inSearch;
		searchFocus.setTarget(inSearch ? 1.0f : 0.0f);
		if (inSearch) {
			return true;
		}

		// Sidebar categories
		float startY = py + HEADER_H + CONTENT_PAD;
		for (int i = 0; i < CATEGORIES.length; i++) {
			float cy = startY + i * (CAT_H + CAT_GAP);
			float cx = px + CONTENT_PAD;
			float cw = SIDEBAR_W - CONTENT_PAD * 2.0f;
			if (inside(mouseX, mouseY, cx, cy, cw, CAT_H) && button == 0) {
				if (selectedCategory != i) {
					selectedCategory = i;
					rebuildModules(true);
				}
				return true;
			}
		}

		// Module rows
		float listX = px + SIDEBAR_W + CONTENT_PAD;
		float listW = PANEL_W - SIDEBAR_W - CONTENT_PAD * 2.0f;
		float listY = py + HEADER_H + CONTENT_PAD;
		float listH = listHeight();

		if (mouseX >= listX && mouseX < listX + listW
		 && mouseY >= listY && mouseY < listY + listH) {

			float scrollOff = scrollAnim.getValue();
			float ry = listY - scrollOff;
			for (int i = 0; i < visibleModules.size(); i++) {
				if (mouseX >= listX && mouseX < listX + listW
				 && mouseY >= ry && mouseY < ry + ROW_H) {
					Module mod = visibleModules.get(i);

					if (button == 1) {
						popup.toggle(mod);
						return true;
					}
					if (button == 2) {
						bindingModule = (bindingModule == mod) ? null : mod;
						return true;
					}

					// Three-dot region opens settings, anywhere else toggles.
					if (clickedDots(mouseX, listX, listW, mod)) {
						popup.toggle(mod);
						return true;
					}

					mod.toggle();
					Animation flash = rowFlash.get(i);
					flash.setImmediate(0.0f);
					flash.setTarget(1.0f);
					return true;
				}
				ry += ROW_H + ROW_GAP;
			}
			return true;
		}

		// Inside panel chrome — consume so cards behind don't react.
		if (inside(mouseX, mouseY, px, py, PANEL_W, PANEL_H)) {
			return true;
		}

		// Outside everything — close any open popup.
		popup.close();
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/**
	 * Hit-test for the small three-dot affordance, accounting for the
	 * toggle pill and an optional bind chip on its right.
	 */
	private boolean clickedDots(double mx, float rx, float rw, Module mod) {
		if (mod.settings.isEmpty()) return false;
		float toggleArea = TOGGLE_W + 6.0f;
		boolean hasBind = (bindingModule == mod) || KeyName.isBound(mod.keybind);
		float bindArea  = hasBind ? 30.0f : 0.0f;
		float dotsW     = 1.5f * 6.0f + 2.0f * 2.0f;
		float dotsRight = rx + rw - 6.0f - toggleArea - bindArea;
		float dotsX     = dotsRight - dotsW;
		return mx >= dotsX - 4.0f && mx <= dotsX + dotsW + 4.0f;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		float px = (this.width - PANEL_W) * 0.5f;
		px = Math.max(2.0f, Math.min(px, this.width - PANEL_W - 2.0f));
		if (popup.mouseDragged(mx, my, button, px, PANEL_W)) {
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		popup.mouseReleased(button);
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
		float px = (this.width - PANEL_W) * 0.5f;
		px = Math.max(2.0f, Math.min(px, this.width - PANEL_W - 2.0f));
		float py = (this.height - PANEL_H) * 0.5f;
		py = Math.max(2.0f, Math.min(py, this.height - PANEL_H - 2.0f));

		if (popup.mouseScrolled(mx, my, vert, px, py, PANEL_W, PANEL_H)) {
			return true;
		}

		float listX = px + SIDEBAR_W + CONTENT_PAD;
		float listW = PANEL_W - SIDEBAR_W - CONTENT_PAD * 2.0f;
		float listY = py + HEADER_H + CONTENT_PAD;
		float listH = listHeight();
		if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
			scrollTarget = Math.max(0.0f,
				Math.min(maxScroll, scrollTarget - (float) vert * 22.0f));
			scrollAnim.setTarget(scrollTarget);
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Bind capture beats everything else
		if (bindingModule != null) {
			if (keyCode == 256 /* ESC */) {
				bindingModule.keybind = -1;
			} else {
				bindingModule.keybind = keyCode;
			}
			bindingModule = null;
			return true;
		}

		if (popup.keyPressed(keyCode)) {
			return true;
		}

		if (searchActive) {
			if (keyCode == 256 || keyCode == 257) { // ESC / ENTER
				searchActive = false;
				searchFocus.setTarget(0.0f);
				return true;
			}
			if (keyCode == 259 /* BACKSPACE */ && !searchText.isEmpty()) {
				searchText = searchText.substring(0, searchText.length() - 1);
				rebuildModules(false);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchActive && chr >= 32 && chr != 127) {
			searchText += chr;
			rebuildModules(false);
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	// =========================================================================
	//  Helpers
	// =========================================================================

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	/** Seconds since UNIX epoch (modulo enough to avoid float precision loss),
	 *  used to drive ambient pulse / breathing animations. */
	private static float now() {
		return (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
	}
}
