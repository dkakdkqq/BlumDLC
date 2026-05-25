package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * ClickTP — teleports to the block you're looking at when right-clicking.
 * Works only when enabled and holding no item in offhand (safety check).
 */
public final class ClickTP extends Module {

	public final NumberSetting maxDist;

	public ClickTP() {
		super("ClickTP", "Teleport to where you look on use", Category.PLAYER);
		this.maxDist = new NumberSetting("Max Distance", 100.0, 10.0, 256.0, 1.0);
		addSetting(maxDist);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null) return;

		// Trigger on right-click (use key)
		if (mc.options.useKey.isPressed() && mc.currentScreen == null) {
			HitResult hit = player.raycast(maxDist.get(), 0, false);
			if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
				Vec3d pos = Vec3d.ofCenter(blockHit.getBlockPos().up());
				player.setPosition(pos.x, pos.y, pos.z);
			}
		}
	}
}
