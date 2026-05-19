package dev.blumdlc.client.modules;

import org.joml.Matrix4f;

import dev.blumdlc.client.ui.hud.HudEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

/**
 * Base class for any HUD overlay that should be draggable through the chat-screen
 * "HUD editor" mode.
 *
 * <p>A {@code HudModule} owns its {@link #x top-left position} on the scaled
 * screen. Subclasses report their currently-rendered bounding box so the
 * {@link HudEditor} can pick them up, drag them around and clamp them to the
 * window. Subclasses do NOT override {@link #onRender(Matrix4f, float)} —
 * they implement {@link #renderHud(Matrix4f, float, boolean)} instead and
 * read {@link #x}/{@link #y} for their own anchor.
 *
 * <p>Initial placement is computed lazily on the first render frame via
 * {@link #computeDefaultPosition(int, int)}, so subclasses can pick a default
 * that depends on the actual scaled screen size (e.g. right-aligned widgets).
 */
public abstract class HudModule extends Module {

	/** Top-left X in scaled screen coordinates. */
	public float x;
	/** Top-left Y in scaled screen coordinates. */
	public float y;

	private boolean positionInitialised = false;

	protected HudModule(String name, String description) {
		super(name, description, Category.RENDER);
	}

	/** Width of the HUD as currently rendered. Used for hit-testing. */
	public abstract float hudWidth();

	/** Height of the HUD as currently rendered. Used for hit-testing. */
	public abstract float hudHeight();

	/**
	 * Subclass-supplied draw routine. Called once per frame at the HUD's
	 * current {@link #x}/{@link #y}.
	 *
	 * @param editing {@code true} while the HUD editor is open. Subclasses
	 *                that normally hide when "empty" should still render a
	 *                visual stand-in in this case so the user has something
	 *                to grab.
	 */
	protected abstract void renderHud(Matrix4f matrix, float tickDelta, boolean editing);

	/**
	 * Picks the initial on-screen anchor. The default is the top-left corner
	 * with a 6 px margin; right- or bottom-anchored HUDs should override.
	 */
	protected void computeDefaultPosition(int screenWidth, int screenHeight) {
		this.x = 6.0f;
		this.y = 6.0f;
	}

	/** Forces the next frame to re-pick the default anchor. */
	public final void resetPosition() {
		this.positionInitialised = false;
	}

	@Override
	public final void onRender(Matrix4f matrix, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Window window = mc.getWindow();
		int sw = window.getScaledWidth();
		int sh = window.getScaledHeight();

		if (!positionInitialised) {
			computeDefaultPosition(sw, sh);
			positionInitialised = true;
		}

		// Clamp every frame so the HUD stays on-screen across resizes / scale changes.
		float w = Math.max(1.0f, hudWidth());
		float h = Math.max(1.0f, hudHeight());
		if (x < 0.0f)            x = 0.0f;
		if (y < 0.0f)            y = 0.0f;
		if (x + w > sw)          x = Math.max(0.0f, sw - w);
		if (y + h > sh)          y = Math.max(0.0f, sh - h);

		renderHud(matrix, tickDelta, HudEditor.isActive());
	}
}
