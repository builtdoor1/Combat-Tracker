package jump_reset_tracker.hud;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.stats.StatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Small unobtrusive HUD overlay. Highlights the two headline stats
 * (jump resets hit / missed) and the supporting timing data. Position is taken
 * from the config and clamped on-screen.
 */
public class HudRenderer {
    private static final int PADDING = 2;

    private HudRenderer() {
    }

    /** Normal in-game render: respects the toggle, hides with the GUI / main menu. */
    public static void render(GuiGraphics graphics) {
        JrtConfig config = JrtConfig.get();
        if (!config.hudEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int x = Math.max(1, Math.min(config.hudX, graphics.guiWidth() - boxWidth()));
        int y = Math.max(1, Math.min(config.hudY, graphics.guiHeight() - boxHeight()));
        renderAt(graphics, x, y);
    }

    /** Draws the HUD at an explicit position (used by the position editor). */
    public static void renderAt(GuiGraphics graphics, int x, int y) {
        Font font = Minecraft.getInstance().font;
        StatsTracker stats = StatsTracker.get();
        String[] lines = lines(stats);
        int[] colors = colors(stats);

        int lineStep = font.lineHeight + 1;
        graphics.fill(x - 1, y - 1, x - 1 + boxWidth(), y - 1 + boxHeight(), 0x90000000);

        int textY = y + PADDING;
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], x + PADDING, textY, colors[i]);
            textY += lineStep;
        }
    }

    public static int boxWidth() {
        Font font = Minecraft.getInstance().font;
        int maxWidth = 0;
        for (String line : lines(StatsTracker.get())) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        return maxWidth + PADDING * 2;
    }

    public static int boxHeight() {
        Font font = Minecraft.getInstance().font;
        return lines(StatsTracker.get()).length * (font.lineHeight + 1) + PADDING * 2;
    }

    private static String[] lines(StatsTracker stats) {
        return new String[]{
                "Combat Tracker",
                "Hits: " + stats.hits(),
                "Misses: " + stats.misses(),
                String.format("Rate: %.1f%%", stats.successRate()),
                String.format("Avg: %.1fms  SD: %.1fms", stats.averageDelta(), stats.stdDev()),
                "Last: " + stats.lastResult()
        };
    }

    private static int[] colors(StatsTracker stats) {
        return new int[]{
                0xFFFFFF55, // title - yellow
                0xFF55FF55, // hits - green
                0xFFFF5555, // misses - red
                0xFFFFFFFF, // rate - white
                0xFFAAAAAA, // avg/sd - gray
                stats.lastResultColor()
        };
    }
}
