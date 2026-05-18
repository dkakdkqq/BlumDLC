package dev.blumdlc.client;

import org.lwjgl.glfw.GLFW;

import dev.blumdlc.client.ui.ClickGuiScreen;
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

		// Per-frame HUD render: dispatch to all enabled modules.
		HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
			BlumDLC.MODULES.render(
				drawContext.getMatrices().peek().getPositionMatrix(),
				tickCounter.getTickDelta(true)
			);
		});
	}

}
