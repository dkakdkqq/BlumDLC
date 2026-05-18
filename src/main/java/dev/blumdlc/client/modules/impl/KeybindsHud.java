package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.KeyName;
import net.minecraft.client.MinecraftClient;

/**
 * Right-side panel listing every module that has a keybind, with its current
 * key on the right. Mirrors the typical Minced/Celestial keybinds list.
 *
 * <p>Each row is its own pill so it visually pops away from the world.
 */
public final class KeybindsHud extends Module {

	public final NumberSetting fontSize;

	public KeybindsHud() {
		super("Keybinds", "Lists modules with keybinds", Category.RENDER);
		this.fontSize = new NumberSetting("Font Size", 8.5, 6.0, 12.0, 0.5);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		MsdfFont font = Fonts.BIKO.get();
		MinecraftClient client = MinecraftClient.getInstance();

		List<Module> bound = new ArrayList<>();
		for (Module m : BlumDLC.MODULES.all()) {
			if (KeyName.isBound(m.keybind) && m != this) {
				bound.add(m);
			}
		}
		if (bound.isEmpty()) {
			// Always show a hint header so the user knows the HUD is alive.
			drawTitle(matrix, font, client);
			return;
		}
		bound.sort(Comparator.comparing((Module m) -> m.name));

		float fs = fontSize.getFloat();
		float padX = 8.0f;
		float rowH = fs + 8.0f;
		float gap  = 4.0f;

		// Pre-compute the widest row so all pills line up.
		float maxRowW = 0.0f;
		for (Module m : bound) {
			float lw = UIRender.textWidth(font, m.name, fs);
			float kw = UIRender.textWidth(font, "[" + KeyName.describe(m.keybind) + "]", fs * 0.9f);
			maxRowW = Math.max(maxRowW, lw + 12.0f + kw);
		}
		float w = maxRowW + padX * 2;

		float screenW = (float) client.getWindow().getScaledWidth();
		float x = screenW - w - 6.0f;
		float y = 6.0f;

		drawTitle(matrix, font, client);

		// First row sits below the title.
		float titleH = 9.0f + 8.0f; // matches drawTitle's fs + padding
		y += titleH + 4.0f;
		for (Module m : bound) {
			drawRow(matrix, font, m, x, y, w, rowH, fs);
			y += rowH + gap;
		}
	}

	private void drawTitle(Matrix4f matrix, MsdfFont font, MinecraftClient client) {
		String title = "Keybinds";
		float fs = 9.0f;
		float tw = UIRender.textWidth(font, title, fs);
		float w = tw + 14.0f;
		float h = fs + 8.0f;
		float x = client.getWindow().getScaledWidth() - w - 6.0f;
		float y = 6.0f;

		UIRender.rect(matrix, x, y, w, h, h * 0.5f,
			ColorUtil.withAlpha(Theme.PANEL_BG, 0.92f));
		UIRender.border(matrix, x, y, w, h, h * 0.5f, 1.0f, Theme.PANEL_BORDER);

		UIRender.text(matrix, font, title, x + 7.0f, y + 4.0f,
			fs, Theme.ACCENT, 0.07f);
	}

	private void drawRow(Matrix4f matrix, MsdfFont font, Module m,
			float x, float y, float w, float h, float fs) {
		// Background pill.
		UIRender.rectGradientH(matrix, x, y, w, h, h * 0.4f,
			0xE01A1A24, 0xE023232F);
		UIRender.border(matrix, x, y, w, h, h * 0.4f, 1.0f, Theme.PANEL_BORDER);

		// Name on the left.
		UIRender.text(matrix, font, m.name, x + 9.0f, y + 4.0f,
			fs, m.enabled ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY, 0.05f);

		// Key on the right.
		String key = "[" + KeyName.describe(m.keybind) + "]";
		float kw = UIRender.textWidth(font, key, fs * 0.9f);
		UIRender.text(matrix, font, key, x + w - kw - 9.0f, y + 4.5f,
			fs * 0.9f, Theme.ACCENT, 0.05f);
	}
}
