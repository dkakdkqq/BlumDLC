package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class Fly extends Module {

	public final ModeSetting mode;
	public final NumberSetting speed;

	public Fly() {
		super("Fly", "Allows you to fly in survival", Category.MOVEMENT);
		this.mode = new ModeSetting("Mode", "Vanilla", "Vanilla", "Smooth");
		this.speed = new NumberSetting("Speed", 2.0, 0.5, 10.0, 0.1);
		addSetting(mode);
		addSetting(speed);
	}

	@Override
	protected void onEnable() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.getAbilities().flying = true;
		}
	}

	@Override
	protected void onDisable() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator()) {
			mc.player.getAbilities().flying = false;
		}
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null) return;

		switch (mode.get()) {
			case "Vanilla" -> {
				player.getAbilities().flying = true;
				player.getAbilities().setFlySpeed((float)(speed.get() * 0.05));
			}
			case "Smooth" -> {
				player.getAbilities().flying = true;
				Vec3d vel = player.getVelocity();
				double sy = 0;
				if (player.input.jumping) sy = speed.get() * 0.1;
				else if (player.input.sneaking) sy = -speed.get() * 0.1;
				player.setVelocity(vel.x, sy, vel.z);
			}
		}
	}
}
