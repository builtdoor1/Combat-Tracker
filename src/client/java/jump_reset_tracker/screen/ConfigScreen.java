package jump_reset_tracker.screen;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.stats.StatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.LongConsumer;

/**
 * Native configuration screen, opened from the Mod Menu entry. Lets the player
 * tune the jump-reset timing window and toggle the HUD / chat messages.
 */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private final JrtConfig config = JrtConfig.get();

    public ConfigScreen(Screen parent) {
        super(Component.literal("Jump Reset Tracker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 220;
        int h = 20;
        int gap = 24;
        int y = 44;

        addRenderableWidget(new MsSlider(cx - w / 2, y, w, h, "Success window LOWER",
                config.window.lowerBoundMs, v -> config.window.lowerBoundMs = v));
        y += gap;
        addRenderableWidget(new MsSlider(cx - w / 2, y, w, h, "Success window UPPER",
                config.window.upperBoundMs, v -> config.window.upperBoundMs = v));
        y += gap;
        addRenderableWidget(new MsSlider(cx - w / 2, y, w, h, "Outer attempt window",
                config.window.outerWindowMs, v -> config.window.outerWindowMs = v));
        y += gap + 8;

        addRenderableWidget(Button.builder(hudText(), b -> {
            config.hudEnabled = !config.hudEnabled;
            b.setMessage(hudText());
        }).bounds(cx - w / 2, y, w, h).build());
        y += gap;

        addRenderableWidget(Button.builder(chatText(), b -> {
            config.chatEnabled = !config.chatEnabled;
            b.setMessage(chatText());
        }).bounds(cx - w / 2, y, w, h).build());
        y += gap + 8;

        addRenderableWidget(Button.builder(Component.literal("Reset Stats"), b -> {
            StatsTracker.get().reset();
            StatsTracker.get().save();
        }).bounds(cx - w / 2, y, w, h).build());
        y += gap;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(cx - w / 2, y, w, h).build());
    }

    private Component hudText() {
        return Component.literal("HUD: " + (config.hudEnabled ? "ON" : "OFF"));
    }

    private Component chatText() {
        return Component.literal("Chat messages: " + (config.chatEnabled ? "ON" : "OFF"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = this.font.width(this.title);
        graphics.drawString(this.font, this.title, this.width / 2 - titleWidth / 2, 18, 0xFFFFFF55);
    }

    @Override
    public void onClose() {
        // Keep the success window sane before persisting.
        if (config.window.upperBoundMs < config.window.lowerBoundMs) {
            config.window.upperBoundMs = config.window.lowerBoundMs;
        }
        JrtConfig.save();
        StatsTracker.get().save();
        Minecraft.getInstance().setScreen(parent);
    }

    /** Slider mapping a 0..600ms value onto the [0,1] slider range. */
    private static final class MsSlider extends AbstractSliderButton {
        private static final double MAX_MS = 600.0;
        private final String label;
        private final LongConsumer setter;

        MsSlider(int x, int y, int width, int height, String label, long initialMs, LongConsumer setter) {
            super(x, y, width, height, Component.literal(label), clamp01(initialMs / MAX_MS));
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        private static double clamp01(double v) {
            return Math.max(0.0, Math.min(1.0, v));
        }

        private long currentMs() {
            return Math.round(this.value * MAX_MS);
        }

        @Override
        protected void updateMessage() {
            if (label == null) {
                return; // guard against the super constructor calling this early
            }
            setMessage(Component.literal(label + ": " + currentMs() + " ms"));
        }

        @Override
        protected void applyValue() {
            if (setter == null) {
                return;
            }
            setter.accept(currentMs());
        }
    }
}
