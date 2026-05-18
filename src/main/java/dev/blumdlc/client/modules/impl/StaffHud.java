package dev.blumdlc.client.modules.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Bottom-right alerter for staff members: when a player whose name is in the
 * watch list joins the server (or comes within tab-list visibility), an
 * animated card slides in from the right and fades out after a few seconds.
 */
public final class StaffHud extends Module {

	public final MultiSetting watchList;
	public final NumberSetting alertSeconds;

	private static final List<String> KNOWN_TAGS = List.of(
		"admin", "owner", "mod", "staff", "helper", "support", "yt", "youtube"
	);

	private final List<String> seenLastTick = new ArrayList<>();
	private final List<Alert> alerts = new ArrayList<>();

	public StaffHud() {
		super("Staff", "Alerts when staff join the server", Category.RENDER);
		this.watchList    = new MultiSetting("Watch List",
			KNOWN_TAGS,
			KNOWN_TAGS.toArray(new String[0]));
		this.alertSeconds = new NumberSetting("Alert Seconds", 5.0, 1.0, 15.0, 0.5);
		addSetting(watchList);
		addSetting(alertSeconds);
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
	public void onRender(Matrix4f matrix, float tickDelta) {
		long now = System.currentTimeMillis();
		long lifeMillis = (long) (alertSeconds.get() * 1000.0);

		// Drop expired alerts.
		Iterator<Alert> it = alerts.iterator();
		while (it.hasNext()) {
			if (now - it.next().shownAt > lifeMillis) it.remove();
		}
		if (alerts.isEmpty()) return;

		MinecraftClient client = MinecraftClient.getInstance();
		MsdfFont font = Fonts.BIKO.get();
		float screenW = (float) client.getWindow().getScaledWidth();
		float screenH = (float) client.getWindow().getScaledHeight();
		float w = 160.0f;
		float h = 32.0f;
		float gap = 6.0f;
		float baseY = screenH - 6.0f - h;
		float x = screenW - w - 6.0f;

		float y = baseY;
		for (int i = alerts.size() - 1; i >= 0; i--) {
			Alert a = alerts.get(i);
			float age = (now - a.shownAt) / Math.max(1.0f, lifeMillis);
			float slideIn  = Math.min(1.0f, age * 6.0f);                  // first ~16%
			float fadeOut  = Math.min(1.0f, (1.0f - age) * 4.0f);         // last ~25%
			float ax = x + (1.0f - slideIn) * 28.0f;
			float alpha = fadeOut;

			drawAlert(matrix, font, a.name, ax, y, w, h, alpha);
			y -= (h + gap);
		}
	}

	private void drawAlert(Matrix4f matrix, MsdfFont font, String name,
			float x, float y, float w, float h, float alpha) {
		int bg     = ColorUtil.multiplyAlpha(0xFF1A1A24, alpha);
		int bgEnd  = ColorUtil.multiplyAlpha(0xFF23232F, alpha);
		int border = ColorUtil.multiplyAlpha(Theme.DANGER, alpha);
		int title  = ColorUtil.multiplyAlpha(Theme.DANGER, alpha);
		int sub    = ColorUtil.multiplyAlpha(Theme.TEXT_SECONDARY, alpha);

		UIRender.rectGradientH(matrix, x, y, w, h, 6.0f, bg, bgEnd);
		UIRender.border(matrix, x, y, w, h, 6.0f, 1.0f, border);
		// Vertical accent strip on the left.
		UIRender.rect(matrix, x, y, 2.5f, h, 1.0f, border);

		UIRender.text(matrix, font, "Staff online", x + 10.0f, y + 5.0f,
			9.0f, title, 0.07f);
		UIRender.text(matrix, font, name, x + 10.0f, y + 17.0f,
			8.0f, sub, 0.05f);
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
