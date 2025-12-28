package scheduling;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TiredThreadTest {

    @Test
    void testInitialization() {
        // Verify that the constructor initializes the fields correctly
        int id = 1;
        double fatigueFactor = 1.5;
        TiredThread worker = new TiredThread(id, fatigueFactor);

        assertEquals(id, worker.getWorkerId());
        assertEquals(0, worker.getTimeUsed());
        assertEquals(0, worker.getFatigue()); // Initial fatigue should be 0
    }

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
    void testFatigueAccumulation() {
        // Verify that fatigue increases when the thread performs work
        double fatigueFactor = 2.0;
        TiredThread worker = new TiredThread(0, fatigueFactor);
        worker.start();

        // Submit a task that takes some time (to accumulate fatigue)
        worker.newTask(() -> {
            try {
                Thread.sleep(10); // Simulate work
            } catch (InterruptedException e) {}
        });

        worker.shutdown();
        try {
            worker.join();
        } catch (InterruptedException e) {}

        // Verify that time used is positive
        assertTrue(worker.getTimeUsed() > 0, "Time used should be greater than 0");
        
        // Verify that fatigue calculation is correct (Time * Factor)
        // Note: We use a small delta (epsilon) for double comparison
        double expectedFatigue = worker.getTimeUsed() * fatigueFactor;
        assertEquals(expectedFatigue, worker.getFatigue(), 0.0001);
    }
}