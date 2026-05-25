package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Criticals — forces critical hits on every attack by sending
 * micro-jump packets.
 */
public final class Criticals extends Module {

	public final ModeSetting mode;

	public Criticals() {
		super("Criticals", "Forces critical hits on attacks", Category.COMBAT);
		this.mode = new ModeSetting("Mode", "Packet", "Packet", "MiniJump");
		addSetting(mode);
	}

	@Override
	public void onTick() {
		// Criticals logic is triggered externally when an attack happens.
		// For packet mode, we send fake movement packets right before attacking.
	}

	/**
	 * Called from outside (e.g., hooked into attack logic) to trigger crits.
	 * For standalone operation, this runs every tick when attack cooldown resets.
	 */
	public void doCrit() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.getNetworkHandler() == null) return;
		if (!player.isOnGround()) return;

		switch (mode.get()) {
			case "Packet" -> {
				double x = player.getX();
				double y = player.getY();
				double z = player.getZ();
				mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
					x, y + 0.0625, z, false, player.horizontalCollision));
				mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
					x, y, z, false, player.horizontalCollision));
				mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
					x, y + 1.1E-5, z, false, player.horizontalCollision));
				mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
					x, y, z, true, player.horizontalCollision));
			}
			case "MiniJump" -> {
				player.jump();
			}
		}
	}
}
