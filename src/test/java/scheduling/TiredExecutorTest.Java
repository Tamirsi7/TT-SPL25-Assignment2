package scheduling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TiredExecutorTest {

    @Test
    void testConstructor() {
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

}