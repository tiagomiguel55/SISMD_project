import java.awt.Color;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;


public class ForkJoinFilter {

    private static final int THRESHOLD = 100;

    private final Color[][] image;
    private final int numThreads;
    private final ForkJoinPool pool;

    public ForkJoinFilter(String filename, int numThreads) {
        this.image = Utils.loadImage(filename);
        this.numThreads = numThreads;
        this.pool = new ForkJoinPool(numThreads);
    }

    public void applyHistogramFilter(String outputFile) throws Exception {
        Utils.writeImage(processImage(), outputFile);
    }

    public Color[][] processImage() throws Exception {
        Color[][] tmp   = Utils.copyImage(image);
        int width       = tmp.length;
        int height      = tmp[0].length;
        int totalPixels = width * height;

        AtomicInteger[] atomicHist = new AtomicInteger[256];
        for (int i = 0; i < 256; i++) atomicHist[i] = new AtomicInteger(0);

        pool.invoke(new HistogramTask(tmp, 0, width, height, atomicHist));

        int[] hist = new int[256];
        for (int i = 0; i < 256; i++) hist[i] = atomicHist[i].get();

        int[] cumulative = computeCumulativeHistogram(hist);

        int cdfMin = 0;
        for (int i = 0; i < 256; i++)
            if (cumulative[i] != 0) { cdfMin = cumulative[i]; break; }

        pool.invoke(new TransformTask(tmp, 0, width, height, cumulative, totalPixels, cdfMin));
        return tmp;
    }

    private static class HistogramTask extends RecursiveAction {
        private final Color[][] tmp;
        private final int startX, endX, height;
        private final AtomicInteger[] atomicHist;

        HistogramTask(Color[][] tmp, int startX, int endX, int height,
                      AtomicInteger[] atomicHist) {
            this.tmp = tmp; this.startX = startX; this.endX = endX;
            this.height = height; this.atomicHist = atomicHist;
        }

        @Override
        protected void compute() {
            if (endX - startX <= THRESHOLD) {
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
                return;
            }
            int mid = startX + (endX - startX) / 2;
            HistogramTask right = new HistogramTask(tmp, mid, endX, height, atomicHist);
            right.fork();
            new HistogramTask(tmp, startX, mid, height, atomicHist).compute();
            right.join();
        }
    }

    private int[] computeCumulativeHistogram(int[] hist) {
        int[] cumulative = new int[256];
        cumulative[0] = hist[0];
        for (int i = 1; i < 256; i++)
            cumulative[i] = cumulative[i - 1] + hist[i];
        return cumulative;
    }

    private static class TransformTask extends RecursiveAction {
        private final Color[][] tmp;
        private final int startX, endX, height, totalPixels, cdfMin;
        private final int[] cumulative;

        TransformTask(Color[][] tmp, int startX, int endX, int height,
                      int[] cumulative, int totalPixels, int cdfMin) {
            this.tmp = tmp; this.startX = startX; this.endX = endX;
            this.height = height; this.cumulative = cumulative;
            this.totalPixels = totalPixels; this.cdfMin = cdfMin;
        }

        @Override
        protected void compute() {
            if (endX - startX <= THRESHOLD) {
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
                return;
            }
            int mid = startX + (endX - startX) / 2;
            TransformTask right = new TransformTask(
                    tmp, mid, endX, height, cumulative, totalPixels, cdfMin);
            right.fork();
            new TransformTask(tmp, startX, mid, height, cumulative, totalPixels, cdfMin).compute();
            right.join();
        }
    }

    public void shutdown() { pool.shutdown(); }

    private static int computeLuminosity(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }
}