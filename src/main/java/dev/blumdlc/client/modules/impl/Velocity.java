package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Velocity (AntiKnockback) — reduces knockback from hits.
 */
public final class Velocity extends Module {

	public final NumberSetting horizontal;
	public final NumberSetting vertical;

	public Velocity() {
		super("Velocity", "Reduces knockback from attacks", Category.COMBAT);
		this.horizontal = new NumberSetting("Horizontal", 0.0, 0.0, 100.0, 1.0);
		this.vertical = new NumberSetting("Vertical", 0.0, 0.0, 100.0, 1.0);
		addSetting(horizontal);
		addSetting(vertical);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null) return;

		if (player.hurtTime == player.maxHurtTime && player.hurtTime > 0) {
			Vec3d vel = player.getVelocity();
			double hMul = horizontal.get() / 100.0;
			double vMul = vertical.get() / 100.0;
			player.setVelocity(vel.x * hMul, vel.y * vMul, vel.z * hMul);
		}
	}
}
