import java.awt.Color;

/**
 * Histogram Equalization - Sequential Implementation
 * Baseline used for speedup calculations.
 */
public class Filters {

    private final Color[][] image;

    public Filters(String filename) {
        this.image = Utils.loadImage(filename);
    }

    public void HistogramFilter(String outputFile) {
        Color[][] result = processImage();
        Utils.writeImage(result, outputFile);
    }

    public Color[][] processImage() {
        Color[][] tmp   = Utils.copyImage(image);
        int width       = tmp.length;
        int height      = tmp[0].length;
        int totalPixels = width * height;

        // Stage 1: luminosity histogram
        int[] hist = new int[256];
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++) {
                Color px = tmp[i][j];
                hist[computeLuminosity(px.getRed(), px.getGreen(), px.getBlue())]++;
            }

        // Stage 2: cumulative histogram
        int[] cumulative = new int[256];
        cumulative[0] = hist[0];
        for (int i = 1; i < 256; i++)
            cumulative[i] = cumulative[i - 1] + hist[i];

        // cdfMin: first non-zero entry of the cumulative histogram
        int cdfMin = 0;
        for (int i = 0; i < 256; i++) {
            if (cumulative[i] != 0) { cdfMin = cumulative[i]; break; }
        }

        // Stage 3: pixel transformation
        for (int i = 0; i < width; i++)
            for (int j = 0; j < height; j++) {
                Color px   = tmp[i][j];
                int lum    = computeLuminosity(px.getRed(), px.getGreen(), px.getBlue());
                double cdf = (double) cumulative[lum] / (double) (totalPixels - cdfMin);
                int newLum = Math.min(255, (int) Math.round(255.0 * cdf));
                tmp[i][j]  = new Color(newLum, newLum, newLum);
            }

        return tmp;
    }

    private int computeLuminosity(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }
}