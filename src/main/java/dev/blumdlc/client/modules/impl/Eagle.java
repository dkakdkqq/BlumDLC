package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;

/**
 * Eagle (Scaffold Helper) — automatically sneaks at block edges for bridging.
 */
public final class Eagle extends Module {

	public Eagle() {
		super("Eagle", "Auto-sneak at block edges for bridging", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || !player.isOnGround()) return;

		// Check if player is at the edge of a block
		double edgeX = player.getX() - Math.floor(player.getX());
		double edgeZ = player.getZ() - Math.floor(player.getZ());
		boolean atEdge = edgeX < 0.3 || edgeX > 0.7 || edgeZ < 0.3 || edgeZ > 0.7;

		if (atEdge && (player.input.movementForward != 0 || player.input.movementSideways != 0)) {
			player.input.sneaking = true;
		}
	}
}
