package jump_reset_tracker.hud;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.record.SessionRecorder;
import jump_reset_tracker.stats.ComboStatsTracker;
import jump_reset_tracker.stats.StatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Customizable HUD overlay: position, scale, background opacity, accent theme and
 * a compact / detailed layout. Shows jump-reset stats, combo timing and a
 * recording indicator.
 */
public class HudRenderer {
    private static final int PADDING = 2;

    /** Selectable accent colors (used for the title and combo line). */
    public static final int[] THEME_COLORS = {
            0xFFFFFF55, 0xFF55FFFF, 0xFF55FF55, 0xFFFF77FF, 0xFFFFAA00, 0xFFFFFFFF
    };
    public static final String[] THEME_NAMES = {
            "Yellow", "Aqua", "Green", "Pink", "Orange", "White"
    };

    private HudRenderer() {
    }

    private record Line(String text, int color) {
    }

    public static int accentColor() {
        int i = JrtConfig.get().hudThemeIndex;
        if (i < 0 || i >= THEME_COLORS.length) {
            i = 0;
        }
        return THEME_COLORS[i];
    }

    private static int bgColor() {
        int alpha = Math.max(0, Math.min(100, JrtConfig.get().hudBgOpacityPct)) * 255 / 100;
        return (alpha << 24);
    }

    private static double scale() {
        return Math.max(0.5, Math.min(2.0, JrtConfig.get().hudScale));
    }

    // ── In-game render ─────────────────────────────────────────────────────────

    public static void render(GuiGraphics graphics) {
        JrtConfig config = JrtConfig.get();
        if (!config.hudEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int w = scaledWidth();
        int h = scaledHeight();
        int x = Math.max(1, Math.min(config.hudX, graphics.guiWidth() - w));
        int y = Math.max(1, Math.min(config.hudY, graphics.guiHeight() - h));
        renderScaledAt(graphics, x, y);
    }

    /** Render the HUD with the configured scale, top-left anchored at (x, y) in screen pixels. */
    public static void renderScaledAt(GuiGraphics graphics, int x, int y) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale((float) scale());
        drawContent(graphics);
        pose.popMatrix();
    }

    private static void drawContent(GuiGraphics graphics) {
        Font font = Minecraft.getInstance().font;
        List<Line> lines = buildLines();
        int lineStep = font.lineHeight + 1;

        graphics.fill(0, 0, unscaledWidth(lines, font), unscaledHeight(lines, font), bgColor());

        int textY = PADDING;
        for (Line line : lines) {
            graphics.drawString(font, line.text(), PADDING, textY, line.color());
            textY += lineStep;
        }
    }

    // ── Dimensions ─────────────────────────────────────────────────────────────

    public static int scaledWidth() {
        Font font = Minecraft.getInstance().font;
        List<Line> lines = buildLines();
        return (int) Math.ceil(unscaledWidth(lines, font) * scale());
    }

    public static int scaledHeight() {
        Font font = Minecraft.getInstance().font;
        List<Line> lines = buildLines();
        return (int) Math.ceil(unscaledHeight(lines, font) * scale());
    }

    private static int unscaledWidth(List<Line> lines, Font font) {
        int max = 0;
        for (Line line : lines) {
            max = Math.max(max, font.width(line.text()));
        }
        return max + PADDING * 2;
    }

    private static int unscaledHeight(List<Line> lines, Font font) {
        return lines.size() * (font.lineHeight + 1) + PADDING * 2;
    }

    // ── Content ────────────────────────────────────────────────────────────────

    private static List<Line> buildLines() {
        JrtConfig cfg = JrtConfig.get();
        StatsTracker jr = StatsTracker.get();
        ComboStatsTracker combo = ComboStatsTracker.get();
        int accent = accentColor();
        List<Line> lines = new ArrayList<>();

        SessionRecorder rec = SessionRecorder.get();
        if (rec.isRecording()) {
            long s = Math.max(0, (System.currentTimeMillis() - rec.startEpochMs()) / 1000);
            lines.add(new Line(String.format("● REC %d:%02d", s / 60, s % 60), 0xFFFF5555));
        }

        if (cfg.hudCompact) {
            lines.add(new Line(String.format("CT  JR %d/%d  %.0f%%", jr.hits(), jr.misses(), jr.successRate()), accent));
            lines.add(new Line(String.format("Combo %.0f±%.0fms", combo.averageInterval(), combo.jitter()), 0xFFFFFFFF));
        } else {
            lines.add(new Line("Combat Tracker", accent));
            lines.add(new Line(String.format("Jump: %d hit / %d miss", jr.hits(), jr.misses()), 0xFFFFFFFF));
            lines.add(new Line(String.format("Rate %.1f%%  Avg %.0f SD %.0f", jr.successRate(), jr.averageDelta(), jr.stdDev()), 0xFFAAAAAA));
            lines.add(new Line(String.format("Combo %.0fms  jitter ±%.0fms", combo.averageInterval(), combo.jitter()), accent));
            lines.add(new Line(String.format("Combos %d   Last JR: %s", combo.combos(), jr.lastResult()), jr.lastResultColor()));
        }
        return lines;
    }
}
