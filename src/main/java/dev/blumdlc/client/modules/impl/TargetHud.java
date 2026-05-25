package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.animation.Animation;
import dev.blumdlc.client.ui.animation.Easing;
import dev.blumdlc.client.ui.hud.HudStyle;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

/**
 * Minced-style target HUD. Renders a card with the locked target's avatar,
 * name, HP value and a coloured HP bar (with optional gold absorption strip
 * stacked on top) while {@link AttackAura} has a target.
 *
 * <p>This is a {@link HudModule}, so the user can:
 * <ul>
 *   <li>drag it around in the chat-screen HUD editor;</li>
 *   <li>toggle it on/off through the "HUD Modules" panel just like
 *       Watermark or PotionsHud — it auto-appears there because
 *       {@link dev.blumdlc.client.modules.impl.HudSettings#populate()}
 *       discovers every {@code HudModule}.</li>
 * </ul>
 *
 * <p>Behaviour:
 * <ul>
 *   <li>While the aura has a target, the card fades in and tracks that
 *       target's name + HP (smoothed for visual continuity).</li>
 *   <li>When the target dies / is released, the card fades out over
 *       ~280 ms before disappearing.</li>
 *   <li>While the chat-screen HUD editor is active, the card is forced
 *       visible — using either the last real target (if alive) or the
 *       local player as a stand-in — so the user has something to grab.</li>
 * </ul>
 *
 * <p>Avatar rendering: for player targets we render the head face from the
 * player's skin texture (base layer + hat overlay), with the standard
 * 8/64..16/64 UV slice. For non-player living entities (mobs, animals)
 * we fall back to a coloured letter avatar using the entity name's first
 * character — never let the card render with an empty hole.
 */
public final class TargetHud extends HudModule {

	private static final float CARD_W   = 158.0f;
	private static final float CARD_H   = 44.0f;
	private static final float FACE     = 28.0f;

	private final AttackAura attackAura;

	/** Smoothed HP/max/absorption — drives the bar without flicker on hit. */
	private float displayHp  = 20.0f;
	private float displayMax = 20.0f;
	private float displayAbs = 0.0f;

	/** 0..1 fade alpha; rises while a target is locked, falls afterwards. */
	private final Animation fadeAnim = new Animation(0.0f, 280, Easing.EASE_OUT_CUBIC);

	/** Most-recent locked target. Used to render the fade-out tail. */
	private LivingEntity lastTarget;

	public TargetHud(AttackAura attackAura) {
		super("TargetHUD", "Shows AttackAura target info");
		this.attackAura = attackAura;
		this.enabled = true; // visible by default; only renders while a target is locked
	}

	@Override public float hudWidth()  { return CARD_W; }
	@Override public float hudHeight() { return CARD_H; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		// Lower-center: comfortable for combat HUDs, doesn't fight the hotbar.
		this.x = (sw - CARD_W) * 0.5f;
		this.y = sh * 0.66f;
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		LivingEntity target = (attackAura.enabled) ? attackAura.getTarget() : null;
		if (target != null && !target.isAlive()) {
			target = null;
		}

		// Pick what to draw: real target → fade-out tail of last target →
		// editor ghost → nothing.
		boolean hasReal = target != null;
		LivingEntity render;
		if (hasReal) {
			lastTarget = target;
			render = target;
		} else if (lastTarget != null && lastTarget.isAlive()) {
			render = lastTarget;
		} else if (editing) {
			render = MinecraftClient.getInstance().player;
		} else {
			render = null;
		}

		// Drive fade
		boolean wantVisible = hasReal || (editing && render != null);
		fadeAnim.setTarget(wantVisible ? 1.0f : 0.0f);
		float a = fadeAnim.getValue();

		if (a < 0.005f || render == null) {
			return;
		}

		// Smoothed numbers
		float curHp = render.getHealth();
		float maxHp = Math.max(1.0f, render.getMaxHealth());
		float abs   = render.getAbsorptionAmount();
		displayHp  += (curHp  - displayHp)  * 0.18f;
		displayMax += (maxHp  - displayMax) * 0.18f;
		displayAbs += (abs    - displayAbs) * 0.18f;
		if (Math.abs(displayHp  - curHp) < 0.05f)  displayHp  = curHp;
		if (Math.abs(displayMax - maxHp) < 0.05f)  displayMax = maxHp;
		if (Math.abs(displayAbs - abs)   < 0.05f)  displayAbs = abs;

		float px = this.x;
		float py = this.y;
		MsdfFont font = Fonts.BIKO.get();

		// Card chrome
		HudStyle.card(matrix, px, py, CARD_W, CARD_H, a);

		// Avatar on the left
		float fx = px + HudStyle.PAD_X;
		float fy = py + (CARD_H - FACE) * 0.5f;
		renderAvatar(matrix, font, render, fx, fy, FACE, a);

		// Right column: name + HP text + bar
		float rx = fx + FACE + 8.0f;
		float ry = py + 7.0f;
		float rw = CARD_W - (rx - px) - HudStyle.PAD_X;

		String name = UIRender.ellipsize(font, render.getName().getString(), 9.5f, rw);
		UIRender.text(matrix, font, name, rx, ry, 9.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, a), 0.07f);

		String hpText = String.format("%.1f / %.0f", displayHp, displayMax);
		if (displayAbs > 0.05f) {
			hpText += String.format(" +%.0f", displayAbs);
		}
		float hpFs = 7.0f;
		String hpEl = UIRender.ellipsize(font, hpText, hpFs, rw);
		UIRender.text(matrix, font, hpEl, rx, ry + 12.0f, hpFs,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, a), 0.05f);

		// HP bar
		float barY = py + CARD_H - 9.0f;
		float barH = 3.0f;
		float frac = clamp01(displayHp / displayMax);
		HudStyle.progressBar(matrix, rx, barY, rw, barH, frac, hpColor(frac), a);

		// Absorption strip (gold, on top of HP bar)
		if (displayAbs > 0.05f) {
			float absFrac = clamp01(displayAbs / displayMax);
			UIRender.rect(matrix, rx, barY - 2.0f, rw * absFrac, 1.5f, 0.75f,
				ColorUtil.multiplyAlpha(0xFFFCD34D, a));
		}
	}

	/**
	 * Renders the target avatar. For player targets uses the actual skin
	 * head face (base layer + hat overlay); for non-player living entities
	 * draws a coloured letter tile using the entity name's first character.
	 */
	private void renderAvatar(Matrix4f matrix, MsdfFont font, LivingEntity target,
			float fx, float fy, float fSize, float alpha) {
		if (target instanceof AbstractClientPlayerEntity player) {
			try {
				Identifier skinTex = player.getSkinTextures().texture();
				int tint = ColorUtil.multiplyAlpha(0xFFFFFFFF, alpha);
				// Base head — vanilla skin layout: 8/64..16/64 (u, v).
				UIRender.texture(matrix, skinTex, fx, fy, fSize, fSize, 3.0f,
					8.0f / 64.0f, 8.0f / 64.0f, 8.0f / 64.0f, 8.0f / 64.0f, tint);
				// Hat overlay: 40/64..48/64.
				UIRender.texture(matrix, skinTex, fx, fy, fSize, fSize, 3.0f,
					40.0f / 64.0f, 8.0f / 64.0f, 8.0f / 64.0f, 8.0f / 64.0f, tint);
				return;
			} catch (Throwable ignored) {
				// fall through to letter avatar
			}
		}

		// Letter avatar fallback — coloured tile + first-letter glyph.
		int avatarFill = ColorUtil.lerp(ClientTheme.from(), ClientTheme.to(), 0.5f);
		UIRender.rect(matrix, fx, fy, fSize, fSize, 3.0f,
			ColorUtil.multiplyAlpha(avatarFill, 0.30f * alpha));
		UIRender.border(matrix, fx, fy, fSize, fSize, 3.0f, 0.8f,
			ColorUtil.multiplyAlpha(avatarFill, 0.7f * alpha));

		String n = target.getName().getString();
		String letter = (n != null && !n.isEmpty())
			? n.substring(0, 1).toUpperCase()
			: "?";
		float lFs = fSize * 0.55f;
		float lW = UIRender.textWidth(font, letter, lFs);
		UIRender.text(matrix, font, letter,
			fx + (fSize - lW) * 0.5f, fy + fSize * 0.20f, lFs,
			ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, alpha), 0.08f);
	}

	/**
	 * Smoothly lerps from red (0%) through yellow (50%) to green (100%) so
	 * the HP bar reads at a glance — same colour ramp Minced uses.
	 */
	private static int hpColor(float frac) {
		frac = clamp01(frac);
		if (frac > 0.5f) {
			float t = (frac - 0.5f) * 2.0f; // 0..1 over [0.5..1.0]
			int r = Math.round(255 * (1.0f - t));
			int g = 255;
			return 0xFF000000 | (r << 16) | (g << 8);
		} else {
			float t = frac * 2.0f;          // 0..1 over [0..0.5]
			int r = 255;
			int g = Math.round(255 * t);
			return 0xFF000000 | (r << 16) | (g << 8);
		}
	}

	private static float clamp01(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}
}
