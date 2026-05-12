import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class MultithreadedFilter {

    private final Color[][] image;
    private final int numThreads;

    public MultithreadedFilter(String filename, int numThreads) {
        this.image = Utils.loadImage(filename);
        this.numThreads = numThreads;
    }

    public void applyHistogramFilter(String outputFile) throws Exception {
        Utils.writeImage(processImage(), outputFile);
    }

    public Color[][] processImage() throws Exception {
        Color[][] tmp   = Utils.copyImage(image);
        int width       = tmp.length;
        int height      = tmp[0].length;
        int totalPixels = width * height;

        int[] hist       = computeHistogramParallel(tmp, width, height);
        int[] cumulative = computeCumulativeHistogram(hist);

        int cdfMin = 0;
        for (int i = 0; i < 256; i++)
            if (cumulative[i] != 0) { cdfMin = cumulative[i]; break; }

        transformPixelsParallel(tmp, width, height, cumulative, totalPixels, cdfMin);
        return tmp;
    }

    private int[] computeHistogramParallel(Color[][] tmp, int width, int height)
            throws InterruptedException {

        int[] hist = new int[256];
        int chunkSize = width / numThreads;
        List<Thread> threads = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int startX = t * chunkSize;
            final int endX   = (t == numThreads - 1) ? width : startX + chunkSize;

            Thread thread = new Thread(() -> {
                // Phase 1: accumulate locally — no lock needed
                int[] localHist = new int[256];
                for (int i = startX; i < endX; i++)
                    for (int j = 0; j < height; j++) {
                        Color px = tmp[i][j];
                        localHist[computeLuminosity(
                                px.getRed(), px.getGreen(), px.getBlue())]++;
                    }
                // Phase 2: merge into shared histogram — synchronized ONCE per thread
                synchronized (hist) {
                    for (int i = 0; i < 256; i++) hist[i] += localHist[i];
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) thread.join();
        return hist;
    }

    private int[] computeCumulativeHistogram(int[] hist) {
        int[] cumulative = new int[256];
        cumulative[0] = hist[0];
        for (int i = 1; i < 256; i++)
            cumulative[i] = cumulative[i - 1] + hist[i];
        return cumulative;
    }

    private void transformPixelsParallel(Color[][] tmp, int width, int height,
                                         int[] cumulative, int totalPixels, int cdfMin)
            throws InterruptedException {

        int chunkSize = width / numThreads;
        List<Thread> threads = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int startX = t * chunkSize;
            final int endX   = (t == numThreads - 1) ? width : startX + chunkSize;

            Thread thread = new Thread(() -> {
                for (int i = startX; i < endX; i++)
                    for (int j = 0; j < height; j++) {
                        Color px   = tmp[i][j];
                        int lum    = computeLuminosity(
                                px.getRed(), px.getGreen(), px.getBlue());
                        double cdf = (double) cumulative[lum]
                                / (double) (totalPixels - cdfMin);
                        int newLum = Math.min(255, (int) Math.round(255.0 * cdf));
                        tmp[i][j]  = new Color(newLum, newLum, newLum);
                    }
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) thread.join();
    }

    private int computeLuminosity(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }
}