package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.c2s.common.ClientStatusC2SPacket;

/**
 * AutoRespawn — clicks "Respawn" for the player after the configured delay.
 * Detects the {@link DeathScreen} every tick and schedules a respawn packet.
 */
public final class AutoRespawn extends Module {

	public final NumberSetting delay;

	private long deathSeenAt = 0L;

	public AutoRespawn() {
		super("AutoRespawn", "Respawns automatically", Category.PLAYER);
		delay = new NumberSetting("Delay (ms)", 200.0, 0.0, 5000.0, 50.0);
		addSetting(delay);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!(mc.currentScreen instanceof DeathScreen)) { deathSeenAt = 0L; return; }

		long now = System.currentTimeMillis();
		if (deathSeenAt == 0L) { deathSeenAt = now; return; }
		if (now - deathSeenAt < delay.get()) return;

		if (mc.player != null && mc.getNetworkHandler() != null) {
			mc.getNetworkHandler().sendPacket(
				new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
			mc.setScreen(null);
		}
		deathSeenAt = 0L;
	}
}
