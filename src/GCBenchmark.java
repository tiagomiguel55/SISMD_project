import java.util.Scanner;

/**
 * GCBenchmark — Garbage Collector Tuning Analysis
 *
 * Runs the histogram equalization benchmark under different GC configurations.
 * This class is intended to be launched multiple times with different JVM flags:
 *
 *   G1GC (default):
 *     java -XX:+UseG1GC -Xms512m -Xmx2g -Xlog:gc:file=gc_g1.log:time,uptime,level,tags GCBenchmark
 *
 *   Parallel GC:
 *     java -XX:+UseParallelGC -Xms512m -Xmx2g -Xlog:gc:file=gc_parallel.log:time,uptime,level,tags GCBenchmark
 *
 *   ZGC:
 *     java -XX:+UseZGC -Xms512m -Xmx2g -Xlog:gc:file=gc_zgc.log:time,uptime,level,tags GCBenchmark
 *
 * The benchmark uses more repetitions than ApplyFilters to stress the GC
 * and produce measurable garbage collection activity.
 */
public class GCBenchmark {

    static final int WARMUP_RUNS   = 3;
    static final int MEASURED_RUNS = 10; // more runs = more GC pressure
    static final int NUM_THREADS   = 8;

    public static void main(String[] args) throws Exception {

        Scanner input = new Scanner(System.in);
        System.out.println("Insert the path of the image file:");
        String filePath = input.nextLine();
        input.close();

        // Print active GC
        String gcName = java.lang.management.ManagementFactory
                .getGarbageCollectorMXBeans()
                .stream()
                .map(gc -> gc.getName())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " + " + b);

        System.out.println("\n=== GC Benchmark ===");
        System.out.println("Active GC   : " + gcName);
        System.out.println("CPU cores   : " + Runtime.getRuntime().availableProcessors());
        System.out.println("Threads     : " + NUM_THREADS);
        System.out.println("Warmup runs : " + WARMUP_RUNS);
        System.out.println("Measured    : " + MEASURED_RUNS);
        System.out.println("Image       : " + filePath);
        System.out.println();

        long seqAvg = runBenchmark("Sequential",        () -> new Filters(filePath).processImage());
        long mtAvg  = runBenchmark("Multithreaded",     () -> new MultithreadedFilter(filePath, NUM_THREADS).processImage());
        long tpAvg  = runBenchmark("Thread Pool",       () -> new ThreadPoolFilter(filePath, NUM_THREADS).processImage());
        long fjAvg  = runBenchmark("Fork/Join",         () -> new ForkJoinFilter(filePath, NUM_THREADS).processImage());
        long cfAvg  = runBenchmark("CompletableFuture", () -> new CompletableFutureFilter(filePath, NUM_THREADS).processImage());

        // GC stats after benchmark
        long totalGcCount = java.lang.management.ManagementFactory
                .getGarbageCollectorMXBeans()
                .stream()
                .mapToLong(gc -> gc.getCollectionCount())
                .sum();
        long totalGcTime = java.lang.management.ManagementFactory
                .getGarbageCollectorMXBeans()
                .stream()
                .mapToLong(gc -> gc.getCollectionTime())
                .sum();

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
        System.out.println();
        System.out.println("=== GC Summary ===");
        System.out.println("  GC collections : " + totalGcCount);
        System.out.println("  GC total time  : " + totalGcTime + " ms");
        System.out.println("  Active GC      : " + gcName);
    }

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