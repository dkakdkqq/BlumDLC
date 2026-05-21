package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

/**
 * AutoWalk — holds the forward (or selectable) movement key for you.
 * Pauses while inventory/menus are open if "Pause in screens" is on.
 */
public final class AutoWalk extends Module {

	public final ModeSetting    direction;
	public final BooleanSetting pauseInScreens;

	public AutoWalk() {
		super("AutoWalk", "Auto-presses a movement key", Category.MOVEMENT);
		direction = new ModeSetting("Direction", "Forward",
			"Forward", "Back", "Left", "Right");
		pauseInScreens = new BooleanSetting("Pause in screens", true);
		addSetting(direction);
		addSetting(pauseInScreens);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			release(mc);
			return;
		}
		boolean inMenu = mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen);
		if (pauseInScreens.get() && inMenu) {
			release(mc);
			return;
		}
		release(mc); // make sure no stale key from a different direction
		switch (direction.get()) {
			case "Back"  -> mc.options.backKey.setPressed(true);
			case "Left"  -> mc.options.leftKey.setPressed(true);
			case "Right" -> mc.options.rightKey.setPressed(true);
			default      -> mc.options.forwardKey.setPressed(true);
		}
	}

	@Override
	protected void onDisable() {
		release(MinecraftClient.getInstance());
	}

	private static void release(MinecraftClient mc) {
		if (mc == null) return;
		mc.options.forwardKey.setPressed(false);
		mc.options.backKey.setPressed(false);
		mc.options.leftKey.setPressed(false);
		mc.options.rightKey.setPressed(false);
	}
}
