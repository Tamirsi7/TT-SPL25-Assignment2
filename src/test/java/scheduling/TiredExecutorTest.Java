package scheduling;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class TiredExecutorTest {

    @Test
    void testConstructorThrowsOnInvalidThreadCount() {
        // Edge Case: 0 threads should throw exception
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(0));
        
        // Edge Case: Negative threads should throw exception
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(-1));
    }

    @Test
    void testSubmitAllBlocksUntilCompletion() {
        // Main Logic Test: Verify that submitAll waits (blocks) until all tasks are finished.
        // This is crucial for the wait/notify implementation.
        TiredExecutor executor = new TiredExecutor(4);
        AtomicInteger counter = new AtomicInteger(0);
        int numTasks = 100; // Sufficient number to ensure concurrency
        
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < numTasks; i++) {
            tasks.add(() -> {
                try { 
                    // Simulate work (short sleep to trigger context switches)
                    Thread.sleep(2); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                counter.incrementAndGet();
            });
        }
        
        long start = System.currentTimeMillis();
        executor.submitAll(tasks); // Should block here until inFlight becomes 0
        long end = System.currentTimeMillis();
        
        // Verify all tasks ran
        assertEquals(numTasks, counter.get(), "All tasks must be finished after submitAll returns");
        
        // Sanity check: execution should take at least some time (proving it didn't return immediately)
        assertTrue((end - start) > 0, "Execution time should be greater than 0");

        try { executor.shutdown(); } catch (InterruptedException e) {}
    }

    @Test
    void testSubmitAllWithEmptyList() {
        // Edge Case: Submitting an empty list.
        // With wait/notify, we must ensure the main thread doesn't wait indefinitely 
        // if inFlight is already 0.
        TiredExecutor executor = new TiredExecutor(2);
        
        List<Runnable> emptyTasks = Collections.emptyList();
        
        // This should return immediately and not hang
        assertDoesNotThrow(() -> executor.submitAll(emptyTasks));
        
        try { executor.shutdown(); } catch (InterruptedException e) {}
    }

    @Test
    void testSubmitSingleTask() {
        // Basic functionality check for single submission
        TiredExecutor executor = new TiredExecutor(1);
        AtomicInteger counter = new AtomicInteger(0);
        
        executor.submit(() -> counter.incrementAndGet());
        
        // Give it a moment to finish (since submit() is non-blocking, unlike submitAll)
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        assertEquals(1, counter.get());
        
        try { executor.shutdown(); } catch (InterruptedException e) {}
    }

    @Test
    void testStressTest() {
        // Stress Test: Run many small tasks to ensure race conditions in notify/wait are handled.
        // If notify() is missed, this test might hang or fail.
        int threads = 8;
        TiredExecutor executor = new TiredExecutor(threads);
        int numTasks = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < numTasks; i++) {
            tasks.add(counter::incrementAndGet);
        }
        
        executor.submitAll(tasks);
        
        assertEquals(numTasks, counter.get(), "Stress test failed: not all tasks completed");
        
        try { executor.shutdown(); } catch (InterruptedException e) {}
    }

    @Test
    void testWorkerReportFormat() {
        // Verify the report string structure contains expected headers
        TiredExecutor executor = new TiredExecutor(2);
        String report = executor.getWorkerReport();
        
        assertNotNull(report, "Report should not be null");
        assertTrue(report.contains("Worker ID"), "Report should contain 'Worker ID'");
        assertTrue(report.contains("Fatigue status"), "Report should contain 'Fatigue status'");
        
        try { executor.shutdown(); } catch (InterruptedException e) {}
    }

    @Test
    void testFairnessAndFatigueBalance() throws NoSuchFieldException, IllegalAccessException {
        // 1. Create an Executor with a fixed number of threads
        int numThreads = 5;
        TiredExecutor executor = new TiredExecutor(numThreads);
        
        // 2. Setup tasks
        // Reduced to 500 tasks to keep test duration reasonable with the added delays
        int numTasks = 500; 
        List<Runnable> tasks = new ArrayList<>();
        
        for (int i = 0; i < numTasks; i++) {
             // Simulate work (1ms)
            tasks.add(() -> { try { Thread.sleep(1); } catch (InterruptedException e) {} });
        }
        
        // 3. Submit tasks manually with a delay.
        // If we simply use submitAll(tasks), the main thread floods the executor 
        // faster than workers can process. The idle queue empties out, and 
        // behavior degrades to FIFO instead of Priority-based.
        // The delay allows workers to return to the heap and be sorted by fatigue.
        new Thread(() -> {
            for (Runnable t : tasks) {
                executor.submit(t);
                try { 
                    Thread.sleep(2); // Small delay to prevent saturation
                } catch (InterruptedException e) {}
            }
        }).start();

        // 4. Wait for completion
        // 500 tasks * ~2ms delay = ~1000ms. We wait 2000ms to be safe.
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // 5. Use Reflection to access the private 'workers' array to inspect internal state
        java.lang.reflect.Field workersField = TiredExecutor.class.getDeclaredField("workers");
        workersField.setAccessible(true);
        TiredThread[] workers = (TiredThread[]) workersField.get(executor);
        
        // 6. Collect data, Calculate Average for Normalization, and find gaps
        double totalFatigue = 0;
        double minFatigue = Double.MAX_VALUE;
        double maxFatigue = Double.MIN_VALUE;
        
        // First pass: Calculate total and find min/max for assertion
        for (TiredThread worker : workers) {
            double f = worker.getFatigue();
            totalFatigue += f;
            if (f < minFatigue) minFatigue = f;
            if (f > maxFatigue) maxFatigue = f;
        }

        // Calculate average
        double averageFatigue = totalFatigue / workers.length;

        System.out.println("--- Fairness Report ---");
        // Second pass: Print normalized values
        for (TiredThread worker : workers) {
            double currentFatigue = worker.getFatigue();
            double normalizedFatigue = currentFatigue / averageFatigue;

            // Output format: Worker ID | Norm (e.g., 1.01) | Raw | Time
            System.out.printf("Worker %d | Fatigue (Norm): %.3f | Raw: %.0f | Time: %d ns%n", 
                    worker.getWorkerId(), 
                    normalizedFatigue, 
                    currentFatigue, 
                    worker.getTimeUsed());
        }
        System.out.println("-----------------------");

        // 7. Assert Fairness: 
        // With the delay added above, the priority queue logic should work correctly,
        // ensuring the fatigue difference remains small.
        
        double difference = maxFatigue - minFatigue;
        
        // We allow a reasonable gap (approx 50M units, roughly 10-20ms of work drift)
        assertTrue(difference < 50_000_000, 
            "Load balancing failed! The gap between most and least tired worker is too large: " + difference);

        try { executor.shutdown(); } catch (InterruptedException e) {}
    }
}