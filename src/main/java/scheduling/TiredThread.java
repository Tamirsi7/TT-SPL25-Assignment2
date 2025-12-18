package scheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TiredThread extends Thread implements Comparable<TiredThread> {

    private static final Runnable POISON_PILL = () -> {
    }; // Special task to signal shutdown

    private final int id; // Worker index assigned by the executor
    private final double fatigueFactor; // Multiplier for fatigue calculation

    private final AtomicBoolean alive = new AtomicBoolean(true); // Indicates if the worker should keep running

    // Single-slot handoff queue; executor will put tasks here
    private final BlockingQueue<Runnable> handoff = new ArrayBlockingQueue<>(1);

    private final AtomicBoolean busy = new AtomicBoolean(false); // Indicates if the worker is currently executing a
                                                                 // task

    private final AtomicLong timeUsed = new AtomicLong(0); // Total time spent executing tasks
    private final AtomicLong timeIdle = new AtomicLong(0); // Total time spent idle
    private final AtomicLong idleStartTime = new AtomicLong(0); // Timestamp when the worker became idle

    public TiredThread(int id, double fatigueFactor) {
        this.id = id;
        this.fatigueFactor = fatigueFactor;
        this.idleStartTime.set(System.nanoTime());
        setName(String.format("FF=%.2f", fatigueFactor));
    }

    public int getWorkerId() {
        return id;
    }

    public double getFatigue() {
        return fatigueFactor * timeUsed.get();
    }

    public boolean isBusy() {
        return busy.get();
    }

    public long getTimeUsed() {
        return timeUsed.get();
    }

    public long getTimeIdle() {
        return timeIdle.get();
    }

    /**
     * Assign a task to this worker.
     * This method is non-blocking: if the worker is not ready to accept a task,
     * it throws IllegalStateException.
     */
    public void newTask(Runnable task) {
        this.handoff.add(task); // try to add the task to handoff queue. the IllegalStateException is thrown
                                // from add method and will be cought by Executor
    }

    /**
     * Request this worker to stop after finishing current task.
     * Inserts a poison pill so the worker wakes up and exits.
     */
    public void shutdown() {
        // Shutting down the thread
        this.alive.set(false);
        // Using put to make sure that the thread takes the poison pill as the next task.
        try {
            this.handoff.put(POISON_PILL);
        } catch (InterruptedException error) {
        }
    }

    @Override
    public void run() {
        try {
            // Making sure the thread keeps taking tasks when is possible
            while (alive.get()) {
                // taking the task that is waiting in queue
                Runnable task = handoff.take();
                // eliminating the worker - no more tasks
                if (task == POISON_PILL) {
                    alive.set(false);
                    break;
                }
                // setting the exact idle time
                long currTime = System.nanoTime();
                // updating total idle time
                this.timeIdle.addAndGet(currTime - idleStartTime.get());
                this.busy.set(true);
                // After updatding the relevat fields, worker starting to work
                // *using another try catch to make sure worker does not "die" if task fails.
                try{
                    task.run();
                } catch (RuntimeException e){
                    System.err.println(e.getMessage());
                }
                // updating exact "busy time"
                long timeNow = System.nanoTime();
                timeUsed.addAndGet(timeNow - currTime);
                // Worker stopped working
                this.busy.set(false);
                // Worker enteres "Idle" time again.
                this.idleStartTime.set(System.nanoTime());
            }
        } catch (InterruptedException error) {
        }
    }

    @Override
    public int compareTo(TiredThread o) {
        // getting the fatigues
        double currFatigue = this.getFatigue();
        double otherFatigue = o.getFatigue();
        // checking who is more tired and returning 0/1/-1
        if (currFatigue > otherFatigue) {
            return 1;
        }
        else if ( currFatigue == otherFatigue) {
            return 0;
        }
        else {
            return -1;
        }
    }
}