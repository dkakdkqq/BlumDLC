package dev.blumdlc.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Pure-math helpers used by combat modules to compute and step rotations
 * toward an aim point. All angles are in degrees and use the Minecraft
 * convention (yaw=0 looks toward +Z, pitch positive looks down).
 *
 * <p>This is the single source of truth for everything rotation-related:
 * computing aim angles, stepping toward them with humanlike easing,
 * quantising to the user's mouse-sensitivity GCD (so synthetic deltas
 * are indistinguishable from a real mouse), picking visible aim points
 * inside an entity's hitbox, and predicting target motion.
 */
public final class RotationUtil {

	private RotationUtil() {
	}

	// =========================================================================
	// Aim-angle math
	// =========================================================================

	/**
	 * Compute the yaw/pitch (in degrees) that points {@code from} at
	 * {@code target}.
	 */
	public static float[] aimAt(Vec3d from, Vec3d target) {
		double dx = target.x - from.x;
		double dy = target.y - from.y;
		double dz = target.z - from.z;
		double dh = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
		return new float[] { MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.5f, 89.5f) };
	}

	/**
	 * Convenience: aim {@code from} at {@code targetEntity}'s bounding-box
	 * center.
	 */
	public static float[] aimAt(Vec3d from, Entity targetEntity) {
		return aimAt(from, targetEntity.getBoundingBox().getCenter());
	}

	/** Direction unit vector for the given yaw/pitch (degrees). */
	public static Vec3d directionFromAngles(float yaw, float pitch) {
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		double cosPitch = Math.cos(pitchRad);
		return new Vec3d(
			-Math.sin(yawRad) * cosPitch,
			-Math.sin(pitchRad),
			 Math.cos(yawRad) * cosPitch
		);
	}

	/**
	 * @return the smallest absolute angle (degrees) between two yaw/pitch
	 *         orientations, computed via the dot-product of their direction
	 *         vectors. Useful for "is my rotation actually pointing at the
	 *         target yet?" gating.
	 */
	public static float angleBetween(float yaw1, float pitch1, float yaw2, float pitch2) {
		Vec3d a = directionFromAngles(yaw1, pitch1);
		Vec3d b = directionFromAngles(yaw2, pitch2);
		double dot = MathHelper.clamp(a.dotProduct(b), -1.0, 1.0);
		return (float) Math.toDegrees(Math.acos(dot));
	}

	// =========================================================================
	// Step utilities (linear cap; legacy-friendly)
	// =========================================================================

	/**
	 * Step {@code current} toward {@code target} by at most {@code maxDelta}
	 * degrees, taking the shortest signed path on the yaw circle.
	 */
	public static float approachYaw(float current, float target, float maxDelta) {
		float delta = MathHelper.wrapDegrees(target - current);
		float clamped = MathHelper.clamp(delta, -maxDelta, maxDelta);
		return MathHelper.wrapDegrees(current + clamped);
	}

	/**
	 * Step {@code current} toward {@code target} by at most {@code maxDelta}
	 * degrees on the pitch axis, with the result clamped to the legal pitch
	 * range.
	 */
	public static float approachPitch(float current, float target, float maxDelta) {
		float clampedTarget = MathHelper.clamp(target, -89.5f, 89.5f);
		float delta = MathHelper.clamp(clampedTarget - current, -maxDelta, maxDelta);
		return MathHelper.clamp(current + delta, -89.5f, 89.5f);
	}

	/**
	 * Ease-out step on a single axis: take a fraction {@code factor} of the
	 * remaining delta but clamp the resulting magnitude to the
	 * [{@code minStep}, {@code maxStep}] window. Always converges (never
	 * overshoots), and decelerates as we close in — which is what makes the
	 * motion read as "human" instead of "programmed".
	 *
	 * @param delta   signed remaining angle (target - current)
	 * @param factor  proportional gain (0..1); higher = snappier
	 * @param minStep minimum magnitude per tick (degrees), unless
	 *                {@code |delta|} is already below it
	 * @param maxStep upper cap on per-tick magnitude (degrees)
	 */
	public static float easeStep(float delta, float factor, float minStep, float maxStep) {
		if (delta == 0.0f) {
			return 0.0f;
		}
		float sign = Math.signum(delta);
		float absDelta = Math.abs(delta);
		float magnitude = absDelta * factor;
		if (magnitude < minStep) {
			magnitude = Math.min(absDelta, minStep);
		}
		if (magnitude > maxStep) {
			magnitude = maxStep;
		}
		if (magnitude > absDelta) {
			magnitude = absDelta;
		}
		return sign * magnitude;
	}

	// =========================================================================
	// Mouse-sensitivity quantisation (anti-cheat-friendly rotations)
	// =========================================================================

	/**
	 * Minecraft's mouse-step quantum at the user's current sensitivity. Real
	 * mouse input always produces rotation deltas that are integer multiples
	 * of this value — quantising our synthetic deltas to it makes them
	 * indistinguishable from a player physically moving the mouse.
	 *
	 * <p>Derivation (matches vanilla's {@code MouseHandler}):
	 * <pre>
	 *     d   = sensitivity * 0.6 + 0.2
	 *     gcd = d^3 * 8 * 0.15
	 * </pre>
	 */
	public static float mouseGcd() {
		try {
			double sens = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
			float f = (float) (sens * 0.6 + 0.2);
			return f * f * f * 8.0f * 0.15f;
		} catch (Throwable t) {
			// During headless tests / very early init the options object may
			// not be ready yet; fall back to the default-50% GCD.
			return 0.15f;
		}
	}

	/**
	 * Snap a candidate next angle so the delta from {@code current} is an
	 * integer multiple of the user's {@link #mouseGcd() mouse GCD}.
	 */
	public static float quantizeToMouseGcd(float current, float next) {
		float gcd = mouseGcd();
		if (gcd <= 0.0001f) {
			return next;
		}
		float delta = next - current;
		int steps = Math.round(delta / gcd);
		return current + steps * gcd;
	}

	// =========================================================================
	// Hitbox aim-point selection (with line-of-sight)
	// =========================================================================

	/**
	 * Closest point inside (or on the surface of) {@code box} to
	 * {@code origin} — equivalent to projecting {@code origin} onto the box.
	 */
	public static Vec3d closestPointInBox(Vec3d origin, Box box) {
		return new Vec3d(
			MathHelper.clamp(origin.x, box.minX, box.maxX),
			MathHelper.clamp(origin.y, box.minY, box.maxY),
			MathHelper.clamp(origin.z, box.minZ, box.maxZ)
		);
	}

	/**
	 * Find a visible (raycast-clear) aim point inside {@code target}'s
	 * bounding box, preferring the closest point to the user's eyes (the
	 * most natural place to aim — minimises the rotation we have to make).
	 *
	 * <p>Falls back through a small set of canonical aim points (eyes, body
	 * centre, top, sides) and finally returns the box centre if every
	 * sampled point is occluded — callers that want strict line-of-sight
	 * should pair this with their own visibility check.
	 *
	 * @param maxRangeSq squared max range; points farther than this are
	 *                   skipped (avoids picking a corner of the hitbox we
	 *                   wouldn't be allowed to reach anyway)
	 */
	public static Vec3d findVisibleAimPoint(World world, Vec3d eyes, Entity target,
			Entity self, double maxRangeSq) {
		Box box = target.getBoundingBox();

		// Best candidate: closest point in box to the eyes — the natural aim.
		Vec3d closest = closestPointInBox(eyes, box);
		if (eyes.squaredDistanceTo(closest) <= maxRangeSq && canSee(world, eyes, closest, self)) {
			return closest;
		}

		Vec3d center = box.getCenter();
		double eyeY = (target instanceof LivingEntity le) ? le.getEyeY() : center.y;

		// Ordered fallbacks: eyes → centre → upper torso → corners.
		Vec3d[] candidates = new Vec3d[] {
			new Vec3d(center.x, eyeY,                 center.z),
			center,
			new Vec3d(center.x, box.maxY - 0.10,      center.z),
			new Vec3d(center.x, box.minY + 0.40,      center.z),
			new Vec3d(box.minX + 0.05, center.y,      center.z),
			new Vec3d(box.maxX - 0.05, center.y,      center.z),
			new Vec3d(center.x, center.y,             box.minZ + 0.05),
			new Vec3d(center.x, center.y,             box.maxZ - 0.05)
		};
		for (Vec3d c : candidates) {
			if (eyes.squaredDistanceTo(c) <= maxRangeSq && canSee(world, eyes, c, self)) {
				return c;
			}
		}
		return center;
	}

	/**
	 * Raycast from {@code from} to {@code to}, ignoring entities. Returns
	 * true when nothing blocks the line (i.e. either no hit or the hit is
	 * effectively at the destination).
	 */
	public static boolean canSee(World world, Vec3d from, Vec3d to, Entity self) {
		HitResult hit = world.raycast(new RaycastContext(from, to,
			RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE,
			self));
		if (hit.getType() == HitResult.Type.MISS) {
			return true;
		}
		// Treat "stopped right at the target" as a hit.
		return hit.getPos().squaredDistanceTo(to) < 0.01;
	}

	// =========================================================================
	// Motion prediction
	// =========================================================================

	/**
	 * Predict where {@code target}'s bounding-box centre will be in
	 * {@code ticks} ticks based on its current velocity. Useful for leading
	 * the aim toward a moving entity instead of always pointing at where it
	 * was last tick.
	 */
	public static Vec3d predictPosition(LivingEntity target, double ticks) {
		Vec3d velocity = target.getVelocity();
		Vec3d pos = target.getBoundingBox().getCenter();
		return new Vec3d(
			pos.x + velocity.x * ticks,
			pos.y + velocity.y * ticks,
			pos.z + velocity.z * ticks
		);
	}
}
