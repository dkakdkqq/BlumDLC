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
import dev.blumdlc.client.ui.hud.HudStyle;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

/**
 * Minced-style potion HUD: each active effect is its own compact card,
 * stacked vertically, with the effect's colour driving the accent strip
 * and a thin progress bar showing remaining duration.
 *
 * <p>Movable through the chat-screen HUD editor; default anchor bottom-left.
 * While the editor is active and the player has no live effects, a single
 * ghost card is drawn so the user has something to grab.
 */
public final class PotionsHud extends HudModule {

	private static final float CARD_W   = 138.0f;
	private static final float CARD_H   = 22.0f;
	private static final float CARD_GAP = 4.0f;

	public final NumberSetting fontSize;

	private float lastHeight = CARD_H;

	public PotionsHud() {
		super("Potions", "Active potion effects");
		this.fontSize = new NumberSetting("Font Size", 8.0, 6.0, 12.0, 0.5);
		addSetting(fontSize);
		this.enabled = true;
	}

	@Override public float hudWidth()  { return CARD_W; }
	@Override public float hudHeight() { return lastHeight; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		// Anchor so the bottom of the (single-effect) card sits 6 px above
		// the bottom edge. As more effects pile up the stack grows upward,
		// which is the standard Minced direction.
		this.x = 6.0f;
		this.y = Math.max(6.0f, sh - 6.0f - CARD_H);
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		List<StatusEffectInstance> effects = (player != null)
			? new ArrayList<>(player.getStatusEffects())
			: new ArrayList<>();
		// Longest first so the topmost is the one most likely to expire last.
		effects.sort(Comparator.comparingInt((StatusEffectInstance e) -> -e.getDuration()));

		MsdfFont font = Fonts.BIKO.get();
		float fs = fontSize.getFloat();

		boolean ghost = effects.isEmpty();
		int n = ghost ? (editing ? 1 : 0) : effects.size();
		if (n == 0) {
			lastHeight = 0.0f;
			return;
		}

		float totalH = n * CARD_H + (n - 1) * CARD_GAP;
		lastHeight = totalH;

		// Stack grows downward from this.y — but because the default anchor
		// keeps the bottom edge fixed, multi-effect stacks visually grow up
		// only when the user manually drags the HUD upward.
		float px = this.x;
		float py = this.y;

		if (ghost) {
			drawGhost(matrix, font, px, py, fs);
			return;
		}

		for (StatusEffectInstance ei : effects) {
			drawEffect(matrix, font, ei, px, py, CARD_W, CARD_H, fs);
			py += CARD_H + CARD_GAP;
		}
	}

	private void drawEffect(Matrix4f matrix, MsdfFont font,
			StatusEffectInstance ei,
			float x, float y, float w, float h, float fs) {

		RegistryEntry<StatusEffect> entry = ei.getEffectType();
		int effectColor = entry.value().getColor() | 0xFF000000;
		float a = 1.0f;

		// Card chrome with the effect's colour as the accent strip
		HudStyle.card(matrix, x, y, w, h, a, effectColor);

		float contentX = x + HudStyle.ACCENT_W + 4.0f + (HudStyle.PAD_X - 4.0f);

		// Effect name + amplifier
		String name = Text.translatable(entry.value().getTranslationKey()).getString();
		String amp = ei.getAmplifier() > 0 ? " " + roman(ei.getAmplifier() + 1) : "";
		String label = name + amp;

		// Duration text (right-aligned)
		String time = ei.isInfinite()
			? "**:**"
			: StatusEffectUtil.getDurationText(ei, 1.0f, 20.0f).getString();
		float tw = UIRender.textWidth(font, time, fs * 0.85f);

		float labelMaxW = w - (contentX - x) - tw - HudStyle.PAD_X - 4.0f;
		String shown = UIRender.ellipsize(font, label, fs, labelMaxW);

		UIRender.text(matrix, font, shown, contentX, y + 4.0f, fs,
			Theme.TEXT_PRIMARY, 0.05f);
		UIRender.text(matrix, font, time,
			x + w - tw - HudStyle.PAD_X, y + 4.5f, fs * 0.85f,
			Theme.TEXT_SECONDARY, 0.04f);

		// Progress bar — represents remaining duration over a reference window
		// of 60 s (1200 ticks). Long buffs cap at 100% and tick down once
		// they drop below the reference.
		float frac = ei.isInfinite()
			? 1.0f
			: Math.min(1.0f, Math.max(ei.getDuration(), 1) / 1200.0f);
		float barX = x + HudStyle.ACCENT_W + 4.0f + 2.0f;
		float barY = y + h - 4.0f;
		float barW = w - (barX - x) - HudStyle.PAD_X;
		HudStyle.progressBar(matrix, barX, barY, barW, 1.5f, frac, effectColor, a);
	}

	private void drawGhost(Matrix4f matrix, MsdfFont font,
			float x, float y, float fs) {
		float a = 0.6f;
		HudStyle.card(matrix, x, y, CARD_W, CARD_H, a);
		float contentX = x + HudStyle.ACCENT_W + 4.0f + (HudStyle.PAD_X - 4.0f);
		UIRender.text(matrix, font, "no effects", contentX, y + 4.0f, fs,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, a), 0.05f);
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
