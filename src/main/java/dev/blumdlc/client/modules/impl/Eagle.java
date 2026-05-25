package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;

/**
 * Eagle (Scaffold Helper) — automatically sneaks at block edges for bridging.
 *
 * <p>1.21.4 reworked {@code Input}: the boolean flags moved into a
 * {@link PlayerInput} record stored on {@code Input.playerInput}. Records
 * are immutable, so to inject "sneak" we replace the whole record while
 * preserving the other flags. We also call {@link ClientPlayerEntity#setSneaking(boolean)}
 * so the same-tick movement code (which reads {@code isSneaking()} for ledge
 * clipping) sees the change immediately.
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
			PlayerInput cur = player.input.playerInput;
			player.input.playerInput = new PlayerInput(
				cur.forward(), cur.backward(), cur.left(), cur.right(),
				cur.jump(), true, cur.sprint());
			player.setSneaking(true);
		}
	}
}
