package memory;

public class SharedMatrix {

    private volatile SharedVector[] vectors = {}; // underlying vectors
    // added field: matrix orientation
    private VectorOrientation MatrixOrientation = VectorOrientation.ROW_MAJOR;

    public SharedMatrix() {
        // notice the matrix is initialized as empty matrix in vectors field.
    }

    public SharedMatrix(double[][] matrix) {
        loadRowMajor(matrix); // using loadRowMajor mthod to replace internal data with rowmajor matrix
    }
    
    public void loadRowMajor(double[][] matrix) {
        //we dont need to lock the matrix because no thread "knows" the loaded matrix and old matrix is irrelevant
        // initializing this.orientation and vectors according to given matrix
        this.MatrixOrientation = VectorOrientation.ROW_MAJOR;
        this.vectors = new SharedVector[matrix.length];
        // looping through matrix row vectors and loading them to this.vectors in the
        // matching slot
        for (int i = 0; i < matrix.length; i++) {
            this.vectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
        }
    }

    public void loadColumnMajor(double[][] matrix) {
        //we dont need to lock the matrix because no thread "knows" the loaded matrix and old matrix is irrelevant
        // initializing this.orientation according to given matrix
        this.MatrixOrientation = VectorOrientation.COLUMN_MAJOR;

        if (matrix.length == 0) { // making sure "int matrixCols = matrix[0].length;" will not crash
            this.vectors = new SharedVector[0];
            return;
        }
        int matrixRow = matrix.length;
        int matrixCols = matrix[0].length;// legit because we made sure matrix[0]!=null

        // initializing vectors sized matrixCol
        vectors = new SharedVector[matrixCols];
        // For each column, initialize a new array represented as row, and insert the
        // relevant values
        for (int i = 0; i < matrixCols; i++) {
            double[] ColElements = new double[matrixRow];
            for (int j = 0; j < matrixRow; j++) {
                ColElements[j] = matrix[j][i];
            }
            // Inserting the arrays into the matrix
            this.vectors[i] = new SharedVector(ColElements, VectorOrientation.COLUMN_MAJOR);
        }
    }

    public double[][] readRowMajor() {
        // keeping vectors in current variable to make sure we keep read from the right vectors
        SharedVector[] currVectors = this.vectors;
        // locking all readlocks of curr vectors because we are about to read the elements from matrix (so no one can write other values)
        acquireAllVectorReadLocks(currVectors);                                    
        double[][] resultMatrix;
        try {
            if (currVectors.length == 0) { // if vectors is empty return empty matrix
                return new double[0][0];
            }
            // case 1: matrix is rows major
            if (MatrixOrientation == VectorOrientation.ROW_MAJOR) {
                // initializing resultMatrix
                resultMatrix = new double[currVectors.length][currVectors[0].length()];
                // filling resultMatrix:
                for (int i = 0; i < currVectors.length; i++) {
                    for (int j = 0; j < currVectors[0].length(); j++) {
                        resultMatrix[i][j] = currVectors[i].get(j);
                    }
                }
            } else {
                // case 2: matrix is column major
                // initializing resultMatrix - opposite to case 1: rows is columns and columns is rows
                resultMatrix = new double[currVectors[0].length()][currVectors.length];
                // filling resultMatrix: Note: running over each vector to efficiently scan all the array before moving to the next one
                for (int j = 0; j < currVectors.length; j++) {
                    for (int i = 0; i < currVectors[0].length(); i++) {
                        resultMatrix[i][j] = currVectors[j].get(i);
                    }
                }
            }
            return resultMatrix;
        } finally { //after reading everything and returning the resultmatrix - unlock all readerslocks
            releaseAllVectorReadLocks(currVectors); 
        }
    }

    public SharedVector get(int index) {
        return vectors[index];
    }

    public int length() {
        return vectors.length;
    }

    public VectorOrientation getOrientation() {
        return this.MatrixOrientation;
    }

    private void acquireAllVectorReadLocks(SharedVector[] vecs) {
        for (int i = 0; i < vecs.length; i++) {
            vecs[i].readLock();
        }
    }

    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        for (int i = 0; i < vecs.length; i++) {
            vecs[i].readUnlock();
        }
    }

    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        for (int i = 0; i < vecs.length; i++) {
            vecs[i].writeLock();
        }
    }

    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        for (int i = 0; i < vecs.length; i++) {
            vecs[i].writeUnlock();
        }
    }
    
    //adding setter for matrix orientatino
    public void setOrientation (VectorOrientation orientation) {
        this.MatrixOrientation = orientation;
    }
}
