import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Scanner;

public class ApplyFilters {

    static final int WARMUP_RUNS   = 3;
    static final int MEASURED_RUNS = 5;

    public static void main(String[] args) throws Exception {

        Scanner input = new Scanner(System.in);
        System.out.println("Insert the path of the image file:");
        String filePath = input.nextLine();
        System.out.println("Number of threads to use:");
        int numThreads = Integer.parseInt(input.nextLine().trim());
        input.close();

        System.out.println("\nCPU cores     : " + Runtime.getRuntime().availableProcessors());
        System.out.println("Threads       : " + numThreads);
        System.out.println("Warmup runs   : " + WARMUP_RUNS);
        System.out.println("Measured runs : " + MEASURED_RUNS);
        System.out.println("Image         : " + filePath);
        System.out.println();

        // ---------------------------------------------------------------
        // Benchmark — one implementation at a time, measuring only
        // processImage() (no I/O, no pool creation overhead).
        // Each instance goes out of scope after its method returns so the
        // GC can reclaim its Color[][] before the next image load.
        // ---------------------------------------------------------------
        long seqAvg = benchmarkSequential(filePath);
        long mtAvg  = benchmarkMT(filePath, numThreads);
        long tpAvg  = benchmarkTP(filePath, numThreads);
        long fjAvg  = benchmarkFJ(filePath, numThreads);
        long cfAvg  = benchmarkCF(filePath, numThreads);

        System.out.println("==============================================");
        System.out.println("              FINAL RESULTS                  ");
        System.out.println("==============================================");
        System.out.printf ("  %-28s %6s  %7s%n", "Implementation", "Avg ms", "Speedup");
        System.out.println("==============================================");
        printRow("Sequential",        seqAvg, seqAvg);
        printRow("Multithreaded",     mtAvg,  seqAvg);
        printRow("Thread Pool",       tpAvg,  seqAvg);
        printRow("Fork/Join",         fjAvg,  seqAvg);
        printRow("CompletableFuture", cfAvg,  seqAvg);
        System.out.println("==============================================");

        // GC Statistics
        System.out.println("\n=== GC Statistics ===");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName()
                    + " | Collections: " + gc.getCollectionCount()
                    + " | Time: "        + gc.getCollectionTime() + " ms");
        }

        // ---------------------------------------------------------------
        // Write output images — done AFTER benchmark to avoid any
        // interference with timing measurements.
        // Each implementation is instantiated fresh here.
        // ---------------------------------------------------------------
        System.out.println("\nGenerating output images...");
        writeOutputs(filePath, numThreads);
        System.out.println("Done. Check out_*.jpg files.");
    }

    // -------------------------------------------------------------------------
    // Benchmark methods
    // -------------------------------------------------------------------------

    static long benchmarkSequential(String fp) throws Exception {
        Filters impl = new Filters(fp);
        return runBenchmark("Sequential", impl::processImage);
    }

    static long benchmarkMT(String fp, int n) throws Exception {
        MultithreadedFilter impl = new MultithreadedFilter(fp, n);
        return runBenchmark("Multithreaded (Producer-Consumer)", impl::processImage);
    }

    static long benchmarkTP(String fp, int n) throws Exception {
        ThreadPoolFilter impl = new ThreadPoolFilter(fp, n);
        long result = runBenchmark("Thread Pool (Local histograms)", impl::processImage);
        impl.shutdown();
        return result;
    }

    static long benchmarkFJ(String fp, int n) throws Exception {
        ForkJoinFilter impl = new ForkJoinFilter(fp, n);
        long result = runBenchmark("Fork/Join", impl::processImage);
        impl.shutdown();
        return result;
    }

    static long benchmarkCF(String fp, int n) throws Exception {
        CompletableFutureFilter impl = new CompletableFutureFilter(fp, n);
        long result = runBenchmark("CompletableFuture", impl::processImage);
        impl.shutdown();
        return result;
    }

    // -------------------------------------------------------------------------
    // Write one output image per implementation — called after benchmark
    // -------------------------------------------------------------------------

    static void writeOutputs(String fp, int n) throws Exception {
        java.io.File outDir = new java.io.File("docs/processed_images");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        // Sequential
        new Filters(fp).HistogramFilter("docs/processed_images/out_sequential.jpg");
        System.out.println("  [OK] docs/processed_images/out_sequential.jpg");
        System.gc(); Thread.sleep(200);

        // Multithreaded
        new MultithreadedFilter(fp, n).applyHistogramFilter("docs/processed_images/out_multithreaded.jpg");
        System.out.println("  [OK] docs/processed_images/out_multithreaded.jpg");
        System.gc(); Thread.sleep(200);

        // Thread Pool
        ThreadPoolFilter tp = new ThreadPoolFilter(fp, n);
        tp.applyHistogramFilter("docs/processed_images/out_threadpool.jpg");
        tp.shutdown();
        System.out.println("  [OK] docs/processed_images/out_threadpool.jpg");
        System.gc(); Thread.sleep(200);

        // Fork/Join
        ForkJoinFilter fj = new ForkJoinFilter(fp, n);
        fj.applyHistogramFilter("docs/processed_images/out_forkjoin.jpg");
        fj.shutdown();
        System.out.println("  [OK] docs/processed_images/out_forkjoin.jpg");
        System.gc(); Thread.sleep(200);

        // CompletableFuture
        CompletableFutureFilter cf = new CompletableFutureFilter(fp, n);
        cf.applyHistogramFilter("docs/processed_images/out_completablefuture.jpg");
        cf.shutdown();
        System.out.println("  [OK] docs/processed_images/out_completablefuture.jpg");
    }

    // -------------------------------------------------------------------------
    // Generic benchmark runner
    // -------------------------------------------------------------------------

    static long runBenchmark(String name, BenchmarkTask task) throws Exception {
        System.out.println("[" + name + "]");

        System.out.println("  Warming up...");
        for (int r = 0; r < WARMUP_RUNS; r++) task.run();

        long[] times = new long[MEASURED_RUNS];
        for (int r = 0; r < MEASURED_RUNS; r++) {
            System.gc();
            Thread.sleep(50);
            long start = System.currentTimeMillis();
            task.run();
            times[r] = System.currentTimeMillis() - start;
            System.out.println("  Run " + (r + 1) + ": " + times[r] + " ms");
        }

        long sum = 0;
        for (long t : times) sum += t;
        long avg = sum / MEASURED_RUNS;

        double variance = 0;
        for (long t : times) variance += (t - avg) * (t - avg);
        long stdDev = (long) Math.sqrt(variance / MEASURED_RUNS);

        System.out.println("  Avg: " + avg + " ms  ±" + stdDev + " ms\n");

        System.gc();
        Thread.sleep(200);
        return avg;
    }

    static void printRow(String name, long ms, long seqMs) {
        System.out.printf("  %-28s %6d  %6.2fx%n", name, ms, (double) seqMs / ms);
    }

    @FunctionalInterface
    interface BenchmarkTask {
        void run() throws Exception;
    }
}