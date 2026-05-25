package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class Sprint extends Module {

	public Sprint() {
		super("Sprint", "Automatically sprints when moving", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return;
		if (player.input.movementForward > 0 && !player.isSneaking() && !player.horizontalCollision) {
			player.setSprinting(true);
		}
	}
}
