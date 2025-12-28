package scheduling;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class TiredExecutorTest {

    @Test
    void testSubmitAllBlocksUntilCompletion() {
        // Verify that submitAll waits for all tasks to finish
        TiredExecutor executor = new TiredExecutor(4);
        AtomicInteger counter = new AtomicInteger(0);
        
        int numTasks = 50;
        List<Runnable> tasks = new ArrayList<>();
        
        for (int i = 0; i < numTasks; i++) {
            tasks.add(() -> {
                try { 
                    // Simulate some work
                    Thread.sleep(10); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                counter.incrementAndGet();
            });
        }
        
        executor.submitAll(tasks);
        
        // If we reached here, all tasks should be done
        assertEquals(numTasks, counter.get(), "Executor should verify all tasks completed before returning");
        
        // Clean up
        try {
            executor.shutdown();
        } catch (InterruptedException e) {
            // Ignore for test
        }
    }

    @Test
    void testWorkerReportFormat() {
        // Verify the report string structure
        TiredExecutor executor = new TiredExecutor(2);
        String report = executor.getWorkerReport();
        
        assertNotNull(report, "Report should not be null");
        assertTrue(report.contains("Worker ID"), "Report should contain 'Worker ID'");
        assertTrue(report.contains("Fatigue status"), "Report should contain 'Fatigue status'");
        
        try {
            executor.shutdown();
        } catch (InterruptedException e) {}
    }

    @Test
    void testConstructorThrowsOnInvalidThreadCount() {
        // Edge Case: 0 threads
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(0));
        // Edge Case: Negative threads
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(-1));
    }
}