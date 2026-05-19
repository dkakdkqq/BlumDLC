package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

/**
 * Bottom-left list of active potion effects with a colored bar showing the
 * amplifier and a {@code mm:ss} countdown.
 *
 * <p>Movable via the chat-screen HUD editor. While the editor is active and
 * no real effects are present, a ghost row is drawn so the user has something
 * to grab.
 */
public final class PotionsHud extends HudModule {

	private static final float HUD_WIDTH = 130.0f;

	public final NumberSetting fontSize;

	private float lastHeight = 0.0f;

	public PotionsHud() {
		super("Potions", "Active potion effects");
		this.fontSize = new NumberSetting("Font Size", 8.5, 6.0, 12.0, 0.5);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override
	public float hudWidth() {
		return HUD_WIDTH;
	}

	@Override
	public float hudHeight() {
		return lastHeight;
	}

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		// Bottom-left: same visual home as before. Approximate height for
		// roughly three effects so the default anchor sits at the right spot.
		float fs = fontSize.getFloat();
		float rowH = fs + 11.0f;
		float gap = 3.0f;
		float approxH = 3.0f * rowH + 2.0f * gap;
		this.x = 6.0f;
		this.y = Math.max(6.0f, sh - 6.0f - approxH);
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		List<StatusEffectInstance> effects = (player != null)
			? new ArrayList<>(player.getStatusEffects())
			: new ArrayList<>();
		effects.sort(Comparator.comparingInt((StatusEffectInstance e) -> -e.getDuration()));

		MsdfFont font = Fonts.BIKO.get();
		float fs = fontSize.getFloat();
		float rowH = fs + 11.0f;
		float gap = 3.0f;

		float w = HUD_WIDTH;
		int rows = effects.size();
		if (editing && rows == 0) {
			rows = 1; // ghost row for positioning
		}
		float h = rows == 0
			? 0.0f
			: rows * rowH + (rows - 1) * gap;

		this.lastHeight = h;
		if (rows == 0) {
			return; // truly nothing to render
		}

		float x = this.x;
		float y = this.y;
		float age = (player != null) ? player.age + tickDelta : 0.0f;

		if (effects.isEmpty()) {
			drawGhost(matrix, font, x, y, w, rowH, fs);
			return;
		}

		for (StatusEffectInstance ei : effects) {
			drawEffect(matrix, font, ei, x, y, w, rowH, fs, age);
			y += rowH + gap;
		}
	}

	private void drawEffect(Matrix4f matrix, MsdfFont font,
			StatusEffectInstance ei, float x, float y, float w, float h, float fs,
			float worldTick) {

		RegistryEntry<StatusEffect> entry = ei.getEffectType();
		int color = entry.value().getColor() | 0xFF000000;

		// Background pill with a subtle tint of the effect color on the left.
		UIRender.rectGradientH(matrix, x, y, w, h, 5.0f,
			ColorUtil.multiplyAlpha(color, 0.30f),
			0xE01A1A24);
		UIRender.border(matrix, x, y, w, h, 5.0f, 1.0f, Theme.PANEL_BORDER);

		String name = Text.translatable(entry.value().getTranslationKey()).getString();
		String amp = ei.getAmplifier() > 0 ? " " + roman(ei.getAmplifier() + 1) : "";
		UIRender.text(matrix, font, name + amp, x + 8.0f, y + 3.0f,
			fs, Theme.TEXT_PRIMARY, 0.05f);

		String time = ei.isInfinite()
			? "**:**"
			: StatusEffectUtil.getDurationText(ei, 1.0f, 20.0f).getString();
		float tw = UIRender.textWidth(font, time, fs * 0.85f);
		UIRender.text(matrix, font, time, x + w - tw - 8.0f, y + 3.5f,
			fs * 0.85f, Theme.TEXT_SECONDARY, 0.04f);

		float maxDur = Math.max(ei.getDuration(), 1);
		float frac = ei.isInfinite() ? 1.0f : Math.min(1.0f, maxDur / 1200.0f);
		float barW = w - 12.0f;
		UIRender.rect(matrix, x + 6.0f, y + h - 4.5f, barW, 1.5f, 1.0f, 0x33FFFFFF);
		UIRender.rect(matrix, x + 6.0f, y + h - 4.5f, barW * frac, 1.5f, 1.0f, color);
	}

	private void drawGhost(Matrix4f matrix, MsdfFont font,
			float x, float y, float w, float h, float fs) {
		UIRender.rect(matrix, x, y, w, h, 5.0f,
			ColorUtil.withAlpha(0xFF1A1A24, 0.55f));
		UIRender.border(matrix, x, y, w, h, 5.0f, 1.0f,
			ColorUtil.withAlpha(Theme.PANEL_BORDER, 0.6f));
		UIRender.text(matrix, font, "no effects", x + 8.0f, y + 3.0f,
			fs, ColorUtil.withAlpha(Theme.TEXT_SECONDARY, 0.55f), 0.05f);
	}

	private static String roman(int n) {
		switch (n) {
			case 1:  return "I";
			case 2:  return "II";
			case 3:  return "III";
			case 4:  return "IV";
			case 5:  return "V";
			case 6:  return "VI";
			case 7:  return "VII";
			case 8:  return "VIII";
			case 9:  return "IX";
			case 10: return "X";
			default: return Integer.toString(n);
		}
	}
}
