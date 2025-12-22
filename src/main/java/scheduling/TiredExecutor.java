package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public TiredExecutor(int numThreads) {
        // making sure we have workers!
        if (numThreads <= 0) {
            throw new IllegalArgumentException("error: must have at least one worker");
        }
        // initializing new array for workers
        workers = new TiredThread[numThreads];
        // initializing threads and "waking" them up to get ready for tasks
        for (int i = 0; i < numThreads; i++) {
            // id is unique (because we have only one executor) and fatigueFactor is a
            // random num between 0.5 and 1.5
            workers[i] = new TiredThread(i, 0.5 + Math.random());
            // waking up each worker. (if we have no task in the heap - he will fall asleep)
            workers[i].start();
            // adding worker to the idleHeap - since he does not have a task yet.
            idleMinHeap.add(workers[i]);
        }
    }

    public void submit(Runnable task) {
        try {
            // getting the least fatigued worker
            TiredThread currThread = idleMinHeap.take();
            // updating num of inFlights with atomic method
            inFlight.incrementAndGet();
            // creating lambda function (one-time use class), to make sure workers return to the heap after running.
            Runnable boomerangTask = () -> {
                try {
                    task.run();
                } finally {
                    inFlight.decrementAndGet();
                    idleMinHeap.offer(currThread);
                }
            };
            // assigning the worker the given task and making sure it is returning to heap
            currThread.newTask(boomerangTask);
            // If take() fails, we catch the error
        } catch (InterruptedException e) {
            // in case of a "forced" shut down, the program make sure to flag the mark,
            // so in the next iteration .take() will notice the flag and fail
            Thread.currentThread().interrupt();
        }
    }

    public void submitAll(Iterable<Runnable> tasks) {
        // iterating through all tasks and submitting them
        for (Runnable t : tasks) {
            submit(t);
        }
        // checking if there are still any open tasks
        while (inFlight.get() > 0) {
            // If there are, don't check activly - release the CPU for other threads to save
            // time and effiency.
            Thread.yield();
        }
    }

    public void shutdown() throws InterruptedException {
        // shutting down all workers
        for (int i = 0; i < workers.length; i++) {
            workers[i].shutdown();
        }
        // Making sure all workers finished their tasks, shutting down only once done
        for (int i = 0; i < workers.length; i++) {
            workers[i].join();
        }
    }

    public synchronized String getWorkerReport() {
        // Starting with new string for the report
        String report = "Worker report:\n";
        for (int i = 0; i < workers.length; i++) {
            // for each worker, we print the id, workTime, idletime and fatigue status
            report += "---------------------------\n";
            // converting the time to milliseconds
            long workTime = workers[i].getTimeUsed() / 1000000;
            long idleTime = workers[i].getTimeIdle() / 1000000;
            report += "Worker ID:" + workers[i].getWorkerId() + ":\n";
            report += "Total work time:" + workTime + " ms\n";
            report += "Total idle time:" + idleTime + " ms\n";
            report += "Fatigue status:" + String.format("%.2f", workers[i].getFatigue()) + "\n";
            report += "---------------------------\n";
        }
        return report;
    }
}