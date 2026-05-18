package dev.blumdlc.client.modules.impl;

import java.util.Comparator;
import java.util.List;

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
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * AttackAura — auto-attacks the nearest valid entity within range.
 *
 * <p>The module reuses {@link Module}'s tick callback. Every tick (when the
 * player is in-game) it:
 * <ol>
 *   <li>picks the best target according to {@link #targets} and the configured
 *       range/vision;</li>
 *   <li>moves the rotation toward that target according to the selected
 *       {@link #rotations} profile;</li>
 *   <li>delivers the rotation either silently (rotation-only packet, the
 *       default) or by writing it onto the player (visible / "Targeted"
 *       movement correction);</li>
 *   <li>clicks once the vanilla attack cooldown has fully recharged and any
 *       extra constraints (criticals only, weapon required, ...) pass.</li>
 * </ol>
 */
public final class AttackAura extends Module {

	// --- settings -----------------------------------------------------------

	public final NumberSetting range;
	public final NumberSetting vision;
	public final ModeSetting   rotations;
	public final MultiSetting  targets;
	public final ModeSetting   movementCorrection;
	public final MultiSetting  extras;

	// --- runtime state ------------------------------------------------------

	private LivingEntity target;
	/** Server-facing yaw used when sending silent rotation packets. */
	private float serverYaw;
	/** Server-facing pitch used when sending silent rotation packets. */
	private float serverPitch;
	private boolean rotating;

	public AttackAura() {
		super("AttackAura", "Auto-attacks nearby targets in range", Category.COMBAT);

		this.range  = new NumberSetting("Range",  4.5, 3.0, 6.0, 0.1);
		this.vision = new NumberSetting("Vision", 4.0, 2.5, 6.0, 0.1);

		this.rotations = new ModeSetting("Rotations", "SpookyTime",
			"SpookyTime", "HolyWorld", "ReallyWorld", "FunTime");

		this.targets = new MultiSetting("Target",
			List.of("Players", "Animals", "Mobs", "Friends"),
			"Players");

		this.movementCorrection = new ModeSetting("Movement Correction",
			"Free", "Free", "Targeted");

		this.extras = new MultiSetting("Extras",
			List.of(
				"Don't hit while eating",
				"Only with weapon",
				"Don't hit in inventory",
				"Only criticals"
			));

		addSetting(this.range);
		addSetting(this.vision);
		addSetting(this.rotations);
		addSetting(this.targets);
		addSetting(this.movementCorrection);
		addSetting(this.extras);
	}

	@Override
	protected void onDisable() {
		this.target = null;
		this.rotating = false;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		ClientPlayNetworkHandler net = client.getNetworkHandler();
		if (player == null || world == null || net == null) {
			this.target = null;
			this.rotating = false;
			return;
		}

		if (extras.isSelected("Don't hit in inventory") && client.currentScreen instanceof HandledScreen<?>) {
			this.target = null;
			this.rotating = false;
			return;
		}
		if (extras.isSelected("Don't hit while eating") && player.isUsingItem()) {
			this.target = null;
			this.rotating = false;
			return;
		}
		if (extras.isSelected("Only with weapon") && !isWeapon(player.getMainHandStack())) {
			this.target = null;
			this.rotating = false;
			return;
		}

		this.target = pickTarget(player, world);
		if (this.target == null) {
			this.rotating = false;
			return;
		}

		// 1. compute the desired rotation toward the target
		Vec3d eyes = player.getCameraPosVec(1.0f);
		float[] aim = RotationUtil.aimAt(eyes, this.target);
		applyRotationStep(player, aim[0], aim[1]);

		// 2. attack if the cooldown is full and any extra constraints pass
		if (player.getAttackCooldownProgress(0.0f) >= 1.0f && canAttackNow(player)) {
			swingAndAttack(client, player, this.target);
		}
	}

	// --- target selection ---------------------------------------------------

	private LivingEntity pickTarget(ClientPlayerEntity self, ClientWorld world) {
		double maxRange = Math.max(range.get(), vision.get());
		double rangeSq = range.get() * range.get();
		double visionSq = vision.get() * vision.get();
		Vec3d eyes = self.getCameraPosVec(1.0f);

		LivingEntity best = null;
		double bestDistSq = Double.MAX_VALUE;

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
			if (distSq > maxRange * maxRange) {
				continue;
			}

			boolean visible = hasLineOfSight(eyes, living, world);
			if (visible) {
				if (distSq > rangeSq) {
					continue;
				}
			} else {
				if (distSq > visionSq) {
					continue;
				}
			}

			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = living;
			}
		}
		return best;
	}

	private boolean isAllowedType(LivingEntity entity) {
		if (entity instanceof PlayerEntity player) {
			boolean isFriend = Friends.contains(player);
			if (isFriend) {
				// only consider friends if the user explicitly opted in
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
		// other LivingEntity (bosses, slimes, etc.) are grouped with mobs
		return targets.isSelected("Mobs");
	}

	// --- rotation -----------------------------------------------------------

	/**
	 * Per-profile soft caps on the yaw/pitch step we are willing to take in a
	 * single tick. Lower numbers feel smoother and more "human", higher ones
	 * snap faster.
	 */
	private float[] rotationSpeed() {
		return switch (rotations.get()) {
			// fastest; mostly snaps onto the target
			case "FunTime"     -> new float[] { 180.0f, 90.0f };
			// medium-fast, still fairly direct
			case "ReallyWorld" -> new float[] {  75.0f, 45.0f };
			// medium, smooth
			case "HolyWorld"   -> new float[] {  45.0f, 30.0f };
			// slowest, smoothest
			case "SpookyTime"  -> new float[] {  30.0f, 20.0f };
			default            -> new float[] {  45.0f, 30.0f };
		};
	}

	private void applyRotationStep(ClientPlayerEntity player, float wantedYaw, float wantedPitch) {
		float[] speed = rotationSpeed();
		float currentYaw = this.rotating ? this.serverYaw : player.getYaw();
		float currentPitch = this.rotating ? this.serverPitch : player.getPitch();

		float nextYaw = RotationUtil.approachYaw(currentYaw, wantedYaw, speed[0]);
		float nextPitch = RotationUtil.approachPitch(currentPitch, wantedPitch, speed[1]);

		this.serverYaw = nextYaw;
		this.serverPitch = nextPitch;
		this.rotating = true;

		boolean visible = movementCorrection.is("Targeted");
		if (visible) {
			// "Targeted": we move the actual player rotation so the camera
			// follows the target. The vanilla movement packets emitted by the
			// game tick will carry the new yaw/pitch — we don't have to send
			// anything ourselves.
			player.setYaw(nextYaw);
			player.setPitch(nextPitch);
			player.setHeadYaw(nextYaw);
			player.setBodyYaw(nextYaw);
		} else {
			// "Free": send a rotation-only packet so the server sees us aiming
			// at the target while the local camera stays put.
			ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
			if (net != null) {
				net.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
					nextYaw, nextPitch, player.isOnGround(), player.horizontalCollision));
			}
		}
	}

	// --- attacking ----------------------------------------------------------

	private boolean canAttackNow(ClientPlayerEntity player) {
		if (extras.isSelected("Only criticals")) {
			boolean falling = player.fallDistance > 0.0f
				&& !player.isOnGround()
				&& !player.isClimbing()
				&& !player.isTouchingWater()
				&& !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
				&& !player.hasVehicle()
				&& !player.isSprinting();
			if (!falling) {
				return false;
			}
		}
		return true;
	}

	private void swingAndAttack(MinecraftClient client, ClientPlayerEntity player, LivingEntity victim) {
		if (client.interactionManager == null) {
			return;
		}
		client.interactionManager.attackEntity(player, victim);
		player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
	}

	private static boolean isWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof SwordItem
			|| stack.getItem() instanceof AxeItem
			|| stack.getItem() instanceof TridentItem;
	}

	// --- introspection (useful for HUDs and tests) --------------------------

	public LivingEntity getTarget() {
		return target;
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

	/**
	 * Test whether the configured aim point on {@code target} is reachable
	 * from {@code from} without hitting a block (used for the "vision" gate).
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
		MinecraftClient client = MinecraftClient.getInstance();
		Vec3d to = target.getBoundingBox().getCenter();
		HitResult hit = world.raycast(new RaycastContext(from, to,
			RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE,
			client.player));
		return hit.getType() == HitResult.Type.MISS;
	}
}
