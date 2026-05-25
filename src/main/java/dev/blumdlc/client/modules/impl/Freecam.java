package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam — detaches the camera from the player, allowing free movement.
 * Simple implementation: stores original pos, moves a phantom camera.
 */
public final class Freecam extends Module {

	public final NumberSetting speed;
	private Vec3d savedPos;
	private float savedYaw, savedPitch;

	public Freecam() {
		super("Freecam", "Free camera movement", Category.RENDER);
		this.speed = new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1);
		addSetting(speed);
	}

	@Override
	protected void onEnable() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			savedPos = mc.player.getPos();
			savedYaw = mc.player.getYaw();
			savedPitch = mc.player.getPitch();
		}
	}

	@Override
	protected void onDisable() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && savedPos != null) {
			mc.player.setPosition(savedPos);
			mc.player.setYaw(savedYaw);
			mc.player.setPitch(savedPitch);
		}
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null) return;

		float spd = speed.getFloat() * 0.5f;
		Vec3d look = player.getRotationVec(1.0f).multiply(spd);

		double mx = 0, my = 0, mz = 0;
		if (player.input.movementForward > 0) { mx += look.x; my += look.y; mz += look.z; }
		if (player.input.movementForward < 0) { mx -= look.x; my -= look.y; mz -= look.z; }
		if (player.input.jumping) my += spd;
		if (player.input.sneaking) my -= spd;

		player.setPosition(player.getX() + mx, player.getY() + my, player.getZ() + mz);
		player.setVelocity(Vec3d.ZERO);
		player.setOnGround(true);
	}
}
