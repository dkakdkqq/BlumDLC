package dev.blumdlc.client.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Tiny in-memory friends registry. Names are stored case-insensitively.
 * Used by combat modules to decide whether a player is a friend and therefore
 * should (or should not) be considered a valid target.
 */
public final class Friends {

	private static final Set<String> NAMES = new LinkedHashSet<>();

	private Friends() {
	}

	public static boolean add(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		return NAMES.add(name.toLowerCase(Locale.ROOT));
	}

	public static boolean remove(String name) {
		if (name == null) {
			return false;
		}
		return NAMES.remove(name.toLowerCase(Locale.ROOT));
	}

	public static boolean contains(String name) {
		return name != null && NAMES.contains(name.toLowerCase(Locale.ROOT));
	}

	public static boolean contains(PlayerEntity player) {
		return player != null && contains(player.getGameProfile().getName());
	}

	public static Set<String> all() {
		return Collections.unmodifiableSet(NAMES);
	}

	public static void clear() {
		NAMES.clear();
	}
}
