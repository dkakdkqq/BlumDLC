package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;

/**
 * ArrayList HUD — sleek list of currently-enabled modules. Each row has a
 * thin accent stripe on its alignment-side and a soft gradient pill body.
 *
 * <p>Top-right by default; reanchors to the bottom on the same side when
 * the user drags it past the screen midline. Hidden / HudModule entries are
 * filtered out so the list only shows toggleable modules the user cares
 * about.
 */
public final class ArrayListHud extends HudModule {

	public final ModeSetting   align;
	public final ModeSetting   sort;
	public final NumberSetting fontSize;

	private float lastW = 80.0f;
	private float lastH = 0.0f;

	public ArrayListHud() {
		super("ArrayList", "Enabled modules list");
		align    = new ModeSetting("Align", "Right", "Right", "Left");
		sort     = new ModeSetting("Sort",  "Width", "Width", "Name");
		fontSize = new NumberSetting("Font Size", 7.5, 5.0, 12.0, 0.5);
		addSetting(align);
		addSetting(sort);
		addSetting(fontSize);
	}

	@Override public float hudWidth()  { return lastW; }
	@Override public float hudHeight() { return lastH; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = Math.max(0.0f, sw - lastW - 4.0f);
		this.y = 28.0f;
	}


	@Override
	protected void renderHud(Matrix4f m, float tickDelta, boolean editing) {
		MsdfFont font = Fonts.BIKO.get();
		float fs = fontSize.getFloat();

		List<Module> enabled = new ArrayList<>();
		for (Module mod : BlumDLC.MODULES.all()) {
			if (mod.enabled && !mod.hidden && !(mod instanceof HudModule)) {
				enabled.add(mod);
			}
		}

		if (enabled.isEmpty() && !editing) {
			lastH = 0.0f;
			return;
		}

		if (sort.is("Width")) {
			enabled.sort(Comparator.comparingDouble(
				(Module mm) -> -UIRender.textWidth(font, mm.name, fs)));
		} else {
			enabled.sort(Comparator.comparing((Module mm) -> mm.name));
		}

		float padX = 5.0f;
		float padY = 2.0f;
		float rowH = fs + padY * 2 + 1.0f;

		float maxW = 0.0f;
		for (Module mod : enabled) {
			float tw = UIRender.textWidth(font, mod.name, fs);
			if (tw > maxW) maxW = tw;
		}
		String ghost = "ArrayList";
		if (enabled.isEmpty() && editing) {
			maxW = UIRender.textWidth(font, ghost, fs);
		}
		lastW = maxW + padX * 2 + 6.0f;
		lastH = Math.max(rowH, Math.max(1, enabled.size()) * rowH);

		boolean rightAlign = align.is("Right");
		float drawY = this.y;
		List<Module> rendered = enabled.isEmpty() && editing
			? List.of()  // we'll paint a fake ghost row separately
			: enabled;

		for (Module mod : rendered) {
			drawRow(m, font, mod.name, drawY, rowH, padX, fs, rightAlign);
			drawY += rowH;
		}
		if (enabled.isEmpty() && editing) {
			drawRow(m, font, ghost, drawY, rowH, padX, fs, rightAlign);
		}
	}


	private void drawRow(Matrix4f m, MsdfFont font, String name,
			float drawY, float rowH, float padX, float fs, boolean rightAlign) {
		float tw = UIRender.textWidth(font, name, fs);
		float rowX = rightAlign ? this.x + lastW - tw - padX : this.x + padX;

		// Background pill
		float bgX = rightAlign
			? this.x + lastW - tw - padX * 2
			: this.x;
		int accentDim = ColorUtil.multiplyAlpha(ClientTheme.accent(), 0.22f);
		int dark = 0xC8141720;
		UIRender.rectGradientH(m, bgX, drawY, tw + padX * 2, rowH, 3.0f,
			rightAlign ? dark : accentDim,
			rightAlign ? accentDim : dark);

		// Accent stripe on the alignment edge
		float stripeX = rightAlign ? this.x + lastW - 2.0f : this.x;
		UIRender.rect(m, stripeX, drawY, 2.0f, rowH, 1.0f,
			ColorUtil.multiplyAlpha(ClientTheme.accent(), 0.95f));

		UIRender.text(m, font, name, rowX, drawY + 3.0f, fs,
			Theme.TEXT_PRIMARY, 0.05f);
	}
}
