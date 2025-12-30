package parser;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class ComputationNodeTest {

    @Test
    void testFindResolvableOnMatrixReturnsNull() {
        // Edge Case: Calling findResolvable on a leaf (MATRIX) should return null
        ComputationNode leaf = new ComputationNode(new double[][]{{1}});
        assertNull(leaf.findResolvable());
    }

    @Test
    void testFindResolvableImmediate() {
        // Case: (MATRIX + MATRIX) -> Root is resolvable
        ComputationNode matrix1 = new ComputationNode(new double[][]{{1}});
        ComputationNode matrix2 = new ComputationNode(new double[][]{{2}});
        List<ComputationNode> children = new ArrayList<>();
        children.add(matrix1);
        children.add(matrix2);
        
        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, children);
        
        assertEquals(root, root.findResolvable());
    }

    @Test
    void testFindResolvableDeep() {
        // Case: (MATRIX + (MATRIX * MATRIX))
        // The deeper node (MULTIPLY) must be resolved first
        ComputationNode matrix1 = new ComputationNode(new double[][]{{1}});
        ComputationNode matrix2 = new ComputationNode(new double[][]{{2}});
        ComputationNode matrix3 = new ComputationNode(new double[][]{{3}});
        
        // Build right branch: (2 * 3)
        List<ComputationNode> multChildren = new ArrayList<>();
        multChildren.add(matrix2);
        multChildren.add(matrix3);
        ComputationNode multNode = new ComputationNode(ComputationNodeType.MULTIPLY, multChildren);
        
        // Build root: (1 + multNode)
        List<ComputationNode> addChildren = new ArrayList<>();
        addChildren.add(matrix1);
        addChildren.add(multNode);
        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, addChildren);

        // Verify deep node is found
        assertEquals(multNode, root.findResolvable());
    }

    @Test
    void testAssociativeNestingHandlesThreeOperands() {
        // Case: A + B + C -> (A + B) + C
        ComputationNode A = new ComputationNode(new double[][]{{1}});
        ComputationNode B = new ComputationNode(new double[][]{{2}});
        ComputationNode C = new ComputationNode(new double[][]{{3}});
        
        List<ComputationNode> children = new ArrayList<>();
        children.add(A);
        children.add(B);
        children.add(C);
        
        ComputationNode root = new ComputationNode(ComputationNodeType.ADD, children);
        
        assertEquals(3, root.getChildren().size(), "Initially has 3 children");
        
        root.associativeNesting();
        
        // Assertions after nesting
        assertEquals(2, root.getChildren().size(), "Should become binary (2 children)");
        
        ComputationNode leftChild = root.getChildren().get(0);
        assertEquals(ComputationNodeType.ADD, leftChild.getNodeType(), "Left child should be a new ADD node");
        assertEquals(2, leftChild.getChildren().size(), "New nested node should have 2 children (A and B)");
        assertEquals(C, root.getChildren().get(1), "Right child should be C");
    }
}