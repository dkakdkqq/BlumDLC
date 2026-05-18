package dev.blumdlc.client.modules.impl;

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
import net.minecraft.entity.LivingEntity;

/**
 * TargetHUD — shows a card on-screen with the target's face, name, HP bar,
 * and absorption when AttackAura has a target locked.
 *
 * <p>Styled after Minced/Celestial: dark rounded card, player head on the left,
 * name + HP text on the right, colored HP bar at the bottom. Positioned at the
 * bottom-center of the screen by default.
 */
public final class TargetHud extends Module {

	public final NumberSetting posX;
	public final NumberSetting posY;

	private final AttackAura attackAura;

	/** Smooth HP for the bar animation. */
	private float displayHp = 0.0f;

	public TargetHud(AttackAura attackAura) {
		super("TargetHUD", "Shows target info card", Category.RENDER);
		this.attackAura = attackAura;

		this.posX = new NumberSetting("X Offset", 0.0, -500.0, 500.0, 1.0);
		this.posY = new NumberSetting("Y Offset", 0.0, -400.0, 400.0, 1.0);
		addSetting(posX);
		addSetting(posY);
		this.enabled = true;
	}

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		if (!attackAura.enabled) {
			displayHp = 0.0f;
			return;
		}
		LivingEntity target = attackAura.getTarget();
		if (target == null || !target.isAlive()) {
			displayHp = 0.0f;
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		MsdfFont font = Fonts.BIKO.get();

		float screenW = client.getWindow().getScaledWidth();
		float screenH = client.getWindow().getScaledHeight();

		// Card dimensions
		float cardW = 160.0f;
		float cardH = 48.0f;
		float headSize = 32.0f;
		float barH = 4.0f;
		float pad = 8.0f;

		// Default position: bottom-center
		float cx = (screenW - cardW) * 0.5f + posX.getFloat();
		float cy = screenH * 0.65f + posY.getFloat();

		// --- Background ---
		UIRender.rect(matrix, cx, cy, cardW, cardH, 8.0f, ColorUtil.withAlpha(Theme.PANEL_BG, 0.92f));
		UIRender.border(matrix, cx, cy, cardW, cardH, 8.0f, 1.0f, Theme.PANEL_BORDER);

		// --- Player Head (rendered via vanilla skin system) ---
		// We draw a placeholder rect here; the actual head texture is drawn
		// through DrawContext in a second pass (see below).
		float headX = cx + pad;
		float headY = cy + (cardH - headSize) * 0.5f;
		UIRender.rect(matrix, headX, headY, headSize, headSize, 4.0f, 0xFF2A2A36);

		// --- Name ---
		String name = target.getName().getString();
		float textX = headX + headSize + pad;
		float textY = cy + 8.0f;
		UIRender.text(matrix, font, name, textX, textY, 9.5f, Theme.TEXT_PRIMARY, 0.06f);

		// --- HP text ---
		float currentHp = target.getHealth();
		float maxHp = target.getMaxHealth();
		float absorption = target.getAbsorptionAmount();

		// Smooth interpolation for the bar
		displayHp += (currentHp - displayHp) * 0.15f;
		if (Math.abs(displayHp - currentHp) < 0.01f) displayHp = currentHp;

		String hpText = String.format("HP: %.1f (%.1f)", currentHp, absorption);
		UIRender.text(matrix, font, hpText, textX, textY + 13.0f, 7.5f, Theme.TEXT_SECONDARY, 0.05f);

		// --- HP Bar ---
		float barX = textX;
		float barY = cy + cardH - barH - 6.0f;
		float barW = cardW - headSize - pad * 3;
		float frac = Math.max(0.0f, Math.min(1.0f, displayHp / Math.max(1.0f, maxHp)));

		// Background track
		UIRender.rect(matrix, barX, barY, barW, barH, 2.0f, 0x33FFFFFF);
		// Filled portion — color shifts from green to red
		int barColor = hpColor(frac);
		UIRender.rect(matrix, barX, barY, barW * frac, barH, 2.0f, barColor);

		// Absorption overlay (gold, on top)
		if (absorption > 0.0f) {
			float absFrac = Math.min(1.0f, absorption / maxHp);
			UIRender.rect(matrix, barX, barY - 2.0f, barW * absFrac, 2.0f, 1.0f, 0xFFFCD34D);
		}
	}

	/**
	 * Lerp from red (0%) through yellow (50%) to green (100%).
	 */
	private static int hpColor(float frac) {
		if (frac > 0.5f) {
			// yellow -> green
			float t = (frac - 0.5f) * 2.0f;
			int r = Math.round(255 * (1.0f - t));
			int g = 255;
			return 0xFF000000 | (r << 16) | (g << 8) | 0x00;
		} else {
			// red -> yellow
			float t = frac * 2.0f;
			int r = 255;
			int g = Math.round(255 * t);
			return 0xFF000000 | (r << 16) | (g << 8) | 0x00;
		}
	}
}
