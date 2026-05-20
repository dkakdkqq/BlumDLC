package dev.blumdlc.client.modules.impl;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.ModeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Sprint — keeps the player sprinting whenever movement input is detected.
 * "Vanilla" only sprints forward; "Legit" also sprints when strafing;
 * "Rage" sprints unconditionally (even while still).
 */
public final class Sprint extends Module {

	public final ModeSetting    mode;
	public final BooleanSetting whileEating;

	public Sprint() {
		super("Sprint", "Always sprint while moving", Category.MOVEMENT);
		mode = new ModeSetting("Mode", "Vanilla", "Vanilla", "Legit", "Rage");
		whileEating = new BooleanSetting("While using item", false);
		addSetting(mode);
		addSetting(whileEating);
	}

	@Override
	public void onTick() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity p = mc.player;
		if (p == null) return;
		if (!whileEating.get() && p.isUsingItem()) return;
		if (p.isSneaking()) return;
		if (p.getHungerManager().getFoodLevel() <= 6 && !mode.is("Rage")) return;

		var opt = mc.options;
		boolean fwd = opt.forwardKey.isPressed();
		boolean side = opt.leftKey.isPressed() || opt.rightKey.isPressed()
			|| opt.backKey.isPressed();

		boolean shouldSprint = switch (mode.get()) {
			case "Rage"   -> true;
			case "Legit"  -> fwd || side;
			default       -> fwd; // "Vanilla"
		};
		if (shouldSprint) {
			p.setSprinting(true);
		}
	}
}
