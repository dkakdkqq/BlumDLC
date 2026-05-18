package dev.blumdlc.client.util;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.util.InputUtil;

/**
 * Pretty-printer for GLFW key codes.
 *
 * <p>Used by the ClickGUI bind UI and by the in-game HUD to display a short,
 * human-readable label like {@code "RSHIFT"} or {@code "G"} for whichever
 * key is bound to a module.
 */
public final class KeyName {

	private KeyName() {
	}

	/** {@code -1} marks "no bind" — anything outside the GLFW range is unbound. */
	public static boolean isBound(int keyCode) {
		return keyCode >= 0;
	}

	public static String describe(int keyCode) {
		if (!isBound(keyCode)) return "—";

		// Letters / digits / common print keys: use the glyph itself.
		if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
			return Character.toString((char) ('A' + (keyCode - GLFW.GLFW_KEY_A)));
		}
		if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
			return Character.toString((char) ('0' + (keyCode - GLFW.GLFW_KEY_0)));
		}
		if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
			return "F" + (1 + keyCode - GLFW.GLFW_KEY_F1);
		}

		switch (keyCode) {
			case GLFW.GLFW_KEY_SPACE:         return "SPACE";
			case GLFW.GLFW_KEY_LEFT_SHIFT:    return "LSHIFT";
			case GLFW.GLFW_KEY_RIGHT_SHIFT:   return "RSHIFT";
			case GLFW.GLFW_KEY_LEFT_CONTROL:  return "LCTRL";
			case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCTRL";
			case GLFW.GLFW_KEY_LEFT_ALT:      return "LALT";
			case GLFW.GLFW_KEY_RIGHT_ALT:     return "RALT";
			case GLFW.GLFW_KEY_TAB:           return "TAB";
			case GLFW.GLFW_KEY_CAPS_LOCK:     return "CAPS";
			case GLFW.GLFW_KEY_ENTER:         return "ENTER";
			case GLFW.GLFW_KEY_BACKSPACE:     return "BACK";
			case GLFW.GLFW_KEY_ESCAPE:        return "ESC";
			case GLFW.GLFW_KEY_INSERT:        return "INS";
			case GLFW.GLFW_KEY_DELETE:        return "DEL";
			case GLFW.GLFW_KEY_HOME:          return "HOME";
			case GLFW.GLFW_KEY_END:           return "END";
			case GLFW.GLFW_KEY_PAGE_UP:       return "PGUP";
			case GLFW.GLFW_KEY_PAGE_DOWN:     return "PGDN";
			case GLFW.GLFW_KEY_LEFT:          return "LEFT";
			case GLFW.GLFW_KEY_RIGHT:         return "RIGHT";
			case GLFW.GLFW_KEY_UP:            return "UP";
			case GLFW.GLFW_KEY_DOWN:          return "DOWN";
			case GLFW.GLFW_KEY_GRAVE_ACCENT:  return "`";
			case GLFW.GLFW_KEY_MINUS:         return "-";
			case GLFW.GLFW_KEY_EQUAL:         return "=";
			case GLFW.GLFW_KEY_LEFT_BRACKET:  return "[";
			case GLFW.GLFW_KEY_RIGHT_BRACKET: return "]";
			case GLFW.GLFW_KEY_BACKSLASH:     return "\\";
			case GLFW.GLFW_KEY_SEMICOLON:     return ";";
			case GLFW.GLFW_KEY_APOSTROPHE:    return "'";
			case GLFW.GLFW_KEY_COMMA:         return ",";
			case GLFW.GLFW_KEY_PERIOD:        return ".";
			case GLFW.GLFW_KEY_SLASH:         return "/";
			default:
				// Fallback: ask Minecraft for a localized translation key, which
				// at worst gives us "key.keyboard.<id>".
				try {
					return InputUtil.Type.KEYSYM.createFromCode(keyCode)
						.getLocalizedText().getString().toUpperCase();
				} catch (Throwable t) {
					return "K" + keyCode;
				}
		}
	}
}
