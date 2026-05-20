package dev.blumdlc.client.ui;

import java.util.ArrayList;
import java.util.List;

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


/**
 * Recoded ClickGUI — cleaner, softer look with fluid animations.
 *
 * Layout:
 *   Sidebar (categories incl. Themes) | Search + Card grid | Settings popup
 *
 * Compared to the old version:
 *   - Softer dim layer (no pure black), subtle slide-in with scale
 *   - Cards use rounded corners with gentle gradient on active state
 *   - Sidebar selector pill animates smoothly between categories
 *   - Click flash ring, staggered card entrances, search focus glow
 *   - Theme.refresh() called each frame so accent colours react live
 */
public final class ClickGuiScreen extends Screen {

	// =========================================================================
	//  Geometry
	// =========================================================================

	private static final float PANEL_W = 520.0f;
	private static final float PANEL_H = 270.0f;
	private static final float SIDEBAR_W = 105.0f;

	private static final float CARD_W = 92.0f;
	private static final float CARD_H = 40.0f;
	private static final float CARD_GAP_X = 5.0f;
	private static final float CARD_GAP_Y = 5.0f;
	private static final float CARD_AREA_PAD_X = 10.0f;
	private static final float CARD_AREA_PAD_TOP = 38.0f;
	private static final float CARD_AREA_PAD_BOTTOM = 8.0f;
	private static final int   CARDS_PER_ROW = 4;


	// =========================================================================
	//  Animations
	// =========================================================================

	private final Animation openAnim  = new Animation(0.0f, 320, Easing.EASE_OUT_QUINT);
	private final Animation slideAnim = new Animation(30.0f, 380, Easing.EASE_OUT_EXPO);

	// =========================================================================
	//  Sidebar state
	// =========================================================================

	private Category selectedCategory = Category.COMBAT;
	private final Animation selectorY      = new Animation(0.0f,  280, Easing.EASE_OUT_EXPO);
	private final Animation selectorHeight = new Animation(22.0f, 280, Easing.EASE_OUT_EXPO);
	private final Animation[] categoryHover = new Animation[Category.values().length];
	private final Animation quitHover      = new Animation(0.0f, 160, Easing.EASE_OUT_CUBIC);

	// =========================================================================
	//  Search bar
	// =========================================================================

	private String searchText = "";
	private final Animation searchFocus = new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC);
	private boolean searchActive = false;
	private long lastBlinkSwap = System.currentTimeMillis();
	private boolean caretVisible = true;


	// =========================================================================
	//  Card grid
	// =========================================================================

	private List<Module> visibleModules = new ArrayList<>();
	private final List<Animation> cardHover  = new ArrayList<>();
	private final List<Animation> cardEnter  = new ArrayList<>();
	private final List<Animation> cardActive = new ArrayList<>();
	private final List<Animation> cardFlash  = new ArrayList<>();

	private final Animation scroll = new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC);
	private float scrollTarget = 0.0f;
	private float maxScroll = 0.0f;

	// =========================================================================
	//  Settings popup
	// =========================================================================

	private final SettingsPopup popup = new SettingsPopup();

	// =========================================================================
	//  Bind capture
	// =========================================================================

	private Module bindingModule = null;

	// =========================================================================
	//  Constructor / lifecycle
	// =========================================================================

	public ClickGuiScreen() {
		super(Text.literal("Blum"));
		for (int i = 0; i < categoryHover.length; i++) {
			categoryHover[i] = new Animation(0.0f, 160, Easing.EASE_OUT_CUBIC);
		}
	}


	@Override
	protected void init() {
		super.init();
		openAnim.setTarget(1.0f);
		slideAnim.setTarget(0.0f);
		rebuildVisible(true);
		updateSelectorAnim(true);
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

	@Override
	public void close() {
		openAnim.setTarget(0.0f);
		slideAnim.setTarget(30.0f);
		bindingModule = null;
		popup.close();
		super.close();
	}

	// =========================================================================
	//  Rebuild helpers
	// =========================================================================

	private void rebuildVisible(boolean stagger) {
		this.visibleModules = BlumDLC.MODULES.search(selectedCategory, searchText);
		Module popupMod = popup.getModule();
		if (popupMod != null && !visibleModules.contains(popupMod)) popup.detach();

		int size = visibleModules.size();
		resize(cardHover, size, () -> new Animation(0.0f, 140, Easing.EASE_OUT_CUBIC));
		resize(cardFlash, size, () -> new Animation(0.0f, 260, Easing.EASE_OUT_CUBIC));

		while (cardEnter.size() > size) cardEnter.remove(cardEnter.size() - 1);
		while (cardActive.size() > size) cardActive.remove(cardActive.size() - 1);


		for (int i = 0; i < size; i++) {
			if (i < cardEnter.size()) {
				if (stagger) { cardEnter.get(i).setImmediate(0.0f); cardEnter.get(i).setTarget(1.0f, 20L * i); }
			} else {
				Animation e = new Animation(0.0f, 340, Easing.EASE_OUT_QUINT);
				if (stagger) e.setTarget(1.0f, 20L * i); else e.setImmediate(1.0f);
				cardEnter.add(e);
			}
			if (i < cardActive.size()) {
				cardActive.get(i).setTarget(visibleModules.get(i).enabled ? 1.0f : 0.0f);
			} else {
				Animation a = new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC);
				a.setImmediate(visibleModules.get(i).enabled ? 1.0f : 0.0f);
				cardActive.add(a);
			}
		}
		clampScroll();
	}

	private static void resize(List<Animation> list, int size, java.util.function.Supplier<Animation> f) {
		while (list.size() > size) list.remove(list.size() - 1);
		while (list.size() < size) list.add(f.get());
	}

	private void updateSelectorAnim(boolean immediate) {
		float y = catRowY(selectedCategory.ordinal());
		if (immediate) { selectorY.setImmediate(y); selectorHeight.setImmediate(22.0f); }
		else { selectorY.setTarget(y); selectorHeight.setTarget(22.0f); }
	}

	private static float catRowY(int idx) { return 58.0f + idx * 24.0f; }

	private void clampScroll() {
		int rows = (int) Math.ceil(visibleModules.size() / (float) CARDS_PER_ROW);
		float content = rows * (CARD_H + CARD_GAP_Y) - CARD_GAP_Y;
		float visible = PANEL_H - CARD_AREA_PAD_TOP - CARD_AREA_PAD_BOTTOM;
		maxScroll = Math.max(0.0f, content - visible);
		scrollTarget = Math.max(0.0f, Math.min(scrollTarget, maxScroll));
		scroll.setTarget(scrollTarget);
	}


	// =========================================================================
	//  Render
	// =========================================================================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		// Sync theme colours every frame so live-changing palette is reflected
		Themes.syncAll();
		Theme.refresh();

		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		MsdfFont font = Fonts.BIKO.get();
		float open = openAnim.getValue();
		float slide = slideAnim.getValue();

		// Dim backdrop
		UIRender.rect(matrix, 0, 0, this.width, this.height, 0,
			ColorUtil.multiplyAlpha(Theme.DIM, open));

		// Panel with scale-in
		float px = (this.width - PANEL_W) * 0.5f;
		float py = (this.height - PANEL_H) * 0.5f + slide;
		float sc = 0.97f + 0.03f * open;
		float sw = PANEL_W * sc, sh = PANEL_H * sc;
		px += (PANEL_W - sw) * 0.5f;
		py += (PANEL_H - sh) * 0.5f;

		drawPanel(matrix, font, px, py, sw, sh, open, mouseX, mouseY);
		popup.render(matrix, font, px, py, sw, sh, open, mouseX, mouseY);
		super.render(context, mouseX, mouseY, deltaTicks);
	}

	private void drawPanel(Matrix4f m, MsdfFont font,
			float x, float y, float w, float h, float open, int mx, int my) {
		// Background
		UIRender.rect(m, x, y, w, h, 12.0f, ColorUtil.multiplyAlpha(Theme.PANEL_BG, open));
		UIRender.border(m, x, y, w, h, 12.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, open));

		float sbW = SIDEBAR_W * (w / PANEL_W);
		drawSidebar(m, font, x, y, sbW, h, open, mx, my);

		// Divider
		UIRender.rect(m, x + sbW, y + 10, 1.0f, h - 20, 0.0f,
			ColorUtil.multiplyAlpha(Theme.DIVIDER, open));

		drawMain(m, font, x + sbW, y, w - sbW, h, open, mx, my);
	}


	// ---------------------------------------------------------------------
	//  Sidebar
	// ---------------------------------------------------------------------

	private void drawSidebar(Matrix4f m, MsdfFont font,
			float x, float y, float w, float h, float open, int mx, int my) {
		UIRender.rect(m, x, y, w, h, 12.0f,
			ColorUtil.multiplyAlpha(Theme.SIDEBAR_BG, open));

		// Logo area
		float logoX = x + 12.0f, logoY = y + 14.0f, logoS = 20.0f;
		UIRender.rect(m, logoX, logoY, logoS, logoS, 10.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, 0.15f * open));
		UIRender.rectGradientV(m, logoX + 3, logoY + 3, logoS - 6, logoS - 6, 7.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, open),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, open));

		UIRender.text(m, font, "Blum", x + 38.0f, logoY + 5.0f, 11.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, open), 0.06f);

		UIRender.text(m, font, "modules", x + 12.0f, y + 44.0f, 6.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, open));

		// Animated selector pill
		float selY = y + selectorY.getValue();
		float selH = selectorHeight.getValue();
		UIRender.rectGradientH(m, x + 8.0f, selY, w - 16.0f, selH, 6.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, 0.85f * open),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, 0.85f * open));


		// Category rows
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			float rowY = y + catRowY(i);
			boolean sel = selectedCategory == cats[i];
			boolean hov = inside(mx, my, x + 8.0f, rowY, w - 16.0f, 22.0f);
			categoryHover[i].setTarget(hov && !sel ? 1.0f : 0.0f);
			float ht = categoryHover[i].getValue();
			if (!sel && ht > 0.001f) {
				UIRender.rect(m, x + 8.0f, rowY, w - 16.0f, 22.0f, 6.0f,
					ColorUtil.multiplyAlpha(0x0DFFFFFF, ht * open));
			}
			int tc = sel
				? ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, open)
				: ColorUtil.multiplyAlpha(
					ColorUtil.lerp(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, ht * 0.5f), open);
			UIRender.text(m, font, cats[i].displayName, x + 18.0f, rowY + 6.0f, 8.0f, tc);
		}

		// Quit
		float quitY = y + h - 30.0f;
		boolean qHov = inside(mx, my, x + 8.0f, quitY, w - 16.0f, 20.0f);
		quitHover.setTarget(qHov ? 1.0f : 0.0f);
		float qh = quitHover.getValue();
		if (qh > 0.001f) {
			UIRender.rect(m, x + 8.0f, quitY, w - 16.0f, 20.0f, 6.0f,
				ColorUtil.multiplyAlpha(Theme.DANGER, 0.14f * qh * open));
		}
		int qc = ColorUtil.lerp(Theme.TEXT_SECONDARY, Theme.DANGER, qh);
		UIRender.text(m, font, "Close", x + 18.0f, quitY + 5.0f, 8.0f,
			ColorUtil.multiplyAlpha(qc, open));
	}


	// ---------------------------------------------------------------------
	//  Main area: search + cards
	// ---------------------------------------------------------------------

	private void drawMain(Matrix4f m, MsdfFont font,
			float x, float y, float w, float h, float open, int mx, int my) {
		// Search bar
		float sx = x + 12.0f, sy = y + 12.0f;
		float sw = w - 24.0f, sh = 20.0f;
		float focus = searchFocus.getValue();
		int borderC = ColorUtil.lerp(0x28FFFFFF, Theme.ACCENT, focus);

		UIRender.rect(m, sx, sy, sw, sh, 7.0f,
			ColorUtil.multiplyAlpha(Theme.SEARCH_BG, open));
		UIRender.border(m, sx, sy, sw, sh, 7.0f, 1.0f,
			ColorUtil.multiplyAlpha(borderC, open));

		String disp = searchText.isEmpty() ? "Search..." : searchText;
		int sc = searchText.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT_PRIMARY;
		UIRender.text(m, font, disp, sx + 8.0f, sy + 6.0f, 7.5f,
			ColorUtil.multiplyAlpha(sc, open));

		// Caret
		long now = System.currentTimeMillis();
		if (now - lastBlinkSwap > 520L) { lastBlinkSwap = now; caretVisible = !caretVisible; }
		if (searchActive && caretVisible) {
			float cx = sx + 8.0f + UIRender.textWidth(font, searchText, 7.5f) + 1.0f;
			UIRender.rect(m, cx, sy + 4.0f, 1.0f, sh - 8.0f, 0.5f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, open));
		}

		drawCards(m, font, x, y, w, h, open, mx, my);
	}


	private void drawCards(Matrix4f m, MsdfFont font,
			float x, float y, float w, float h, float open, int mx, int my) {
		float clipTop = y + CARD_AREA_PAD_TOP - 4.0f;
		float clipBot = y + h - CARD_AREA_PAD_BOTTOM;
		float ox = x + CARD_AREA_PAD_X;
		float oy = y + CARD_AREA_PAD_TOP - scroll.getValue();
		Module popMod = popup.getModule();

		for (int i = 0; i < visibleModules.size(); i++) {
			int row = i / CARDS_PER_ROW, col = i % CARDS_PER_ROW;
			float cx = ox + col * (CARD_W + CARD_GAP_X);
			float cy = oy + row * (CARD_H + CARD_GAP_Y);
			if (cy + CARD_H < clipTop - 20 || cy > clipBot + 20) continue;
			drawCard(m, font, i, popMod, cx, cy, clipTop, clipBot, open, mx, my);
		}

		// Scrollbar
		if (maxScroll > 0.5f) {
			float tX = x + w - 5.0f, tY = y + CARD_AREA_PAD_TOP;
			float tH = h - CARD_AREA_PAD_TOP - CARD_AREA_PAD_BOTTOM;
			float thumbH = Math.max(20.0f, tH * (tH / (tH + maxScroll)));
			float thumbY = tY + (scroll.getValue() / maxScroll) * (tH - thumbH);
			UIRender.rect(m, tX, tY, 2.5f, tH, 1.0f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_BG, open));
			UIRender.rect(m, tX, thumbY, 2.5f, thumbH, 1.0f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_FG, open));
		}
	}


	private void drawCard(Matrix4f m, MsdfFont font, int i, Module popMod,
			float cx, float cy, float clipTop, float clipBot,
			float open, int mx, int my) {
		Module mod = visibleModules.get(i);
		float enterT = cardEnter.get(i).getValue();
		float cardX = cx + (1.0f - enterT) * 10.0f;
		float alpha = enterT * open;

		boolean hov = inside(mx, my, cardX, cy, CARD_W, CARD_H)
			&& my >= clipTop && my <= clipBot;
		cardHover.get(i).setTarget(hov ? 1.0f : 0.0f);
		cardActive.get(i).setTarget(mod.enabled ? 1.0f : 0.0f);

		float hovT = cardHover.get(i).getValue();
		float actT = cardActive.get(i).getValue();
		float flT  = cardFlash.get(i).getValue();

		// Inactive bg
		if (actT < 0.999f) {
			int bg = ColorUtil.lerp(Theme.CARD_BG, Theme.CARD_HOVER, hovT);
			UIRender.rect(m, cardX, cy, CARD_W, CARD_H, 8.0f,
				ColorUtil.multiplyAlpha(bg, alpha * (1.0f - actT)));
		}
		// Active gradient
		if (actT > 0.001f) {
			UIRender.rectGradientH(m, cardX, cy, CARD_W, CARD_H, 8.0f,
				ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, alpha * actT * 0.85f),
				ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, alpha * actT * 0.85f));
		}
		// Hover shine
		if (hovT > 0.001f) {
			UIRender.rectGradientV(m, cardX, cy, CARD_W, CARD_H * 0.45f, 8.0f,
				ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.04f * hovT * alpha), 0x00000000);
		}


		// Click flash ring
		if (flT > 0.001f && flT < 0.999f) {
			float ra = (1.0f - flT) * 0.5f * alpha;
			UIRender.border(m, cardX - flT * 2, cy - flT * 2,
				CARD_W + flT * 4, CARD_H + flT * 4,
				8.0f + flT * 3, 1.2f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, ra));
		}

		// Border — highlighted for popup target
		if (popMod == mod) {
			UIRender.border(m, cardX, cy, CARD_W, CARD_H, 8.0f, 1.2f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, 0.8f * alpha));
		} else {
			UIRender.border(m, cardX, cy, CARD_W, CARD_H, 8.0f, 0.8f,
				ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.5f * (1.0f - actT) * alpha));
		}

		// Title
		int titleC = ColorUtil.lerp(Theme.TEXT_PRIMARY, 0xFFFFFFFF, actT);
		UIRender.text(m, font, mod.name, cardX + 8.0f, cy + 8.0f, 8.0f,
			ColorUtil.multiplyAlpha(titleC, alpha), 0.05f);
		// Description
		int descC = ColorUtil.lerp(Theme.TEXT_SECONDARY, 0xFFE0D0F0, actT);
		UIRender.text(m, font, mod.description, cardX + 8.0f, cy + 20.0f, 5.8f,
			ColorUtil.multiplyAlpha(descC, alpha), 0.04f);

		// Settings dots hint
		if (!mod.settings.isEmpty()) {
			float dx = cardX + CARD_W - 13.0f, dy = cy + 8.0f;
			int dc = ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.45f * alpha);
			UIRender.rect(m, dx, dy, 2, 2, 1, dc);
			UIRender.rect(m, dx + 3.5f, dy, 2, 2, 1, dc);
		}


		// Bind chip
		boolean awaiting = (bindingModule == mod);
		if (awaiting || KeyName.isBound(mod.keybind)) {
			String lbl = awaiting ? "..." : "[" + KeyName.describe(mod.keybind) + "]";
			float kfs = 5.5f;
			float kw = UIRender.textWidth(font, lbl, kfs) + 5.0f;
			float kh = kfs + 4.0f;
			float kx = cardX + CARD_W - kw - 5.0f;
			float ky = cy + CARD_H - kh - 4.0f;
			int chipBg = awaiting ? Theme.ACCENT : 0x88000000;
			UIRender.rect(m, kx, ky, kw, kh, kh * 0.5f,
				ColorUtil.multiplyAlpha(chipBg, alpha));
			int ktc = awaiting ? 0xFF0E1018 : Theme.TEXT_PRIMARY;
			UIRender.text(m, font, lbl, kx + 2.5f, ky + 2.0f, kfs,
				ColorUtil.multiplyAlpha(ktc, alpha), 0.05f);
		}
	}


	// =========================================================================
	//  Input
	// =========================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float px = (this.width - PANEL_W) * 0.5f;
		float py = (this.height - PANEL_H) * 0.5f;

		if (popup.mouseClicked(mouseX, mouseY, px, py, PANEL_W, PANEL_H, button)) return true;

		// Sidebar categories
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			float rowY = py + catRowY(i);
			if (inside(mouseX, mouseY, px + 8.0f, rowY, SIDEBAR_W - 16.0f, 22.0f)) {
				if (selectedCategory != cats[i]) {
					selectedCategory = cats[i];
					updateSelectorAnim(false);
					rebuildVisible(true);
				}
				return true;
			}
		}

		// Quit
		float quitY = py + PANEL_H - 30.0f;
		if (inside(mouseX, mouseY, px + 8.0f, quitY, SIDEBAR_W - 16.0f, 20.0f)) {
			close(); return true;
		}

		// Search bar
		float searchX = px + SIDEBAR_W + 12.0f, searchY = py + 12.0f;
		float searchW = PANEL_W - SIDEBAR_W - 24.0f;
		boolean inSearch = inside(mouseX, mouseY, searchX, searchY, searchW, 20.0f);
		searchActive = inSearch;
		searchFocus.setTarget(inSearch ? 1.0f : 0.0f);


		// Cards
		float ox = px + SIDEBAR_W + CARD_AREA_PAD_X;
		float oy = py + CARD_AREA_PAD_TOP - scroll.getValue();
		float clipTop = py + CARD_AREA_PAD_TOP - 4.0f;
		float clipBot = py + PANEL_H - CARD_AREA_PAD_BOTTOM;
		for (int i = 0; i < visibleModules.size(); i++) {
			int row = i / CARDS_PER_ROW, col = i % CARDS_PER_ROW;
			float cx = ox + col * (CARD_W + CARD_GAP_X);
			float cy = oy + row * (CARD_H + CARD_GAP_Y);
			if (!inside(mouseX, mouseY, cx, cy, CARD_W, CARD_H)) continue;
			if (mouseY < clipTop || mouseY > clipBot) continue;

			Module mod = visibleModules.get(i);
			if (button == 1) { popup.toggle(mod); return true; }
			if (button == 2) { bindingModule = (bindingModule == mod) ? null : mod; return true; }
			mod.toggle();
			cardFlash.get(i).setImmediate(0.0f);
			cardFlash.get(i).setTarget(1.0f);
			return true;
		}

		popup.close();
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
		float px = (this.width - PANEL_W) * 0.5f;
		if (popup.mouseDragged(mx, my, btn, px, PANEL_W)) return true;
		return super.mouseDragged(mx, my, btn, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int btn) {
		popup.mouseReleased(btn);
		return super.mouseReleased(mx, my, btn);
	}


	@Override
	public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
		float px = (this.width - PANEL_W) * 0.5f;
		float py = (this.height - PANEL_H) * 0.5f;
		if (popup.mouseScrolled(mx, my, vAmt, px, py, PANEL_W, PANEL_H)) return true;
		scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget - (float) vAmt * 24.0f));
		scroll.setTarget(scrollTarget);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (bindingModule != null) {
			if (keyCode == 256) bindingModule.keybind = -1;
			else bindingModule.keybind = keyCode;
			bindingModule = null;
			return true;
		}
		if (searchActive) {
			if (keyCode == 256) { searchActive = false; searchFocus.setTarget(0.0f); return true; }
			if (keyCode == 259 && !searchText.isEmpty()) {
				searchText = searchText.substring(0, searchText.length() - 1);
				rebuildVisible(false); return true;
			}
			if (keyCode == 257) { searchActive = false; searchFocus.setTarget(0.0f); return true; }
		}
		if (popup.keyPressed(keyCode)) return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchActive && chr >= 32 && chr != 127) {
			searchText += chr;
			rebuildVisible(false);
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	private static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}
}
