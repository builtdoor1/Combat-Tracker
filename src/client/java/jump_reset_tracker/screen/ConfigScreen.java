package jump_reset_tracker.screen;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.hud.HudRenderer;
import jump_reset_tracker.record.SessionRecorder;
import jump_reset_tracker.stats.StatsTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.util.function.DoubleConsumer;

/**
 * Native configuration screen (opened from Mod Menu). Two columns: timing/general
 * on the left, HUD appearance and recording on the right.
 */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private final JrtConfig config = JrtConfig.get();

    private boolean confirmingReset = false;
    private Button resetButton;
    private Button recordButton;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Combat Tracker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 154;
        int h = 20;
        int gap = 24;
        int leftX = cx - 160;
        int rightX = cx + 6;

        // ── Left column: timing + general ────────────────────────────────────
        int y = 40;
        addRenderableWidget(new ValueSlider(leftX, y, w, h, "Success min", " ms", 0, 600,
                config.window.lowerBoundMs, true, v -> config.window.lowerBoundMs = (long) v));
        y += gap;
        addRenderableWidget(new ValueSlider(leftX, y, w, h, "Success max", " ms", 0, 600,
                config.window.upperBoundMs, true, v -> config.window.upperBoundMs = (long) v));
        y += gap;
        addRenderableWidget(Button.builder(hudText(), b -> {
            config.hudEnabled = !config.hudEnabled;
            b.setMessage(hudText());
        }).bounds(leftX, y, w, h).build());
        y += gap;
        addRenderableWidget(Button.builder(chatText(), b -> {
            config.chatEnabled = !config.chatEnabled;
            b.setMessage(chatText());
        }).bounds(leftX, y, w, h).build());
        y += gap;
        addRenderableWidget(Button.builder(Component.literal("Move HUD..."),
                        b -> Minecraft.getInstance().setScreen(new HudPositionScreen(this)))
                .bounds(leftX, y, w, h).build());
        y += gap;
        resetButton = Button.builder(resetText(), b -> onResetClicked()).bounds(leftX, y, w, h).build();
        addRenderableWidget(resetButton);

        // ── Right column: HUD appearance + recording ─────────────────────────
        y = 40;
        addRenderableWidget(new ValueSlider(rightX, y, w, h, "HUD scale", "x", 0.5, 2.0,
                config.hudScale, false, v -> config.hudScale = v));
        y += gap;
        addRenderableWidget(new ValueSlider(rightX, y, w, h, "BG opacity", "%", 0, 100,
                config.hudBgOpacityPct, true, v -> config.hudBgOpacityPct = (int) v));
        y += gap;
        addRenderableWidget(Button.builder(compactText(), b -> {
            config.hudCompact = !config.hudCompact;
            b.setMessage(compactText());
        }).bounds(rightX, y, w, h).build());
        y += gap;
        addRenderableWidget(Button.builder(themeText(), b -> {
            config.hudThemeIndex = (config.hudThemeIndex + 1) % HudRenderer.THEME_NAMES.length;
            b.setMessage(themeText());
        }).bounds(rightX, y, w, h).build());
        y += gap;
        recordButton = Button.builder(recordText(), b -> {
            SessionRecorder.get().toggle();
            b.setMessage(recordText());
        }).bounds(rightX, y, w, h).build();
        addRenderableWidget(recordButton);
        y += gap;
        addRenderableWidget(Button.builder(Component.literal("Open recordings folder"), b -> openRecordings())
                .bounds(rightX, y, w, h).build());

        // ── Done ─────────────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(cx - 75, this.height - 28, 150, h).build());
    }

    private void onResetClicked() {
        if (!confirmingReset) {
            confirmingReset = true;
        } else {
            StatsTracker.get().reset();
            StatsTracker.get().save();
            confirmingReset = false;
        }
        resetButton.setMessage(resetText());
    }

    private void openRecordings() {
        try {
            Files.createDirectories(SessionRecorder.dir());
            Util.getPlatform().openPath(SessionRecorder.dir());
        } catch (Exception ignored) {
            // best-effort; the path is also printed to chat when a recording is saved
        }
    }

    private Component resetText() {
        return confirmingReset
                ? Component.literal("Click again to confirm").withStyle(ChatFormatting.RED)
                : Component.literal("Reset Stats");
    }

    private Component hudText() {
        return Component.literal("HUD: " + (config.hudEnabled ? "ON" : "OFF"));
    }

    private Component chatText() {
        return Component.literal("Chat: " + (config.chatEnabled ? "ON" : "OFF"));
    }

    private Component compactText() {
        return Component.literal("Layout: " + (config.hudCompact ? "Compact" : "Detailed"));
    }

    private Component themeText() {
        int i = config.hudThemeIndex;
        if (i < 0 || i >= HudRenderer.THEME_NAMES.length) {
            i = 0;
        }
        return Component.literal("Theme: " + HudRenderer.THEME_NAMES[i]);
    }

    private Component recordText() {
        return SessionRecorder.get().isRecording()
                ? Component.literal("Stop Recording").withStyle(ChatFormatting.RED)
                : Component.literal("Start Recording").withStyle(ChatFormatting.GREEN);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = this.font.width(this.title);
        graphics.drawString(this.font, this.title, this.width / 2 - titleWidth / 2, 16, HudRenderer.accentColor());
    }

    @Override
    public void onClose() {
        if (config.window.upperBoundMs < config.window.lowerBoundMs) {
            config.window.upperBoundMs = config.window.lowerBoundMs;
        }
        JrtConfig.save();
        StatsTracker.get().save();
        Minecraft.getInstance().setScreen(parent);
    }

    /** Generic slider mapping a [min, max] range onto the [0,1] slider position. */
    private static final class ValueSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final boolean integer;
        private final String label;
        private final String suffix;
        private final DoubleConsumer setter;

        ValueSlider(int x, int y, int width, int height, String label, String suffix,
                    double min, double max, double current, boolean integer, DoubleConsumer setter) {
            super(x, y, width, height, Component.literal(label),
                    Math.max(0.0, Math.min(1.0, (current - min) / (max - min))));
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.label = label;
            this.suffix = suffix;
            this.setter = setter;
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            return integer ? Math.round(v) : v;
        }

        @Override
        protected void updateMessage() {
            if (label == null) {
                return;
            }
            String val = integer ? Long.toString((long) current()) : String.format("%.2f", current());
            setMessage(Component.literal(label + ": " + val + suffix));
        }

        @Override
        protected void applyValue() {
            if (setter == null) {
                return;
            }
            setter.accept(current());
        }
    }
}
