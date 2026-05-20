package dev.blumdlc.client.modules.impl;

import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.builders.Builder;
import dev.blumdlc.client.builders.states.QuadColorState;
import dev.blumdlc.client.builders.states.QuadRadiusState;
import dev.blumdlc.client.builders.states.SizeState;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.util.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Tracers — draws a colored line from a screen anchor (bottom or center) to
 * each visible living entity's torso. Useful for spotting players and mobs
 * through terrain noise without resorting to wall-hacks.
 */
public final class Tracers extends Module {

	public final MultiSetting  targets;
	public final ModeSetting   anchor;
	public final NumberSetting thickness;

	public Tracers() {
		super("Tracers", "Lines from screen anchor to entities", Category.RENDER);
		targets   = new MultiSetting("Targets",
			List.of("Players", "Mobs", "Animals"),
			"Players");
		anchor    = new ModeSetting("Anchor", "Bottom", "Bottom", "Center");
		thickness = new NumberSetting("Thickness", 1.2, 0.5, 4.0, 0.1);
		addSetting(targets);
		addSetting(anchor);
		addSetting(thickness);
	}


	@Override
	public void onRender(Matrix4f m, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;

		var window = mc.getWindow();
		float origX = window.getScaledWidth() * 0.5f;
		float origY = anchor.is("Center")
			? window.getScaledHeight() * 0.5f
			: window.getScaledHeight();

		int color = ClientTheme.accent();
		float t = thickness.getFloat();

		for (var e : mc.world.getEntities()) {
			if (!(e instanceof LivingEntity le) || e == mc.player) continue;
			boolean ok =
				   (le instanceof PlayerEntity  && targets.isSelected("Players"))
				|| (le instanceof HostileEntity && targets.isSelected("Mobs"))
				|| (le instanceof PassiveEntity && targets.isSelected("Animals"));
			if (!ok) continue;

			Vec3d p = le.getLerpedPos(tickDelta);
			var proj = Projection.project(p.x, p.y + le.getHeight() * 0.5, p.z);
			if (!proj.onScreen()) continue;
			drawLine(m, origX, origY, proj.x(), proj.y(), t, color);
		}
	}

	/** Rotated thin rounded rect from (x1,y1) to (x2,y2). */
	private static void drawLine(Matrix4f base,
			float x1, float y1, float x2, float y2,
			float thick, int color) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0f) return;
		float angle = (float) Math.atan2(dy, dx);
		Matrix4f m = new Matrix4f(base).translate(x1, y1, 0.0f).rotateZ(angle);
		float halfT = thick * 0.5f;
		Builder.rectangle()
			.size(new SizeState(len, thick))
			.radius(new QuadRadiusState(halfT))
			.color(new QuadColorState(color))
			.smoothness(1.0f)
			.build()
			.render(m, 0.0f, -halfT);
	}
}
