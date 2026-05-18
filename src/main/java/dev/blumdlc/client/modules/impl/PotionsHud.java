package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
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
 */
public final class PotionsHud extends Module {

	public final NumberSetting fontSize;

	public PotionsHud() {
		super("Potions", "Active potion effects", Category.RENDER);
		this.fontSize = new NumberSetting("Font Size", 8.5, 6.0, 12.0, 0.5);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null) return;

		List<StatusEffectInstance> effects = new ArrayList<>(player.getStatusEffects());
		if (effects.isEmpty()) return;
		effects.sort(Comparator.comparingInt((StatusEffectInstance e) -> -e.getDuration()));

		MsdfFont font = Fonts.BIKO.get();
		float fs = fontSize.getFloat();
		float padX = 8.0f;
		float rowH = fs + 11.0f;
		float gap  = 3.0f;
		float w = 130.0f;

		float screenH = (float) client.getWindow().getScaledHeight();
		float baseY = screenH - 6.0f - effects.size() * (rowH + gap) + gap;
		float x = 6.0f;
		float y = baseY;

		for (StatusEffectInstance ei : effects) {
			drawEffect(matrix, font, ei, x, y, w, rowH, fs, player.age + tickDelta);
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

		// Effect name + amplifier as a roman-ish suffix.
		String name = Text.translatable(entry.value().getTranslationKey()).getString();
		String amp = ei.getAmplifier() > 0 ? " " + roman(ei.getAmplifier() + 1) : "";
		UIRender.text(matrix, font, name + amp, x + 8.0f, y + 3.0f,
			fs, Theme.TEXT_PRIMARY, 0.05f);

		// Time on the right (mm:ss). StatusEffectUtil handles "**:**" for infinite effects.
		String time = ei.isInfinite()
			? "**:**"
			: StatusEffectUtil.getDurationText(ei, 1.0f, 20.0f).getString();
		float tw = UIRender.textWidth(font, time, fs * 0.85f);
		UIRender.text(matrix, font, time, x + w - tw - 8.0f, y + 3.5f,
			fs * 0.85f, Theme.TEXT_SECONDARY, 0.04f);

		// Colored progress bar at the bottom of the row.
		float maxDur = Math.max(ei.getDuration(), 1);
		float frac = ei.isInfinite() ? 1.0f : Math.min(1.0f, maxDur / 1200.0f);
		float barW = w - 12.0f;
		UIRender.rect(matrix, x + 6.0f, y + h - 4.5f, barW, 1.5f, 1.0f, 0x33FFFFFF);
		UIRender.rect(matrix, x + 6.0f, y + h - 4.5f, barW * frac, 1.5f, 1.0f, color);
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
