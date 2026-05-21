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

/**
 * Recoded ClickGUI — five free-floating category panels arranged side-by-side
 * (Combat / Movement / Visuals / Player / Miscellaneous) over a dim backdrop.
 *
 * <p>Each column owns its own header, scroll state and per-row hover / active
 * animations. Modules with settings expose a small "..." affordance and open
 * the shared {@link SettingsPopup} on click; right-click anywhere on a row
 * opens the popup directly, middle-click captures a keybind.
 *
 * <p>The layout matches the reference screenshot: dark, slightly translucent
 * column bodies with rounded corners, a centred title, an inline search bar
 * below the columns and a soft purple gradient highlight on enabled modules.
 */
public final class ClickGuiScreen extends Screen {

	// =========================================================================
	//  Geometry
	// =========================================================================

	private static final float COL_W       = 168.0f;
	private static final float COL_H       = 510.0f;
	private static final float COL_GAP     = 6.0f;

	private static final float HEADER_H    = 38.0f;
	private static final float ROW_H       = 26.0f;
	private static final float ROW_GAP     = 1.0f;
	private static final float ROW_PAD_X   = 4.0f;
	private static final float CONTENT_PAD = 6.0f;

	private static final float SEARCH_W    = 220.0f;
	private static final float SEARCH_H    = 22.0f;
	private static final float SEARCH_GAP  = 10.0f;

	// =========================================================================
	//  Columns
	// =========================================================================

	private static final ColumnDef[] COLUMNS = {
		new ColumnDef("Combat",        new Category[] { Category.COMBAT }),
		new ColumnDef("Movement",      new Category[] { Category.MOVEMENT }),
		new ColumnDef("Visuals",       new Category[] { Category.RENDER }),
		new ColumnDef("Player",        new Category[] { Category.PLAYER }),
		new ColumnDef("Miscellaneous", new Category[] { Category.UTIL, Category.THEMES }),
	};

	private record ColumnDef(String title, Category[] cats) { }

	// =========================================================================
	//  Animations
	// =========================================================================

	private final Animation openAnim  = new Animation(0.0f, 320, Easing.EASE_OUT_QUINT);
	private final Animation slideAnim = new Animation(20.0f, 380, Easing.EASE_OUT_EXPO);

	// One module list + scroll state per column
	private final List<List<Module>> columnModules = new ArrayList<>();
	private final float[]            scrollTarget  = new float[COLUMNS.length];
	private final float[]            maxScroll     = new float[COLUMNS.length];
	private final Animation[]        scroll        = new Animation[COLUMNS.length];

	// Per-row animations (parallel structure to columnModules)
	private final List<List<Animation>> hoverAnim  = new ArrayList<>();
	private final List<List<Animation>> activeAnim = new ArrayList<>();
	private final List<List<Animation>> enterAnim  = new ArrayList<>();
	private final List<List<Animation>> flashAnim  = new ArrayList<>();

	// =========================================================================
	//  Search
	// =========================================================================

	private String   searchText   = "";
	private boolean  searchActive = false;
	private final Animation searchFocus = new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC);
	private long     lastBlinkSwap = System.currentTimeMillis();
	private boolean  caretVisible  = true;

	// =========================================================================
	//  Settings popup + bind capture
	// =========================================================================

	private final SettingsPopup popup = new SettingsPopup();
	private Module bindingModule;

	// =========================================================================
	//  Lifecycle
	// =========================================================================

	public ClickGuiScreen() {
		super(Text.literal("Blum"));
		for (int i = 0; i < COLUMNS.length; i++) {
			columnModules.add(new ArrayList<>());
			hoverAnim .add(new ArrayList<>());
			activeAnim.add(new ArrayList<>());
			enterAnim .add(new ArrayList<>());
			flashAnim .add(new ArrayList<>());
			scroll[i] = new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC);
		}
	}

	@Override
	protected void init() {
		super.init();
		openAnim.setTarget(1.0f);
		slideAnim.setTarget(0.0f);
		rebuildColumns(true);
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

	private void rebuildColumns(boolean stagger) {
		String low = searchText.toLowerCase();
		for (int col = 0; col < COLUMNS.length; col++) {
			List<Module> list = new ArrayList<>();
			for (Category c : COLUMNS[col].cats()) {
				for (Module m : BlumDLC.MODULES.byCategory(c)) {
					if (low.isEmpty() || m.name.toLowerCase().contains(low)) {
						list.add(m);
					}
				}
			}
			list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
			columnModules.set(col, list);

			int n = list.size();
			sync(hoverAnim .get(col), n, () -> new Animation(0.0f, 140, Easing.EASE_OUT_CUBIC));
			sync(activeAnim.get(col), n, () -> new Animation(0.0f, 220, Easing.EASE_OUT_CUBIC));
			sync(enterAnim .get(col), n, () -> new Animation(0.0f, 320, Easing.EASE_OUT_QUINT));
			sync(flashAnim .get(col), n, () -> new Animation(0.0f, 260, Easing.EASE_OUT_CUBIC));

			for (int i = 0; i < n; i++) {
				activeAnim.get(col).get(i).setTarget(list.get(i).enabled ? 1.0f : 0.0f);
				if (stagger) {
					Animation e = enterAnim.get(col).get(i);
					e.setImmediate(0.0f);
					e.setTarget(1.0f, 14L * i + 30L * col);
				} else {
					enterAnim.get(col).get(i).setImmediate(1.0f);
				}
			}
		}

		// Drop the popup if its module disappeared (e.g. filtered out).
		Module popMod = popup.getModule();
		if (popMod != null) {
			boolean still = false;
			for (List<Module> l : columnModules) {
				if (l.contains(popMod)) {
					still = true;
					break;
				}
			}
			if (!still) {
				popup.detach();
			}
		}

		clampScrolls();
	}

	private static void sync(List<Animation> list, int n, Supplier<Animation> factory) {
		while (list.size() > n) list.remove(list.size() - 1);
		while (list.size() < n) list.add(factory.get());
	}

	private void clampScrolls() {
		float listH = listHeight();
		for (int i = 0; i < COLUMNS.length; i++) {
			int n = columnModules.get(i).size();
			float content = n == 0 ? 0.0f : n * (ROW_H + ROW_GAP) - ROW_GAP;
			maxScroll[i] = Math.max(0.0f, content - listH);
			scrollTarget[i] = Math.max(0.0f, Math.min(scrollTarget[i], maxScroll[i]));
			scroll[i].setTarget(scrollTarget[i]);
		}
	}

	private static float listHeight() {
		return COL_H - HEADER_H - CONTENT_PAD * 2.0f;
	}

	private static float layoutWidth() {
		return COL_W * COLUMNS.length + COL_GAP * (COLUMNS.length - 1);
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

		// Dim backdrop
		UIRender.rect(m, 0.0f, 0.0f, this.width, this.height, 0.0f,
			ColorUtil.multiplyAlpha(Theme.DIM, open));

		float lw = layoutWidth();
		float lx = (this.width - lw) * 0.5f;
		float ly = (this.height - COL_H) * 0.5f - 12.0f + slide;

		drawColumns(context, m, font, lx, ly, open, mouseX, mouseY);
		drawSearch(m, font, lx, ly + COL_H + SEARCH_GAP, lw, open, mouseX, mouseY);

		// Settings popup is anchored to the right edge of the layout.
		popup.render(m, font, lx, ly, lw, COL_H, open, mouseX, mouseY);

		super.render(context, mouseX, mouseY, delta);
	}

	// ---------------------------------------------------------------------
	//  Columns
	// ---------------------------------------------------------------------

	private void drawColumns(DrawContext ctx, Matrix4f m, MsdfFont font,
			float lx, float ly, float open, int mouseX, int mouseY) {
		for (int i = 0; i < COLUMNS.length; i++) {
			float cx = lx + i * (COL_W + COL_GAP);
			drawColumn(ctx, m, font, i, cx, ly, open, mouseX, mouseY);
		}
	}

	private void drawColumn(DrawContext ctx, Matrix4f m, MsdfFont font, int idx,
			float cx, float cy, float open, int mouseX, int mouseY) {

		// Body — soft blur so the world below stays slightly visible, with a
		// gentle top-to-bottom gradient on top of it.
		UIRender.blur(m, cx, cy, COL_W, COL_H, 10.0f, 8.0f, 0xFF000000);
		UIRender.rectGradientV(m, cx, cy, COL_W, COL_H, 10.0f,
			ColorUtil.multiplyAlpha(0xE61A1620, open),
			ColorUtil.multiplyAlpha(0xE6121017, open));
		UIRender.border(m, cx, cy, COL_W, COL_H, 10.0f, 1.0f,
			ColorUtil.multiplyAlpha(0x22FFFFFF, open));

		// Header — centred title
		ColumnDef def = COLUMNS[idx];
		float titleSize = 11.0f;
		float titleW = UIRender.textWidth(font, def.title(), titleSize);
		UIRender.text(m, font, def.title(),
			cx + (COL_W - titleW) * 0.5f, cy + 13.0f, titleSize,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, open), 0.06f);

		// Header divider
		UIRender.rect(m, cx + 12.0f, cy + HEADER_H - 1.0f, COL_W - 24.0f, 1.0f, 0.0f,
			ColorUtil.multiplyAlpha(Theme.DIVIDER, open));

		// Module list — scissor-clipped so rows don't bleed out of the column
		float listX = cx;
		float listY = cy + HEADER_H + CONTENT_PAD;
		float listH = listHeight();

		ctx.enableScissor(
			(int) Math.floor(listX),
			(int) Math.floor(listY),
			(int) Math.ceil(listX + COL_W),
			(int) Math.ceil(listY + listH));

		float scrollOff = scroll[idx].getValue();
		List<Module> mods = columnModules.get(idx);
		float ry = listY - scrollOff;

		for (int i = 0; i < mods.size(); i++) {
			drawRow(m, font, idx, i, cx + ROW_PAD_X, ry, COL_W - ROW_PAD_X * 2.0f,
				open, mouseX, mouseY, listY, listY + listH);
			ry += ROW_H + ROW_GAP;
		}
		ctx.disableScissor();

		// Per-column scrollbar
		if (maxScroll[idx] > 0.5f) {
			float trackX = cx + COL_W - 4.5f;
			float trackY = listY;
			float trackH = listH;
			float thumbH = Math.max(18.0f, trackH * (trackH / (trackH + maxScroll[idx])));
			float thumbY = trackY + (scrollOff / maxScroll[idx]) * (trackH - thumbH);

			UIRender.rect(m, trackX, trackY, 2.0f, trackH, 1.0f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_BG, 0.7f * open));
			UIRender.rect(m, trackX, thumbY, 2.0f, thumbH, 1.0f,
				ColorUtil.multiplyAlpha(Theme.SCROLLBAR_FG, open));
		}
	}

	private void drawRow(Matrix4f m, MsdfFont font, int col, int idx,
			float rx, float ry, float rw, float open, int mouseX, int mouseY,
			float clipTop, float clipBot) {

		// Skip if completely out of view
		if (ry + ROW_H < clipTop - 4.0f || ry > clipBot + 4.0f) return;

		Module mod = columnModules.get(col).get(idx);

		float enterT = enterAnim.get(col).get(idx).getValue();
		float rxOff  = rx + (1.0f - enterT) * 8.0f;
		float alpha  = enterT * open;

		boolean hov = mouseX >= rxOff && mouseX < rxOff + rw
			&& mouseY >= ry  && mouseY < ry + ROW_H
			&& mouseY >= clipTop && mouseY <= clipBot;
		hoverAnim .get(col).get(idx).setTarget(hov ? 1.0f : 0.0f);
		activeAnim.get(col).get(idx).setTarget(mod.enabled ? 1.0f : 0.0f);

		float hovT = hoverAnim .get(col).get(idx).getValue();
		float actT = activeAnim.get(col).get(idx).getValue();
		float flT  = flashAnim .get(col).get(idx).getValue();

		// Hover background (only when not active)
		if (hovT > 0.001f && actT < 0.999f) {
			UIRender.rect(m, rxOff, ry, rw, ROW_H, 6.0f,
				ColorUtil.multiplyAlpha(0xFFFFFFFF, 0.06f * hovT * (1.0f - actT) * alpha));
		}

		// Active gradient — soft purple highlight matching the screenshot
		if (actT > 0.001f) {
			UIRender.rectGradientH(m, rxOff, ry, rw, ROW_H, 6.0f,
				ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_FROM, 0.55f * actT * alpha),
				ColorUtil.multiplyAlpha(Theme.CARD_ACTIVE_TO,   0.55f * actT * alpha));
		}

		// Click flash
		if (flT > 0.001f && flT < 0.999f) {
			UIRender.border(m, rxOff - flT, ry - flT, rw + flT * 2.0f, ROW_H + flT * 2.0f,
				6.0f + flT * 2.0f, 1.2f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, (1.0f - flT) * 0.8f * alpha));
		}

		// Highlight when this row is the popup target
		Module popMod = popup.getModule();
		if (popMod == mod) {
			UIRender.border(m, rxOff, ry, rw, ROW_H, 6.0f, 1.0f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, 0.7f * alpha));
		}

		// --- Right side: bind chip / settings dots ---
		float rightX = rxOff + rw - 6.0f;

		boolean awaiting = (bindingModule == mod);
		if (awaiting || KeyName.isBound(mod.keybind)) {
			String label = awaiting ? "..." : KeyName.describe(mod.keybind);
			float fs = 5.5f;
			float lw = UIRender.textWidth(font, label, fs) + 6.0f;
			float lh = fs + 4.0f;
			float lx = rightX - lw;
			float ly = ry + (ROW_H - lh) * 0.5f;
			int chipBg = awaiting ? Theme.ACCENT : 0x66000000;
			UIRender.rect(m, lx, ly, lw, lh, lh * 0.5f,
				ColorUtil.multiplyAlpha(chipBg, alpha));
			int chipColor = awaiting ? 0xFF0E1018 : Theme.TEXT_PRIMARY;
			UIRender.text(m, font, label, lx + 3.0f, ly + 2.0f, fs,
				ColorUtil.multiplyAlpha(chipColor, alpha), 0.05f);
			rightX = lx - 4.0f;
		}

		// "..." dots — only for modules that have settings
		if (!mod.settings.isEmpty()) {
			float dotR = 1.5f;
			float dotGap = 2.0f;
			float dotsW = dotR * 6.0f + dotGap * 2.0f;
			float dotsX = rightX - dotsW;
			float dotsY = ry + (ROW_H - dotR * 2.0f) * 0.5f;
			int dotColor = ColorUtil.multiplyAlpha(0xFFFFFFFF,
				(0.45f + 0.45f * Math.max(hovT, actT)) * alpha);
			for (int d = 0; d < 3; d++) {
				float dx = dotsX + d * (dotR * 2.0f + dotGap);
				UIRender.rect(m, dx, dotsY, dotR * 2.0f, dotR * 2.0f, dotR, dotColor);
			}
			rightX = dotsX - 4.0f;
		}

		// Module name — left-aligned, vertically centred
		int nameColor = ColorUtil.lerp(Theme.TEXT_SECONDARY, 0xFFFFFFFF, actT * 0.85f + hovT * 0.15f);
		float nameSize = 8.0f;
		float maxNameW = rightX - rxOff - 10.0f;
		String name = ellipsize(font, mod.name, nameSize, maxNameW);
		UIRender.text(m, font, name, rxOff + 10.0f, ry + (ROW_H - nameSize) * 0.5f - 0.5f,
			nameSize, ColorUtil.multiplyAlpha(nameColor, alpha), 0.05f);
	}

	private static String ellipsize(MsdfFont font, String s, float size, float maxW) {
		if (maxW <= 0.0f) return "";
		if (UIRender.textWidth(font, s, size) <= maxW) return s;
		String dots = "...";
		float dw = UIRender.textWidth(font, dots, size);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			sb.append(s.charAt(i));
			if (UIRender.textWidth(font, sb.toString(), size) + dw > maxW) {
				if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
				break;
			}
		}
		return sb.append(dots).toString();
	}

	// ---------------------------------------------------------------------
	//  Search bar
	// ---------------------------------------------------------------------

	private void drawSearch(Matrix4f m, MsdfFont font,
			float lx, float sy, float layoutW, float open, int mouseX, int mouseY) {
		float sw = SEARCH_W;
		float sh = SEARCH_H;
		float sx = lx + (layoutW - sw) * 0.5f;

		float focus = searchFocus.getValue();
		int   border = ColorUtil.lerp(0x22FFFFFF, Theme.ACCENT, focus);

		UIRender.rect(m, sx, sy, sw, sh, 7.0f,
			ColorUtil.multiplyAlpha(0xCC141019, open));
		UIRender.border(m, sx, sy, sw, sh, 7.0f, 1.0f,
			ColorUtil.multiplyAlpha(border, open));

		String disp = searchText.isEmpty() ? "Search..." : searchText;
		int dispColor = searchText.isEmpty() ? Theme.TEXT_MUTED : Theme.TEXT_PRIMARY;
		UIRender.text(m, font, disp, sx + 9.0f, sy + (sh - 7.5f) * 0.5f - 0.5f, 7.5f,
			ColorUtil.multiplyAlpha(dispColor, open));

		// Caret
		long now = System.currentTimeMillis();
		if (now - lastBlinkSwap > 520L) {
			lastBlinkSwap = now;
			caretVisible = !caretVisible;
		}
		if (searchActive && caretVisible) {
			float cx = sx + 9.0f + UIRender.textWidth(font, searchText, 7.5f) + 1.0f;
			UIRender.rect(m, cx, sy + 4.0f, 1.0f, sh - 8.0f, 0.5f,
				ColorUtil.multiplyAlpha(Theme.ACCENT, open));
		}
	}

	// =========================================================================
	//  Input
	// =========================================================================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		float lw = layoutWidth();
		float lx = (this.width - lw) * 0.5f;
		float ly = (this.height - COL_H) * 0.5f - 12.0f;

		// Settings popup eats first
		if (popup.mouseClicked(mouseX, mouseY, lx, ly, lw, COL_H, button)) {
			return true;
		}

		// Search bar
		float sw = SEARCH_W;
		float sh = SEARCH_H;
		float sx = lx + (lw - sw) * 0.5f;
		float sy = ly + COL_H + SEARCH_GAP;
		boolean inSearch = inside(mouseX, mouseY, sx, sy, sw, sh);
		searchActive = inSearch;
		searchFocus.setTarget(inSearch ? 1.0f : 0.0f);
		if (inSearch) {
			return true;
		}

		// Columns / rows
		float listY = ly + HEADER_H + CONTENT_PAD;
		float listH = listHeight();
		for (int col = 0; col < COLUMNS.length; col++) {
			float cx = lx + col * (COL_W + COL_GAP);
			if (mouseX < cx || mouseX >= cx + COL_W) continue;
			if (mouseY < ly || mouseY >= ly + COL_H) continue;

			// Inside the row strip?
			if (mouseY < listY || mouseY > listY + listH) {
				return true; // clicked the column chrome — consume but no-op
			}

			float rx = cx + ROW_PAD_X;
			float rw = COL_W - ROW_PAD_X * 2.0f;
			float scrollOff = scroll[col].getValue();
			float ry = listY - scrollOff;
			List<Module> mods = columnModules.get(col);
			for (int i = 0; i < mods.size(); i++) {
				if (mouseX >= rx && mouseX < rx + rw
				 && mouseY >= ry && mouseY < ry + ROW_H) {
					Module mod = mods.get(i);
					if (button == 1) {
						popup.toggle(mod);
						return true;
					}
					if (button == 2) {
						bindingModule = (bindingModule == mod) ? null : mod;
						return true;
					}
					// Left-click on the dots → open popup, anywhere else → toggle
					if (clickedDots(mouseX, rx, rw, mod)) {
						popup.toggle(mod);
						return true;
					}
					mod.toggle();
					Animation flash = flashAnim.get(col).get(i);
					flash.setImmediate(0.0f);
					flash.setTarget(1.0f);
					return true;
				}
				ry += ROW_H + ROW_GAP;
			}
			return true;
		}

		// Outside everything — close any open popup
		popup.close();
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clickedDots(double mx, float rx, float rw, Module mod) {
		if (mod.settings.isEmpty()) return false;
		float dotR = 1.5f;
		float dotGap = 2.0f;
		float dotsW = dotR * 6.0f + dotGap * 2.0f;
		float dotsX = rx + rw - 6.0f - dotsW;
		// Accept clicks in a slightly larger hit area so the tiny dots are easy to tap.
		return mx >= dotsX - 4.0f && mx <= dotsX + dotsW + 4.0f;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		float lw = layoutWidth();
		float lx = (this.width - lw) * 0.5f;
		if (popup.mouseDragged(mx, my, button, lx, lw)) {
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
		float lw = layoutWidth();
		float lx = (this.width - lw) * 0.5f;
		float ly = (this.height - COL_H) * 0.5f - 12.0f;

		if (popup.mouseScrolled(mx, my, vert, lx, ly, lw, COL_H)) {
			return true;
		}

		// Find which column the cursor is in and scroll that one
		for (int col = 0; col < COLUMNS.length; col++) {
			float cx = lx + col * (COL_W + COL_GAP);
			if (mx >= cx && mx < cx + COL_W && my >= ly && my < ly + COL_H) {
				scrollTarget[col] = Math.max(0.0f,
					Math.min(maxScroll[col], scrollTarget[col] - (float) vert * 22.0f));
				scroll[col].setTarget(scrollTarget[col]);
				return true;
			}
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
			if (keyCode == 256 || keyCode == 257) {
				// ESC / ENTER — leave the search field (don't close the GUI)
				searchActive = false;
				searchFocus.setTarget(0.0f);
				return true;
			}
			if (keyCode == 259 /* BACKSPACE */ && !searchText.isEmpty()) {
				searchText = searchText.substring(0, searchText.length() - 1);
				rebuildColumns(false);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchActive && chr >= 32 && chr != 127) {
			searchText += chr;
			rebuildColumns(false);
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
}
