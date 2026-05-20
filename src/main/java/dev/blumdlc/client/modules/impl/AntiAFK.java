package dev.blumdlc.client.modules.impl;

import java.util.List;
import java.util.Random;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;

/**
 * AntiAFK — periodically performs a random idle action so the server / AFK
 * detector keeps you online. All actions are real client-emitted events:
 * rotation packets, real jumps, real swings.
 */
public final class AntiAFK extends Module {

	public final NumberSetting interval;
	public final MultiSetting  actions;

	private final Random rand = new Random();
	private long nextActionAt = 0L;

	public AntiAFK() {
		super("AntiAFK", "Periodic activity to defeat AFK detection", Category.MOVEMENT);
		interval = new NumberSetting("Interval (s)", 25.0, 5.0, 120.0, 1.0);
		actions  = new MultiSetting("Actions",
			List.of("Rotate", "Swing", "Sneak"),
			"Rotate", "Swing");
		addSetting(interval);
		addSetting(actions);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null) return;

		long now = System.currentTimeMillis();
		if (nextActionAt == 0L) { nextActionAt = now + (long) (interval.get() * 1000.0); return; }
		if (now < nextActionAt) return;
		nextActionAt = now + (long) (interval.get() * 1000.0);

		if (actions.isSelected("Rotate") && mc.getNetworkHandler() != null) {
			float yaw = (rand.nextFloat() - 0.5f) * 90.0f;
			float pitch = (rand.nextFloat() - 0.5f) * 30.0f;
			mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
				yaw, pitch, p.isOnGround(), p.horizontalCollision));
		}
		if (actions.isSelected("Sneak")) {
			mc.options.sneakKey.setPressed(!mc.options.sneakKey.isPressed());
		}
		if (actions.isSelected("Swing")) p.swingHand(Hand.MAIN_HAND);
	}
}
