package dev.blumdlc.client.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Pure-math helpers used by combat modules to compute and step rotations
 * toward an aim point. All angles are in degrees and use the Minecraft
 * convention (yaw=0 looks toward +Z, pitch positive looks down).
 */
public final class RotationUtil {

	private RotationUtil() {
	}

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
	 * Convenience: aim {@code from} at {@code targetEntity}'s bounding-box
	 * center.
	 */
	public static float[] aimAt(Vec3d from, Entity targetEntity) {
		return aimAt(from, targetEntity.getBoundingBox().getCenter());
	}
}
