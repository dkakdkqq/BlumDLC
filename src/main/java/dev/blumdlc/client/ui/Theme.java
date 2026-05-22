package dev.blumdlc.client.ui;

/**
 * The GUI's flat colour palette.
 *
 * <p>Tuned for the "FeverVisual SecondGui" reference scheme: black-translucent
 * panels with a sharp blue / cyan accent and a brownish version label. The
 * accent quartet — {@link #ACCENT}, {@link #ACCENT_DARK},
 * {@link #CARD_ACTIVE_FROM} and {@link #CARD_ACTIVE_TO} — is recomputed every
 * frame from {@link ClientTheme} via {@link #refresh()}, so the rest of the
 * UI can keep reading them as ordinary fields.
 */
public final class Theme {

	// =========================================================================
	//  Backdrop
	// =========================================================================

	/** Dim layer drawn behind the panel. */
	public static final int DIM            = 0xC0030610;

	// =========================================================================
	//  Panel chrome — slate / midnight glass with a subtle vertical gradient
	// =========================================================================

	/** Top of the panel gradient. */
	public static final int PANEL_BG_TOP   = 0xCC141828;
	/** Bottom of the panel gradient. */
	public static final int PANEL_BG_BOT   = 0xE6080B14;

	/** Solid fallback (popup body, dropdown overlays). Kept for compatibility. */
	public static final int PANEL_BG       = 0xCC0B0F1A;
	public static final int PANEL_BORDER   = 0x40FFFFFF;
	/** Hairline highlight drawn just inside the outer border for depth. */
	public static final int PANEL_INNER    = 0x14FFFFFF;
	public static final int SIDEBAR_BG     = 0xCC0B0F1A;
	public static final int DIVIDER        = 0x33FFFFFF;

	// =========================================================================
	//  Cards (sidebar items, module rows, popup setting controls)
	// =========================================================================

	/** Inactive card — gradient endpoints. */
	public static final int CARD_BG_TOP    = 0x90161B2A;
	public static final int CARD_BG_BOT    = 0x9A0E1220;

	/** Solid fallback. */
	public static final int CARD_BG        = 0x90121726;
	public static final int CARD_HOVER     = 0xA01F2638;
	public static final int CARD_BORDER    = 0x22FFFFFF;

	/** Active-card gradient start. Refreshed each frame from {@link ClientTheme}. */
	public static int CARD_ACTIVE_FROM     = 0xFF005DFF;
	/** Active-card gradient end.   Refreshed each frame from {@link ClientTheme}. */
	public static int CARD_ACTIVE_TO       = 0xFF00FFFF;

	// =========================================================================
	//  Accents (refreshed each frame)
	// =========================================================================

	public static int ACCENT               = 0xFF00FFFF;
	public static int ACCENT_DARK          = 0xFF005DFF;
	/** Brownish red repurposed from the FeverVisual version-label colour. */
	public static final int DANGER         = 0xFFA65252;

	// =========================================================================
	//  Text
	// =========================================================================

	public static final int TEXT_PRIMARY   = 0xFFFFFFFF;
	public static final int TEXT_SECONDARY = 0xFFB6B9C2;
	public static final int TEXT_MUTED     = 0xFF7B7E8C;

	// =========================================================================
	//  Search / scrollbar
	// =========================================================================

	public static final int SEARCH_BG      = 0x64000000;
	public static final int SCROLLBAR_BG   = 0x14FFFFFF;
	public static final int SCROLLBAR_FG   = 0x55FFFFFF;

	// =========================================================================
	//  Refresh
	// =========================================================================

	/**
	 * Pulls the current accent quartet from {@link ClientTheme}. Cheap (only
	 * a couple of colour-lerp ops) so safe to call once per render frame.
	 */
	public static void refresh() {
		CARD_ACTIVE_FROM = ClientTheme.from();
		CARD_ACTIVE_TO   = ClientTheme.to();
		ACCENT           = ClientTheme.accent();
		ACCENT_DARK      = ClientTheme.accentDark();
	}

	private Theme() {
	}

}
