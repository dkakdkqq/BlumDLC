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

	/**
	 * High-level behaviour mode.
	 * <ul>
	 *   <li><b>Free</b>: the original behaviour — every tick picks the best
	 *       target by {@link #priority}, respects the configured {@link #fov}
	 *       and debounces target swaps with {@link #switchDelayMs}.</li>
	 *   <li><b>Focused</b>: server-side lock-on. Once a target is acquired
	 *       we stick with it until it dies or leaves {@code max(range,
	 *       vision)} — even if a closer/lower-HP target appears. The FOV
	 *       gate is dropped (full 360° tracking) and the aim tolerance is
	 *       tightened so clicks land precisely on the chosen body part.
	 *       Rotations are <em>always</em> sent server-side only (rotation
	 *       packet after vanilla's per-tick movement); the local camera is
	 *       never touched, so the user keeps full mouse control.</li>
	 * </ul>
	 */
	public final ModeSetting   mode;
	public final NumberSetting range;
	public final NumberSetting vision;
	public final NumberSetting fov;
	public final ModeSetting   priority;
	public final ModeSetting   rotations;
	public final NumberSetting cps;
	public final NumberSetting switchDelayMs;
	public final NumberSetting aimTolerance;
	/**
	 * Forces the aim point to a specific body part instead of the auto-picked
	 * "closest visible point on hitbox". {@code Auto} keeps the original
	 * sticky heuristic (closest reachable hitbox point with stickiness across
	 * ticks); the other options pin the aim to a fixed slice of the hitbox.
	 */
	public final ModeSetting   aimPart;
	public final MultiSetting  targets;
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
	/**
	 * Aim-part the cached offset was computed for. When the user flips the
	 * {@link #aimPart} dropdown mid-fight we drop the cache so the next
	 * tick picks a fresh point matching the new selection.
	 */
	private String cachedAimPart;

	private final Random random = new Random();

	// =========================================================================
	// Construction
	// =========================================================================

	public AttackAura() {
		super("AttackAura", "Auto-attacks nearby targets in range", Category.COMBAT);

		this.mode = new ModeSetting("Mode", "Free", "Free", "Focused");

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

		this.aimPart = new ModeSetting("Aim Part", "Auto",
			"Auto", "Head", "Body", "Legs");

		this.targets = new MultiSetting("Target",
			List.of("Players", "Animals", "Mobs", "Friends"),
			"Players");

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

		// Hide Free-only settings while in Focused mode — Focused locks on
		// the first valid target ignoring FOV, never auto-switches by
		// priority, and has no switch debounce, so these knobs would just
		// be misleading dead weight in the popup.
		this.priority.visibleWhen(() -> mode.is("Free"));
		this.switchDelayMs.visibleWhen(() -> mode.is("Free"));
		this.fov.visibleWhen(() -> mode.is("Free"));

		addSetting(this.mode);
		addSetting(this.range);
		addSetting(this.vision);
		addSetting(this.fov);
		addSetting(this.priority);
		addSetting(this.rotations);
		addSetting(this.cps);
		addSetting(this.switchDelayMs);
		addSetting(this.aimTolerance);
		addSetting(this.aimPart);
		addSetting(this.targets);
		addSetting(this.extras);
	}

	@Override
	protected void onDisable() {
		this.target = null;
		this.rotating = false;
		this.currentAimPoint = null;
		this.cachedAimOffset = null;
		this.cachedAimFor = null;
		this.cachedAimPart = null;
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
		// We always run silent (rotation-only packets), so the server has
		// been holding our synthetic rotation since the last
		// LookAndOnGround we sent. Vanilla won't update it until the user
		// physically moves the mouse — which means that until they do,
		// manual attacks land at our last fake angle, not at the
		// crosshair. Send one final rotation packet that snaps the server
		// back to the player's actual local rotation.
		if (wasRotating) {
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
		this.cachedAimPart = null;
	}

	private void commitTarget(LivingEntity picked) {
		if (this.target != picked) {
			this.target = picked;
			this.targetLockedAtMs = System.currentTimeMillis();
			this.cachedAimOffset = null;
			this.cachedAimFor = null;
			this.cachedAimPart = null;
		}
	}

	/**
	 * @return the aim-tolerance threshold (degrees) actually used to gate
	 *         attacks. Focused mode clamps it to a tight ≤4° regardless of
	 *         the user's slider — combined with the server-side rotation
	 *         lock this gives the "guaranteed headshot" feel.
	 */
	private float effectiveAimTolerance() {
		float base = aimTolerance.getFloat();
		if (mode.is("Focused")) {
			return Math.min(base, 4.0f);
		}
		return base;
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
		boolean focused = mode.is("Focused");
		// Focused mode is a "lock-on": the FOV gate is ignored once we've
		// acquired anything. Free mode honours the user's FOV slider.
		float fovDeg = focused ? 360.0f : fov.getFloat();
		boolean throughWalls = extras.isSelected("Through walls");
		long now = System.currentTimeMillis();
		long switchHoldMs = (long) Math.round(switchDelayMs.get());

		// Focused: try to keep the existing target unconditionally as long as
		// it's still alive, eligible, and within max reach + visible (or
		// hidden but within vision). We don't run the priority/score loop —
		// "lock-on" means the user actively chose this victim and doesn't
		// want us flipping to a closer one mid-combo.
		if (focused && this.target != null && this.target.isAlive() && !this.target.isRemoved()
			&& isAllowedType(this.target)) {
			double curDistSq = eyes.squaredDistanceTo(this.target.getBoundingBox().getCenter());
			if (curDistSq <= maxReachSq) {
				boolean visible = throughWalls || hasLineOfSight(eyes, this.target, world);
				if ((visible && curDistSq <= rangeSq) || (!visible && curDistSq <= visionSq)) {
					return this.target;
				}
			}
			// Current focused target became invalid → fall through to acquire
			// a fresh one below.
		}

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

			// In Focused mode we're acquiring a fresh target, so prefer the
			// one closest to the crosshair (smallest FOV offset) — that's
			// what the user is most likely "asking" us to lock onto. Free
			// mode uses the user-configured priority.
			double score = focused ? fovOffsetDegrees(self, living) : score(living, distSq, self);
			if (score < bestScore) {
				bestScore = score;
				best = living;
			}
		}

		// Switch debounce (Free mode only): if we already had a valid target
		// and not enough time has passed since lock, stick with it. Stops
		// the aura from ping-ponging between two equally-eligible targets
		// every tick. Focused mode handled stickiness above and skips this.
		if (!focused && best != null && this.target != null && this.target != best
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

		// Always silent: send a rotation-only packet AFTER vanilla's
		// per-tick movement packet, so the server sees our rotation last
		// while the local camera stays untouched. The user keeps full
		// mouse control even while the aura is locked on a target.
		ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
		if (net != null) {
			net.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
				nextYaw, nextPitch, player.isOnGround(), player.horizontalCollision));
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
	 * Pick a hit point inside {@code target}'s bounding box.
	 *
	 * <p>If {@link #aimPart} is set to a fixed body part (Head / Body /
	 * Legs) we forge the aim point from a deterministic vertical slice of
	 * the hitbox, and only fall back to the auto picker when that slice is
	 * occluded or out of range. Otherwise (Auto) we use the original
	 * "closest visible point with cross-tick stickiness" heuristic, which
	 * tracks the target as it moves while staying glued to the same body
	 * part — so the rotation feels like "lock-on" instead of "snapping
	 * around the target every frame".
	 */
	private Vec3d resolveAimPoint(ClientWorld world, Vec3d eyes, LivingEntity target,
			ClientPlayerEntity self, double rangeSq, boolean throughWalls) {
		Box box = target.getBoundingBox();
		Vec3d center = box.getCenter();
		String part = aimPart.get();

		// Drop the cache when the user flips the aim-part dropdown so we
		// don't keep aiming at the previously cached body part.
		if (this.cachedAimFor == target && !part.equals(this.cachedAimPart)) {
			this.cachedAimOffset = null;
		}

		// Forced body-part aim: build the candidate analytically from the
		// hitbox geometry (eye height for Head, etc.). When the forced
		// point is reachable + visible we use it as-is so the lock-on
		// always points where the user told it to. Otherwise we keep the
		// pre-existing auto-fallback so attacks still land while the
		// preferred point is occluded.
		if (!"Auto".equals(part)) {
			Vec3d forced = pinnedAimPoint(target, box, center, part);
			boolean reachable = eyes.squaredDistanceTo(forced) <= rangeSq
				&& (throughWalls || RotationUtil.canSee(world, eyes, forced, self));
			if (reachable) {
				this.cachedAimOffset = forced.subtract(center);
				this.cachedAimFor = target;
				this.cachedAimPart = part;
				return forced;
			}
			// Forced point unreachable — fall through to the auto picker
			// rather than thrashing every tick. Cache is intentionally
			// left in whatever state the auto path will set below.
		}

		if (this.cachedAimFor == target && this.cachedAimOffset != null
			&& "Auto".equals(this.cachedAimPart)) {
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
		this.cachedAimPart = "Auto";
		return resolved;
	}

	/**
	 * Build the aim point for a fixed body-part selection. All variants
	 * stay on the box's centre column (x,z) and only vary the vertical
	 * coordinate so the rotation is dictated entirely by the user's choice
	 * of "where on the body".
	 */
	private static Vec3d pinnedAimPoint(LivingEntity target, Box box, Vec3d center, String part) {
		return switch (part) {
			case "Head" -> new Vec3d(center.x, target.getEyeY(), center.z);
			case "Legs" -> new Vec3d(center.x, box.minY + 0.30, center.z);
			default     -> center; // "Body"
		};
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
		// "Free" mode). Focused mode uses a tighter tolerance for precision.
		float curYaw   = this.rotating ? this.serverYaw   : player.getYaw();
		float curPitch = this.rotating ? this.serverPitch : player.getPitch();
		float angle = RotationUtil.angleBetween(curYaw, curPitch, desiredAim[0], desiredAim[1]);
		if (angle > effectiveAimTolerance()) {
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
