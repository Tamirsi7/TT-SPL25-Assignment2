package scheduling;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

class TiredThreadTest {

    @Test
    void testTaskExecution() {
        // Verify that the thread can execute a simple task
        TiredThread worker = new TiredThread(0, 1.0);
        AtomicInteger counter = new AtomicInteger(0);

        // The thread must be started to listen for tasks
        worker.start();

        // Submit a task
        worker.newTask(() -> counter.incrementAndGet());

        // Shutdown the thread (waits for the current task to finish)
        worker.shutdown();
        try {
            worker.join(); // Wait for the thread to actually die
        } catch (InterruptedException e) {
            fail("Thread was interrupted");
        }

        assertEquals(1, counter.get(), "Task should have been executed");
    }

    @Test
    void testCompareToInitialState() {
        // making sure both workers (didnt work yet) have 0 fatigue
        TiredThread t1 = new TiredThread(1, 1.0);
        TiredThread t2 = new TiredThread(2, 2.0);
        assertEquals(0, t1.compareTo(t2), "Initial workers should have equal fatigue (0)");
    }

    @Test
    void testThreadSurvivesTaskException() {
        // initialize and start a new worker thread
        TiredThread worker = new TiredThread(1, 1.0);
        worker.start();
        
        AtomicInteger counter = new AtomicInteger(0);

        // task 1: Intentionally throws an exception.
        // The worker should catch this exception and continue running.
        worker.newTask(() -> { 
            throw new RuntimeException("Intentional Failure"); 
        });

        // task 2: A valid task that increments the counter.
        // If the worker survived Task 1, this task should be executed.
        worker.newTask(() -> counter.incrementAndGet());

        // Gracefully shut down the worker and wait for it to finish
        worker.shutdown();
        try { 
            worker.join(); 
        } catch (InterruptedException e) {
            fail("Test was interrupted while waiting for worker to join");
        }

        // assert: The counter should be 1, proving Task 2 ran successfully despite the failure in Task 1.
        assertEquals(1, counter.get(), "Thread should continue working after an exception in a task");
    }
}