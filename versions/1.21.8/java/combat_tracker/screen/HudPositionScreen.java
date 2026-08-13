package combat_tracker.screen;

import combat_tracker.config.CtConfig;
import combat_tracker.hud.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lets the player drag the HUD overlay to any position on the screen.
 * Opened from the config screen via "Move HUD...".
 */
public class HudPositionScreen extends Screen {
    private static final int LEFT_BUTTON = 0;

    private final Screen parent;
    private final CtConfig config = CtConfig.get();

    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public HudPositionScreen(Screen parent) {
        super(Component.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
        config.hudX = clampX(config.hudX);
        config.hudY = clampY(config.hudY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        String hint = "Drag the overlay to reposition it, then click Done";
        graphics.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 24, 0xFFFFFFFF);

        HudRenderer.renderScaledAt(graphics, config.hudX, config.hudY);
        outline(graphics, config.hudX - 1, config.hudY - 1,
                HudRenderer.scaledWidth() + 2, HudRenderer.scaledHeight() + 2,
                dragging ? 0xFFFFFF55 : 0xFF55FF55);
    }

    /**
     * A one-pixel border, drawn as four fills.
     *
     * <p>{@code GuiGraphics.renderOutline} would do this in one call and does not
     * keep the same signature across 1.21.x, which is a poor reason to pin the
     * source tree to a single Minecraft version. {@code fill} has been stable
     * throughout.</p>
     */
    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    // Variant for 1.21.5 - 1.21.8: Screen's pointer callbacks still take loose
    // doubles and an int button. The event records arrived in 1.21.9.
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == LEFT_BUTTON && insideBox(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = (int) (mouseX - config.hudX);
            dragOffsetY = (int) (mouseY - config.hudY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == LEFT_BUTTON) {
            config.hudX = clampX((int) (mouseX - dragOffsetX));
            config.hudY = clampY((int) (mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == LEFT_BUTTON && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insideBox(double mx, double my) {
        int x = config.hudX;
        int y = config.hudY;
        int w = HudRenderer.scaledWidth();
        int h = HudRenderer.scaledHeight();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int clampX(int x) {
        return Math.max(1, Math.min(x, this.width - HudRenderer.scaledWidth()));
    }

    private int clampY(int y) {
        return Math.max(1, Math.min(y, this.height - HudRenderer.scaledHeight()));
    }

    @Override
    public void onClose() {
        CtConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }
}
