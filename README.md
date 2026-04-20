![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Build](https://img.shields.io/badge/build-Maven-red?logo=apachemaven)
![Concurrency](https://img.shields.io/badge/concurrency-custom%20thread%20pool-blue)
![Tests](https://img.shields.io/badge/tests-JUnit%205-green)
![BGU SPL](https://img.shields.io/badge/course-BGU%20SPL-orange)

# Parallel Linear Algebra Engine

> A Java 21 multi-threaded engine that evaluates matrix expression trees in parallel —
> featuring a **custom thread pool with fatigue-based load balancing**,
> **ReentrantReadWriteLock** synchronization per vector, and a bottom-up computation DAG traversal.

---

## Technical Skills Demonstrated

| Category | Specifics |
|---|---|
| Concurrency | Custom thread pool, `ReentrantReadWriteLock` per vector, `AtomicInteger`/`AtomicLong` |
| Thread Scheduling | Fatigue-based min-heap (`PriorityBlockingQueue`) for load balancing |
| Synchronization | `wait()`/`notifyAll()` batch completion, poison-pill shutdown, spurious-wakeup guard |
| Data Structures | Expression tree (DAG), n-ary → binary associative nesting, bottom-up traversal |
| Memory Layout | Row-major vs column-major storage for cache-efficient matrix multiplication |
| OOP / Design | Strategy pattern for 4 operations, service-layer separation (parser/engine/scheduler) |
| Build & Test | Maven, fat JAR (shade plugin), JUnit 5, Jackson JSON (parse input + serialize output) |
| Language | Java 21, lambdas, `volatile`, `BlockingQueue`, `Comparable<T>`, `AtomicBoolean` |

---

## System Architecture

```
 JSON input file  (matrix expression tree)
        │
        ▼
  InputParser  ──►  ComputationNode tree
                        │
                        ▼
           LinearAlgebraEngine.run()
           ┌──────────────────────────────────────────┐
           │                                          │
           │  while root != MATRIX:                   │
           │    1. findResolvable()   bottom-up DFS   │
           │    2. associativeNesting() if n-ary       │
           │    3. loadAndCompute(node)                │
           │       ├─ loadRowMajor(left)              │
           │       ├─ loadColumnMajor(right) [MUL]    │
           │       ├─ createXxxTasks()  1 task/row    │
           │       └─ executor.submitAll(tasks) ──┐   │
           │                                      │   │
           └──────────────────────────────────────│───┘
                                                  │
                           TiredExecutor          │
           ┌───────────────────────────────────── ▼ ──┐
           │                                          │
           │  PriorityBlockingQueue<TiredThread>       │
           │  (min-heap sorted by fatigue score)       │
           │                                          │
           │  TiredThread-0  TiredThread-1  ...        │
           │  [handoff:1]    [handoff:1]               │
           │  fatigue = FF × timeUsed (nanos)          │
           │                                          │
           │  inFlight counter → notifyAll() on 0     │
           └──────────────────────────────────────────┘
                    │ each task owns row-level locks
                    ▼
           SharedMatrix / SharedVector
           ┌──────────────────────────────────────────┐
           │  volatile SharedVector[] vectors          │
           │  Each row: ReentrantReadWriteLock         │
           │  ADD:       write(row_i) + read(row_i)    │
           │  MULTIPLY:  write(row_i) + read(all cols) │
           │  NEGATE:    write(row_i)                  │
           │  TRANSPOSE: write(row_i) → flip enum      │
           └──────────────────────────────────────────┘
                    │
                    ▼
  OutputWriter  ──►  JSON result file
```

---

## What Was Built

This is a university assignment for the Systems Programming Lab (SPL) course at Ben-Gurion University.
The course scaffold defined interfaces and package structure; everything in `src/main/java/` is
original student implementation.

The engine accepts a JSON file describing a matrix expression tree (e.g. `(A + Tᵀ) × B × -C`)
and evaluates it in parallel using a custom thread pool. The computation graph is traversed
bottom-up: the engine repeatedly finds the deepest resolvable node (all children are concrete
matrices), dispatches one task per matrix row to the thread pool, waits for all tasks to complete,
then replaces the node with the result matrix — until the root itself becomes a matrix.

Thread safety is enforced at the vector level using `ReentrantReadWriteLock`. Addition, negation,
and transpose tasks each acquire a write lock on their row and read locks on any shared data.
Multiplication acquires a write lock on the left-matrix row and read locks on all right-matrix
columns, with a partial-unlock counter to safely release only the locks that were actually acquired.

The thread pool uses a custom fatigue metric — `fatigueFactor × totalTimeUsed` — to route each new
task to the least-tired worker. Fatigue factors are randomized per thread (0.5–1.5), preventing
systematic imbalance when tasks have unequal durations.

---

## Key Concepts Demonstrated

### 1. Custom Thread Pool — `TiredExecutor` + `TiredThread`

Workers sit in a `PriorityBlockingQueue` sorted by fatigue. `submit()` pops the least-tired worker,
wraps the task in a "boomerang" lambda that returns the worker to the heap when done and signals
batch completion via `notifyAll()`. `submitAll()` blocks the calling thread until `inFlight` reaches
zero, using a `while` loop to guard against spurious wakeups:

```java
// submit(): wrap task so worker auto-returns to heap and signals completion
public void submit(Runnable task) {
    TiredThread currThread = idleMinHeap.take();  // blocks until a worker is free
    inFlight.incrementAndGet();
    Runnable boomerangTask = () -> {
        try {
            task.run();
        } finally {
            if (inFlight.decrementAndGet() == 0) {
                synchronized (this) { this.notifyAll(); }  // wake submitAll()
            }
            idleMinHeap.add(currThread);  // return worker to heap
        }
    };
    currThread.newTask(boomerangTask);
}

// submitAll(): block until every task in the batch has finished
public void submitAll(Iterable<Runnable> tasks) {
    for (Runnable t : tasks) submit(t);
    synchronized (this) {
        while (inFlight.get() > 0) this.wait();  // while-loop: spurious wakeup guard
    }
}
```

Each `TiredThread` tracks nanosecond-precision work and idle time using `AtomicLong`, computes
its fatigue as `fatigueFactor × timeUsed`, and shuts down gracefully on a poison-pill signal:

```java
// compareTo: enables min-heap ordering by fatigue score
public int compareTo(TiredThread o) {
    double curr = fatigueFactor * timeUsed.get();
    double other = o.fatigueFactor * o.timeUsed.get();
    return curr > other ? 1 : curr < other ? -1 : 0;
}

// shutdown: sets alive=false and unblocks handoff.take() via poison pill
public void shutdown() {
    alive.set(false);
    handoff.put(POISON_PILL);  // wakes sleeping worker so it can exit cleanly
}
```

---

### 2. Row-Level Lock Granularity — `SharedVector`

Each `SharedVector` owns its own `ReentrantReadWriteLock`. This allows concurrent reads across
different rows (multiple threads reading right-matrix columns simultaneously) while protecting
writes. Transpose is O(1) — it flips the orientation enum without touching the underlying array:

```java
// Transpose: logical O(1) — flip orientation flag, no array copy
public void transpose() {
    orientation = (orientation == VectorOrientation.ROW_MAJOR)
        ? VectorOrientation.COLUMN_MAJOR
        : VectorOrientation.ROW_MAJOR;
}

// dot product: validates ROW × COLUMN precondition before summing
public double dot(SharedVector other) {
    if (this.orientation == VectorOrientation.COLUMN_MAJOR)
        throw new IllegalArgumentException("left vector must be row-major");
    if (other.orientation == VectorOrientation.ROW_MAJOR)
        throw new IllegalArgumentException("right vector must be column-major");
    double sum = 0;
    for (int i = 0; i < vector.length; i++) sum += vector[i] * other.vector[i];
    return sum;
}
```

---

### 3. Row × Matrix Multiplication with Safe Lock Acquisition

Multiplication is the most complex locking case: each task acquires a write lock on its left-matrix
row and read locks on **all** right-matrix columns. A `lockedCount` counter ensures that only the
locks that were successfully acquired are released in the finally block:

```java
res.add(() -> {
    SharedVector v1 = leftMatrix.get(index);
    v1.writeLock();
    int lockedCount = 0;
    try {
        for (int k = 0; k < rightMatrix.length(); k++) {
            rightMatrix.get(k).readLock();
            lockedCount++;              // increment only after successful lock
        }
        v1.vecMatMul(rightMatrix);      // row × column-major matrix
    } finally {
        for (int k = 0; k < lockedCount; k++)
            rightMatrix.get(k).readUnlock();  // release only what was acquired
    }
    v1.writeUnlock();
});
```

---

### 4. Bottom-Up Expression Tree Evaluation

The engine evaluates the computation tree without recursion. `findResolvable()` performs a DFS
to find the deepest node whose children are all concrete matrices. N-ary operations (A + B + C)
are lazily rewritten into left-associative binary trees before evaluation:

```java
// run(): iteratively resolve nodes until the root becomes a MATRIX
while (computationRoot.getNodeType() != ComputationNodeType.MATRIX) {
    ComputationNode curr = computationRoot.findResolvable();
    if (curr.getChildren().size() > 2) {
        curr.associativeNesting();  // rewrite: (A+B+C) → ((A+B)+C)
        continue;                   // re-scan; don't compute yet
    }
    loadAndCompute(curr);           // dispatch row tasks → submitAll → block
}
```

---

### 5. Column-Major Layout for Cache-Efficient Multiplication

When loading the right operand for multiplication, the engine stores it column-major.
This means each column is a contiguous `double[]` array — allowing the dot product loop
to read sequentially rather than striding across rows:

```java
// loadAndCompute(): right matrix stored column-major for multiplication
if (node.getNodeType() == ComputationNodeType.MULTIPLY) {
    leftMatrix.loadRowMajor(node.getChildren().get(0).getMatrix());
    rightMatrix.loadColumnMajor(node.getChildren().get(1).getMatrix()); // columns contiguous
    executor.submitAll(createMultiplyTasks());
}
```

---

## Project Structure

```
TT-SPL25-Assignment2/
├── pom.xml                                   # Java 21, Jackson, JUnit 5, shade plugin
├── src/
│   ├── main/java/
│   │   ├── spl/lae/
│   │   │   ├── LinearAlgebraEngine.java      # Orchestrator: tree traversal + task dispatch
│   │   │   └── Main.java                     # CLI: numThreads inputPath outputPath
│   │   ├── memory/
│   │   │   ├── SharedVector.java             # Thread-safe vector: RWLock + operations
│   │   │   ├── SharedMatrix.java             # Row/column-major matrix; volatile array ref
│   │   │   └── VectorOrientation.java        # Enum: ROW_MAJOR | COLUMN_MAJOR
│   │   ├── scheduling/
│   │   │   ├── TiredExecutor.java            # Thread pool: fatigue min-heap, batch wait
│   │   │   └── TiredThread.java              # Worker: single-slot handoff, ns time tracking
│   │   └── parser/
│   │       ├── ComputationNode.java          # Expression tree node: ops + MATRIX leaves
│   │       ├── ComputationNodeType.java      # Enum: ADD MULTIPLY NEGATE TRANSPOSE MATRIX
│   │       ├── InputParser.java              # JSON → ComputationNode tree (Jackson)
│   │       └── OutputWriter.java             # double[][] / error → JSON file
│   └── test/java/spl/lae/
│       └── LinearAlgebraEngineTest.java      # JUnit 5: add, mul, transpose, negate, mixed
└── example.json                              # Sample: (A + Tᵀ) × B × -C  (10×10 matrices)
```

---

## Build & Run

### Prerequisites

- Java 21+
- Maven 3.6+

### Build

```bash
mvn clean package
# produces: target/lga-1.0-shaded.jar  (fat JAR, all dependencies bundled)
```

### Run

```bash
java -jar target/lga-1.0-shaded.jar <numThreads> <inputPath> <outputPath>

# Example:
java -jar target/lga-1.0-shaded.jar 4 example.json out.json
```

**Input format** — a JSON expression tree:
```json
{
  "operator": "+",
  "operands": [
    [[1, 2], [3, 4]],
    [[5, 6], [7, 8]]
  ]
}
```

**Output format**:
```json
{ "result": [[6, 8], [10, 12]] }
```

On error:
```json
{ "error": "Illegal operation: dimensions mismatch" }
```

### Run Tests

```bash
mvn test
```

---

## About

**Assignment**: SPL (Systems Programming Lab) — Assignment 2
**Institution**: Ben-Gurion University of the Negev, Department of Computer Science
**Student**: Tamir Siman-Tov — 2nd Year, B.Sc. Computer Science + Psychology
All implementation code in `src/main/java/` is original student work.

[LinkedIn](https://linkedin.com/in/tamir-siman-tov) · [GitHub](https://github.com/Tamirsi7)
