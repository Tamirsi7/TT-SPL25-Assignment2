package spl.lae;

import org.junit.jupiter.api.Test;
import parser.ComputationNode;
import parser.ComputationNodeType;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LinearAlgebraEngineTest {

    @Test
    void testSimpleAddition() {
        // Case: 2 Threads, 1x2 Matrix + 1x2 Matrix
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        
        double[][] data1 = {{1.0, 2.0}};
        double[][] data2 = {{3.0, 4.0}};
        
        ComputationNode m1 = new ComputationNode(data1);
        ComputationNode m2 = new ComputationNode(data2);
        
        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, Arrays.asList(m1, m2));
        
        ComputationNode resultNode = engine.run(root);
        double[][] result = resultNode.getMatrix();
        
        assertEquals(4.0, result[0][0]); // 1+3
        assertEquals(6.0, result[0][1]); // 2+4
    }

    @Test
    void testMultiplicationRowByMatrix() {
        // Case: [1, 2] * [[3, 4], [5, 6]]
        // Calculation: [1*3 + 2*5, 1*4 + 2*6] = [3+10, 4+12] = [13, 16]
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        
        double[][] left = {{1.0, 2.0}};
        double[][] right = {
            {3.0, 4.0},
            {5.0, 6.0}
        };
        
        ComputationNode m1 = new ComputationNode(left);
        ComputationNode m2 = new ComputationNode(right);
        
        ComputationNode root = new ComputationNode(ComputationNodeType.MULTIPLY, Arrays.asList(m1, m2));
        
        ComputationNode resultNode = engine.run(root);
        double[][] result = resultNode.getMatrix();
        
        assertEquals(13.0, result[0][0]);
        assertEquals(16.0, result[0][1]);
    }
    
    @Test
    void testTransposeSingleRow() {
        // Case: Transpose a 1x3 row vector -> should become 3x1 column vector
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        double[][] data = {{1.0, 2.0, 3.0}};
        
        ComputationNode m1 = new ComputationNode(data);
        ComputationNode root = new ComputationNode(ComputationNodeType.TRANSPOSE, Arrays.asList(m1));
        
        ComputationNode resultNode = engine.run(root);
        double[][] result = resultNode.getMatrix();
        
        // Assert dimensions
        assertEquals(3, result.length, "Should result in 3 rows");
        assertEquals(1, result[0].length, "Should result in 1 column");
        
        // Assert values
        assertEquals(1.0, result[0][0]);
        assertEquals(2.0, result[1][0]);
        assertEquals(3.0, result[2][0]);
    }

    @Test
    void testNegate() {
        // Case: Negate [[1, -1]] -> [[-1, 1]]
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        double[][] data = {{1.0, -1.0}};
        
        ComputationNode m1 = new ComputationNode(data);
        ComputationNode root = new ComputationNode(ComputationNodeType.NEGATE, Arrays.asList(m1));
        
        ComputationNode resultNode = engine.run(root);
        double[][] result = resultNode.getMatrix();
        
        assertEquals(-1.0, result[0][0]);
        assertEquals(1.0, result[0][1]);
    }

    @Test
    void testAssociativeNestingExecution() {
        // Test A + B + C (Should be computed as (A+B)+C)
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        
        double[][] d1 = {{1.0}};
        double[][] d2 = {{2.0}};
        double[][] d3 = {{3.0}};
        
        ComputationNode n1 = new ComputationNode(d1);
        ComputationNode n2 = new ComputationNode(d2);
        ComputationNode n3 = new ComputationNode(d3);
        
        // Create node with 3 children
        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, Arrays.asList(n1, n2, n3));
        
        ComputationNode resultNode = engine.run(root);
        double[][] result = resultNode.getMatrix();
        
        assertEquals(6.0, result[0][0]);
    }

    @Test
    void testMixedOperations() {
        // Test (A + B) * C
        // ([1] + [2]) * [3] = [3] * [3] = [9]
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        
        ComputationNode A = new ComputationNode(new double[][]{{1.0}});
        ComputationNode B = new ComputationNode(new double[][]{{2.0}});
        ComputationNode C = new ComputationNode(new double[][]{{3.0}});
        
        ComputationNode addNode = new ComputationNode(ComputationNodeType.ADD, Arrays.asList(A, B));
        ComputationNode root = new ComputationNode(ComputationNodeType.MULTIPLY, Arrays.asList(addNode, C));
        
        ComputationNode resultNode = engine.run(root);
        assertEquals(9.0, resultNode.getMatrix()[0][0]);
    }
}