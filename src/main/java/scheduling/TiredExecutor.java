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
        try{
            //getting least fatigued worker
            TiredThread currThread = idleMinHeap.take();
            //updating num of inFlights with atomic method
            inFlight.incrementAndGet();
            // 
            Runnable boomerangTask = () -> {
                try {
                    task.run();
                } finally {
                    inFlight.decrementAndGet();
                    idleMinHeap.offer(currThread);
                }
            };
            currThread.newTask(boomerangTask);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }   
    }

    public void submitAll(Iterable<Runnable> tasks) {
        // TODO: submit tasks one by one and wait until all finish
    }

    public void shutdown() throws InterruptedException {
        // TODO
    }

    public synchronized String getWorkerReport() {
        // TODO: return readable statistics for each worker
        return null;
    }
}
