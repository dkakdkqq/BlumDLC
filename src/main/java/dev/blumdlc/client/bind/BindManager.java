package dev.blumdlc.client.bind;

import java.util.HashSet;
import java.util.Set;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/**
 * Polls each tick and toggles modules when their bound key transitions
 * up -> down (edge detection), as long as no GUI is focused.
 *
 * <p>This avoids hijacking the global GLFW key callback (which can break
 * other listeners) and is safe to call from the existing
 * {@link net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 * client tick} hook.
 */
public final class BindManager {

	private static final Set<Integer> downKeys = new HashSet<>();

	private BindManager() {
	}

	/** Drive once per client tick. */
	public static void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		long handle = client.getWindow().getHandle();
		boolean guiFocused = client.currentScreen != null;

		for (Module m : BlumDLC.MODULES.all()) {
			int key = m.keybind;
			if (key < 0) continue;

			boolean held;
			try {
				held = InputUtil.isKeyPressed(handle, key);
			} catch (Throwable t) {
				continue;
			}

			boolean wasDown = downKeys.contains(key);
			if (held && !wasDown) {
				downKeys.add(key);
				if (!guiFocused) {
					m.toggle();
				}
			} else if (!held && wasDown) {
				downKeys.remove(key);
			}
		}
	}
}
