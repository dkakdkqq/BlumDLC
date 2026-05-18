package dev.blumdlc.client.util;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Projects 3D world-space coordinates to scaled-screen (HUD) coordinates.
 *
 * <p>Usage:
 * <ol>
 *   <li>From a world-render hook (e.g. {@code WorldRenderEvents.LAST}), call
 *       {@link #capture(Matrix4f, Matrix4f, Camera)} every frame to record the
 *       current view/projection matrices and camera position.</li>
 *   <li>From a HUD-render hook, call {@link #project(double, double, double)}
 *       to map a world-space point to scaled-screen pixels (the same space
 *       that {@code DrawContext} draws in).</li>
 * </ol>
 *
 * The returned {@link Result} carries an {@code onScreen} flag; callers should
 * check it before drawing, otherwise points behind the camera will be drawn
 * mirrored in front of it.
 */
public final class Projection {

	/** Stable view matrix from the last world render. */
	private static final Matrix4f VIEW = new Matrix4f();
	/** Stable projection matrix from the last world render. */
	private static final Matrix4f PROJECTION = new Matrix4f();
	/** Camera position from the last world render. */
	private static double camX, camY, camZ;
	private static boolean ready;

	private Projection() {
	}

	/** Captured per frame from a world render hook. */
	public static void capture(Matrix4f view, Matrix4f projection, Camera camera) {
		VIEW.set(view);
		PROJECTION.set(projection);
		Vec3d pos = camera.getPos();
		camX = pos.x;
		camY = pos.y;
		camZ = pos.z;
		ready = true;
	}

	/**
	 * Project a world-space point to scaled-screen coordinates.
	 *
	 * @return a {@link Result}; check {@link Result#onScreen()} before
	 *         drawing.
	 */
	public static Result project(double worldX, double worldY, double worldZ) {
		if (!ready) {
			return Result.OFFSCREEN;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		double scale = client.getWindow().getScaleFactor();
		float scaledW = (float) (client.getWindow().getFramebufferWidth() / scale);
		float scaledH = (float) (client.getWindow().getFramebufferHeight() / scale);

		Vector4f v = new Vector4f(
			(float) (worldX - camX),
			(float) (worldY - camY),
			(float) (worldZ - camZ),
			1.0f);
		VIEW.transform(v);
		PROJECTION.transform(v);

		// Behind the near plane -> not visible
		if (v.w <= 0.0f) {
			return Result.OFFSCREEN;
		}

		float invW = 1.0f / v.w;
		float ndcX = v.x * invW;
		float ndcY = v.y * invW;
		float ndcZ = v.z * invW;

		// NDC (-1..1) -> scaled-screen pixels (y flipped)
		float screenX = (ndcX * 0.5f + 0.5f) * scaledW;
		float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * scaledH;

		boolean visible = ndcZ >= -1.0f && ndcZ <= 1.0f
			&& screenX >= 0.0f && screenX <= scaledW
			&& screenY >= 0.0f && screenY <= scaledH;

		return new Result(screenX, screenY, true, visible);
	}

	/** Convenience overload. */
	public static Result project(Vec3d worldPos) {
		return project(worldPos.x, worldPos.y, worldPos.z);
	}

	/** Result of a world->screen projection. */
	public record Result(float x, float y, boolean inFront, boolean inViewport) {
		public static final Result OFFSCREEN = new Result(0.0f, 0.0f, false, false);

		/**
		 * @return {@code true} when the projected point is in front of the
		 *         camera. Use this — not {@code inViewport} — to gate drawing
		 *         off-screen indicators that should still hug the screen
		 *         edges.
		 */
		public boolean onScreen() {
			return inFront;
		}
	}
}
