package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.Hand;

/**
 * AutoFish — automatically reels and recasts the fishing rod.
 */
public final class AutoFish extends Module {

	private int waitTicks = 0;

	public AutoFish() {
		super("AutoFish", "Automatic fishing", Category.PLAYER);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.interactionManager == null) return;
		if (!(player.getMainHandStack().getItem() instanceof FishingRodItem)) return;

		FishingBobberEntity bobber = player.fishHook;
		if (bobber == null) {
			// No bobber — cast the rod
			if (waitTicks > 0) { waitTicks--; return; }
			mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
			waitTicks = 20;
			return;
		}

		// Detect a catch: velocity spike downward
		if (bobber.getVelocity().y < -0.04) {
			mc.interactionManager.interactItem(player, Hand.MAIN_HAND); // reel in
			waitTicks = 15;
		}
	}

	@Override
	protected void onDisable() {
		waitTicks = 0;
	}
}
