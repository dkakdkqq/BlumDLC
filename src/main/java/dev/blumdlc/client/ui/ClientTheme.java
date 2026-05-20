package dev.blumdlc.client.ui;

import java.util.List;

import dev.blumdlc.client.ui.animation.Animation;
import dev.blumdlc.client.ui.animation.Easing;
import dev.blumdlc.client.ui.util.ColorUtil;

/**
 * The "client colour" — controls every accent-driven surface in the GUI
 * (active card gradient, search-bar focus ring, watermark glow, HUD editor
 * selection, ...). Picked by the user from the {@link dev.blumdlc.client.modules.impl.Themes}
 * module sitting in {@code Category.THEMES}.
 *
 * <p>Each preset stores three colours:
 * <ul>
 *   <li>{@code from} / {@code to} — endpoints of the gradient used on
 *       active cards and the sidebar selector pill;</li>
 *   <li>{@code accent} — the saturated tone used for borders, focus rings,
 *       caret, click flash and the watermark dot.</li>
 * </ul>
 *
 * <p>Static accessors below crossfade between the previous and the newly
 * selected theme over ~360 ms so colour swaps feel like part of the UI
 * rather than a screen-clear.
 */
public final class ClientTheme {

	public final String name;
	public final int from;
	public final int to;
	public final int accent;

	public ClientTheme(String name, int from, int to, int accent) {
		this.name = name;
		this.from = from;
		this.to = to;
		this.accent = accent;
	}

	// ---------------------------------------------------------------------
	//  Presets
	// ---------------------------------------------------------------------

	public static final List<ClientTheme> PRESETS = List.of(
		new ClientTheme("Aurora",   0xFF8B5CF6, 0xFF6366F1, 0xFFA78BFA),
		new ClientTheme("Lavender", 0xFFB892FF, 0xFF7C5CFF, 0xFFB8A9FF),
		new ClientTheme("Ocean",    0xFF22D3EE, 0xFF3B82F6, 0xFF60A5FA),
		new ClientTheme("Mint",     0xFF34D399, 0xFF14B8A6, 0xFF5EEAD4),
		new ClientTheme("Lime",     0xFFA3E635, 0xFF65A30D, 0xFFBEF264),
		new ClientTheme("Sunset",   0xFFFB923C, 0xFFEF4444, 0xFFFCA5A5),
		new ClientTheme("Cherry",   0xFFEC4899, 0xFFBE185D, 0xFFF472B6),
		new ClientTheme("Crimson",  0xFFEF4444, 0xFFB91C1C, 0xFFF87171),
		new ClientTheme("Gold",     0xFFFCD34D, 0xFFD97706, 0xFFFDE68A),
		new ClientTheme("Mono",     0xFFE2E8F0, 0xFF94A3B8, 0xFFF1F5F9)
	);

	public static ClientTheme byName(String name) {
		for (ClientTheme t : PRESETS) {
			if (t.name.equalsIgnoreCase(name)) {
				return t;
			}
		}
		return PRESETS.get(0);
	}

	// ---------------------------------------------------------------------
	//  Active theme + crossfade
	// ---------------------------------------------------------------------

	private static ClientTheme active = PRESETS.get(0);
	private static ClientTheme previous = PRESETS.get(0);
	private static final Animation TRANSITION = new Animation(1.0f, 360, Easing.EASE_OUT_CUBIC);

	static {
		TRANSITION.setImmediate(1.0f);
	}

	public static ClientTheme active() {
		return active;
	}

	public static ClientTheme previous() {
		return previous;
	}

	/** 0 == fully on previous theme, 1 == fully on active theme. */
	public static float transitionProgress() {
		return Math.max(0.0f, Math.min(1.0f, TRANSITION.getValue()));
	}

	/** Switch to a preset by name and start the crossfade. No-op if already active. */
	public static void apply(String name) {
		ClientTheme target = byName(name);
		if (target == active) {
			return;
		}
		previous = active;
		active = target;
		TRANSITION.setImmediate(0.0f);
		TRANSITION.setTarget(1.0f);
	}

	/** Switch to a preset directly (skips the crossfade). */
	public static void applyImmediate(String name) {
		active = byName(name);
		previous = active;
		TRANSITION.setImmediate(1.0f);
	}

	// ---------------------------------------------------------------------
	//  Live accessors (used by the GUI / HUD render code)
	// ---------------------------------------------------------------------

	/** Current "from" colour, blended through the crossfade. */
	public static int from() {
		return ColorUtil.lerp(previous.from, active.from, transitionProgress());
	}

	/** Current "to" colour, blended through the crossfade. */
	public static int to() {
		return ColorUtil.lerp(previous.to, active.to, transitionProgress());
	}

	/** Current accent colour, blended through the crossfade. */
	public static int accent() {
		return ColorUtil.lerp(previous.accent, active.accent, transitionProgress());
	}

	/** Slightly darker accent (e.g. for HUD editor pulse). */
	public static int accentDark() {
		// Mix the accent with black @ 35% to get a darker companion tone.
		return ColorUtil.lerp(accent(), 0xFF000000, 0.35f);
	}
}
