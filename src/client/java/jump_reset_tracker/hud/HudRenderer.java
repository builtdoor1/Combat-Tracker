package jump_reset_tracker.hud;

import jump_reset_tracker.config.JrtConfig;
import jump_reset_tracker.stats.StatsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Small unobtrusive top-left HUD overlay. Highlights the two headline stats
 * (jump resets hit / missed) and the supporting timing data.
 */
public class HudRenderer {
    private HudRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        JrtConfig config = JrtConfig.get();
        if (!config.hudEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        Font font = mc.font;
        StatsTracker stats = StatsTracker.get();

        String[] lines = {
                "Jump Reset Tracker",
                "Hits: " + stats.hits(),
                "Misses: " + stats.misses(),
                String.format("Rate: %.1f%%", stats.successRate()),
                String.format("Avg: %.1fms  SD: %.1fms", stats.averageDelta(), stats.stdDev()),
                "Last: " + stats.lastResult()
        };
        int[] colors = {
                0xFFFFFF55, // title - yellow
                0xFF55FF55, // hits - green
                0xFFFF5555, // misses - red
                0xFFFFFFFF, // rate - white
                0xFFAAAAAA, // avg/sd - gray
                stats.lastResultColor()
        };

        int x = 4;
        int y = 4;
        int pad = 2;
        int lineStep = font.lineHeight + 1;

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        int boxWidth = maxWidth + pad * 2;
        int boxHeight = lines.length * lineStep + pad * 2;
        graphics.fill(x - 1, y - 1, x - 1 + boxWidth, y - 1 + boxHeight, 0x90000000);

        int textY = y + pad;
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], x + pad, textY, colors[i]);
            textY += lineStep;
        }
    }
}
