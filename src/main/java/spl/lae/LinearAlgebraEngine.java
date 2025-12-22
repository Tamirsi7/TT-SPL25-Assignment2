package spl.lae;

import java.util.ArrayList;
import java.util.List;

import memory.SharedMatrix;
import memory.SharedVector;
import memory.VectorOrientation;
import parser.ComputationNode;
import parser.ComputationNodeType;
import scheduling.TiredExecutor;

public class LinearAlgebraEngine {

    private SharedMatrix leftMatrix = new SharedMatrix();
    private SharedMatrix rightMatrix = new SharedMatrix();
    private TiredExecutor executor;

    public LinearAlgebraEngine(int numThreads) {
        executor = new TiredExecutor(numThreads);
    }

    public ComputationNode run(ComputationNode computationRoot) {

        if (computationRoot.getNodeType() == ComputationNodeType.MATRIX) {
            return computationRoot;
        }

        while (computationRoot.getNodeType() != ComputationNodeType.MATRIX) { 
            ComputationNode curr = computationRoot.findResolvable();
            if (curr == null) {
                break;
            }

            if (curr.getChildren().size() > 2) {
                curr.associativeNesting();
                continue;
            }
            loadAndCompute(curr);
        }
        return computationRoot;
    }

    public void loadAndCompute(ComputationNode node) {
        double[][] result;
        // Assuming given node has 2 matrix childs
        // Loading the left child, as it's the matrix in the head of the list index(0).
        leftMatrix.loadRowMajor(node.getChildren().get(0).getMatrix());
        // Case 1 - add operator:
        if (node.getNodeType() == ComputationNodeType.ADD) {
            // Loading the right matrix (index 1) as rows as well
            rightMatrix.loadRowMajor(node.getChildren().get(1).getMatrix());
            // Creating the tasks in the executer and running them
            executor.submitAll(createAddTasks());
        }
          // Case 2 - Multiply operator:
        else if (node.getNodeType() == ComputationNodeType.MULTIPLY) {
            // Loading the right matrix (index 1) as columns
            rightMatrix.loadColumnMajor(node.getChildren().get(1).getMatrix());
            // Creating the tasks in the executer and running them
            executor.submitAll(createMultiplyTasks());
        }
        // Case 3 - Negate operator:
        else if (node.getNodeType() == ComputationNodeType.NEGATE) {
            // Creating the tasks in the executer and running them
            executor.submitAll(createNegateTasks());
        }
        // Case 4 - Transpose operator:
        else if (node.getNodeType() == ComputationNodeType.TRANSPOSE) {
            // Creating the tasks in the executer and running them
            executor.submitAll(createTransposeTasks());
            // Changing orientation in the matrix "defintion" level
            leftMatrix.setOrientation(VectorOrientation.COLUMN_MAJOR);
        }
        // Locking the left matrix with readRowMajor, so we can read the correct data
        result = leftMatrix.readRowMajor();
        // Using the "result" method, making sure the operator becomes the calculated matrix, without childrens
        node.resolve(result);
    }

    public List<Runnable> createAddTasks() {
        // creating an array of runnable (tasks) in the size of the leftMatrix dimension
        List<Runnable> res = new ArrayList<Runnable>(leftMatrix.length());
        // creating n tasks , where n is the number of rows
        for (int i = 0 ; i < leftMatrix.length() ; i++) {
            // Each iteration, the loop "sets" constant index, so when the future task will happen it has specific index.
            final int index = i;
            // creating lambda for future task, adding the matching row vector from the right matrix to the left
            res.add(() -> {
                // locking the relevant vectors for write/read in each matrix
                SharedVector v1 = leftMatrix.get(index);
                SharedVector v2 = rightMatrix.get(index);
                // lock write to left vector, lock read to right vector
                v1.writeLock();
                v2.readLock();
                // applying add method on the vectors
                try {
                    v1.add(v2);
                } finally {
                // unlocking both vectors in opposite order
                    v2.readUnlock();
                    v1.writeUnlock();
                }
            });
        }
        return res;
    }

    public List<Runnable> createMultiplyTasks() {
        // creating an array of runnable (tasks) in the size of the leftMatrix dimension
        List<Runnable> res = new ArrayList<Runnable>(leftMatrix.length());
        // creating n tasks , where n is the number of rows
        for (int i = 0 ; i < leftMatrix.length() ; i++) {
            // Each iteration, the loop "sets" constant index, so when the future task will happen it has specific index.
            final int index = i;
            // creating lambda for future task, perform row × matrix for each row in the left matrix.
            res.add(() -> {
                SharedVector v1 = leftMatrix.get(index);
                // locking left matrix vector to write before multiplying
                v1.writeLock();
                // locking all vectors in right matrix for read
                for (int j = 0 ; j < rightMatrix.length() ; j++) {
                    rightMatrix.get(j).readLock();
                }
                // applying multiply method on vector x rightMatrix
                try {
                    v1.vecMatMul(rightMatrix);
                // unlocking all vectors 
                } finally {
                    for (int k = 0 ; k < rightMatrix.length() ; k++) {
                        rightMatrix.get(k).readUnlock();
                    }
                }
                v1.writeUnlock();
            });
        }
        return res;
    }

    public List<Runnable> createNegateTasks() {
        // creating an array of runnable (tasks) in the size of the leftMatrix dimension
        List<Runnable> res = new ArrayList<Runnable>(leftMatrix.length());
        // creating n tasks , where n is the number of rows
        for (int i = 0 ; i < leftMatrix.length() ; i++) {
            // Each iteration, the loop "sets" constant index, so when the future task will happen it has specific index.
            final int index = i;
            // creating lambda for future task, perform negate method for each row in the left matrix.
            res.add(() -> {
                SharedVector v1 = leftMatrix.get(index);
                // locking leftMatrix vector for write, performing negate and then unlocking:
                v1.writeLock();
                try {
                    v1.negate();
                } finally {
                    v1.writeUnlock();
                }
            });
        }
        return res;
    }

    public List<Runnable> createTransposeTasks() {
        // creating an array of runnable (tasks) in the size of the leftMatrix dimension
        List<Runnable> res = new ArrayList<Runnable>(leftMatrix.length());
        // creating n tasks , where n is the number of rows
        for (int i = 0 ; i < leftMatrix.length() ; i++) {
            // Each iteration, the loop "sets" constant index, so when the future task will happen it has specific index.
            final int index = i;
            // creating lambda for future task, perform transpose method for each row in the left matrix.
            res.add(() -> {
                SharedVector v1 = leftMatrix.get(index);
                // locking leftMatrix vector for write, performing transform and then unlocking:
                v1.writeLock();
                try {
                    v1.transpose();
                } finally {
                    v1.writeUnlock();    
                }
            });
        }
        return res;
    }

    public String getWorkerReport() {
        // calling the executer report method
        return executor.getWorkerReport();
    }
}
