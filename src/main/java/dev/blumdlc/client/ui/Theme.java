package dev.blumdlc.client.ui;

/**
 * The GUI's flat colour palette.
 *
 * <p>Static-final values are surfaces that don't change with the user-chosen
 * client colour (panel chrome, neutral text, search/scrollbar). The accent
 * tones — {@link #ACCENT}, {@link #ACCENT_DARK}, {@link #CARD_ACTIVE_FROM}
 * and {@link #CARD_ACTIVE_TO} — are recomputed every frame from
 * {@link ClientTheme} via {@link #refresh()}, so the rest of the UI can keep
 * reading them as ordinary fields.
 */
public final class Theme {

	// =========================================================================
	//  Backdrop
	// =========================================================================

	/** Dim layer drawn behind the panel. Slightly softer than pure black so
	 *  the world stays readable underneath. */
	public static final int DIM            = 0xB8050710;

	// =========================================================================
	//  Panel chrome (cool slate, far less harsh than the original almost-black)
	// =========================================================================

	public static final int PANEL_BG       = 0xF20E1018;
	public static final int PANEL_BORDER   = 0x33FFFFFF;
	public static final int SIDEBAR_BG     = 0xFF0A0C13;
	public static final int DIVIDER        = 0x1AFFFFFF;

	// =========================================================================
	//  Cards
	// =========================================================================

	public static final int CARD_BG        = 0xFF181B26;
	public static final int CARD_HOVER     = 0xFF1F2330;
	public static final int CARD_BORDER    = 0x22FFFFFF;

	/** Active-card gradient start. Refreshed each frame from {@link ClientTheme}. */
	public static int CARD_ACTIVE_FROM     = 0xFF8B5CF6;
	/** Active-card gradient end.   Refreshed each frame from {@link ClientTheme}. */
	public static int CARD_ACTIVE_TO       = 0xFF6366F1;

	// =========================================================================
	//  Accents (refreshed each frame)
	// =========================================================================

	public static int ACCENT               = 0xFFA78BFA;
	public static int ACCENT_DARK          = 0xFF5B45A8;
	public static final int DANGER         = 0xFFF87171;

	// =========================================================================
	//  Text
	// =========================================================================

	public static final int TEXT_PRIMARY   = 0xFFE9EAEC;
	public static final int TEXT_SECONDARY = 0xFF9DA0AC;
	public static final int TEXT_MUTED     = 0xFF565969;

	// =========================================================================
	//  Search / scrollbar
	// =========================================================================

	public static final int SEARCH_BG      = 0xFF14171F;
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
