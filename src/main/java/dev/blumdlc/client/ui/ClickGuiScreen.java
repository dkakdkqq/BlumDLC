package dev.blumdlc.client.ui;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.BooleanSetting;
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
 * Celestial-style ClickGUI rendered exclusively through the project's Builder API.
 *
 * Layout:
 *   sidebar  (categories) | main area (search + 4-column card grid) | settings popup (right click)
 */
public final class ClickGuiScreen extends Screen {

	// --- Panel geometry ---
	private static final float PANEL_W = 540.0f;
	private static final float PANEL_H = 280.0f;
	private static final float SIDEBAR_W = 110.0f;
	private static final float CARD_W = 96.0f;
	private static final float CARD_H = 42.0f;
	private static final float CARD_GAP_X = 5.0f;
	private static final float CARD_GAP_Y = 5.0f;
	private static final float CARD_AREA_PAD_X = 10.0f;
	private static final float CARD_AREA_PAD_TOP = 40.0f;
	private static final float CARD_AREA_PAD_BOTTOM = 8.0f;
	private static final int   CARDS_PER_ROW = 4;

	// --- Settings popup ---
	private static final float POPUP_W = 200.0f;
	private static final float POPUP_GAP = 7.0f;
	private static final float POPUP_PAD_X = 12.0f;
	private static final float POPUP_HEADER_H = 32.0f;
	private static final float POPUP_ROW_GAP = 7.0f;

	// --- Animations: panel ---
	private final Animation openAnim   = new Animation(0.0f, 360, Easing.EASE_OUT_QUINT);
	private final Animation slideAnim  = new Animation(40.0f, 420, Easing.EASE_OUT_EXPO);

	// --- Sidebar state ---
	private Category selectedCategory = Category.COMBAT;
	private final Animation selectorY      = new Animation(0.0f,  320, Easing.EASE_OUT_EXPO);
	private final Animation selectorHeight = new Animation(24.0f, 320, Easing.EASE_OUT_EXPO);
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

	// --- Settings popup state ---
	private Module settingsModule = null;
	private final Animation settingsAnim = new Animation(0.0f, 320, Easing.EASE_OUT_EXPO);
	private NumberSetting draggingSlider = null;

	// --- Dropdown overlay state ---
	/** ModeSetting whose dropdown is currently open (null == none). */
	private ModeSetting openDropdown = null;
	/**
	 * Screen-space rect of the closed selector that opened the dropdown.
	 * Used to anchor the option list and to detect "click outside".
	 */
	private float dropdownX, dropdownY, dropdownW, dropdownH;

	// --- Bind capture state ---
	/** Module currently waiting for a key press to set its bind (null == none). */
	private Module bindingModule = null;

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
		// Close settings popup if its module disappears from view
		if (settingsModule != null && !visibleModules.contains(settingsModule)) {
			settingsModule = null;
			settingsAnim.setTarget(0.0f);
			openDropdown = null;
		}

		this.cardHover.clear();
		this.cardEnter.clear();
		this.cardActive.clear();
		this.cardClickFlash.clear();
		for (int i = 0; i < visibleModules.size(); i++) {
			cardHover.add(new Animation(0.0f, 160, Easing.EASE_OUT_CUBIC));
			Animation enter = new Animation(0.0f, 380, Easing.EASE_OUT_QUINT);
			if (stagger) {
				enter.setTarget(1.0f, 25L * i);
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
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Disable vanilla blur; we draw our own dim layer in render().
	}

	@Override
	public void close() {
		openAnim.setTarget(0.0f);
		slideAnim.setTarget(40.0f);
		bindingModule = null;
		openDropdown = null;
		super.close();
	}

	// =====================================================================
	//  RENDER
	// =====================================================================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		MsdfFont font = Fonts.BIKO.get();

		float open = openAnim.getValue();
		float slide = slideAnim.getValue();

		int dim = ColorUtil.multiplyAlpha(Theme.DIM, open);
		UIRender.rect(matrix, 0, 0, this.width, this.height, 0, dim);

		float panelX = (this.width  - PANEL_W) * 0.5f;
		float panelY = (this.height - PANEL_H) * 0.5f + slide;

		float scale = 0.96f + 0.04f * open;
		float scaledW = PANEL_W * scale;
		float scaledH = PANEL_H * scale;
		panelX += (PANEL_W - scaledW) * 0.5f;
		panelY += (PANEL_H - scaledH) * 0.5f;

		drawPanel(matrix, font, panelX, panelY, scaledW, scaledH, open, mouseX, mouseY);

		// Settings popup (right of the panel)
		float popupT = settingsAnim.getValue();
		if (popupT > 0.001f && settingsModule != null) {
			drawSettingsPopup(matrix, font, panelX, panelY, scaledW, scaledH, open, popupT, mouseX, mouseY);
		}

		super.render(context, mouseX, mouseY, deltaTicks);
	}

	private void drawPanel(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float open, int mouseX, int mouseY) {

		UIRender.rect(matrix, x, y, w, h, 14.0f, ColorUtil.multiplyAlpha(Theme.PANEL_BG, open));
		UIRender.border(matrix, x, y, w, h, 14.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, open));

		float sbW = SIDEBAR_W * (w / PANEL_W);
		drawSidebar(matrix, font, x, y, sbW, h, open, mouseX, mouseY);

		UIRender.rect(matrix, x + sbW, y + 12, 1.0f, h - 24,
			0.0f, ColorUtil.multiplyAlpha(Theme.DIVIDER, open));

		drawMain(matrix, font, x + sbW, y, w - sbW, h, open, mouseX, mouseY);
	}

	// ---------------------------------------------------------------------
	//  Sidebar
	// ---------------------------------------------------------------------

	private void drawSidebar(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float open, int mouseX, int mouseY) {

		UIRender.rect(matrix, x, y, w, h, 14.0f, ColorUtil.multiplyAlpha(Theme.SIDEBAR_BG, open));

		// Brand
		float logoSize = 22.0f;
		float logoX = x + 14.0f;
		float logoY = y + 16.0f;
		UIRender.rect(matrix, logoX, logoY, logoSize, logoSize, 11.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, 0.18f * open));
		UIRender.rectGradientV(matrix, logoX + 3, logoY + 3, logoSize - 6, logoSize - 6, 8.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, open),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, open));
		UIRender.rect(matrix, logoX + 6, logoY + 5, 3, 2, 1.0f,
			ColorUtil.withAlpha(0xFFFFFFFF, 0.6f * open));

		UIRender.text(matrix, font, "Blum", x + 42.0f, logoY + 6.0f,
			12.0f, ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, open), 0.06f);

		UIRender.text(matrix, font, "Modules", x + 14.0f, y + 48.0f,
			7.0f, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, open));

		// Animated selector pill
		float selY = y + selectorY.getValue();
		float selH = selectorHeight.getValue();
		UIRender.rectGradientH(matrix, x + 10.0f, selY, w - 20.0f, selH, 7.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, open),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO, open));

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
		int searchColor = searchText.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT_PRIMARY;
		UIRender.text(matrix, font, displayText, searchX + 10.0f, searchY + 7.0f, 8.0f,
			ColorUtil.multiplyAlpha(searchColor, open));

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

		drawIcon(matrix, x + w - 44.0f, searchY + 4.0f, 12.0f, ColorUtil.multiplyAlpha(0x80FFFFFF, open));
		drawIcon(matrix, x + w - 26.0f, searchY + 4.0f, 12.0f, ColorUtil.multiplyAlpha(0x80FFFFFF, open));

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

			if (cy + CARD_H < clipTop - 30.0f || cy > clipBottom + 30.0f) {
				continue;
			}

			Module module = visibleModules.get(i);
			Animation enter = cardEnter.get(i);
			Animation hoverA = cardHover.get(i);
			Animation activeA = cardActive.get(i);
			Animation flashA = cardClickFlash.get(i);

			float enterT = enter.getValue();
			float cardX = cx + (1.0f - enterT) * 14.0f;
			float cardAlpha = enterT * open;

			boolean hovered = isInside(mouseX, mouseY, cardX, cy, CARD_W, CARD_H)
				&& mouseY >= clipTop && mouseY <= clipBottom;
			hoverA.setTarget(hovered ? 1.0f : 0.0f);
			activeA.setTarget(module.enabled ? 1.0f : 0.0f);

			float hoverT = hoverA.getValue();
			float activeT = activeA.getValue();
			float flashT = flashA.getValue();

			int bgInactive = ColorUtil.lerp(Theme.CARD_BG, Theme.CARD_HOVER, hoverT);

			if (activeT < 0.999f) {
				UIRender.rect(matrix, cardX, cy, CARD_W, CARD_H, 10.0f,
					ColorUtil.multiplyAlpha(bgInactive, cardAlpha * (1.0f - activeT)));
			}
			if (activeT > 0.001f) {
				UIRender.rectGradientH(matrix, cardX, cy, CARD_W, CARD_H, 10.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, cardAlpha * activeT),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   cardAlpha * activeT));
			}

			if (hoverT > 0.001f) {
				UIRender.rectGradientV(matrix, cardX, cy, CARD_W, CARD_H * 0.5f, 10.0f,
					ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.05f * hoverT * cardAlpha),
					0x00000000);
			}

			if (flashT > 0.001f && flashT < 0.999f) {
				float ringAlpha = (1.0f - flashT) * 0.6f * cardAlpha;
				UIRender.border(matrix, cardX - flashT * 2.0f, cy - flashT * 2.0f,
					CARD_W + flashT * 4.0f, CARD_H + flashT * 4.0f,
					10.0f + flashT * 4.0f, 1.5f,
					ColorUtil.multiplyAlpha(Theme.ACCENT, ringAlpha));
			}

			// Highlight ring when this card's settings popup is open
			if (settingsModule == module) {
				UIRender.border(matrix, cardX, cy, CARD_W, CARD_H, 10.0f, 1.5f,
					ColorUtil.multiplyAlpha(Theme.ACCENT, 0.9f * cardAlpha));
			} else {
				UIRender.border(matrix, cardX, cy, CARD_W, CARD_H, 10.0f, 1.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.6f * (1.0f - activeT) * cardAlpha));
			}

			int titleColor = ColorUtil.lerp(Theme.TEXT_PRIMARY, 0xFFFFFFFF, activeT);
			UIRender.text(matrix, font, module.name, cardX + 9.0f, cy + 8.0f, 8.5f,
				ColorUtil.multiplyAlpha(titleColor, cardAlpha), 0.05f);

			// Description (clipped if too long visually)
			int descColor = ColorUtil.lerp(Theme.TEXT_SECONDARY, 0xFFE9D2F2, activeT);
			UIRender.text(matrix, font, module.description, cardX + 9.0f, cy + 22.0f, 6.0f,
				ColorUtil.multiplyAlpha(descColor, cardAlpha), 0.04f);

			// Tiny "has settings" indicator
			if (!module.settings.isEmpty()) {
				float ix = cardX + CARD_W - 14.0f;
				float iy = cy + 8.0f;
				UIRender.rect(matrix, ix,        iy, 2.0f, 2.0f, 1.0f, ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.55f * cardAlpha));
				UIRender.rect(matrix, ix + 4.0f, iy, 2.0f, 2.0f, 1.0f, ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.55f * cardAlpha));
				UIRender.rect(matrix, ix + 8.0f, iy, 2.0f, 2.0f, 1.0f, ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.55f * cardAlpha));
			}

			// Bind chip in the bottom-right corner of the card.
			boolean awaitingBind = (bindingModule == module);
			if (awaitingBind || KeyName.isBound(module.keybind)) {
				String label = awaitingBind ? "..." : "[" + KeyName.describe(module.keybind) + "]";
				float kfs = 6.0f;
				float kw = UIRender.textWidth(font, label, kfs) + 6.0f;
				float kh = kfs + 4.0f;
				float kx = cardX + CARD_W - kw - 6.0f;
				float ky = cy + CARD_H - kh - 5.0f;
				int chipBg = awaitingBind ? Theme.ACCENT : 0x99000000;
				UIRender.rect(matrix, kx, ky, kw, kh, kh * 0.5f,
					ColorUtil.multiplyAlpha(chipBg, cardAlpha));
				int textColor = awaitingBind ? 0xFF14141C : Theme.TEXT_PRIMARY;
				UIRender.text(matrix, font, label, kx + 3.0f, ky + 2.0f, kfs,
					ColorUtil.multiplyAlpha(textColor, cardAlpha), 0.05f);
			}
		}

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

	// =====================================================================
	//  Settings popup (right of the main panel)
	// =====================================================================

	private void drawSettingsPopup(Matrix4f matrix, MsdfFont font,
			float panelX, float panelY, float panelW, float panelH,
			float open, float t, int mouseX, int mouseY) {

		float baseX = panelX + panelW + POPUP_GAP;
		float startX = baseX - 16.0f;            // slide-in from the left
		float x = startX + (baseX - startX) * t;
		float y = panelY;
		float w = POPUP_W;
		float h = panelH;
		float a = open * t;

		UIRender.rect(matrix, x, y, w, h, 14.0f, ColorUtil.multiplyAlpha(Theme.PANEL_BG, a));
		UIRender.border(matrix, x, y, w, h, 14.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, a));

		// Header
		UIRender.text(matrix, font, settingsModule.name, x + POPUP_PAD_X, y + 12.0f,
			11.0f, ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.06f);
		UIRender.text(matrix, font, settingsModule.description, x + POPUP_PAD_X, y + 26.0f,
			6.5f, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a), 0.04f);

		// Close X (top-right)
		float closeSize = 14.0f;
		float closeX = x + w - POPUP_PAD_X - closeSize;
		float closeY = y + 12.0f;
		boolean closeHover = isInside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
		int closeColor = closeHover ? Theme.DANGER : 0xFFA0A0B0;
		UIRender.rect(matrix, closeX, closeY, closeSize, closeSize, 4.0f,
			ColorUtil.multiplyAlpha(closeHover ? Theme.DANGER : 0x10FFFFFF, (closeHover ? 0.18f : 1.0f) * a));
		UIRender.text(matrix, font, "x", closeX + 4.0f, closeY + 3.0f, 8.0f,
			ColorUtil.multiplyAlpha(closeColor, a));

		// Divider
		UIRender.rect(matrix, x + POPUP_PAD_X, y + POPUP_HEADER_H, w - POPUP_PAD_X * 2, 1.0f,
			0.0f, ColorUtil.multiplyAlpha(Theme.DIVIDER, a));

		// Settings list
		float cy = y + POPUP_HEADER_H + 10.0f;
		float bottom = y + h - 10.0f;
		float contentX = x + POPUP_PAD_X;
		float contentW = w - POPUP_PAD_X * 2;

		for (Setting<?> s : settingsModule.settings) {
			if (cy >= bottom) break;
			cy = drawSetting(matrix, font, s, contentX, cy, contentW, a, mouseX, mouseY);
			cy += POPUP_ROW_GAP;
		}

		// Open dropdown is rendered last so it overlays subsequent rows.
		drawDropdownOverlay(matrix, font, a, mouseX, mouseY);
	}

	private float drawSetting(Matrix4f matrix, MsdfFont font, Setting<?> s,
			float x, float y, float w, float a, int mouseX, int mouseY) {

		// Label
		UIRender.text(matrix, font, s.name, x, y, 8.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);

		float ny = y + 12.0f;

		if (s instanceof BooleanSetting bs) {
			ny = drawBoolean(matrix, bs, x, y, w, a, mouseX, mouseY);
		} else if (s instanceof NumberSetting ns) {
			ny = drawSlider(matrix, font, ns, x, ny, w, a, mouseX, mouseY);
		} else if (s instanceof ModeSetting ms) {
			ny = drawMode(matrix, font, ms, x, ny, w, a, mouseX, mouseY);
		} else if (s instanceof MultiSetting mu) {
			ny = drawMulti(matrix, font, mu, x, ny, w, a, mouseX, mouseY);
		} else {
			ny += 14.0f;
		}
		return ny;
	}

	private float drawBoolean(Matrix4f matrix, BooleanSetting s,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		// Toggle on the right of the label row
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
			float x, float y, float w, float a, int mouseX, int mouseY) {
		float trackH = 4.0f;
		float trackY = y + 4.0f;
		float trackW = w;
		float frac = (float) ((s.get() - s.min) / (s.max - s.min));
		frac = Math.max(0.0f, Math.min(1.0f, frac));

		UIRender.rect(matrix, x, trackY, trackW, trackH, 2.0f,
			ColorUtil.multiplyAlpha(0x22FFFFFF, a));
		UIRender.rectGradientH(matrix, x, trackY, trackW * frac, trackH, 2.0f,
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
			ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   a));

		float knobX = x + trackW * frac - 4.0f;
		UIRender.rect(matrix, knobX, trackY - 3.0f, 8.0f, 10.0f, 4.0f,
			ColorUtil.multiplyAlpha(0xFFFFFFFF, a));

		String value = String.format("%.1f", s.get());
		float vw = UIRender.textWidth(font, value, 7.0f);
		UIRender.text(matrix, font, value, x + w - vw, y + 12.0f, 7.0f,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, a));

		return y + 22.0f;
	}

	private float drawMode(Matrix4f matrix, MsdfFont font, ModeSetting s,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		// Expanded mode: always-visible radio list (like MultiSetting but single-select)
		if (s.expanded) {
			return drawModeExpanded(matrix, font, s, x, y, w, a, mouseX, mouseY);
		}

		float h = 16.0f;
		boolean hovered = isInside(mouseX, mouseY, x, y, w, h);
		boolean open = (openDropdown == s);

		UIRender.rect(matrix, x, y, w, h, 6.0f,
			ColorUtil.multiplyAlpha(open ? Theme.CARD_HOVER : (hovered ? Theme.CARD_HOVER : Theme.CARD_BG), a));
		UIRender.border(matrix, x, y, w, h, 6.0f, 1.0f,
			ColorUtil.multiplyAlpha(open ? Theme.ACCENT : Theme.CARD_BORDER, a));
		UIRender.text(matrix, font, s.get(), x + 8.0f, y + 4.5f, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.05f);
		// Caret indicator: down when closed, up when open.
		UIRender.text(matrix, font, open ? "^" : "v", x + w - 12.0f, y + 4.5f, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, a));

		// Remember anchor for the overlay-pass dropdown render.
		if (open) {
			dropdownX = x;
			dropdownY = y;
			dropdownW = w;
			dropdownH = h;
		}

		return y + h + 2.0f;
	}

	/** Draws an expanded (always-visible) radio selector for a ModeSetting. */
	private float drawModeExpanded(Matrix4f matrix, MsdfFont font, ModeSetting s,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		float rowH = 14.0f;
		float rowGap = 3.0f;
		float cy = y;
		String selected = s.get();
		for (String mode : s.modes) {
			boolean isSelected = mode.equals(selected);
			boolean hovered = isInside(mouseX, mouseY, x, cy, w, rowH);

			if (isSelected) {
				UIRender.rectGradientH(matrix, x, cy, w, rowH, 5.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   a));
			} else {
				UIRender.rect(matrix, x, cy, w, rowH, 5.0f,
					ColorUtil.multiplyAlpha(hovered ? Theme.CARD_HOVER : Theme.CARD_BG, a));
				UIRender.border(matrix, x, cy, w, rowH, 5.0f, 1.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.5f * a));
			}

			int textColor = isSelected ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			UIRender.text(matrix, font, mode, x + 8.0f, cy + 3.5f, 7.0f,
				ColorUtil.multiplyAlpha(textColor, a), 0.04f);

			cy += rowH + rowGap;
		}
		return cy;
	}

	private void drawDropdownOverlay(Matrix4f matrix, MsdfFont font, float a, int mouseX, int mouseY) {
		ModeSetting s = openDropdown;
		if (s == null) return;

		float rowH = 14.0f;
		float listH = s.modes.size() * rowH;
		float x = dropdownX;
		float y = dropdownY + dropdownH + 2.0f;
		float w = dropdownW;

		UIRender.rect(matrix, x, y, w, listH, 6.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BG, a));
		UIRender.border(matrix, x, y, w, listH, 6.0f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.ACCENT, a));

		float cy = y;
		String selected = s.get();
		for (String mode : s.modes) {
			boolean isSelected = mode.equals(selected);
			boolean hovered = isInside(mouseX, mouseY, x, cy, w, rowH);

			if (isSelected) {
				UIRender.rectGradientH(matrix, x + 1, cy, w - 2, rowH, 5.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   a));
			} else if (hovered) {
				UIRender.rect(matrix, x + 1, cy, w - 2, rowH, 5.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_HOVER, a));
			}

			int textColor = isSelected ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			UIRender.text(matrix, font, mode, x + 8.0f, cy + 3.5f, 7.0f,
				ColorUtil.multiplyAlpha(textColor, a), 0.04f);

			cy += rowH;
		}
	}

	private float drawMulti(Matrix4f matrix, MsdfFont font, MultiSetting s,
			float x, float y, float w, float a, int mouseX, int mouseY) {
		float rowH = 14.0f;
		float rowGap = 3.0f;
		float cy = y;
		for (String opt : s.options) {
			boolean selected = s.isSelected(opt);
			boolean hovered = isInside(mouseX, mouseY, x, cy, w, rowH);

			if (selected) {
				UIRender.rectGradientH(matrix, x, cy, w, rowH, 5.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, a),
					ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   a));
			} else {
				UIRender.rect(matrix, x, cy, w, rowH, 5.0f,
					ColorUtil.multiplyAlpha(hovered ? Theme.CARD_HOVER : Theme.CARD_BG, a));
				UIRender.border(matrix, x, cy, w, rowH, 5.0f, 1.0f,
					ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.5f * a));
			}

			int textColor = selected ? 0xFFFFFFFF : Theme.TEXT_SECONDARY;
			UIRender.text(matrix, font, opt, x + 8.0f, cy + 3.5f, 7.0f,
				ColorUtil.multiplyAlpha(textColor, a), 0.04f);

			cy += rowH + rowGap;
		}
		return cy;
	}

	// =====================================================================
	//  INPUT
	// =====================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float panelX = (this.width  - PANEL_W) * 0.5f;
		float panelY = (this.height - PANEL_H) * 0.5f;

		// --- Settings popup interactions (handled first when open) ---
		if (settingsModule != null && settingsAnim.getValue() > 0.5f) {
			float popupX = panelX + PANEL_W + POPUP_GAP;
			float popupY = panelY;
			float popupW = POPUP_W;
			float popupH = PANEL_H;

			if (isInside(mouseX, mouseY, popupX, popupY, popupW, popupH)) {
				// Close X
				float closeSize = 14.0f;
				float closeX = popupX + popupW - POPUP_PAD_X - closeSize;
				float closeY = popupY + 12.0f;
				if (button == 0 && isInside(mouseX, mouseY, closeX, closeY, closeSize, closeSize)) {
					settingsModule = null;
					settingsAnim.setTarget(0.0f);
					openDropdown = null;
					return true;
				}

				// Settings rows
				if (handleSettingsClick(mouseX, mouseY, popupX, popupY, popupW, button)) {
					return true;
				}
				// Click anywhere else inside popup: just consume to avoid leaking to cards
				return true;
			}
		}

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

				Module module = visibleModules.get(i);
				if (button == 1) {
					// Right click: toggle settings popup
					if (settingsModule == module) {
						settingsModule = null;
						settingsAnim.setTarget(0.0f);
						openDropdown = null;
					} else if (!module.settings.isEmpty()) {
						settingsModule = module;
						settingsAnim.setImmediate(0.0f);
						settingsAnim.setTarget(1.0f);
						openDropdown = null;
					}
					return true;
				}
				if (button == 2) {
					// Middle click: enter bind-capture mode for this module.
					bindingModule = (bindingModule == module) ? null : module;
					return true;
				}
				// Left click: toggle module
				module.toggle();
				cardClickFlash.get(i).setImmediate(0.0f);
				cardClickFlash.get(i).setTarget(1.0f);
				return true;
			}
		}

		// Click outside everything: close settings popup
		if (settingsModule != null) {
			settingsModule = null;
			settingsAnim.setTarget(0.0f);
			openDropdown = null;
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean handleSettingsClick(double mouseX, double mouseY,
			float popupX, float popupY, float popupW, int button) {
		float contentX = popupX + POPUP_PAD_X;
		float contentW = popupW - POPUP_PAD_X * 2;
		float cy = popupY + POPUP_HEADER_H + 10.0f;

		// 0. If a dropdown is open, intercept clicks on its option list first.
		if (openDropdown != null && button == 0) {
			float listY = dropdownY + dropdownH + 2.0f;
			float rowH = 14.0f;
			float listH = openDropdown.modes.size() * rowH;
			if (isInside(mouseX, mouseY, dropdownX, listY, dropdownW, listH)) {
				int idx = (int) ((mouseY - listY) / rowH);
				if (idx >= 0 && idx < openDropdown.modes.size()) {
					openDropdown.set(openDropdown.modes.get(idx));
				}
				openDropdown = null;
				return true;
			}
			// Click on the selector itself = close the dropdown.
			if (isInside(mouseX, mouseY, dropdownX, dropdownY, dropdownW, dropdownH)) {
				openDropdown = null;
				return true;
			}
			// Click was outside both the selector and the option list:
			// close the dropdown but still let other handlers process the click.
			openDropdown = null;
		}

		for (Setting<?> s : settingsModule.settings) {
			float labelY = cy;
			float controlY = cy + 12.0f;

			if (s instanceof BooleanSetting bs) {
				float trackW = 22.0f;
				float trackH = 12.0f;
				float tx = contentX + contentW - trackW;
				float ty = labelY - 1.0f;
				if (isInside(mouseX, mouseY, tx, ty, trackW, trackH) && button == 0) {
					bs.toggle();
					return true;
				}
				cy = labelY + 14.0f + POPUP_ROW_GAP;

			} else if (s instanceof NumberSetting ns) {
				float trackY = controlY + 4.0f;
				float trackW = contentW;
				if (isInside(mouseX, mouseY, contentX, trackY - 4.0f, trackW, 14.0f) && button == 0) {
					float frac = (float) ((mouseX - contentX) / trackW);
					frac = Math.max(0.0f, Math.min(1.0f, frac));
					double v = ns.min + (ns.max - ns.min) * frac;
					double step = ns.step;
					if (step > 0.0) {
						v = Math.round(v / step) * step;
					}
					ns.set(v);
					draggingSlider = ns;
					return true;
				}
				cy = controlY + 22.0f + POPUP_ROW_GAP;

			} else if (s instanceof ModeSetting ms) {
				if (ms.expanded) {
					// Expanded radio list: click on a row to select it
					float rowH = 14.0f;
					float rowGap = 3.0f;
					float ry = controlY;
					for (String mode : ms.modes) {
						if (isInside(mouseX, mouseY, contentX, ry, contentW, rowH) && button == 0) {
							ms.set(mode);
							return true;
						}
						ry += rowH + rowGap;
					}
					cy = ry + POPUP_ROW_GAP;
				} else {
					float h = 16.0f;
					if (isInside(mouseX, mouseY, contentX, controlY, contentW, h) && button == 0) {
						// Toggle the dropdown for this setting.
						openDropdown = (openDropdown == ms) ? null : ms;
						return true;
					}
					cy = controlY + h + 2.0f + POPUP_ROW_GAP;
				}

			} else if (s instanceof MultiSetting mu) {
				float rowH = 14.0f;
				float rowGap = 3.0f;
				float ry = controlY;
				for (String opt : mu.options) {
					if (isInside(mouseX, mouseY, contentX, ry, contentW, rowH) && button == 0) {
						mu.toggle(opt);
						return true;
					}
					ry += rowH + rowGap;
				}
				cy = ry + POPUP_ROW_GAP;

			} else {
				cy = controlY + 14.0f + POPUP_ROW_GAP;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingSlider != null && button == 0 && settingsModule != null) {
			float panelX = (this.width  - PANEL_W) * 0.5f;
			float popupX = panelX + PANEL_W + POPUP_GAP;
			float contentX = popupX + POPUP_PAD_X;
			float trackW = POPUP_W - POPUP_PAD_X * 2;
			float frac = (float) ((mouseX - contentX) / trackW);
			frac = Math.max(0.0f, Math.min(1.0f, frac));
			NumberSetting ns = draggingSlider;
			double v = ns.min + (ns.max - ns.min) * frac;
			if (ns.step > 0.0) {
				v = Math.round(v / ns.step) * ns.step;
			}
			ns.set(v);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			draggingSlider = null;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget - (float) verticalAmount * 28.0f));
		scroll.setTarget(scrollTarget);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Bind capture takes priority over everything else.
		if (bindingModule != null) {
			if (keyCode == 256 /* ESC */) {
				// ESC during capture = clear the bind.
				bindingModule.keybind = -1;
			} else {
				bindingModule.keybind = keyCode;
			}
			bindingModule = null;
			return true;
		}

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
		if (keyCode == 256 /* ESC */ && settingsModule != null) {
			if (openDropdown != null) {
				openDropdown = null;
				return true;
			}
			settingsModule = null;
			settingsAnim.setTarget(0.0f);
			return true;
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

	private static boolean isInside(double mouseX, double mouseY, float x, float y, float w, float h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

}
