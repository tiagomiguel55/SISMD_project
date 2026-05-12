import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public class CompletableFutureFilter {

    private final Color[][] image;
    private final int numThreads;
    private final ExecutorService pool;

    public CompletableFutureFilter(String filename, int numThreads) {
        this.image = Utils.loadImage(filename);
        this.numThreads = numThreads;
        this.pool = Executors.newFixedThreadPool(numThreads);
    }

    public void applyHistogramFilter(String outputFile) throws Exception {
        Utils.writeImage(processImage(), outputFile);
    }

    public Color[][] processImage() throws Exception {
        Color[][] tmp   = Utils.copyImage(image);
        int width       = tmp.length;
        int height      = tmp[0].length;
        int totalPixels = width * height;

        CompletableFuture
                .supplyAsync(() -> computeHistogramParallel(tmp, width, height), pool)
                .thenApply(hist -> computeCumulativeHistogram(hist))
                .thenApply(cum  -> transformPixelsParallel(tmp, width, height, cum, totalPixels))
                .thenAccept(result -> { })
                .join();

        return tmp;
    }

    private int[] computeHistogramParallel(Color[][] tmp, int width, int height) {
        AtomicInteger[] atomicHist = new AtomicInteger[256];
        for (int i = 0; i < 256; i++) atomicHist[i] = new AtomicInteger(0);

        int chunkSize = width / numThreads;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int startX = t * chunkSize;
            final int endX   = (t == numThreads - 1) ? width : startX + chunkSize;

            futures.add(CompletableFuture.runAsync(() -> {
                // Phase 1: accumulate locally — no atomic ops during iteration
                int[] localHist = new int[256];
                for (int i = startX; i < endX; i++)
                    for (int j = 0; j < height; j++) {
                        Color px = tmp[i][j];
                        localHist[computeLuminosity(
                                px.getRed(), px.getGreen(), px.getBlue())]++;
                    }
                // Phase 2: merge into shared AtomicInteger[] — addAndGet per bucket
                for (int i = 0; i < 256; i++)
                    if (localHist[i] > 0)
                        atomicHist[i].addAndGet(localHist[i]);
            }, pool));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int[] hist = new int[256];
        for (int i = 0; i < 256; i++) hist[i] = atomicHist[i].get();
        return hist;
    }

    private int[] computeCumulativeHistogram(int[] hist) {
        int[] cumulative = new int[256];
        cumulative[0] = hist[0];
        for (int i = 1; i < 256; i++)
            cumulative[i] = cumulative[i - 1] + hist[i];
        return cumulative;
    }

    private Color[][] transformPixelsParallel(Color[][] tmp, int width, int height,
                                              int[] cumulative, int totalPixels) {
        int cdfMin = 0;
        for (int i = 0; i < 256; i++)
            if (cumulative[i] != 0) { cdfMin = cumulative[i]; break; }
        final int cdfMinFinal = cdfMin;

        int chunkSize = width / numThreads;
        CompletableFuture<?>[] stripeFutures = new CompletableFuture[numThreads];

        for (int t = 0; t < numThreads; t++) {
            final int startX = t * chunkSize;
            final int endX   = (t == numThreads - 1) ? width : startX + chunkSize;

            stripeFutures[t] = CompletableFuture.runAsync(() -> {
                for (int i = startX; i < endX; i++)
                    for (int j = 0; j < height; j++) {
                        Color px   = tmp[i][j];
                        int lum    = computeLuminosity(
                                px.getRed(), px.getGreen(), px.getBlue());
                        double cdf = (double) cumulative[lum]
                                / (double) (totalPixels - cdfMinFinal);
                        int newLum = Math.min(255, (int) Math.round(255.0 * cdf));
                        tmp[i][j]  = new Color(newLum, newLum, newLum);
                    }
            }, pool);
        }

        CompletableFuture.allOf(stripeFutures).join();
        return tmp;
    }

    public void shutdown() { pool.shutdown(); }

    private int computeLuminosity(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }
}