package dev.blumdlc.client.ui.hud;

import java.util.List;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.HudModule;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.Window;

/**
 * "HUD editor" overlay that activates whenever the player opens chat in-game.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>While {@link ChatScreen} is the current screen, every {@link HudModule}
 *       gets a highlighted hit-box drawn around it and reacts to left-click
 *       drags by updating its {@link HudModule#x}/{@link HudModule#y}.</li>
 *   <li>HUDs that normally hide themselves when there is nothing to display
 *       (e.g. {@code PotionsHud} when no effects are active) draw a placeholder
 *       through the {@code editing} flag passed to
 *       {@link HudModule#renderHud(Matrix4f, float, boolean)}.</li>
 *   <li>Mouse position is read straight from GLFW so the drag works alongside
 *       the chat input box without us having to mixin into {@code ChatScreen}.</li>
 *   <li>HUDs snap to within {@code SNAP} pixels of any screen edge.</li>
 * </ul>
 *
 * <p>The editor is a singleton with all state held in static fields — it is
 * driven from {@code BlumDLCClient}'s HUD render callback (one update + one
 * render per frame).
 */
public final class HudEditor {

	private static final float SNAP = 6.0f;

	private static boolean active = false;
	private static HudModule hovered = null;
	private static HudModule dragging = null;
	private static double dragOffX, dragOffY;
	private static boolean prevMouseDown = false;
	private static long activatedAtMs = 0L;

	private HudEditor() { }

	public static boolean isActive() {
		return active;
	}

	/** Run once per frame BEFORE HUDs render. */
	public static void update() {
		MinecraftClient mc = MinecraftClient.getInstance();
		Window window = mc.getWindow();
		if (window == null || window.getHandle() == 0L) {
			return;
		}

		boolean mouseDown = GLFW.glfwGetMouseButton(window.getHandle(),
			GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		boolean shouldBeActive = mc.currentScreen instanceof ChatScreen && mc.world != null;

		if (active != shouldBeActive) {
			// Sync edge-detect state across mode transitions so that the click
			// that opened/closed the chat doesn't immediately start a drag.
			prevMouseDown = mouseDown;
			if (!shouldBeActive) {
				dragging = null;
				hovered = null;
			} else {
				activatedAtMs = System.currentTimeMillis();
			}
		}
		active = shouldBeActive;
		if (!active) {
			return;
		}

		if (window.getWidth() == 0 || window.getHeight() == 0) {
			return;
		}
		double sx = mc.mouse.getX() * window.getScaledWidth()  / (double) window.getWidth();
		double sy = mc.mouse.getY() * window.getScaledHeight() / (double) window.getHeight();

		// Hover detection only when not currently dragging.
		if (dragging == null) {
			hovered = findAt(sx, sy);
		}

		// Mouse-down edge → begin drag if we were over a HUD.
		if (mouseDown && !prevMouseDown && hovered != null) {
			dragging = hovered;
			dragOffX = sx - dragging.x;
			dragOffY = sy - dragging.y;
		}

		// Held → update position (with edge snap).
		if (mouseDown && dragging != null) {
			float newX = (float) (sx - dragOffX);
			float newY = (float) (sy - dragOffY);
			float w = dragging.hudWidth();
			float h = dragging.hudHeight();
			int sw = window.getScaledWidth();
			int sh = window.getScaledHeight();

			if (newX < SNAP)               newX = 0.0f;
			else if (newX + w > sw - SNAP) newX = sw - w;
			if (newY < SNAP)               newY = 0.0f;
			else if (newY + h > sh - SNAP) newY = sh - h;

			dragging.x = clamp(newX, 0.0f, Math.max(0.0f, sw - w));
			dragging.y = clamp(newY, 0.0f, Math.max(0.0f, sh - h));
		}

		// Mouse-up edge → end drag.
		if (!mouseDown && prevMouseDown) {
			dragging = null;
		}

		prevMouseDown = mouseDown;
	}

	/** Run once per frame AFTER HUDs render to draw the selection overlay. */
	public static void render(Matrix4f matrix) {
		if (!active) {
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		Window window = mc.getWindow();
		MsdfFont font = Fonts.BIKO.get();

		// Hint banner top-centre.
		String hint = "HUD Editor — drag to move, close chat to lock";
		float hintFs = 8.0f;
		float hintW = UIRender.textWidth(font, hint, hintFs) + 18.0f;
		float hintH = hintFs + 10.0f;
		float hintX = (window.getScaledWidth() - hintW) * 0.5f;
		float hintY = 4.0f;

		// Subtle pop-in animation for the banner.
		float since = (System.currentTimeMillis() - activatedAtMs) / 220.0f;
		if (since > 1.0f) since = 1.0f;
		float bannerAlpha = since;

		UIRender.rect(matrix, hintX + 1.0f, hintY + 2.0f, hintW, hintH, hintH * 0.5f,
			ColorUtil.multiplyAlpha(0xFF000000, bannerAlpha * 0.35f));
		UIRender.rectGradientH(matrix, hintX, hintY, hintW, hintH, hintH * 0.5f,
			ColorUtil.multiplyAlpha(0xFF1A1A24, bannerAlpha),
			ColorUtil.multiplyAlpha(0xFF23232F, bannerAlpha));
		UIRender.border(matrix, hintX, hintY, hintW, hintH, hintH * 0.5f, 1.0f,
			ColorUtil.multiplyAlpha(Theme.PANEL_BORDER, bannerAlpha));
		UIRender.text(matrix, font, hint, hintX + 9.0f, hintY + 5.0f,
			hintFs, ColorUtil.multiplyAlpha(Theme.TEXT_PRIMARY, bannerAlpha), 0.05f);

		// Per-HUD selection box.
		List<Module> all = BlumDLC.MODULES.all();
		for (Module m : all) {
			if (m instanceof HudModule h && h.enabled) {
				drawSelection(matrix, font, h, h == hovered, h == dragging);
			}
		}
	}

	// =========================================================================
	//  internals
	// =========================================================================

	private static HudModule findAt(double sx, double sy) {
		List<Module> all = BlumDLC.MODULES.all();
		// Iterate in reverse so the topmost-rendered HUD wins ties.
		for (int i = all.size() - 1; i >= 0; i--) {
			Module m = all.get(i);
			if (m instanceof HudModule h && h.enabled) {
				float w = h.hudWidth();
				float hh = h.hudHeight();
				if (w > 0.0f && hh > 0.0f
				    && sx >= h.x && sx < h.x + w
				    && sy >= h.y && sy < h.y + hh) {
					return h;
				}
			}
		}
		return null;
	}

	private static void drawSelection(Matrix4f m, MsdfFont font, HudModule h,
	                                  boolean isHover, boolean isDrag) {
		float x = h.x;
		float y = h.y;
		float w = h.hudWidth();
		float hh = h.hudHeight();
		if (w <= 0.0f || hh <= 0.0f) {
			return;
		}

		int border;
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() * 0.005);
		if (isDrag) {
			border = Theme.ACCENT;
		} else if (isHover) {
			border = ColorUtil.lerp(Theme.ACCENT_DARK, Theme.ACCENT, pulse);
		} else {
			border = ColorUtil.withAlpha(0xFFFFFFFF, 0.30f);
		}

		// Soft tinted backdrop for the active card.
		if (isHover || isDrag) {
			UIRender.rect(m, x, y, w, hh, 4.0f, 0x14FFFFFF);
		}
		UIRender.border(m, x - 1.5f, y - 1.5f, w + 3.0f, hh + 3.0f, 5.0f, 1.5f, border);

		// Floating label above (or below, if we'd clip the screen top).
		String label = h.name;
		float fs = 7.0f;
		float lw = UIRender.textWidth(font, label, fs) + 10.0f;
		float lh = fs + 4.0f;
		float lx = x;
		float ly = y - lh - 2.0f;
		if (ly < 0.0f) ly = y + hh + 2.0f;

		UIRender.rect(m, lx, ly, lw, lh, lh * 0.5f, ColorUtil.withAlpha(Theme.PANEL_BG, 0.95f));
		UIRender.border(m, lx, ly, lw, lh, lh * 0.5f, 1.0f, Theme.PANEL_BORDER);
		UIRender.text(m, font, label, lx + 5.0f, ly + 2.5f, fs, Theme.ACCENT, 0.06f);
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}
}
