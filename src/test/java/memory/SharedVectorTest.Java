package memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SharedVectorTest {

    private SharedVector rowVec;
    private SharedVector colVec;

    @BeforeEach
    void setUp() {
        // Setup: Initialize a row vector and a column vector before each test
        double[] data = {1.0, 2.0, 3.0};
        rowVec = new SharedVector(data.clone(), VectorOrientation.ROW_MAJOR);
        colVec = new SharedVector(data.clone(), VectorOrientation.COLUMN_MAJOR);
    }

    @Test
    void testTransposeTogglesOrientation() {
        // Verify initial state
        assertEquals(VectorOrientation.ROW_MAJOR, rowVec.getOrientation());
        
        // Perform transpose
        rowVec.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, rowVec.getOrientation());
        
        // Transpose back
        rowVec.transpose();
        assertEquals(VectorOrientation.ROW_MAJOR, rowVec.getOrientation());
    }

    @Test
    void testAddSuccess() {
        // Case: Adding two row vectors of the same length
        double[] otherData = {4.0, 5.0, 6.0};
        SharedVector other = new SharedVector(otherData, VectorOrientation.ROW_MAJOR);
        
        rowVec.add(other); 

        // Assert values are summed correctly
        assertEquals(5.0, rowVec.get(0), "1.0 + 4.0 should be 5.0");
        assertEquals(7.0, rowVec.get(1), "2.0 + 5.0 should be 7.0");
        assertEquals(9.0, rowVec.get(2), "3.0 + 6.0 should be 9.0");
    }

    @Test
    void testAddThrowsOnDimensionMismatch() {
        // Edge Case: Adding vectors of different lengths
        double[] wrongSizeData = {1.0, 2.0}; // Length 2 instead of 3
        SharedVector wrongSize = new SharedVector(wrongSizeData, VectorOrientation.ROW_MAJOR);
        
        assertThrows(IllegalArgumentException.class, () -> rowVec.add(wrongSize), 
            "Should throw exception when lengths differ");
    }

    @Test
    void testAddThrowsOnOrientationMismatch() {
        // Edge Case: Adding a row vector to a column vector
        assertThrows(IllegalArgumentException.class, () -> rowVec.add(colVec),
            "Should throw exception when orientations differ");
    }

    @Test
    void testDotProductSuccess() {
        // Case: Dot product of Row * Column (1*1 + 2*2 + 3*3 = 14)
        double result = rowVec.dot(colVec);
        assertEquals(14.0, result, 0.0001);
    }

    @Test
    void testDotProductThrowsOnWrongOrientation() {
        // Edge Case: Dot product requires Left=Row, Right=Column
        
        // 1. Left is Column (invalid)
        SharedVector v1 = new SharedVector(new double[]{1}, VectorOrientation.COLUMN_MAJOR);
        SharedVector v2 = new SharedVector(new double[]{1}, VectorOrientation.COLUMN_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> v1.dot(v2));

        // 2. Right is Row (invalid)
        SharedVector v3 = new SharedVector(new double[]{1}, VectorOrientation.ROW_MAJOR);
        SharedVector v4 = new SharedVector(new double[]{1}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> v3.dot(v4));
    }

    @Test
    void testNegate() {
        // Case: Negating values
        double[] data = {10.0, -5.0};
        SharedVector v = new SharedVector(data, VectorOrientation.ROW_MAJOR);
        
        v.negate();
        
        assertEquals(-10.0, v.get(0));
        assertEquals(5.0, v.get(2));
    }

    @Test
        void testVecMatMulSuccess() {
            // Vector: [1, 2]
            // Matrix (stored as columns): [[3, 4], [5, 6]]
            // Calculation: 1*3 + 2*4 = 11, 1*5 + 2*6 = 17 -> Result [11, 17]
            
            SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
            
            double[][] matData = {
                {3.0, 5.0},
                {4.0, 6.0}
            };
            SharedMatrix mat = new SharedMatrix();
            mat.loadColumnMajor(matData); // Columns will be {3,4} and {5,6}

            v.vecMatMul(mat);

            assertEquals(2, v.length());
            assertEquals(11.0, v.get(0));
            assertEquals(17.0, v.get(1));
        }

        @Test
        void testVecMatMulThrowsOnDimensionMismatch() {
            // Vector length 2, Matrix column length 3
            SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
            double[][] matData = {{1, 2}, {3, 4}, {5, 6}}; // 3 rows, 2 cols
            SharedMatrix mat = new SharedMatrix();
            mat.loadColumnMajor(matData);

            assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(mat));
        }

        @Test
        void testVecMatMulThrowsOnOrientationMismatch() {
            // Matrix must be COLUMN_MAJOR
            SharedVector v = new SharedVector(new double[]{1.0, 2.0}, VectorOrientation.ROW_MAJOR);
            double[][] matData = {{1, 2}, {3, 4}};
            SharedMatrix mat = new SharedMatrix();
            mat.loadRowMajor(matData); // Invalid for vecMatMul

            assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(mat));
        }
}