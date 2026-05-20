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
import dev.blumdlc.client.ui.components.SettingsPopup;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.KeyName;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Celestial-style ClickGUI rendered exclusively through the project's Builder API.
 *
 * <h2>Layout</h2>
 * <pre>
 *   ┌──────────┬─────────────────────────┬──────────────┐
 *   │ Sidebar  │  Search + card grid     │ Settings     │
 *   │          │                         │ popup        │
 *   └──────────┴─────────────────────────┴──────────────┘
 * </pre>
 *
 * <h2>Architecture</h2>
 * Each region is owned by a focused unit:
 * <ul>
 *   <li>{@link SettingsPopup} — right-hand settings panel (slider, colour
 *       picker, dropdowns, scrolling). Owns its own animation and input
 *       state; this screen just forwards events to it.</li>
 *   <li>This class — panel chrome, sidebar, search bar, card grid and
 *       global input dispatch (bind-capture, ESC, etc.).</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * Mouse / keyboard events are dispatched to the popup first, falling through
 * to the panel only if the popup didn't consume them. This makes opening,
 * editing and closing the popup feel snappy without sneaking inputs through
 * to the cards underneath.
 */
public final class ClickGuiScreen extends Screen {

	// =========================================================================
	//  Geometry
	// =========================================================================

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

	// =========================================================================
	//  Animations
	// =========================================================================

	private final Animation openAnim   = new Animation(0.0f, 360, Easing.EASE_OUT_QUINT);
	private final Animation slideAnim  = new Animation(40.0f, 420, Easing.EASE_OUT_EXPO);

	// =========================================================================
	//  Sidebar state
	// =========================================================================

	private Category selectedCategory = Category.COMBAT;
	private final Animation selectorY      = new Animation(0.0f,  320, Easing.EASE_OUT_EXPO);
	private final Animation selectorHeight = new Animation(24.0f, 320, Easing.EASE_OUT_EXPO);
	private final Animation[] categoryHover = new Animation[Category.values().length];
	private final Animation quitHover      = new Animation(0.0f, 180, Easing.EASE_OUT_CUBIC);

	// =========================================================================
	//  Search bar
	// =========================================================================

	private String searchText = "";
	private final Animation searchFocus = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
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
	private final List<Animation> cardClickFlash = new ArrayList<>();

	private final Animation scroll = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
	private float scrollTarget = 0.0f;
	private float maxScroll = 0.0f;

	// =========================================================================
	//  Settings popup (own its own state)
	// =========================================================================

	private final SettingsPopup popup = new SettingsPopup();

	// =========================================================================
	//  Bind capture state
	// =========================================================================

	/** Module currently waiting for a key press to set its bind (null == none). */
	private Module bindingModule = null;

	// =========================================================================
	//  Lifecycle
	// =========================================================================

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

		// If the popup's module just left the visible list, detach it.
		Module popupModule = popup.getModule();
		if (popupModule != null && !visibleModules.contains(popupModule)) {
			popup.detach();
		}

		int size = visibleModules.size();

		// Reuse existing animation lists — only add/remove what's necessary
		// to match the new size. Avoids GC churn on FoldCraft Launcher.
		resizeAnimList(cardHover, size, () -> new Animation(0.0f, 160, Easing.EASE_OUT_CUBIC));
		resizeAnimList(cardClickFlash, size, () -> new Animation(0.0f, 280, Easing.EASE_OUT_CUBIC));

		// Enter and active anims need special handling (state-dependent)
		while (cardEnter.size() > size) cardEnter.remove(cardEnter.size() - 1);
		while (cardActive.size() > size) cardActive.remove(cardActive.size() - 1);

		for (int i = 0; i < size; i++) {
			if (i < cardEnter.size()) {
				// Existing slot — just re-trigger if staggering
				if (stagger) {
					cardEnter.get(i).setImmediate(0.0f);
					cardEnter.get(i).setTarget(1.0f, 25L * i);
				}
			} else {
				Animation enter = new Animation(0.0f, 380, Easing.EASE_OUT_QUINT);
				if (stagger) {
					enter.setTarget(1.0f, 25L * i);
				} else {
					enter.setImmediate(1.0f);
				}
				cardEnter.add(enter);
			}

			if (i < cardActive.size()) {
				cardActive.get(i).setTarget(visibleModules.get(i).enabled ? 1.0f : 0.0f);
			} else {
				Animation activeAnim = new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC);
				activeAnim.setImmediate(visibleModules.get(i).enabled ? 1.0f : 0.0f);
				cardActive.add(activeAnim);
			}
		}

		clampScroll();
	}

	/** Resizes an animation list to the desired size, reusing existing objects. */
	private static void resizeAnimList(List<Animation> list, int size,
			java.util.function.Supplier<Animation> factory) {
		while (list.size() > size) list.remove(list.size() - 1);
		while (list.size() < size) list.add(factory.get());
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
		popup.close();
		super.close();
	}

	// =========================================================================
	//  Render
	// =========================================================================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		MsdfFont font = Fonts.BIKO.get();

		float open = openAnim.getValue();
		float slide = slideAnim.getValue();

		// Dim
		UIRender.rect(matrix, 0, 0, this.width, this.height, 0,
			ColorUtil.multiplyAlpha(Theme.DIM, open));

		// Panel rect (with subtle scale-in)
		float panelX = (this.width  - PANEL_W) * 0.5f;
		float panelY = (this.height - PANEL_H) * 0.5f + slide;
		float scale = 0.96f + 0.04f * open;
		float scaledW = PANEL_W * scale;
		float scaledH = PANEL_H * scale;
		panelX += (PANEL_W - scaledW) * 0.5f;
		panelY += (PANEL_H - scaledH) * 0.5f;

		drawPanel(matrix, font, panelX, panelY, scaledW, scaledH, open, mouseX, mouseY);

		// Settings popup hangs to the right of the panel
		popup.render(matrix, font, panelX, panelY, scaledW, scaledH, open, mouseX, mouseY);

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

		// Selector pill
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
	//  Main: search + card grid
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

		Module popupModule = popup.getModule();
		for (int i = 0; i < visibleModules.size(); i++) {
			int row = i / CARDS_PER_ROW;
			int col = i % CARDS_PER_ROW;
			float cx = originX + col * (CARD_W + CARD_GAP_X);
			float cy = originY + row * (CARD_H + CARD_GAP_Y);

			if (cy + CARD_H < clipTop - 30.0f || cy > clipBottom + 30.0f) continue;

			drawCard(matrix, font, i, popupModule, cx, cy, clipTop, clipBottom, open, mouseX, mouseY);
		}

		// Right-edge card scrollbar
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

	private void drawCard(Matrix4f matrix, MsdfFont font, int i, Module popupModule,
			float cx, float cy, float clipTop, float clipBottom,
			float open, int mouseX, int mouseY) {

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

		// Background
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

		// Click ring flash
		if (flashT > 0.001f && flashT < 0.999f) {
			float ringAlpha = (1.0f - flashT) * 0.6f * cardAlpha;
			UIRender.border(matrix, cardX - flashT * 2.0f, cy - flashT * 2.0f,
				CARD_W + flashT * 4.0f, CARD_H + flashT * 4.0f,
				10.0f + flashT * 4.0f, 1.5f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, ringAlpha));
		}

		// Border (highlighted when popup is showing this card)
		if (popupModule == module) {
			UIRender.border(matrix, cardX, cy, CARD_W, CARD_H, 10.0f, 1.5f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, 0.9f * cardAlpha));
		} else {
			UIRender.border(matrix, cardX, cy, CARD_W, CARD_H, 10.0f, 1.0f,
				ColorUtil.multiplyAlpha(Theme.CARD_BORDER, 0.6f * (1.0f - activeT) * cardAlpha));
		}

		// Title + description
		int titleColor = ColorUtil.lerp(Theme.TEXT_PRIMARY, 0xFFFFFFFF, activeT);
		UIRender.text(matrix, font, module.name, cardX + 9.0f, cy + 8.0f, 8.5f,
			ColorUtil.multiplyAlpha(titleColor, cardAlpha), 0.05f);
		int descColor = ColorUtil.lerp(Theme.TEXT_SECONDARY, 0xFFE9D2F2, activeT);
		UIRender.text(matrix, font, module.description, cardX + 9.0f, cy + 22.0f, 6.0f,
			ColorUtil.multiplyAlpha(descColor, cardAlpha), 0.04f);

		// "..." hint when the card has settings
		if (!module.settings.isEmpty()) {
			float ix = cardX + CARD_W - 14.0f;
			float iy = cy + 8.0f;
			int dot = ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.55f * cardAlpha);
			UIRender.rect(matrix, ix,        iy, 2.0f, 2.0f, 1.0f, dot);
			UIRender.rect(matrix, ix + 4.0f, iy, 2.0f, 2.0f, 1.0f, dot);
			UIRender.rect(matrix, ix + 8.0f, iy, 2.0f, 2.0f, 1.0f, dot);
		}

		// Bind chip in the bottom-right of the card
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

	// =========================================================================
	//  Input
	// =========================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float panelX = (this.width  - PANEL_W) * 0.5f;
		float panelY = (this.height - PANEL_H) * 0.5f;

		// Popup gets first dibs on input.
		if (popup.mouseClicked(mouseX, mouseY, panelX, panelY, PANEL_W, PANEL_H, button)) {
			return true;
		}

		// Sidebar
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
		float cardClipTop = panelY + CARD_AREA_PAD_TOP - 4.0f;
		float cardClipBottom = panelY + PANEL_H - CARD_AREA_PAD_BOTTOM;
		for (int i = 0; i < visibleModules.size(); i++) {
			int row = i / CARDS_PER_ROW;
			int col = i % CARDS_PER_ROW;
			float cx = originX + col * (CARD_W + CARD_GAP_X);
			float cy = originY + row * (CARD_H + CARD_GAP_Y);
			if (!isInside(mouseX, mouseY, cx, cy, CARD_W, CARD_H)) continue;
			if (mouseY < cardClipTop || mouseY > cardClipBottom) continue;

			Module module = visibleModules.get(i);
			if (button == 1) {
				// Right click: toggle settings popup
				popup.toggle(module);
				return true;
			}
			if (button == 2) {
				// Middle click: enter / cancel bind capture
				bindingModule = (bindingModule == module) ? null : module;
				return true;
			}
			// Left click: toggle module
			module.toggle();
			cardClickFlash.get(i).setImmediate(0.0f);
			cardClickFlash.get(i).setTarget(1.0f);
			return true;
		}

		// Click outside everything: close any open popup
		popup.close();
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		float panelX = (this.width  - PANEL_W) * 0.5f;
		if (popup.mouseDragged(mouseX, mouseY, button, panelX, PANEL_W)) {
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		popup.mouseReleased(button);
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY,
			double horizontalAmount, double verticalAmount) {
		float panelX = (this.width  - PANEL_W) * 0.5f;
		float panelY = (this.height - PANEL_H) * 0.5f;
		if (popup.mouseScrolled(mouseX, mouseY, verticalAmount, panelX, panelY, PANEL_W, PANEL_H)) {
			return true;
		}
		scrollTarget = Math.max(0.0f, Math.min(maxScroll, scrollTarget - (float) verticalAmount * 28.0f));
		scroll.setTarget(scrollTarget);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Bind capture takes priority over everything else.
		if (bindingModule != null) {
			if (keyCode == 256 /* ESC */) {
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

		// Popup may close itself / its dropdown on ESC
		if (popup.keyPressed(keyCode)) {
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

	private static boolean isInside(double mouseX, double mouseY,
			float x, float y, float w, float h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}
}
