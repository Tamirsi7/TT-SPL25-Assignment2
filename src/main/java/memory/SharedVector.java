package memory;

import java.util.concurrent.locks.ReadWriteLock;

public class SharedVector {

    private double[] vector;
    private VectorOrientation orientation;
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    public SharedVector(double[] vector, VectorOrientation orientation) {
        this.orientation = orientation; // storing orientation in matching field
        this.vector = vector; // due to efficiency and to avoid holding unnecesary vectors
    }

    public double get(int index) {
        return vector[index];
    }

    public int length() {
        return vector.length;
    }

    public VectorOrientation getOrientation() {
        return orientation;
    }

    public void writeLock() {
        lock.writeLock().lock(); // accessing lock's writelock and locking it.
    }

    public void writeUnlock() {
        lock.writeLock().unlock(); // accessing lock's writelock and unlocking it.
    }

    public void readLock() {
        lock.readLock().lock(); // accessing lock's readlock and locking it.
    }

    public void readUnlock() {
        lock.readLock().unlock(); // accessing lock's readlock and unlocking it.
    }

    public void transpose() {
        // transposing vector from row to column or opposite
        if (orientation == VectorOrientation.ROW_MAJOR) {
            orientation = VectorOrientation.COLUMN_MAJOR;
        } else {
            orientation = VectorOrientation.ROW_MAJOR;
        }
    }

    public void add(SharedVector other) {
        if (this.length() != other.length()) {
            throw new IllegalArgumentException("error: Illegal operation: dimensions mismatch"); //throwing exception if size of vectors is not right
        }
        if (this.getOrientation() != other.getOrientation()) {
            throw new IllegalArgumentException("error: Illegal operation: dimensions mismatch"); //throwing exception if both vectors are not same "type" (column \ row)
        }
        for (int i = 0; i < vector.length; i++) {
            this.vector[i] = this.vector[i] + other.get(i); //summing values to this.vector in matching elemnt slots
        }
    }

    public void negate() {
        for (int i = 0; i < vector.length; i++) { // negating each element in the vector
            vector[i] = -vector[i];
        }
    }

    public double dot(SharedVector other) {
        if (this.length() != other.length()) {
            throw new IllegalArgumentException("error: Illegal operation: dimensions mismatch"); //throwing exception if size of vectors is not right
        }
        if (this.getOrientation() == VectorOrientation.COLUMN_MAJOR) {
            throw new IllegalArgumentException("error: Illegal operation: left vector is a column vector"); //throwing exception if left vector is not in the right "type" (row * column)
        }
        if (other.getOrientation() == VectorOrientation.ROW_MAJOR) {
            throw new IllegalArgumentException("error: Illegal operation: right vector is a row vector"); //throwing exception if right vector is not in the right "type"  (row * column)
        }
        double sum=0;
        for(int i=0; i<vector.length;i++){
            sum+= this.vector[i]*other.vector[i];
        }
        return sum;
    }

    public void vecMatMul(SharedMatrix matrix) {
        // TODO: compute row-vector × matrix
    }
}
