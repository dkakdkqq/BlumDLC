package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.hud.HudStyle;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Bottom-right alerter for staff members joining the server. When a watched
 * tag (e.g. {@code "admin"}) appears in a newly-seen player name, an
 * animated alert card slides in from the right and fades out a few seconds
 * later.
 *
 * <p>Each card uses {@link HudStyle#card} with a fixed danger-red accent
 * strip so staff alerts are visually distinct from the rest of the HUDs.
 * Movable through the chat-screen HUD editor.
 */
public final class StaffHud extends HudModule {

	private static final float CARD_W   = 168.0f;
	private static final float CARD_H   = 30.0f;
	private static final float CARD_GAP = 5.0f;
	/** Danger red used for the accent strip and the title text. */
	private static final int   ACCENT   = 0xFFEF4444;

	public final MultiSetting watchList;
	public final NumberSetting alertSeconds;

	private static final List<String> KNOWN_TAGS = List.of(
		"admin", "owner", "mod", "staff", "helper", "support", "yt", "youtube"
	);

	private final List<String> seenLastTick = new ArrayList<>();
	private final List<Alert>  alerts       = new ArrayList<>();
	private float lastHeight = CARD_H;

	public StaffHud() {
		super("Staff", "Alerts when staff join the server");
		this.watchList    = new MultiSetting("Watch List",
			KNOWN_TAGS,
			KNOWN_TAGS.toArray(new String[0]));
		this.alertSeconds = new NumberSetting("Alert Seconds", 5.0, 1.0, 15.0, 0.5);
		addSetting(watchList);
		addSetting(alertSeconds);
	}

	@Override public float hudWidth()  { return CARD_W; }
	@Override public float hudHeight() { return lastHeight; }

	@Override
	protected void computeDefaultPosition(int sw, int sh) {
		this.x = Math.max(0.0f, sw - CARD_W - 6.0f);
		this.y = Math.max(0.0f, sh - CARD_H - 6.0f);
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() == null) {
			seenLastTick.clear();
			return;
		}

		List<String> nowSeen = new ArrayList<>();
		for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
			String name = e.getProfile().getName();
			if (name == null) continue;
			nowSeen.add(name);
			if (!seenLastTick.contains(name) && isStaff(name)) {
				alerts.add(new Alert(name, System.currentTimeMillis()));
			}
		}
		seenLastTick.clear();
		seenLastTick.addAll(nowSeen);
	}

	@Override
	protected void renderHud(Matrix4f matrix, float tickDelta, boolean editing) {
		long now = System.currentTimeMillis();
		long lifeMillis = (long) (alertSeconds.get() * 1000.0);

		// Drop expired alerts
		Iterator<Alert> it = alerts.iterator();
		while (it.hasNext()) {
			if (now - it.next().shownAt > lifeMillis) it.remove();
		}

		MsdfFont font = Fonts.BIKO.get();

		if (alerts.isEmpty()) {
			if (editing) {
				lastHeight = CARD_H;
				drawAlert(matrix, font, "drag me", this.x, this.y, 0.6f, true);
			} else {
				lastHeight = 0.0f;
			}
			return;
		}

		float totalH = alerts.size() * CARD_H + (alerts.size() - 1) * CARD_GAP;
		lastHeight = totalH;

		float y = this.y;
		for (Alert a : alerts) {
			float age = (now - a.shownAt) / Math.max(1.0f, lifeMillis);
			// Slide in over the first ~17% of life, fade out over the last ~25%.
			float slideIn = Math.min(1.0f, age * 6.0f);
			float fadeOut = Math.min(1.0f, (1.0f - age) * 4.0f);
			float alpha   = Math.max(0.0f, Math.min(slideIn, fadeOut));
			float ax      = this.x + (1.0f - slideIn) * 28.0f;

			drawAlert(matrix, font, a.name, ax, y, alpha, false);
			y += CARD_H + CARD_GAP;
		}
	}

	private void drawAlert(Matrix4f matrix, MsdfFont font, String name,
			float x, float y, float alpha, boolean ghost) {
		int accent = ghost ? Theme.PANEL_BORDER : ACCENT;
		HudStyle.card(matrix, x, y, CARD_W, CARD_H, alpha, accent);

		float contentX = x + HudStyle.ACCENT_W + 4.0f + (HudStyle.PAD_X - 4.0f);

		String title = ghost ? "Staff alert" : "Staff online";
		int titleColor = ghost ? Theme.TEXT_SECONDARY : ACCENT;
		UIRender.text(matrix, font, title, contentX, y + 5.0f, 8.5f,
			ColorUtil.multiplyAlpha(titleColor, alpha), 0.07f);

		String shown = UIRender.ellipsize(font, name, 7.5f, CARD_W - (contentX - x) - HudStyle.PAD_X);
		UIRender.text(matrix, font, shown, contentX, y + 17.0f, 7.5f,
			ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, alpha), 0.05f);
	}

	private boolean isStaff(String name) {
		String low = name.toLowerCase();
		for (String tag : watchList.options) {
			if (watchList.isSelected(tag) && low.contains(tag.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private static final class Alert {
		final String name;
		final long shownAt;
		Alert(String name, long shownAt) {
			this.name = name;
			this.shownAt = shownAt;
		}
	}
}
