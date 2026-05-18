package dev.blumdlc.client.ui;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.ui.animation.Animation;
import dev.blumdlc.client.ui.animation.Easing;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Celestial-style ClickGUI rendered exclusively through the project's Builder API.
 *
 * Layout (panel local coords):
 *   sidebar  : x = 0..160      (logo + brand, category list, "Quit")
 *   main     : x = 160..PANEL  (search bar, scrollable card grid)
 */
public final class ClickGuiScreen extends Screen {

	// --- Panel geometry ---
	private static final float PANEL_W = 460.0f;
	private static final float PANEL_H = 290.0f;
	private static final float SIDEBAR_W = 130.0f;
	private static final float CARD_W = 152.0f;
	private static final float CARD_H = 48.0f;
	private static final float CARD_GAP_X = 8.0f;
	private static final float CARD_GAP_Y = 8.0f;
	private static final float CARD_AREA_PAD_X = 14.0f;
	private static final float CARD_AREA_PAD_TOP = 46.0f;   // below the search bar
	private static final float CARD_AREA_PAD_BOTTOM = 10.0f;
	private static final int   CARDS_PER_ROW = 2;

	// --- Animations: panel ---
	private final Animation openAnim   = new Animation(0.0f, 360, Easing.EASE_OUT_QUINT);
	private final Animation slideAnim  = new Animation(40.0f, 420, Easing.EASE_OUT_EXPO);

	// --- Sidebar state ---
	private Category selectedCategory = Category.COMBAT;
	private final Animation selectorY      = new Animation(0.0f,   320, Easing.EASE_OUT_EXPO);
	private final Animation selectorHeight = new Animation(24.0f,  320, Easing.EASE_OUT_EXPO);
	private final Animation[] categoryHover = new Animation[Category.values().length];
	private final Animation quitHover      = new Animation(0.0f, 180, Easing.EASE_OUT_CUBIC);

	// --- Search bar ---
	private String searchText = "";
	private final Animation searchFocus = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
	private boolean searchActive = false;
	private long lastBlinkSwap = System.currentTimeMillis();
	private boolean caretVisible = true;

	// --- Cards ---
	private List<Module> visibleModules = new ArrayList<>();
	private final List<Animation> cardHover  = new ArrayList<>();
	private final List<Animation> cardEnter  = new ArrayList<>();
	private final List<Animation> cardActive = new ArrayList<>();
	private final List<Animation> cardClickFlash = new ArrayList<>();

	// --- Scroll ---
	private final Animation scroll = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
	private float scrollTarget = 0.0f;
	private float maxScroll = 0.0f;

	public ClickGuiScreen() {
		super(Text.literal("Blum"));
		for (int i = 0; i < categoryHover.length; i++) {
			categoryHover[i] = new Animation(0.0f, 180, Easing.EASE_OUT_CUBIC);
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

	private void rebuildVisible(boolean stagger) {
		this.visibleModules = BlumDLC.MODULES.search(selectedCategory, searchText);

		this.cardHover.clear();
		this.cardEnter.clear();
		this.cardActive.clear();
		this.cardClickFlash.clear();
		for (int i = 0; i < visibleModules.size(); i++) {
			cardHover.add(new Animation(0.0f, 160, Easing.EASE_OUT_CUBIC));
			Animation enter = new Animation(0.0f, 380, Easing.EASE_OUT_QUINT);
			if (stagger) {
				enter.setTarget(1.0f, 30L * i);
			} else {
				enter.setImmediate(1.0f);
			}
			cardEnter.add(enter);

			Animation activeAnim = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
			activeAnim.setImmediate(visibleModules.get(i).enabled ? 1.0f : 0.0f);
			cardActive.add(activeAnim);

			cardClickFlash.add(new Animation(0.0f, 280, Easing.EASE_OUT_CUBIC));
		}
		clampScroll();
	}

	private void updateSelectorAnim(boolean immediate) {
		int idx = selectedCategory.ordinal();
		float y = categoryRowY(idx);
		if (immediate) {
			selectorY.setImmediate(y);
			selectorHeight.setImmediate(24.0f);
		} else {
			selectorY.setTarget(y);
			selectorHeight.setTarget(24.0f);
		}
	}

	private static float categoryRowY(int index) {
		// First row position relative to panel; sidebar header is ~62px tall
		return 62.0f + index * 26.0f;
	}

	private void clampScroll() {
		int rows = (int) Math.ceil(visibleModules.size() / (float) CARDS_PER_ROW);
		float contentHeight = rows * (CARD_H + CARD_GAP_Y) - CARD_GAP_Y;
		float visibleHeight = PANEL_H - CARD_AREA_PAD_TOP - CARD_AREA_PAD_BOTTOM;
		this.maxScroll = Math.max(0.0f, contentHeight - visibleHeight);
		this.scrollTarget = Math.max(0.0f, Math.min(scrollTarget, maxScroll));
		this.scroll.setTarget(scrollTarget);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		openAnim.setTarget(0.0f);
		slideAnim.setTarget(40.0f);
		super.close();
	}

	// =====================================================================
	//  RENDER
	// =====================================================================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		MsdfFont font = Fonts.BIKO.get();

		float open = openAnim.getValue();        // 0..1
		float slide = slideAnim.getValue();      // 40..0

		// Backdrop dim (no fullscreen blur — blur only behind the panel)
		int dim = ColorUtil.multiplyAlpha(Theme.DIM, open);
		UIRender.rect(matrix, 0, 0, this.width, this.height, 0, dim);

		// Panel placement (centered)
		float panelX = (this.width  - PANEL_W) * 0.5f;
		float panelY = (this.height - PANEL_H) * 0.5f + slide;

		float scale = 0.96f + 0.04f * open;     // subtle zoom-in
		float scaledW = PANEL_W * scale;
		float scaledH = PANEL_H * scale;
		panelX += (PANEL_W - scaledW) * 0.5f;
		panelY += (PANEL_H - scaledH) * 0.5f;

		drawPanel(matrix, font, panelX, panelY, scaledW, scaledH, open, mouseX, mouseY);

		super.render(context, mouseX, mouseY, deltaTicks);
	}

	private void drawPanel(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float open, int mouseX, int mouseY) {

		// Panel base + soft outer accent ring
		UIRender.rect(matrix, x, y, w, h, 14.0f, ColorUtil.multiplyAlpha(Theme.PANEL_BG, open));
		UIRender.border(matrix, x, y, w, h, 14.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, open));

		// Sidebar
		float sbX = x;
		float sbY = y;
		float sbW = SIDEBAR_W * (w / PANEL_W);
		float sbH = h;
		drawSidebar(matrix, font, sbX, sbY, sbW, sbH, open, mouseX, mouseY);

		// Vertical divider between sidebar and main
		UIRender.rect(matrix, sbX + sbW, sbY + 12, 1.0f, sbH - 24,
			0.0f, ColorUtil.multiplyAlpha(Theme.DIVIDER, open));

		// Main area
		float mX = sbX + sbW;
		float mY = sbY;
		float mW = w - sbW;
		float mH = sbH;
		drawMain(matrix, font, mX, mY, mW, mH, open, mouseX, mouseY);
	}

	// ---------------------------------------------------------------------
	//  Sidebar
	// ---------------------------------------------------------------------

	private void drawSidebar(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float open, int mouseX, int mouseY) {

		UIRender.rect(matrix, x, y, w, h, 14.0f, ColorUtil.multiplyAlpha(Theme.SIDEBAR_BG, open));

		// Brand: gradient circular badge + "Blum" text
		float logoSize = 22.0f;
		float logoX = x + 14.0f;
		float logoY = y + 16.0f;
		// outer glow (rect + border)
		UIRender.rect(matrix, logoX, logoY, logoSize, logoSize, 11.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, 0.18f * open));
		// gradient core
		UIRender.rectGradientV(matrix, logoX + 3, logoY + 3, logoSize - 6, logoSize - 6, 8.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, open),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, open));
		// inner highlight pixel
		UIRender.rect(matrix, logoX + 6, logoY + 5, 3, 2, 1.0f,
			ColorUtil.withAlpha(0xFFFFFFFF, 0.6f * open));

		UIRender.text(matrix, font, "Blum", x + 42.0f, logoY + 6.0f,
			12.0f, ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, open), 0.06f);

		// Section label
		UIRender.text(matrix, font, "Modules", x + 14.0f, y + 48.0f,
			7.0f, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, open));

		// Animated selector (gradient pill)
		float selY = y + selectorY.getValue();
		float selH = selectorHeight.getValue();
		UIRender.rectGradientH(matrix, x + 10.0f, selY, w - 20.0f, selH, 7.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, open),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, open));

		// Category rows
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			float rowY = y + categoryRowY(i);
			boolean selected = selectedCategory == cats[i];
			boolean hovered = isInside(mouseX, mouseY, x + 10.0f, rowY, w - 20.0f, 24.0f);
			categoryHover[i].setTarget(hovered && !selected ? 1.0f : 0.0f);

			float hov = categoryHover[i].getValue();
			if (!selected && hov > 0.001f) {
				UIRender.rect(matrix, x + 10.0f, rowY, w - 20.0f, 24.0f, 7.0f,
					ColorUtil.multiplyAlpha(0x10FFFFFF, hov * open));
			}

			int textColor = selected
				? ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, open)
				: ColorUtil.lerp(Theme.TEXT_SECONDARY, Theme.TEXT_PRIMARY, hov * 0.6f);
			textColor = ColorUtil.multiplyAlpha(textColor, open);
			UIRender.text(matrix, font, cats[i].displayName, x + 20.0f, rowY + 7.0f, 8.5f, textColor);
		}

		// "Others" divider label
		UIRender.text(matrix, font, "Others", x + 14.0f, y + 196.0f, 7.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, open));
		UIRender.text(matrix, font, "Configs", x + 20.0f, y + 212.0f, 8.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, open));
		UIRender.text(matrix, font, "Themes",  x + 20.0f, y + 232.0f, 8.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, open));

		// Quit row
		float quitY = y + h - 32.0f;
		boolean quitHovered = isInside(mouseX, mouseY, x + 10.0f, quitY, w - 20.0f, 22.0f);
		quitHover.setTarget(quitHovered ? 1.0f : 0.0f);
		float qh = quitHover.getValue();
		if (qh > 0.001f) {
			UIRender.rect(matrix, x + 10.0f, quitY, w - 20.0f, 22.0f, 7.0f,
				ColorUtil.multiplyAlpha(Theme.DANGER, 0.16f * qh * open));
		}
		int quitColor = ColorUtil.lerp(Theme.TEXT_SECONDARY, Theme.DANGER, qh);
		UIRender.text(matrix, font, "Quit", x + 20.0f, quitY + 6.0f, 8.5f,
			ColorUtil.multiplyAlpha(quitColor, open));
	}

	// ---------------------------------------------------------------------
	//  Main: search + grid
	// ---------------------------------------------------------------------

	private void drawMain(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float open, int mouseX, int mouseY) {

		// Search bar
		float searchX = x + 14.0f;
		float searchY = y + 14.0f;
		float searchW = w - 28.0f;
		float searchH = 22.0f;
		float focus = searchFocus.getValue();
		int focusBorder = ColorUtil.lerp(0x33FFFFFF, Theme.ACCENT, focus);

		UIRender.rect(matrix, searchX, searchY, searchW, searchH, 8.0f,
			ColorUtil.multiplyAlpha(Theme.SEARCH_BG, open));
		UIRender.border(matrix, searchX, searchY, searchW, searchH, 8.0f, 1.0f,
			ColorUtil.multiplyAlpha(focusBorder, open));

		String displayText = searchText.isEmpty() ? "Search" : searchText;
		int searchColor = searchText.isEmpty()
			? Theme.TEXT_MUTED
			: Theme.TEXT_PRIMARY;
		UIRender.text(matrix, font, displayText, searchX + 10.0f, searchY + 7.0f, 8.0f,
			ColorUtil.multiplyAlpha(searchColor, open));

		// Caret
		long now = System.currentTimeMillis();
		if (now - lastBlinkSwap > 530L) {
			lastBlinkSwap = now;
			caretVisible = !caretVisible;
		}
		if (searchActive && caretVisible) {
			float caretX = searchX + 10.0f + UIRender.textWidth(font, searchText, 8.0f) + 1.0f;
			UIRender.rect(matrix, caretX, searchY + 5.0f, 1.0f, searchH - 10.0f, 0.5f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, open));
		}

		// Folder + gear icons (drawn as rounded rects, no textures)
		drawIcon(matrix, x + w - 44.0f, searchY + 4.0f, 12.0f, ColorUtil.multiplyAlpha(0x80FFFFFF, open));
		drawIcon(matrix, x + w - 26.0f, searchY + 4.0f, 12.0f, ColorUtil.multiplyAlpha(0x80FFFFFF, open));

		// Cards grid
		drawCards(matrix, font, x, y, w, h, open, mouseX, mouseY);
	}

	private void drawIcon(Matrix4f matrix, float x, float y, float size, int color) {
		UIRender.rect(matrix, x, y, size, size, 4.0f, ColorUtil.withAlpha(color, 0.22f));
		UIRender.rect(matrix, x + 3, y + 4, size - 6, 1.5f, 0.5f, color);
		UIRender.rect(matrix, x + 3, y + 7, size - 6, 1.5f, 0.5f, color);
		UIRender.rect(matrix, x + 3, y + 10, size - 9, 1.5f, 0.5f, color);
	}

	private void drawCards(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float open, int mouseX, int mouseY) {

		float clipTop = y + CARD_AREA_PAD_TOP - 4.0f;
		float clipBottom = y + h - CARD_AREA_PAD_BOTTOM;

		float originX = x + CARD_AREA_PAD_X;
		float originY = y + CARD_AREA_PAD_TOP - scroll.getValue();

		for (int i = 0; i < visibleModules.size(); i++) {
			int row = i / CARDS_PER_ROW;
			int col = i % CARDS_PER_ROW;
			float cx = originX + col * (CARD_W + CARD_GAP_X);
			float cy = originY + row * (CARD_H + CARD_GAP_Y);

			// Cull off-screen rows (with a margin)
			if (cy + CARD_H < clipTop - 30.0f || cy > clipBottom + 30.0f) {
				continue;
			}

			Module module = visibleModules.get(i);
			Animation enter = cardEnter.get(i);
			Animation hoverA = cardHover.get(i);
			Animation activeA = cardActive.get(i);
			Animation flashA = cardClickFlash.get(i);

			float enterT = enter.getValue();              // 0..1
			float cardX = cx + (1.0f - enterT) * 14.0f;   // slide-in from left
			float cardAlpha = enterT * open;

			boolean hovered = isInside(mouseX, mouseY, cardX, cy, CARD_W, CARD_H)
				&& mouseY >= clipTop && mouseY <= clipBottom;
			hoverA.setTarget(hovered ? 1.0f : 0.0f);
			activeA.setTarget(module.enabled ? 1.0f : 0.0f);

			float hoverT = hoverA.getValue();
			float activeT = activeA.getValue();
			float flashT = flashA.getValue();

			// Base background
			int bgInactive = ColorUtil.lerp(Theme.CARD_BG, Theme.CARD_HOVER, hoverT);
			int bgActiveLeft  = Theme.CARD_ACTIVE_FROM;
			int bgActiveRight = Theme.CARD_ACTIVE_TO;

			// Card body: gradient when active, blended into hover bg when not
			if (activeT < 0.999f) {
				UIRender.rect(matrix, cardX, cy, CARD_W, CARD_H, 10.0f,
					ColorUtil.multiplyAlpha(bgInactive, cardAlpha * (1.0f - activeT)));
			}
			if (activeT > 0.001f) {
				UIRender.rectGradientH(matrix, cardX, cy, CARD_W, CARD_H, 10.0f,
					ColorUtil.multiplyAlpha(bgActiveLeft,  cardAlpha * activeT),
					ColorUtil.multiplyAlpha(bgActiveRight, cardAlpha * activeT));
			}

			// Hover lift: a soft top highlight
			if (hoverT > 0.001f) {
				UIRender.rectGradientV(matrix, cardX, cy, CARD_W, CARD_H * 0.5f, 10.0f,
					ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.05f * hoverT * cardAlpha),
					0x00000000);
			}

			// Click flash ring
			if (flashT > 0.001f && flashT < 0.999f) {
				float ringAlpha = (1.0f - flashT) * 0.6f * cardAlpha;
				UIRender.border(matrix, cardX - flashT * 2.0f, cy - flashT * 2.0f,
					CARD_W + flashT * 4.0f, CARD_H + flashT * 4.0f,
					10.0f + flashT * 4.0f, 1.5f,
					ColorUtil.multiplyAlpha(Theme.ACCENT, ringAlpha));
			}

			// Card border (subtle)
			UIRender.border(matrix, cardX, cy, CARD_W, CARD_H, 10.0f, 1.0f,
				ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.6f * (1.0f - activeT) * cardAlpha));

			// Title + small status link icon
			int titleColor = ColorUtil.lerp(Theme.TEXT_PRIMARY, 0xFFFFFFFF, activeT);
			UIRender.text(matrix, font, module.name, cardX + 10.0f, cy + 8.0f, 9.0f,
				ColorUtil.multiplyAlpha(titleColor, cardAlpha), 0.05f);

			// Settings/info dots
			float dotsX = cardX + 10.0f + UIRender.textWidth(font, module.name, 9.0f) + 6.0f;
			drawTinyIcons(matrix, dotsX, cy + 10.0f,
				ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.65f * cardAlpha));

			// Description
			int descColor = ColorUtil.lerp(Theme.TEXT_SECONDARY, 0xFFE9D2F2, activeT);
			UIRender.text(matrix, font, module.description, cardX + 10.0f, cy + 22.0f, 6.5f,
				ColorUtil.multiplyAlpha(descColor, cardAlpha), 0.04f);
		}

		// Scrollbar
		if (maxScroll > 0.5f) {
			float trackX = x + w - 6.0f;
			float trackY = y + CARD_AREA_PAD_TOP;
			float trackH = h - CARD_AREA_PAD_TOP - CARD_AREA_PAD_BOTTOM;
			float thumbH = Math.max(24.0f, trackH * (trackH / (trackH + maxScroll)));
			float thumbY = trackY + (scroll.getValue() / maxScroll) * (trackH - thumbH);

			UIRender.rect(matrix, trackX, trackY, 3.0f, trackH, 1.5f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_BG, open));
			UIRender.rect(matrix, trackX, thumbY, 3.0f, thumbH, 1.5f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_FG, open));
		}
	}

	private void drawTinyIcons(Matrix4f matrix, float x, float y, int color) {
		// settings gear glyph (3 dots) + chain link glyph (two pills)
		UIRender.rect(matrix, x,        y,     2.0f, 2.0f, 1.0f, color);
		UIRender.rect(matrix, x + 4.0f, y,     2.0f, 2.0f, 1.0f, color);
		UIRender.rect(matrix, x + 8.0f, y,     2.0f, 2.0f, 1.0f, color);
		UIRender.rect(matrix, x + 14.0f, y - 1.0f, 5.0f, 4.0f, 1.5f, color);
		UIRender.rect(matrix, x + 18.0f, y - 1.0f, 5.0f, 4.0f, 1.5f,
			ColorUtil.multiplyAlpha(color, 0.6f));
	}

	// =====================================================================
	//  INPUT
	// =====================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float panelX = (this.width  - PANEL_W) * 0.5f;
		float panelY = (this.height - PANEL_H) * 0.5f;

		// Sidebar categories
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			float rowY = panelY + categoryRowY(i);
			if (isInside(mouseX, mouseY, panelX + 10.0f, rowY, SIDEBAR_W - 20.0f, 24.0f)) {
				if (selectedCategory != cats[i]) {
					selectedCategory = cats[i];
					updateSelectorAnim(false);
					rebuildVisible(true);
				}
				return true;
			}
		}

		// Quit
		float quitY = panelY + PANEL_H - 32.0f;
		if (isInside(mouseX, mouseY, panelX + 10.0f, quitY, SIDEBAR_W - 20.0f, 22.0f)) {
			this.close();
			return true;
		}

		// Search bar
		float searchX = panelX + SIDEBAR_W + 14.0f;
		float searchY = panelY + 14.0f;
		float searchW = PANEL_W - SIDEBAR_W - 28.0f;
		boolean inSearch = isInside(mouseX, mouseY, searchX, searchY, searchW, 22.0f);
		searchActive = inSearch;
		searchFocus.setTarget(inSearch ? 1.0f : 0.0f);

		// Cards
		float originX = panelX + SIDEBAR_W + CARD_AREA_PAD_X;
		float originY = panelY + CARD_AREA_PAD_TOP - scroll.getValue();
		for (int i = 0; i < visibleModules.size(); i++) {
			int row = i / CARDS_PER_ROW;
			int col = i % CARDS_PER_ROW;
			float cx = originX + col * (CARD_W + CARD_GAP_X);
			float cy = originY + row * (CARD_H + CARD_GAP_Y);
			if (isInside(mouseX, mouseY, cx, cy, CARD_W, CARD_H)
					&& mouseY >= panelY + CARD_AREA_PAD_TOP - 4.0f
					&& mouseY <= panelY + PANEL_H - CARD_AREA_PAD_BOTTOM) {
				visibleModules.get(i).toggle();
				cardClickFlash.get(i).setImmediate(0.0f);
				cardClickFlash.get(i).setTarget(1.0f);
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget - (float) verticalAmount * 28.0f));
		scroll.setTarget(scrollTarget);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchActive) {
			if (keyCode == 256 /* ESC */) {
				searchActive = false;
				searchFocus.setTarget(0.0f);
				return true;
			}
			if (keyCode == 259 /* BACKSPACE */) {
				if (!searchText.isEmpty()) {
					searchText = searchText.substring(0, searchText.length() - 1);
					rebuildVisible(false);
				}
				return true;
			}
			if (keyCode == 257 /* ENTER */) {
				searchActive = false;
				searchFocus.setTarget(0.0f);
				return true;
			}
		}
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

	// =====================================================================
	//  HELPERS
	// =====================================================================

	private static boolean isInside(double mouseX, double mouseY, float x, float y, float w, float h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

}
