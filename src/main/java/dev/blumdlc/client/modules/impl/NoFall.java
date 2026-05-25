package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class NoFall extends Module {

	public NoFall() {
		super("NoFall", "Prevents fall damage by spoofing on-ground", Category.PLAYER);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.getNetworkHandler() == null) return;

		if (player.fallDistance > 2.5f) {
			mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
		}
	}
}
