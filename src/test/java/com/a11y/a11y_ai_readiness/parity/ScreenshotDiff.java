package com.a11y.a11y_ai_readiness.parity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Pixel-by-pixel comparison between two screenshots, tolerant of the minor anti-aliasing
 * noise browsers introduce on otherwise-identical renders. Writes a diff image highlighting
 * every differing pixel in magenta, for use as evidence (docs/PLAN.md §6.1).
 */
final class ScreenshotDiff {

    /** RGB Euclidean distance below which two pixels count as "the same". */
    private static final int PER_PIXEL_THRESHOLD = 24;

    private ScreenshotDiff() {
    }

    record Result(double differingPixelPercentage, int width, int height) {
    }

    static Result compare(Path imageA, Path imageB, Path diffOutput) throws IOException {
        BufferedImage a = ImageIO.read(imageA.toFile());
        BufferedImage b = ImageIO.read(imageB.toFile());

        int width = Math.min(a.getWidth(), b.getWidth());
        int height = Math.min(a.getHeight(), b.getHeight());

        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        long differing = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelA = a.getRGB(x, y);
                int pixelB = b.getRGB(x, y);
                if (colorDistance(pixelA, pixelB) > PER_PIXEL_THRESHOLD) {
                    differing++;
                    diff.setRGB(x, y, 0xFFFF00FF);
                } else {
                    diff.setRGB(x, y, pixelA);
                }
            }
        }

        ImageIO.write(diff, "png", diffOutput.toFile());

        double percentage = (differing * 100.0) / ((long) width * height);
        return new Result(percentage, width, height);
    }

    private static int colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return (int) Math.sqrt((double) (dr * dr + dg * dg + db * db));
    }
}
