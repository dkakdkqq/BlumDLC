package dev.blumdlc.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;

import dev.blumdlc.client.BlumDLC;
import dev.blumdlc.client.modules.Category;
import dev.blumdlc.client.modules.Module;
import dev.blumdlc.client.modules.impl.Themes;
import dev.blumdlc.client.msdf.MsdfFont;
import dev.blumdlc.client.settings.*;
import dev.blumdlc.client.ui.animation.Animation;
import dev.blumdlc.client.ui.animation.Easing;
import dev.blumdlc.client.ui.util.ColorUtil;
import dev.blumdlc.client.util.KeyName;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * DropDown ClickGUI — classic category panels at the top of the screen,
 * each expandable into a vertical list of modules. Click a module to toggle;
 * expand its settings sub-panel with a right-click or arrow. Smooth animations,
 * blur backdrop, accent-coloured headers.
 */
public final class ClickGuiScreen extends Screen {

	// =========================================================================
	//  Geometry constants
	// =========================================================================

	private static final float PANEL_W       = 105.0f;
	private static final float HEADER_H      = 18.0f;
	private static final float MODULE_H      = 14.0f;
	private static final float SETTING_H     = 12.0f;
	private static final float CORNER        = 4.0f;
	private static final float GAP           = 3.0f;
	private static final float PANEL_GAP     = 4.0f;

	/** Official Bloom logo — bundled in resources. */
	private static final Identifier LOGO_TEX =
		Identifier.of("blumdlc", "textures/logo/logo.png");
	private static final String     BRAND    = "Bloom";

	// =========================================================================
	//  Category panels
	// =========================================================================

	private static final Category[] CATEGORIES = {
		Category.COMBAT, Category.MOVEMENT, Category.RENDER,
		Category.PLAYER, Category.UTIL
	};

	private final List<Panel> panels = new ArrayList<>();
	private final Animation openAnim = new Animation(0.0f, 280, Easing.EASE_OUT_QUINT);

	// Dragging
	private Panel dragPanel = null;
	private float dragOffX, dragOffY;

	// =========================================================================
	//  Lifecycle
	// =========================================================================

	public ClickGuiScreen() {
		super(Text.literal("Blum+"));
	}

	@Override
	protected void init() {
		super.init();
		if (panels.isEmpty()) {
			float startX = 10.0f;
			for (Category cat : CATEGORIES) {
				Panel p = new Panel(cat, startX, 10.0f);
				panels.add(p);
				startX += PANEL_W + PANEL_GAP;
			}
		}
		openAnim.setImmediate(0.0f);
		openAnim.setTarget(1.0f);
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

	@Override
	public void close() {
		super.close();
	}

	// =========================================================================
	//  Render
	// =========================================================================

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		Themes.syncAll();
		Theme.refresh();

		Matrix4f m = context.getMatrices().peek().getPositionMatrix();
		MsdfFont font = Fonts.BIKO.get();
		float open = openAnim.getValue();

		// Dim backdrop
		UIRender.rect(m, 0, 0, this.width, this.height, 0, ColorUtil.withAlpha(0x000000, 0.45f * open));

		for (Panel panel : panels) {
			panel.render(m, font, mouseX, mouseY, open, delta);
		}

		// Centred branding badge at the bottom — logo + "Bloom" wordmark.
		drawBrandBadge(m, font, open);

		super.render(context, mouseX, mouseY, delta);
	}

	/**
	 * Floating logo + wordmark badge anchored to the bottom-centre of the
	 * screen. Mirrors the Watermark HUD style at a smaller scale so the
	 * GUI always shows the official Bloom branding regardless of which
	 * panels are open.
	 */
	private void drawBrandBadge(Matrix4f m, MsdfFont font, float open) {
		if (open < 0.01f) return;

		float fontSize = 11.0f;
		float padX = 12.0f;
		float padY = 7.0f;
		float gap  = 6.0f;
		float logoSize = fontSize + 3.0f;

		float textW = UIRender.textWidth(font, BRAND, fontSize);
		float w = padX + logoSize + gap + textW + padX;
		float h = fontSize + padY * 2.0f;

		float bx = (this.width - w) * 0.5f;
		float by = this.height - h - 12.0f;

		// Subtle accent halo behind the badge.
		int accent = ClientTheme.accent();
		UIRender.rect(m, bx - 5.0f, by - 4.0f, w + 10.0f, h + 8.0f, 12.0f,
			ColorUtil.withAlpha(accent, 0.18f * open));

		// Frosted glass background.
		UIRender.blur(m, bx, by, w, h, 8.0f, 12.0f,
			ColorUtil.withAlpha(0x000D1117, 0.55f * open));
		UIRender.rectGradientV(m, bx, by, w, h, 8.0f,
			ColorUtil.withAlpha(0x0D1117, 0.85f * open),
			ColorUtil.withAlpha(0x070A12, 0.92f * open));
		UIRender.border(m, bx, by, w, h, 8.0f, 0.9f,
			ColorUtil.withAlpha(accent, 0.55f * open));

		// Bottom accent line (uses theme gradient).
		UIRender.rectGradientH(m, bx + 6.0f, by + h - 1.6f, w - 12.0f, 1.6f, 0.8f,
			ColorUtil.withAlpha(ClientTheme.from(), 0.85f * open),
			ColorUtil.withAlpha(ClientTheme.to(),   0.85f * open));

		// Logo — render with full alpha (the panel alpha already comes from open).
		float logoX = bx + padX;
		float logoY = by + (h - logoSize) * 0.5f;
		UIRender.texture(m, LOGO_TEX,
			logoX, logoY, logoSize, logoSize,
			ColorUtil.withAlpha(0xFFFFFF, open));

		// "Bloom" text — gradient-tinted toward white for crispness.
		float tx = logoX + logoSize + gap;
		float ty = by + padY;
		int textColor = ColorUtil.lerp(ClientTheme.from(), ClientTheme.to(), 0.4f);
		textColor = ColorUtil.lerp(textColor, 0xFFFFFFFF, 0.3f);
		UIRender.text(m, font, BRAND, tx + 0.4f, ty + 0.6f, fontSize,
			ColorUtil.withAlpha(0x000000, 0.5f * open), 0.06f);
		UIRender.text(m, font, BRAND, tx, ty, fontSize,
			ColorUtil.withAlpha(textColor, open), 0.07f);
	}

	// =========================================================================
	//  Input
	// =========================================================================

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		// Process panels in reverse (top-most first)
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel p = panels.get(i);
			if (p.mouseClicked(mx, my, button)) {
				return true;
			}
		}

		// Header drag detection
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel p = panels.get(i);
			if (mx >= p.x && mx <= p.x + PANEL_W && my >= p.y && my <= p.y + HEADER_H) {
				if (button == 0) {
					dragPanel = p;
					dragOffX = (float) mx - p.x;
					dragOffY = (float) my - p.y;
					return true;
				}
				if (button == 1) {
					p.expanded = !p.expanded;
					p.expandAnim.setTarget(p.expanded ? 1.0f : 0.0f);
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (button == 0) dragPanel = null;
		for (Panel p : panels) p.mouseReleased(mx, my, button);
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragPanel != null && button == 0) {
			dragPanel.x = (float) mx - dragOffX;
			dragPanel.y = (float) my - dragOffY;
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
		for (Panel p : panels) {
			if (mx >= p.x && mx <= p.x + PANEL_W) {
				p.scroll -= (float) vert * 14.0f;
				if (p.scroll < 0) p.scroll = 0;
				return true;
			}
		}
		return super.mouseScrolled(mx, my, horiz, vert);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for (Panel p : panels) {
			if (p.keyPressed(keyCode)) return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// =========================================================================
	//  Panel (one per category)
	// =========================================================================

	private class Panel {
		final Category category;
		float x, y;
		boolean expanded = true;
		float scroll = 0;
		final Animation expandAnim = new Animation(1.0f, 240, Easing.EASE_OUT_CUBIC);
		final Map<Module, Animation> moduleAnims = new HashMap<>();
		final Map<Module, Boolean> settingsOpen = new HashMap<>();
		final Map<Module, Animation> settingsAnims = new HashMap<>();
		Module bindingModule = null;

		// Slider dragging
		NumberSetting draggingSlider = null;
		Module draggingModule = null;

		Panel(Category category, float x, float y) {
			this.category = category;
			this.x = x;
			this.y = y;
		}

		List<Module> getModules() {
			return BlumDLC.MODULES.byCategory(category);
		}

		void render(Matrix4f m, MsdfFont font, int mx, int my, float open, float delta) {
			float alpha = open;
			List<Module> modules = getModules();
			float expandT = expandAnim.getValue();

			// Header background with accent gradient
			int headerFrom = ColorUtil.withAlpha(ClientTheme.from(), 0.9f * alpha);
			int headerTo = ColorUtil.withAlpha(ClientTheme.to(), 0.9f * alpha);
			UIRender.rectGradientH(m, x, y, PANEL_W, HEADER_H, CORNER, headerFrom, headerTo);
			UIRender.border(m, x, y, PANEL_W, HEADER_H, CORNER, 0.8f,
				ColorUtil.withAlpha(0xFFFFFF, 0.15f * alpha));

			// Category name
			String name = category.displayName;
			float tw = UIRender.textWidth(font, name, 8.0f);
			UIRender.text(m, font, name, x + (PANEL_W - tw) * 0.5f, y + 5.0f, 8.0f,
				ColorUtil.withAlpha(0xFFFFFF, 0.95f * alpha), 0.07f);

			// Expand indicator
			String arrow = expanded ? "v" : ">";
			UIRender.text(m, font, arrow, x + PANEL_W - 12.0f, y + 5.5f, 6.5f,
				ColorUtil.withAlpha(0xFFFFFF, 0.6f * alpha), 0.05f);

			if (expandT < 0.01f) return;

			// Module list body
			float bodyY = y + HEADER_H + 1.0f;
			float bodyH = computeBodyHeight(modules, font);

			// Body background with blur
			UIRender.blur(m, x, bodyY, PANEL_W, bodyH * expandT, CORNER, 8.0f,
				ColorUtil.withAlpha(0x0A0E1A, 0.7f * alpha));
			UIRender.rect(m, x, bodyY, PANEL_W, bodyH * expandT, CORNER,
				ColorUtil.withAlpha(0x0D1117, 0.88f * alpha));
			UIRender.border(m, x, bodyY, PANEL_W, bodyH * expandT, CORNER, 0.7f,
				ColorUtil.withAlpha(0xFFFFFF, 0.06f * alpha));

			float ry = bodyY + 2.0f - scroll;
			for (Module mod : modules) {
				if (ry - bodyY > bodyH * expandT) break;
				if (ry + MODULE_H < bodyY) { ry += MODULE_H + GAP + settingsHeight(mod, font); continue; }

				Animation togAnim = moduleAnims.computeIfAbsent(mod,
					k -> new Animation(mod.enabled ? 1.0f : 0.0f, 180, Easing.EASE_OUT_CUBIC));
				togAnim.setTarget(mod.enabled ? 1.0f : 0.0f);
				float togT = togAnim.getValue();

				boolean hovered = mx >= x + 2 && mx <= x + PANEL_W - 2
					&& my >= ry && my <= ry + MODULE_H && my >= bodyY && my <= bodyY + bodyH * expandT;

				// Module row background
				if (togT > 0.01f) {
					int onColor = ColorUtil.withAlpha(ClientTheme.accent(), 0.25f * togT * alpha);
					UIRender.rect(m, x + 2, ry, PANEL_W - 4, MODULE_H, 3.0f, onColor);
				}
				if (hovered) {
					UIRender.rect(m, x + 2, ry, PANEL_W - 4, MODULE_H, 3.0f,
						ColorUtil.withAlpha(0xFFFFFF, 0.06f * alpha));
				}

				// Left accent bar when enabled
				if (togT > 0.01f) {
					int barColor = ColorUtil.withAlpha(ClientTheme.accent(), 0.8f * togT * alpha);
					UIRender.rect(m, x + 2, ry + 2, 1.5f, MODULE_H - 4, 0.75f, barColor);
				}

				// Module name
				int nameColor = togT > 0.5f
					? ColorUtil.lerp(0xFFCCCCCC, ClientTheme.accent(), togT * 0.6f)
					: 0xFFBBBBBB;
				UIRender.text(m, font, mod.name, x + 8, ry + 3.5f, 6.5f,
					ColorUtil.withAlpha(nameColor, alpha), 0.05f);

				// Bind indicator
				if (bindingModule == mod) {
					UIRender.text(m, font, "...", x + PANEL_W - 18, ry + 3.5f, 6.5f,
						ColorUtil.withAlpha(ClientTheme.accent(), alpha), 0.05f);
				} else if (KeyName.isBound(mod.keybind)) {
					String kn = KeyName.describe(mod.keybind);
					float knW = UIRender.textWidth(font, kn, 5.5f);
					UIRender.text(m, font, kn, x + PANEL_W - knW - 6, ry + 4.5f, 5.5f,
						ColorUtil.withAlpha(0xFF888888, alpha), 0.04f);
				}

				ry += MODULE_H;

				// Settings sub-panel
				boolean sOpen = settingsOpen.getOrDefault(mod, false);
				Animation sAnim = settingsAnims.computeIfAbsent(mod,
					k -> new Animation(0.0f, 200, Easing.EASE_OUT_CUBIC));
				sAnim.setTarget(sOpen ? 1.0f : 0.0f);
				float sT = sAnim.getValue();

				if (sT > 0.01f && !mod.settings.isEmpty()) {
					float sH = settingsContentHeight(mod, font) * sT;
					UIRender.rect(m, x + 4, ry, PANEL_W - 8, sH, 2.0f,
						ColorUtil.withAlpha(0x000000, 0.3f * alpha));

					float sy = ry + 1.0f;
					for (Setting<?> setting : mod.settings) {
						if (setting.getVisibility() != null && !setting.getVisibility().get()) continue;
						sy = renderSetting(m, font, setting, x + 6, sy, PANEL_W - 12, alpha, mx, my, mod);
					}
					ry += sH + 1.0f;
				} else if (!mod.settings.isEmpty()) {
					// Reserve zero space when closed
				}

				ry += GAP;
			}
		}

		float settingsHeight(Module mod, MsdfFont font) {
			boolean sOpen = settingsOpen.getOrDefault(mod, false);
			Animation sAnim = settingsAnims.get(mod);
			float sT = (sAnim != null) ? sAnim.getValue() : 0.0f;
			if (sT < 0.01f || mod.settings.isEmpty()) return 0;
			return settingsContentHeight(mod, font) * sT + 1.0f;
		}

		float settingsContentHeight(Module mod, MsdfFont font) {
			float h = 0;
			for (Setting<?> s : mod.settings) {
				if (s.getVisibility() != null && !s.getVisibility().get()) continue;
				if (s instanceof ModeSetting ms && ms.expanded) {
					h += SETTING_H + ms.modes.size() * (SETTING_H - 2);
				} else {
					h += SETTING_H;
				}
			}
			return h + 2.0f;
		}

		float computeBodyHeight(List<Module> modules, MsdfFont font) {
			float h = 4.0f;
			for (Module mod : modules) {
				h += MODULE_H + GAP + settingsHeight(mod, font);
			}
			return Math.min(h, 300.0f);
		}

		float renderSetting(Matrix4f m, MsdfFont font, Setting<?> setting,
				float sx, float sy, float sw, float alpha, int mx, int my, Module mod) {
			if (setting instanceof BooleanSetting bs) {
				// Checkbox-style
				boolean on = bs.get();
				int col = on ? ColorUtil.withAlpha(ClientTheme.accent(), 0.8f * alpha)
					: ColorUtil.withAlpha(0x555555, 0.6f * alpha);
				UIRender.rect(m, sx, sy + 1.5f, 8.0f, 8.0f, 2.0f, col);
				if (on) UIRender.text(m, font, "x", sx + 1.5f, sy + 2.0f, 6.0f,
					ColorUtil.withAlpha(0xFFFFFF, alpha), 0.06f);
				UIRender.text(m, font, bs.name, sx + 11, sy + 2.5f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
				return sy + SETTING_H;
			}
			if (setting instanceof NumberSetting ns) {
				// Slider
				UIRender.text(m, font, ns.name, sx, sy + 1.0f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
				float valW = sw;
				float barY = sy + 7.5f;
				float barH = 3.0f;
				float pct = (float) ((ns.get() - ns.min) / (ns.max - ns.min));
				UIRender.rect(m, sx, barY, valW, barH, 1.5f,
					ColorUtil.withAlpha(0x333333, 0.7f * alpha));
				UIRender.rect(m, sx, barY, valW * pct, barH, 1.5f,
					ColorUtil.withAlpha(ClientTheme.accent(), 0.75f * alpha));
				// Value label
				String valStr = String.format("%.1f", ns.get());
				UIRender.text(m, font, valStr, sx + valW - UIRender.textWidth(font, valStr, 5.0f),
					sy + 0.5f, 5.0f, ColorUtil.withAlpha(0xFF999999, alpha), 0.04f);
				return sy + SETTING_H;
			}
			if (setting instanceof ModeSetting ms) {
				UIRender.text(m, font, ms.name + ": " + ms.get(), sx, sy + 2.5f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
				if (ms.expanded) {
					float ey = sy + SETTING_H;
					for (String mode : ms.modes) {
						boolean sel = mode.equals(ms.get());
						int mCol = sel ? ColorUtil.withAlpha(ClientTheme.accent(), 0.7f * alpha)
							: ColorUtil.withAlpha(0xFF888888, alpha);
						UIRender.text(m, font, (sel ? "> " : "  ") + mode, sx + 4, ey + 1.5f, 5.0f,
							mCol, 0.04f);
						ey += SETTING_H - 2;
					}
					return ey;
				}
				return sy + SETTING_H;
			}
			if (setting instanceof ColorSetting cs) {
				UIRender.text(m, font, cs.name, sx, sy + 2.5f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
				int preview = cs.toArgb();
				UIRender.rect(m, sx + sw - 10, sy + 2, 8.0f, 8.0f, 2.0f,
					ColorUtil.withAlpha(preview, alpha));
				return sy + SETTING_H;
			}
			if (setting instanceof MultiSetting ms) {
				UIRender.text(m, font, ms.name, sx, sy + 2.5f, 5.5f,
					ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
				return sy + SETTING_H;
			}
			// Fallback
			UIRender.text(m, font, setting.name, sx, sy + 2.5f, 5.5f,
				ColorUtil.withAlpha(0xFFBBBBBB, alpha), 0.04f);
			return sy + SETTING_H;
		}

		boolean mouseClicked(double mx, double my, int button) {
			if (expandAnim.getValue() < 0.01f) return false;

			List<Module> modules = getModules();
			float bodyY = y + HEADER_H + 1.0f;
			float bodyH = computeBodyHeight(modules, Fonts.BIKO.get());
			float expandT = expandAnim.getValue();

			if (mx < x || mx > x + PANEL_W || my < bodyY || my > bodyY + bodyH * expandT)
				return false;

			float ry = bodyY + 2.0f - scroll;
			MsdfFont font = Fonts.BIKO.get();
			for (Module mod : modules) {
				if (my >= ry && my < ry + MODULE_H) {
					if (button == 0) {
						mod.toggle();
						return true;
					}
					if (button == 1) {
						boolean cur = settingsOpen.getOrDefault(mod, false);
						settingsOpen.put(mod, !cur);
						return true;
					}
					if (button == 2) {
						bindingModule = (bindingModule == mod) ? null : mod;
						return true;
					}
				}
				ry += MODULE_H;

				// Settings area clicks
				boolean sOpen = settingsOpen.getOrDefault(mod, false);
				Animation sAnim = settingsAnims.get(mod);
				float sT = sAnim != null ? sAnim.getValue() : 0;
				if (sT > 0.01f && !mod.settings.isEmpty()) {
					float sH = settingsContentHeight(mod, font) * sT;
					if (my >= ry && my < ry + sH) {
						handleSettingClick(mod, mx, my, ry, font, button);
						return true;
					}
					ry += sH + 1.0f;
				}
				ry += GAP;
			}
			return false;
		}

		void handleSettingClick(Module mod, double mx, double my, float startY, MsdfFont font, int button) {
			float sy = startY + 1.0f;
			for (Setting<?> setting : mod.settings) {
				if (setting.getVisibility() != null && !setting.getVisibility().get()) continue;

				float sH = SETTING_H;
				if (setting instanceof ModeSetting ms && ms.expanded) {
					sH = SETTING_H + ms.modes.size() * (SETTING_H - 2);
				}

				if (my >= sy && my < sy + sH) {
					if (setting instanceof BooleanSetting bs) {
						bs.toggle();
					} else if (setting instanceof NumberSetting ns) {
						float pct = (float) ((mx - (x + 6)) / (PANEL_W - 12));
						pct = Math.max(0, Math.min(1, pct));
						double val = ns.min + (ns.max - ns.min) * pct;
						val = Math.round(val / ns.step) * ns.step;
						ns.set(val);
						draggingSlider = ns;
						draggingModule = mod;
					} else if (setting instanceof ModeSetting ms) {
						if (ms.expanded) {
							float ey = sy + SETTING_H;
							for (String mode : ms.modes) {
								if (my >= ey && my < ey + SETTING_H - 2) {
									ms.set(mode);
									break;
								}
								ey += SETTING_H - 2;
							}
						} else {
							ms.cycle();
						}
					} else if (setting instanceof ColorSetting cs) {
						// Cycle hue by 30
						cs.set((cs.get() + 30.0) % 360.0);
					}
					return;
				}
				sy += sH;
			}
		}

		void mouseReleased(double mx, double my, int button) {
			draggingSlider = null;
			draggingModule = null;
		}

		boolean keyPressed(int keyCode) {
			if (bindingModule != null) {
				if (keyCode == 256) {
					bindingModule.keybind = -1;
				} else {
					bindingModule.keybind = keyCode;
				}
				bindingModule = null;
				return true;
			}
			return false;
		}
	}
}
