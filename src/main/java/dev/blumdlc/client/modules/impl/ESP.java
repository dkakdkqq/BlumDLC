package dev.blumdlc.client.modules.impl;

import org.joml.Matrix4f;

import dev.blumdlc.client.builders.Builder;
import dev.blumdlc.client.builders.states.QuadColorState;
import dev.blumdlc.client.builders.states.QuadRadiusState;
import dev.blumdlc.client.builders.states.SizeState;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.settings.BooleanSetting;
import dev.blumdlc.client.settings.NumberSetting;
import dev.blumdlc.client.ui.ClientTheme;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.Projection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * ESP — highlights players/entities through walls using 2D box projection.
 */
public final class ESP extends Module {

	public final BooleanSetting players;
	public final BooleanSetting mobs;
	public final NumberSetting range;

	public ESP() {
		super("ESP", "See entities through walls", Category.RENDER);
		this.players = new BooleanSetting("Players", true);
		this.mobs = new BooleanSetting("Mobs", false);
		this.range = new NumberSetting("Range", 64.0, 16.0, 128.0, 1.0);
		addSetting(players);
		addSetting(mobs);
		addSetting(range);
	}

	@Override
	public void onRender(Matrix4f matrix, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		ClientWorld world = mc.world;
		if (player == null || world == null) return;

		double rangeSq = range.get() * range.get();

		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof LivingEntity living)) continue;
			if (living == player) continue;
			if (!living.isAlive()) continue;

			if (living instanceof PlayerEntity) {
				if (!players.get()) continue;
			} else {
				if (!mobs.get()) continue;
			}

			double distSq = player.squaredDistanceTo(entity);
			if (distSq > rangeSq) continue;

			Box box = entity.getBoundingBox();
			// Project 8 corners of bounding box
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
			float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
			boolean valid = true;

			double[][] corners = {
				{box.minX, box.minY, box.minZ}, {box.maxX, box.minY, box.minZ},
				{box.minX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.minZ},
				{box.minX, box.minY, box.maxZ}, {box.maxX, box.minY, box.maxZ},
				{box.minX, box.maxY, box.maxZ}, {box.maxX, box.maxY, box.maxZ},
			};

			for (double[] c : corners) {
				Projection.Result r = Projection.project(c[0], c[1], c[2]);
				if (!r.inFront()) { valid = false; break; }
				if (r.x() < minX) minX = r.x();
				if (r.y() < minY) minY = r.y();
				if (r.x() > maxX) maxX = r.x();
				if (r.y() > maxY) maxY = r.y();
			}

			if (!valid) continue;

			float w = maxX - minX;
			float h = maxY - minY;
			if (w < 2 || h < 2) continue;

			int color = living instanceof PlayerEntity
				? ColorUtil.withAlpha(ClientTheme.accent(), 0.8f)
				: ColorUtil.withAlpha(0xFF44FF44, 0.7f);

			// Draw 2D box
			float thick = 0.8f;
			Builder.rectangle().size(new SizeState(w, thick)).radius(QuadRadiusState.NO_ROUND)
				.color(new QuadColorState(color)).smoothness(1.0f).build().render(matrix, minX, minY);
			Builder.rectangle().size(new SizeState(w, thick)).radius(QuadRadiusState.NO_ROUND)
				.color(new QuadColorState(color)).smoothness(1.0f).build().render(matrix, minX, maxY);
			Builder.rectangle().size(new SizeState(thick, h)).radius(QuadRadiusState.NO_ROUND)
				.color(new QuadColorState(color)).smoothness(1.0f).build().render(matrix, minX, minY);
			Builder.rectangle().size(new SizeState(thick, h)).radius(QuadRadiusState.NO_ROUND)
				.color(new QuadColorState(color)).smoothness(1.0f).build().render(matrix, maxX, minY);
		}
	}
}
