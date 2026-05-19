package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.KeyName;

/**
 * Right-side panel listing every module that has a keybind, with its current
 * key on the right. Mirrors the typical Minced/Celestial keybinds list.
 *
 * <p>Each row is its own pill so it visually pops away from the world. The
 * panel is movable via the {@code HudEditor} (open chat to drag).
 */
public final class KeybindsHud extends HudModule {

	public final NumberSetting fontSize;

	/** Most recent rendered bounds, used for hit-testing in the HUD editor. */
	private float lastWidth = 80.0f;
	private float lastHeight = 30.0f;

	public KeybindsHud() {
		super("Keybinds", "Lists modules with keybinds");
		this.fontSize = new NumberSetting("Font Size", 8.5, 6.0, 12.0, 0.5);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override
	public float hudWidth() {
		return lastWidth;
	}

	@Override
	public float hudHeight() {
		return lastHeight;
	}

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		// Default to top-right with a 6 px margin (matches the historical layout).
		this.x = Math.max(0.0f, sw - lastWidth - 6.0f);
		this.y = 6.0f;
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();

		List<Module> bound = new ArrayList<>();
		for (Module m : BlumDLC.MODULES.all()) {
			if (KeyName.isBound(m.keybind) && m != this) {
				bound.add(m);
			}
		}
		bound.sort(Comparator.comparing((Module m) -> m.name));

		float fs = fontSize.getFloat();
		float padX = 8.0f;
		float rowH = fs + 8.0f;
		float gap  = 4.0f;
		float titleFs = 9.0f;
		float titleH = titleFs + 8.0f;

		// Pre-compute the widest row so all pills line up.
		float maxRowW = UIRender.textWidth(font, "Keybinds", titleFs);
		for (Module m : bound) {
			float lw = UIRender.textWidth(font, m.name, fs);
			float kw = UIRender.textWidth(font, "[" + KeyName.describe(m.keybind) + "]", fs * 0.9f);
			maxRowW = Math.max(maxRowW, lw + 12.0f + kw);
		}
		float w = maxRowW + padX * 2;
		float rowsH = bound.isEmpty()
			? 0.0f
			: bound.size() * rowH + (bound.size() - 1) * gap;
		float h = titleH + (rowsH > 0.0f ? rowsH + 4.0f : 0.0f);

		// In edit mode, ensure there's always a meaningful hit-box even when no binds.
		if (editing && bound.isEmpty()) {
			h = titleH + 4.0f + rowH; // pretend one ghost row exists
		}

		this.lastWidth = w;
		this.lastHeight = h;

		float x = this.x;
		float y = this.y;

		drawTitle(matrix, font, x, y, w, titleH, titleFs);

		float ry = y + titleH + 4.0f;
		if (bound.isEmpty()) {
			if (editing) {
				drawGhostRow(matrix, font, x, ry, w, rowH, fs);
			}
			return;
		}
		for (Module m : bound) {
			drawRow(matrix, font, m, x, ry, w, rowH, fs);
			ry += rowH + gap;
		}
	}

	private void drawTitle(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float fs) {
		// Soft drop shadow.
		UIRender.rect(matrix, x + 1.0f, y + 2.0f, w, h, h * 0.5f, 0x44000000);
		UIRender.rect(matrix, x, y, w, h, h * 0.5f,
			ColorUtil.withAlpha(Theme.PANEL_BG, 0.92f));
		UIRender.border(matrix, x, y, w, h, h * 0.5f, 1.0f, Theme.PANEL_BORDER);

		UIRender.text(matrix, font, "Keybinds", x + 8.0f, y + 4.0f,
			fs, Theme.ACCENT, 0.07f);
	}

	private void drawRow(Matrix4f matrix, MsdfFont font, Module m,
			float x, float y, float w, float h, float fs) {
		UIRender.rectGradientH(matrix, x, y, w, h, h * 0.4f,
			0xE01A1A24, 0xE023232F);
		UIRender.border(matrix, x, y, w, h, h * 0.4f, 1.0f, Theme.PANEL_BORDER);

		UIRender.text(matrix, font, m.name, x + 9.0f, y + 4.0f,
			fs, m.enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY, 0.05f);

		String key = "[" + KeyName.describe(m.keybind) + "]";
		float kw = UIRender.textWidth(font, key, fs * 0.9f);
		UIRender.text(matrix, font, key, x + w - kw - 9.0f, y + 4.5f,
			fs * 0.9f, Theme.ACCENT, 0.05f);
	}

	private void drawGhostRow(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float fs) {
		UIRender.rect(matrix, x, y, w, h, h * 0.4f,
			ColorUtil.withAlpha(0xFF1A1A24, 0.55f));
		UIRender.border(matrix, x, y, w, h, h * 0.4f, 1.0f,
			ColorUtil.withAlpha(Theme.PANEL_BORDER, 0.6f));
		UIRender.text(matrix, font, "no keybinds", x + 9.0f, y + 4.0f,
			fs, ColorUtil.withAlpha(Theme.TEXT_SECONDARY, 0.55f), 0.05f);
	}
}
