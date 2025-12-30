package memory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class SharedMatrixTest {

    @Test
    void testLoadAndReadRowMajor() {
        // Standard Case: 2x2 Matrix
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0}
        };
        SharedMatrix matrix = new SharedMatrix();
        matrix.loadRowMajor(data);

        assertEquals(VectorOrientation.ROW_MAJOR, matrix.getOrientation());
        assertEquals(2, matrix.length(), "Should have 2 rows");

        // Verify content consistency
        double[][] result = matrix.readRowMajor();
        assertArrayEquals(data[0], result[0]);
        assertArrayEquals(data[1], result[1]);
    }

    @Test
    void testLoadColumnMajorTransposesStorage() {
        // Case: Loading 3x2 matrix as Column Major
        // Input:
        // 1 2
        // 3 4
        // 5 6
        double[][] data = {
            {1.0, 2.0},
            {3.0, 4.0},
            {5.0, 6.0}
        };
        
        SharedMatrix matrix = new SharedMatrix();
        matrix.loadColumnMajor(data);

        // Expectation: Internal storage should be 2 vectors (the columns), each of length 3
        assertEquals(VectorOrientation.COLUMN_MAJOR, matrix.getOrientation());
        assertEquals(2, matrix.length(), "Should have 2 column vectors");
        assertEquals(3, matrix.get(0).length(), "Each column vector should have length 3");

        // Verify values of first column {1, 3, 5}
        SharedVector col0 = matrix.get(0);
        assertEquals(1.0, col0.get(0));
        assertEquals(3.0, col0.get(1));
        assertEquals(5.0, col0.get(2));
    }

    @Test
    void testReadRowMajorFromColumnMajorStorage() {
        // Edge Case: Ensure readRowMajor reconstructs the original matrix correctly
        // even if it was stored as column-major internally
        double[][] data = {{1, 2}, {3, 4}};
        SharedMatrix matrix = new SharedMatrix();
        matrix.loadColumnMajor(data); // Stored as columns

        double[][] result = matrix.readRowMajor(); // Read back as rows
        
        assertEquals(1.0, result[0][0]);
        assertEquals(2.0, result[0][1]);
        assertEquals(3.0, result[1][0]);
        assertEquals(4.0, result[1][1]);
    }

    @Test
    void testEmptyMatrix() {
        // Edge Case: Empty input array
        double[][] data = new double[0][0];
        SharedMatrix matrix = new SharedMatrix();
        matrix.loadRowMajor(data);
        
        assertEquals(0, matrix.length());
        assertEquals(0, matrix.readRowMajor().length);
    }
}