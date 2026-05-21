package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * NoFall — sends an "on ground" rotation packet while in the air so the
 * server doesn't apply fall damage. "Always" sends every tick airborne;
 * "Smart" only does it when fallDistance is above the configured threshold,
 * which is friendlier to anti-cheats.
 */
public final class NoFall extends Module {

	public final ModeSetting   mode;
	public final NumberSetting threshold;

	public NoFall() {
		super("NoFall", "Cancels fall damage server-side", Category.MOVEMENT);
		mode = new ModeSetting("Mode", "Smart", "Smart", "Always");
		threshold = new NumberSetting("Min Fall (blocks)", 3.0, 1.0, 10.0, 0.5);
		addSetting(mode);
		addSetting(threshold);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.getNetworkHandler() == null) return;
		if (p.isOnGround() || p.isClimbing()
			|| p.isInLava() || p.isTouchingWater() || p.hasVehicle()) return;

		boolean send = mode.is("Always")
			|| p.fallDistance > threshold.getFloat();
		if (!send) return;

		mc.getNetworkHandler().sendPacket(
			new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
	}
}
