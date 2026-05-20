package dev.blumdlc.client;

import org.lwjgl.glfw.GLFW;

import dev.blumdlc.client.modules.impl.Themes;
import dev.blumdlc.client.ui.ClickGuiScreen;
import dev.blumdlc.client.ui.Theme;
import dev.blumdlc.client.ui.hud.HudEditor;
import dev.blumdlc.client.util.Projection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class BlumDLCClient implements ClientModInitializer {

	private static KeyBinding openGuiKey;

	@Override
	public void onInitializeClient() {
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blumdlc.open_gui",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			"category.blumdlc.main"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGuiKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new ClickGuiScreen());
				}
			}
			BlumDLC.MODULES.tick();
			dev.blumdlc.client.bind.BindManager.tick();
		});

		// Capture world-render matrices each frame so modules can project
		// 3D entity positions to 2D HUD coordinates in onRender.
		WorldRenderEvents.LAST.register(ctx -> {
			Projection.capture(
				ctx.positionMatrix(),
				ctx.projectionMatrix(),
				ctx.camera()
			);
		});

		// Per-frame HUD render: drive the chat-screen HUD editor first
		// (so its drag state is up-to-date before HUDs are queried for hit
		// testing), then dispatch to all enabled modules, then draw the
		// editor's selection overlay on top.
		HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
			// Pull the user-picked accent colour into Theme.* so HUD modules
			// see the same palette as the GUI.
			Themes.syncAll();
			Theme.refresh();

			HudEditor.update();
			org.joml.Matrix4f matrix = drawContext.getMatrices().peek().getPositionMatrix();
			BlumDLC.MODULES.render(matrix, tickCounter.getTickDelta(true));
			HudEditor.render(matrix);
		});
	}

}
