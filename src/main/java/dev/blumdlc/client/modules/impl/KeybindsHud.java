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
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.hud.HudStyle;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.KeyName;

/**
 * Minced-style keybinds HUD: a single dark card containing a "Keybinds"
 * title row followed by one row per bound module — module name on the left,
 * key label in accent on the right, with hairline dividers in between.
 *
 * <p>Movable through the chat-screen HUD editor; default position top-right
 * with a 6 px margin.
 */
public final class KeybindsHud extends HudModule {

	public final NumberSetting fontSize;

	private float lastWidth = 90.0f;
	private float lastHeight = 30.0f;

	public KeybindsHud() {
		super("Keybinds", "Lists modules with keybinds");
		this.fontSize = new NumberSetting("Font Size", 8.0, 6.0, 12.0, 0.5);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override public float hudWidth()  { return lastWidth; }
	@Override public float hudHeight() { return lastHeight; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = Math.max(0.0f, sw - lastWidth - 6.0f);
		this.y = 6.0f;
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();

		// Gather bound modules (excluding self).
		List<Module> bound = new ArrayList<>();
		for (Module m : BlumDLC.MODULES.all()) {
			if (KeyName.isBound(m.keybind) && m != this) {
				bound.add(m);
			}
		}
		bound.sort(Comparator.comparing((Module m) -> m.name));

		float fs        = fontSize.getFloat();
		float titleFs   = fs + 1.5f;
		float titleH    = titleFs + 8.0f;
		float rowH      = fs + 7.0f;
		float padX      = HudStyle.PAD_X;
		float padY      = HudStyle.PAD_Y;

		// Compute card width to fit the widest row.
		float maxRowW = UIRender.textWidth(font, "Keybinds", titleFs);
		for (Module m : bound) {
			float lw = UIRender.textWidth(font, m.name, fs);
			float kw = UIRender.textWidth(font, "[" + KeyName.describe(m.keybind) + "]", fs * 0.9f);
			maxRowW = Math.max(maxRowW, lw + 14.0f + kw);
		}
		float w = maxRowW + padX * 2.0f + HudStyle.ACCENT_W + 4.0f;

		// Compute card height. Even with no real binds we reserve a ghost row
		// in editor mode so the user can grab the card.
		int rows = bound.size();
		boolean ghost = editing && rows == 0;
		int displayRows = Math.max(rows, ghost ? 1 : 0);
		float bodyH = (displayRows == 0)
			? 0.0f
			: padY + displayRows * rowH;
		float h = titleH + bodyH;

		this.lastWidth = w;
		this.lastHeight = h;

		float px = this.x;
		float py = this.y;
		float a = 1.0f;

		// Card chrome
		HudStyle.card(matrix, px, py, w, h, a);

		// Title row
		float contentX = px + HudStyle.ACCENT_W + 4.0f;
		UIRender.text(matrix, font, "Keybinds", contentX + (padX - 4.0f), py + 4.0f,
			titleFs, ColorUtil.lerp(ClientTheme.from(), ClientTheme.to(), 0.4f), 0.07f);

		// Title-to-rows divider
		HudStyle.divider(matrix, contentX + 2.0f, py + titleH - 1.5f,
			w - (contentX - px) - padX, a);

		// Rows
		float ry = py + titleH + 2.0f;
		if (ghost) {
			drawRow(matrix, font, "no keybinds", "—", contentX + (padX - 4.0f), ry, w, rowH, fs,
				false, a);
			return;
		}
		for (int i = 0; i < bound.size(); i++) {
			Module m = bound.get(i);
			drawRow(matrix, font, m.name, "[" + KeyName.describe(m.keybind) + "]",
				contentX + (padX - 4.0f), ry, w, rowH, fs, m.enabled, a);
			ry += rowH;
			if (i < bound.size() - 1) {
				HudStyle.divider(matrix, contentX + 4.0f, ry - 0.5f,
					w - (contentX - px) - padX - 6.0f, a * 0.7f);
			}
		}
	}

	private void drawRow(Matrix4f matrix, MsdfFont font,
			String name, String key,
			float contentX, float y, float cardW, float h, float fs,
			boolean active, float alpha) {
		float padRight = HudStyle.PAD_X;
		int nameColor = active ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY;

		UIRender.text(matrix, font, name, contentX, y + 2.5f, fs,
			ColorUtil.multiplyAlpha(nameColor, alpha), 0.05f);

		float keyW = UIRender.textWidth(font, key, fs * 0.9f);
		UIRender.text(matrix, font, key,
			(this.x + cardW) - keyW - padRight,
			y + 3.0f, fs * 0.9f,
			ColorUtil.multiplyAlpha(ClientTheme.accent(), alpha), 0.05f);
	}
}
