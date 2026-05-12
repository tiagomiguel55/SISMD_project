import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Histogram Equalization - Thread Pool Implementation
 *
 * Synchronization strategy — Stage 1: ReentrantLock (once per stripe)
 * Each Runnable accumulates counts into a local int[256] histogram
 * during the parallel phase — no lock needed during pixel processing.
 * At the end of its stripe, the Runnable acquires the ReentrantLock
 * ONCE to merge its local histogram into the shared one (256 additions).
 * ReentrantLock offers more flexibility than synchronized: supports
 * timed waits, interruptible acquisition, and lock polling.
 */
public class ThreadPoolFilter {

    private final Color[][] image;
    private final int numThreads;
    private final ExecutorService pool;

    public ThreadPoolFilter(String filename, int numThreads) {
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

        int[] hist       = computeHistogramParallel(tmp, width, height);
        int[] cumulative = computeCumulativeHistogram(hist);

        int cdfMin = 0;
        for (int i = 0; i < 256; i++)
            if (cumulative[i] != 0) { cdfMin = cumulative[i]; break; }

        transformPixelsParallel(tmp, width, height, cumulative, totalPixels, cdfMin);
        return tmp;
    }

    private int[] computeHistogramParallel(Color[][] tmp, int width, int height)
            throws Exception {

        int[] hist = new int[256];
        ReentrantLock lock = new ReentrantLock();
        int chunkSize = width / numThreads;
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int startX = t * chunkSize;
            final int endX   = (t == numThreads - 1) ? width : startX + chunkSize;

            futures.add(pool.submit(() -> {
                // Phase 1: accumulate locally — no lock needed
                int[] localHist = new int[256];
                for (int i = startX; i < endX; i++)
                    for (int j = 0; j < height; j++) {
                        Color px = tmp[i][j];
                        localHist[computeLuminosity(
                                px.getRed(), px.getGreen(), px.getBlue())]++;
                    }
                // Phase 2: merge into shared histogram — lock ONCE per thread
                lock.lock();
                try {
                    for (int i = 0; i < 256; i++) hist[i] += localHist[i];
                } finally {
                    lock.unlock();
                }
            }));
        }
        for (Future<?> f : futures) f.get();
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
            throws Exception {

        int chunkSize = width / numThreads;
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            final int startX = t * chunkSize;
            final int endX   = (t == numThreads - 1) ? width : startX + chunkSize;

            futures.add(pool.submit(() -> {
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
            }));
        }
        for (Future<?> f : futures) f.get();
    }

    public void shutdown() { pool.shutdown(); }

    private int computeLuminosity(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }
}