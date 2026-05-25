package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.Fonts;
import dev.blumdlc.client.ui.UIRender;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.Projection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * NameTags — bigger, clearer nametags with health info.
 */
public final class NameTags extends Module {

	public final BooleanSetting showHealth;
	public final NumberSetting scale;

	public NameTags() {
		super("NameTags", "Enhanced player nametags", Category.RENDER);
		this.showHealth = new BooleanSetting("Show Health", true);
		this.scale = new NumberSetting("Scale", 1.5, 0.5, 4.0, 0.1);
		addSetting(showHealth);
		addSetting(scale);
	}

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		ClientWorld world = mc.world;
		if (player == null || world == null) return;

		MsdfFont font = Fonts.BIKO.get();
		float fontSize = 6.0f * scale.getFloat();

		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof PlayerEntity target)) continue;
			if (target == player) continue;
			if (!target.isAlive()) continue;
			if (player.squaredDistanceTo(target) > 4096) continue; // 64 block range

			Projection.Result r = Projection.project(
				target.getX(), target.getY() + target.getHeight() + 0.3, target.getZ());
			if (!r.onScreen()) continue;

			String name = target.getGameProfile().getName();
			String text = showHealth.get()
				? name + " " + String.format("%.0f", target.getHealth()) + "HP"
				: name;

			float tw = UIRender.textWidth(font, text, fontSize);
			float tx = r.x() - tw * 0.5f;
			float ty = r.y() - fontSize;

			// Background
			UIRender.rect(matrix, tx - 3, ty - 2, tw + 6, fontSize + 4, 2.0f,
				ColorUtil.withAlpha(0x000000, 0.65f));

			// Text
			UIRender.text(matrix, font, text, tx, ty, fontSize, 0xFFFFFFFF, 0.05f);
		}
	}
}
