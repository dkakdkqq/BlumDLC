package dev.blumdlc.client.modules.impl;

import java.util.List;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.ModeSetting;
import dev.blumdlc.client.settings.MultiSetting;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * EntityESP — projects each living entity's bounding box to scaled-screen
 * space and draws a 2D box around it. Three styles cover most aesthetic
 * preferences: filled box, outline-only frame, or just an underline.
 */
public final class EntityESP extends Module {

	public final MultiSetting targets;
	public final ModeSetting  style;

	public EntityESP() {
		super("EntityESP", "2D box highlight on entities", Category.RENDER);
		targets = new MultiSetting("Targets",
			List.of("Players", "Mobs", "Animals"),
			"Players", "Mobs");
		style = new ModeSetting("Style", "Box", "Box", "Frame", "Underline");
		addSetting(targets);
		addSetting(style);
	}


	@Override
	public void onRender(Matrix4f m, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;

		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof LivingEntity le) || e == mc.player) continue;
			if (!matches(le)) continue;
			drawBox(m, le, tickDelta);
		}
	}

	private boolean matches(LivingEntity le) {
		if (le instanceof PlayerEntity)  return targets.isSelected("Players");
		if (le instanceof HostileEntity) return targets.isSelected("Mobs");
		if (le instanceof PassiveEntity) return targets.isSelected("Animals");
		return false;
	}

	private void drawBox(Matrix4f m, LivingEntity le, float tickDelta) {
		Vec3d pos = le.getLerpedPos(tickDelta);
		double bottomY = pos.y;
		double topY = pos.y + le.getHeight();

		Projection.Result topRes = Projection.project(pos.x, topY, pos.z);
		Projection.Result botRes = Projection.project(pos.x, bottomY, pos.z);
		if (!topRes.onScreen() || !botRes.onScreen()) return;

		float h = botRes.y() - topRes.y();
		if (h < 4.0f) return;
		float w = h * (le.getWidth() / le.getHeight());
		float cx = (topRes.x() + botRes.x()) * 0.5f;
		float x = cx - w * 0.5f;
		float y = topRes.y();

		int accent = ClientTheme.accent();
		switch (style.get()) {
			case "Frame":
				UIRender.border(m, x, y, w, h, 0.0f, 1.4f, accent);
				break;
			case "Underline":
				UIRender.rect(m, x, y + h - 1.4f, w, 1.4f, 0.5f, accent);
				break;
			case "Box":
			default:
				UIRender.rect(m, x, y, w, h, 0.0f,
					ColorUtil.multiplyAlpha(accent, 0.18f));
				UIRender.border(m, x, y, w, h, 0.0f, 1.2f, accent);
		}
	}
}
