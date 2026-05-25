package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class Speed extends Module {

	public final ModeSetting mode;
	public final NumberSetting multiplier;

	public Speed() {
		super("Speed", "Increases your movement speed", Category.MOVEMENT);
		this.mode = new ModeSetting("Mode", "Strafe", true, "Strafe", "Vanilla", "BHop");
		this.multiplier = new NumberSetting("Speed", 1.5, 1.0, 5.0, 0.1);
		addSetting(mode);
		addSetting(multiplier);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || !player.isOnGround()) return;

		double speed = 0.2873 * multiplier.get();

		switch (mode.get()) {
			case "Strafe" -> {
				float forward = player.input.movementForward;
				float strafe = player.input.movementSideways;
				float yaw = player.getYaw();
				if (forward == 0 && strafe == 0) return;

				double rad = Math.toRadians(yaw);
				if (forward != 0) {
					if (strafe > 0) rad -= Math.toRadians(forward > 0 ? 45 : -45);
					else if (strafe < 0) rad += Math.toRadians(forward > 0 ? 45 : -45);
					strafe = 0;
					if (forward > 0) forward = 1;
					else if (forward < 0) forward = -1;
				}

				player.setVelocity(
					-Math.sin(rad) * speed * forward + -Math.cos(rad) * speed * strafe,
					player.getVelocity().y,
					Math.cos(rad) * speed * forward + -Math.sin(rad) * speed * strafe
				);
			}
			case "Vanilla" -> {
				Vec3d vel = player.getVelocity();
				double factor = multiplier.get();
				player.setVelocity(vel.x * factor, vel.y, vel.z * factor);
			}
			case "BHop" -> {
				if (player.input.movementForward != 0 || player.input.movementSideways != 0) {
					player.jump();
					Vec3d vel = player.getVelocity();
					player.setVelocity(vel.x * multiplier.get(), vel.y, vel.z * multiplier.get());
				}
			}
		}
	}
}
