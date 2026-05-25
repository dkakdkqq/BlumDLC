package dev.blumdlc.client.modules.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.util.Friends;
import dev.blumdlc.client.util.RotationUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * AttackAura — auto-attacks the nearest valid entity within range.
 *
 * <p>Each tick (when in-game and the configured "extras" allow it) the module:
 * <ol>
 *   <li>{@linkplain #pickTarget picks} the best target according to the
 *       configured priority + range/vision/FOV, debouncing target switches so
 *       we don't ping-pong between two entities every tick;</li>
 *   <li>finds a {@linkplain RotationUtil#findVisibleAimPoint visible aim
 *       point} inside the target's hitbox (so we aim where we can actually
 *       hit, not always at the bbox centre);</li>
 *   <li>steps the rotation toward that point through the
 *       {@linkplain #rotations selected rotation profile}
 *       (eased, jittered and quantised to the user's mouse-sensitivity GCD
 *       for {@code ReallyWorld} / {@code SpookyTime});</li>
 *   <li>delivers the rotation either silently (rotation-only packet sent
 *       AFTER vanilla's per-tick movement packet, so the server sees ours
 *       last) or by writing it onto the player ({@code Targeted} —
 *       movement-correcting visible camera);</li>
 *   <li>clicks once vanilla's attack cooldown is full, the user-configured
 *       CPS gate has elapsed, the rotation is actually pointing at the hit
 *       point (within the {@code aimTolerance}) and every constraint
 *       (criticals only, weapon required, blocking, ...) passes.</li>
 * </ol>
 */
public final class AttackAura extends Module {

	// =========================================================================
	// Settings
	// =========================================================================

	public final NumberSetting range;
	public final NumberSetting vision;
	public final NumberSetting fov;
	public final ModeSetting   priority;
	public final ModeSetting   rotations;
	public final NumberSetting cps;
	public final NumberSetting switchDelayMs;
	public final NumberSetting aimTolerance;
	public final MultiSetting  targets;
	public final ModeSetting   movementCorrection;
	public final MultiSetting  extras;

	// =========================================================================
	// Runtime state
	// =========================================================================

	private LivingEntity target;
	/** Server-facing yaw used as the basis for the next rotation step. */
	private float serverYaw;
	/** Server-facing pitch used as the basis for the next rotation step. */
	private float serverPitch;
	/** True while we're maintaining a synthetic rotation (i.e. have a target). */
	private boolean rotating;

	/** Wall-clock timestamp of the last successful attack, for CPS pacing. */
	private long lastAttackMs;
	/** Wall-clock timestamp at which the current target was first locked. */
	private long targetLockedAtMs;

	/** Resolved aim point inside the current target's hitbox (cached for HUDs). */
	private Vec3d currentAimPoint;

	/**
	 * Aim point as an offset from the target's bounding-box centre, kept
	 * across ticks so we don't pick a brand-new spot every frame. The
	 * resulting absolute position is recomputed each tick from the live
	 * box centre — so the offset tracks the target as it moves while the
	 * picked spot ("head", "left side", ...) stays stable.
	 */
	private Vec3d cachedAimOffset;
	/** Identity guard so the cached offset is invalidated on target swap. */
	private LivingEntity cachedAimFor;

	private final Random random = new Random();

	// =========================================================================
	// Construction
	// =========================================================================

	public AttackAura() {
		super("AttackAura", "Auto-attacks nearby targets in range", Category.COMBAT);

		this.range  = new NumberSetting("Range",  4.5, 3.0, 6.0, 0.05);
		this.vision = new NumberSetting("Vision", 4.0, 2.5, 6.0, 0.05);
		this.fov    = new NumberSetting("FOV",  180.0, 30.0, 360.0, 5.0);

		this.priority = new ModeSetting("Priority", "Distance",
			"Distance", "Health", "HurtTime", "FOV");

		// ReallyWorld is now the default — eased + jittered + GCD-quantised.
		this.rotations = new ModeSetting("Rotations", "ReallyWorld",
			"ReallyWorld", "FunTime", "HolyWorld", "SpookyTime");

		this.cps           = new NumberSetting("CPS",                  9.0,   1.0,   20.0,  0.1);
		this.switchDelayMs = new NumberSetting("Switch delay (ms)",  220.0,   0.0, 1500.0, 10.0);
		this.aimTolerance  = new NumberSetting("Aim tolerance",        7.0,   1.0,   60.0,  0.5);

		this.targets = new MultiSetting("Target",
			List.of("Players", "Animals", "Mobs", "Friends"),
			"Players");

		this.movementCorrection = new ModeSetting("Movement Correction",
			"Free", "Free", "Targeted");

		this.extras = new MultiSetting("Extras",
			List.of(
				"Don't hit while eating",
				"Don't hit while blocking",
				"Don't hit in inventory",
				"Only with weapon",
				"Only criticals",
				"Sprint reset",
				"Through walls"
			));

		addSetting(this.range);
		addSetting(this.vision);
		addSetting(this.fov);
		addSetting(this.priority);
		addSetting(this.rotations);
		addSetting(this.cps);
		addSetting(this.switchDelayMs);
		addSetting(this.aimTolerance);
		addSetting(this.targets);
		addSetting(this.movementCorrection);
		addSetting(this.extras);
	}

	@Override
	protected void onDisable() {
		this.target = null;
		this.rotating = false;
		this.currentAimPoint = null;
		this.cachedAimOffset = null;
		this.cachedAimFor = null;
	}

	// =========================================================================
	// Tick loop
	// =========================================================================

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		ClientPlayNetworkHandler net = client.getNetworkHandler();
		if (player == null || world == null || net == null) {
			releaseTarget();
			return;
		}

		// "Soft" gates that pause behaviour without losing the target lock.
		if (extras.isSelected("Don't hit in inventory") && client.currentScreen instanceof HandledScreen<?>) {
			releaseTarget();
			return;
		}
		if (extras.isSelected("Don't hit while eating") && player.isUsingItem()) {
			releaseTarget();
			return;
		}
		if (extras.isSelected("Don't hit while blocking") && player.isBlocking()) {
			releaseTarget();
			return;
		}
		if (extras.isSelected("Only with weapon") && !isWeapon(player.getMainHandStack())) {
			releaseTarget();
			return;
		}

		// 1. Pick the best target.
		LivingEntity picked = pickTarget(player, world);
		if (picked == null) {
			releaseTarget();
			return;
		}
		commitTarget(picked);

		// 2. Find a hit point inside the target's hitbox we can actually
		//    see, preserving last tick's choice for stability so the
		//    rotation isn't yanked between equally good fallback points
		//    every frame. resolveAimPoint also handles "Through walls".
		Vec3d eyes = player.getCameraPosVec(1.0f);
		double rangeSq = range.get() * range.get();
		boolean throughWalls = extras.isSelected("Through walls");
		Vec3d aimPoint = resolveAimPoint(world, eyes, target, player, rangeSq, throughWalls);
		this.currentAimPoint = aimPoint;

		// 3. Rotate toward the aim point.
		float[] aim = RotationUtil.aimAt(eyes, aimPoint);
		applyRotationStep(player, aim[0], aim[1]);

		// 4. Click — but only when everything is ready.
		if (canAttackNow(player, eyes, aimPoint, aim, world, throughWalls)) {
			swingAndAttack(client, player, this.target);
		}
	}

	private void releaseTarget() {
		boolean wasRotating = this.rotating;
		// In "Free" mode the server has been holding our synthetic rotation
		// since the last LookAndOnGround we sent. Vanilla won't update it
		// until the user physically moves the mouse — which means that until
		// they do, manual attacks land at our last fake angle, not at the
		// crosshair. Send one final rotation packet that snaps the server
		// back to the player's actual local rotation.
		if (wasRotating && movementCorrection.is("Free")) {
			MinecraftClient client = MinecraftClient.getInstance();
			ClientPlayerEntity player = client.player;
			ClientPlayNetworkHandler net = client.getNetworkHandler();
			if (player != null && net != null) {
				net.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
					player.getYaw(), player.getPitch(),
					player.isOnGround(), player.horizontalCollision));
			}
		}
		this.target = null;
		this.rotating = false;
		this.currentAimPoint = null;
		this.cachedAimOffset = null;
		this.cachedAimFor = null;
	}

	private void commitTarget(LivingEntity picked) {
		if (this.target != picked) {
			this.target = picked;
			this.targetLockedAtMs = System.currentTimeMillis();
			this.cachedAimOffset = null;
			this.cachedAimFor = null;
		}
	}

	// =========================================================================
	// Target selection
	// =========================================================================

	private LivingEntity pickTarget(ClientPlayerEntity self, ClientWorld world) {
		double maxReach = Math.max(range.get(), vision.get());
		double maxReachSq = maxReach * maxReach;
		double rangeSq = range.get() * range.get();
		double visionSq = vision.get() * vision.get();
		Vec3d eyes = self.getCameraPosVec(1.0f);
		float fovDeg = fov.getFloat();
		boolean throughWalls = extras.isSelected("Through walls");
		long now = System.currentTimeMillis();
		long switchHoldMs = (long) Math.round(switchDelayMs.get());

		LivingEntity best = null;
		double bestScore = Double.POSITIVE_INFINITY;

		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			if (living == self || !living.isAlive() || living.isRemoved()) {
				continue;
			}
			if (!isAllowedType(living)) {
				continue;
			}

			double distSq = eyes.squaredDistanceTo(living.getBoundingBox().getCenter());
			if (distSq > maxReachSq) {
				continue;
			}

			boolean visible = throughWalls || hasLineOfSight(eyes, living, world);
			if (visible) {
				if (distSq > rangeSq) {
					continue;
				}
			} else {
				if (distSq > visionSq) {
					continue;
				}
			}

			if (fovDeg < 360.0f && !withinFov(self, living, fovDeg)) {
				continue;
			}

			double score = score(living, distSq, self);
			if (score < bestScore) {
				bestScore = score;
				best = living;
			}
		}

		// Switch debounce: if we already had a valid target and not enough
		// time has passed since lock, stick with it (provided it's still
		// alive and roughly within range). Stops the aura from
		// ping-ponging between two equally-eligible targets every tick.
		if (best != null && this.target != null && this.target != best
			&& this.target.isAlive() && !this.target.isRemoved()
			&& (now - targetLockedAtMs) < switchHoldMs) {
			double curDistSq = eyes.squaredDistanceTo(this.target.getBoundingBox().getCenter());
			if (curDistSq <= maxReachSq && isAllowedType(this.target)) {
				return this.target;
			}
		}
		return best;
	}

	private double score(LivingEntity entity, double distSq, ClientPlayerEntity self) {
		// Lower score = better.
		return switch (priority.get()) {
			case "Health"   -> entity.getHealth() + entity.getAbsorptionAmount();
			case "HurtTime" -> -entity.hurtTime; // recently hit = stay on it
			case "FOV"      -> fovOffsetDegrees(self, entity);
			case "Distance" -> distSq;
			default         -> distSq;
		};
	}

	private boolean isAllowedType(LivingEntity entity) {
		if (entity instanceof PlayerEntity player) {
			boolean isFriend = Friends.contains(player);
			if (isFriend) {
				return targets.isSelected("Friends");
			}
			return targets.isSelected("Players");
		}
		if (entity instanceof PassiveEntity) {
			return targets.isSelected("Animals");
		}
		if (entity instanceof HostileEntity) {
			return targets.isSelected("Mobs");
		}
		return targets.isSelected("Mobs");
	}

	/** True when {@code entity}'s direction from the player is within ±fov/2. */
	private boolean withinFov(ClientPlayerEntity self, LivingEntity entity, float fovDeg) {
		return fovOffsetDegrees(self, entity) <= fovDeg * 0.5f;
	}

	private float fovOffsetDegrees(ClientPlayerEntity self, LivingEntity entity) {
		Vec3d eyes = self.getCameraPosVec(1.0f);
		Vec3d to = entity.getBoundingBox().getCenter().subtract(eyes);
		Vec3d look = self.getRotationVec(1.0f);
		double dot = to.normalize().dotProduct(look.normalize());
		return (float) Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0)));
	}

	// =========================================================================
	// Rotation step (per profile)
	// =========================================================================

	private void applyRotationStep(ClientPlayerEntity player, float wantedYaw, float wantedPitch) {
		float curYaw   = this.rotating ? this.serverYaw   : player.getYaw();
		float curPitch = this.rotating ? this.serverPitch : player.getPitch();

		float deltaYaw = MathHelper.wrapDegrees(wantedYaw - curYaw);
		float deltaPitch = MathHelper.clamp(wantedPitch, -89.5f, 89.5f) - curPitch;

		float[] step = computeStep(deltaYaw, deltaPitch);

		float nextYaw = MathHelper.wrapDegrees(curYaw + step[0]);
		float nextPitch = MathHelper.clamp(curPitch + step[1], -89.5f, 89.5f);

		// Quantise to mouse GCD so the on-the-wire delta looks like a real
		// mouse step. ReallyWorld + SpookyTime always quantise; faster
		// profiles (snappy, intended-to-look-cheaty) skip it for speed.
		String profile = rotations.get();
		if ("ReallyWorld".equals(profile) || "SpookyTime".equals(profile)) {
			nextYaw = RotationUtil.quantizeToMouseGcd(curYaw, nextYaw);
			nextPitch = RotationUtil.quantizeToMouseGcd(curPitch, nextPitch);
			nextYaw = MathHelper.wrapDegrees(nextYaw);
			nextPitch = MathHelper.clamp(nextPitch, -89.5f, 89.5f);
		}

		this.serverYaw = nextYaw;
		this.serverPitch = nextPitch;
		this.rotating = true;

		boolean visible = movementCorrection.is("Targeted");
		if (visible) {
			// "Targeted": move the actual player rotation so the camera
			// follows the target. Vanilla's tick movement packet (sent BEFORE
			// our onTick callback) already shipped this tick's rotation, so
			// the new yaw/pitch will be visible to the server next tick.
			player.setYaw(nextYaw);
			player.setPitch(nextPitch);
			player.setHeadYaw(nextYaw);
			player.setBodyYaw(nextYaw);
		} else {
			// "Free": send a rotation-only packet AFTER vanilla's per-tick
			// movement packet, so the server sees our rotation last while the
			// local camera stays untouched.
			ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
			if (net != null) {
				net.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
					nextYaw, nextPitch, player.isOnGround(), player.horizontalCollision));
			}
		}
	}

	/**
	 * Per-profile step calculation. Returns a 2-element {@code [dYaw, dPitch]}
	 * delta to add to the current rotation.
	 *
	 * <ul>
	 *   <li><b>FunTime</b>: snappy — closes ~85% of the remaining delta per
	 *       tick. No jitter, no quantisation. Fastest, most "obviously a bot".</li>
	 *   <li><b>HolyWorld</b>: medium — eased step, no jitter.</li>
	 *   <li><b>ReallyWorld</b>: human-like — eased step with magnitude that
	 *       grows with remaining distance, plus small correlated jitter and
	 *       GCD quantisation. Default profile.</li>
	 *   <li><b>SpookyTime</b>: very slow + smooth — heavy easing, small
	 *       jitter, GCD quantisation. Reads as a careful human.</li>
	 * </ul>
	 */
	private float[] computeStep(float deltaYaw, float deltaPitch) {
		float stepYaw, stepPitch;
		float jitterYaw = 0.0f, jitterPitch = 0.0f;

		switch (rotations.get()) {
			case "FunTime" -> {
				stepYaw   = deltaYaw   * 0.85f;
				stepPitch = deltaPitch * 0.85f;
			}
			case "HolyWorld" -> {
				stepYaw   = RotationUtil.easeStep(deltaYaw,   0.45f, 4.0f, 35.0f);
				stepPitch = RotationUtil.easeStep(deltaPitch, 0.45f, 4.0f, 25.0f);
			}
			case "SpookyTime" -> {
				stepYaw   = RotationUtil.easeStep(deltaYaw,   0.22f, 2.0f, 14.0f);
				stepPitch = RotationUtil.easeStep(deltaPitch, 0.22f, 1.5f, 10.0f);
				jitterYaw   = jitter(0.18f, deltaYaw);
				jitterPitch = jitter(0.14f, deltaPitch);
			}
			case "ReallyWorld" -> {
				// Distance-aware ease: bigger remaining angle → faster step,
				// so we close 60° flicks in 3-4 ticks but still glide in
				// gently for the final degree.
				float remaining = Math.abs(deltaYaw) + Math.abs(deltaPitch);
				float boost = MathHelper.clamp(remaining / 50.0f, 0.0f, 1.0f);
				float yawFactor   = 0.38f + 0.12f * boost;     // 0.38..0.50
				float pitchFactor = 0.42f + 0.10f * boost;     // 0.42..0.52
				stepYaw   = RotationUtil.easeStep(deltaYaw,   yawFactor,   3.5f, 28.0f);
				stepPitch = RotationUtil.easeStep(deltaPitch, pitchFactor, 3.0f, 18.0f);
				jitterYaw   = jitter(0.40f, deltaYaw);
				jitterPitch = jitter(0.28f, deltaPitch);
			}
			default -> {
				stepYaw   = RotationUtil.easeStep(deltaYaw,   0.40f, 4.0f, 30.0f);
				stepPitch = RotationUtil.easeStep(deltaPitch, 0.40f, 3.0f, 20.0f);
			}
		}

		// Apply jitter, but never overshoot — if the jittered step would push
		// us past the target, clip it back to the un-jittered step.
		stepYaw   = applyJitterClipped(stepYaw,   jitterYaw,   deltaYaw);
		stepPitch = applyJitterClipped(stepPitch, jitterPitch, deltaPitch);

		return new float[] { stepYaw, stepPitch };
	}

	/** Symmetric jitter with magnitude scaled down when we're already close. */
	private float jitter(float maxMagnitude, float remainingDelta) {
		// When we're very close (delta small), suppress jitter so we lock on.
		float scale = MathHelper.clamp(Math.abs(remainingDelta) / 6.0f, 0.0f, 1.0f);
		if (scale <= 0.05f) {
			return 0.0f;
		}
		return (random.nextFloat() * 2.0f - 1.0f) * maxMagnitude * scale;
	}

	private static float applyJitterClipped(float step, float jitter, float remainingDelta) {
		float candidate = step + jitter;
		if (remainingDelta == 0.0f) {
			return 0.0f;
		}
		// Don't let jitter flip the sign or overshoot the remaining delta.
		if (Math.signum(remainingDelta) != Math.signum(candidate) && candidate != 0.0f) {
			candidate = step;
		}
		if (Math.abs(candidate) > Math.abs(remainingDelta)) {
			candidate = remainingDelta;
		}
		return candidate;
	}

	// =========================================================================
	// Aim-point resolution (sticky cache)
	// =========================================================================

	/**
	 * Pick a hit point inside {@code target}'s bounding box, preferring last
	 * tick's choice when it's still reachable + visible. Caching the offset
	 * (rather than the absolute position) means the aim point follows the
	 * target as it moves while staying glued to the same body part — this is
	 * what makes the rotation feel like "lock-on" instead of "snapping
	 * around the target every frame".
	 */
	private Vec3d resolveAimPoint(ClientWorld world, Vec3d eyes, LivingEntity target,
			ClientPlayerEntity self, double rangeSq, boolean throughWalls) {
		Box box = target.getBoundingBox();
		Vec3d center = box.getCenter();

		if (this.cachedAimFor == target && this.cachedAimOffset != null) {
			Vec3d candidate = new Vec3d(
				MathHelper.clamp(center.x + cachedAimOffset.x, box.minX, box.maxX),
				MathHelper.clamp(center.y + cachedAimOffset.y, box.minY, box.maxY),
				MathHelper.clamp(center.z + cachedAimOffset.z, box.minZ, box.maxZ)
			);
			boolean reachable = eyes.squaredDistanceTo(candidate) <= rangeSq
				&& (throughWalls || RotationUtil.canSee(world, eyes, candidate, self));
			if (reachable) {
				return candidate;
			}
			// Cached point became occluded or out of range → fall through and
			// recompute.
		}

		Vec3d resolved = throughWalls
			? RotationUtil.closestPointInBox(eyes, box)
			: RotationUtil.findVisibleAimPoint(world, eyes, target, self, rangeSq);
		this.cachedAimOffset = resolved.subtract(center);
		this.cachedAimFor = target;
		return resolved;
	}

	// =========================================================================
	// Attack gating
	// =========================================================================

	private boolean canAttackNow(ClientPlayerEntity player, Vec3d eyes,
			Vec3d aimPoint, float[] desiredAim, ClientWorld world, boolean throughWalls) {
		// Vanilla cooldown gate (use 0.92 safety margin so we don't waste a
		// click on a 99% cooldown — at 20 tps the next tick is a full hit).
		if (player.getAttackCooldownProgress(0.0f) < 0.92f) {
			return false;
		}

		// CPS gate: throttle to the configured attacks-per-second.
		long now = System.currentTimeMillis();
		long minIntervalMs = (long) Math.max(50.0, 1000.0 / Math.max(0.1, cps.get()));
		if (now - lastAttackMs < minIntervalMs) {
			return false;
		}

		// "Only criticals" gate.
		if (extras.isSelected("Only criticals") && !isCritEligible(player)) {
			return false;
		}

		// Aim-aligned gate: the synthetic rotation must actually be looking
		// at the aim point, otherwise our click would land on whatever the
		// real camera is pointing at (which is usually nothing useful in
		// "Free" mode).
		float curYaw   = this.rotating ? this.serverYaw   : player.getYaw();
		float curPitch = this.rotating ? this.serverPitch : player.getPitch();
		float angle = RotationUtil.angleBetween(curYaw, curPitch, desiredAim[0], desiredAim[1]);
		if (angle > aimTolerance.getFloat()) {
			return false;
		}

		// Reach gate: the chosen aim point must actually be within range.
		double rangeLimit = range.get();
		if (eyes.squaredDistanceTo(aimPoint) > rangeLimit * rangeLimit) {
			return false;
		}

		// LOS gate: when "Through walls" is off, never click on an occluded
		// aim point — even if the rotation looks right, the server will just
		// reject the hit and we'd waste the cooldown.
		if (!throughWalls && !RotationUtil.canSee(world, eyes, aimPoint, player)) {
			return false;
		}

		return true;
	}

	private static boolean isCritEligible(ClientPlayerEntity player) {
		return player.fallDistance > 0.0f
			&& !player.isOnGround()
			&& !player.isClimbing()
			&& !player.isTouchingWater()
			&& !player.hasStatusEffect(StatusEffects.BLINDNESS)
			&& !player.hasVehicle()
			&& !player.isSprinting();
	}

	private void swingAndAttack(MinecraftClient client, ClientPlayerEntity player, LivingEntity victim) {
		if (client.interactionManager == null || client.getNetworkHandler() == null) {
			return;
		}

		// Sprint reset: if requested, stop sprinting on the server BEFORE the
		// hit so the server-side crit eligibility check passes (the server
		// rejects crits while sprinting). Vanilla also auto-toggles sprint
		// off on attack with sprint-knockback, but doing it explicitly here
		// avoids relying on that timing.
		if (extras.isSelected("Sprint reset") && player.isSprinting()) {
			client.getNetworkHandler().sendPacket(
				new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
			player.setSprinting(false);
		}

		client.interactionManager.attackEntity(player, victim);
		player.swingHand(Hand.MAIN_HAND);
		this.lastAttackMs = System.currentTimeMillis();
	}

	private static boolean isWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof SwordItem
			|| stack.getItem() instanceof AxeItem
			|| stack.getItem() instanceof TridentItem;
	}

	// =========================================================================
	// Line-of-sight (used both internally and exposed for HUD modules)
	// =========================================================================

	/**
	 * Public LOS test against the target's bounding-box centre (used by
	 * tests and external HUDs that want to mirror the aura's visibility
	 * heuristic).
	 */
	public boolean hasLineOfSight(Vec3d from, LivingEntity target) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null) {
			return false;
		}
		return hasLineOfSight(from, target, world);
	}

	private boolean hasLineOfSight(Vec3d from, LivingEntity target, ClientWorld world) {
		Vec3d to = target.getBoundingBox().getCenter();
		return RotationUtil.canSee(world, from, to, MinecraftClient.getInstance().player);
	}

	// =========================================================================
	// Introspection (for HUDs and tests)
	// =========================================================================

	public LivingEntity getTarget() {
		return target;
	}

	/**
	 * @return the resolved aim point inside the current target's hitbox, or
	 *         {@code null} when there is no target. Useful for HUD overlays
	 *         that want to draw a "we're aiming here" reticle.
	 */
	public Vec3d getAimPoint() {
		return currentAimPoint;
	}

	/**
	 * @return the entities the auto-targeter would currently consider valid,
	 *         sorted by distance to the player.
	 */
	public List<LivingEntity> debugCandidates() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			return List.of();
		}
		Vec3d eyes = player.getCameraPosVec(1.0f);
		double max = Math.max(range.get(), vision.get());
		double maxSq = max * max;
		return world.getEntitiesByClass(LivingEntity.class,
				player.getBoundingBox().expand(max),
				e -> e != player && e.isAlive() && isAllowedType(e)
					&& eyes.squaredDistanceTo(e.getBoundingBox().getCenter()) <= maxSq)
			.stream()
			.sorted(Comparator.comparingDouble(e -> eyes.squaredDistanceTo(e.getBoundingBox().getCenter())))
			.toList();
	}
}
