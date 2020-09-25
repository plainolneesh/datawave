package datawave.query.jexl.visitors;

import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.JexlNodeFactory;
import datawave.query.jexl.LiteralRange;
import datawave.query.jexl.nodes.BoundedRange;
import datawave.query.jexl.nodes.QueryPropertyMarker;
import org.apache.commons.jexl2.parser.ASTAndNode;
import org.apache.commons.jexl2.parser.ASTNENode;
import org.apache.commons.jexl2.parser.ASTNRNode;
import org.apache.commons.jexl2.parser.ASTNotNode;
import org.apache.commons.jexl2.parser.ASTOrNode;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.JexlNodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pushdown negations until they are either wrapping leaf nodes or bounded ranges. The purpose of this class is to remove edge cases from evaluating
 * executability with respect to negations.
 *
 * Visitor requires a flattened tree prior to execution or DeMorgans law will not be applied correctly. Negations will never be pushed down into EQ/ER nodes.
 * Negations will be pushed down to flip NEQ and NR nodes
 */
public class PushdownNegationVisitor extends BaseVisitor {
    
    /**
     * Create a copy of the tree and flatten it, then pushdown all negations
     * 
     * @param tree
     *            the original tree
     * @return the modified tree with all negations pushed down
     */
    public static JexlNode pushdownNegations(JexlNode tree) {
        // flatten the input
        JexlNode flattened = TreeFlatteningRebuildingVisitor.flatten(tree);
        
        flattened.jjtAccept(new PushdownNegationVisitor(), new NegationState(false));
        
        // flatten the result
        return TreeFlatteningRebuildingVisitor.flatten(flattened);
    }
    
    @Override
    public Object visit(ASTNotNode node, Object data) {
        // if there is already a negation passed down it should negate this node and continue down the tree to check for other negations
        if (data != null && ((NegationState) data).isNegated()) {
            JexlNode nextNode = dropNot(node, (NegationState) data);
            
            // pass down with a new negation state since nothing that comes up from this point should impact what is already being returned (a processed
            // negation)
            nextNode.jjtAccept(this, new NegationState(false));
            
            return data;
        } else {
            // arrived to this node without a negation, apply a negation and push down
            NegationState state = (NegationState) super.visit(node, new NegationState(true));
            
            // if the returned state is false the negation was successfully pushed down, it should be removed
            if (!state.isNegated()) {
                // drop the not because it was pushed down and get the new node
                JexlNode nextNode = dropNot(node, new NegationState(true));
                
                // the negation at this level was removed so the state is false going forward
                nextNode.jjtAccept(this, new NegationState(false));
            }
            
            // any down-tree state manipulation is a result of this NOT so do not back propagate that
            return data;
        }
    }
    
    @Override
    public Object visit(ASTOrNode node, Object data) {
        if (data instanceof NegationState) {
            NegationState state = (NegationState) data;
            
            if (state.isNegated()) {
                // apply de morgans without negating the replacement since that is upstream so can be skipped
                JexlNode newNode = applyDeMorgans(node, false);
                
                // the not has been propagated
                state.setNegated(false);
                
                // abandon the current node and continue down the path of the new node
                return newNode.jjtAccept(this, data);
            }
        }
        
        // keep following the negations
        return super.visit(node, data);
    }
    
    /**
     * @param node
     * @param data
     * @return
     */
    @Override
    public Object visit(ASTAndNode node, Object data) {
        if (data instanceof NegationState) {
            NegationState state = (NegationState) data;
            
            if (state.isNegated()) {
                // look for any markers because we don't want to flip an AND associated with a marker
                if (QueryPropertyMarker.instanceOf(node, null)) {
                    // do not propagate inside an ivarator marker
                    if (JexlASTHelper.isIvaratorMarker(node)) {
                        // don't propagate inside
                        return data;
                    } else if (BoundedRange.instanceOf(node)) {
                        // don't propagate inside
                        return data;
                    }
                    // move inside to the source node
                    JexlNode source = QueryPropertyMarker.getQueryPropertySource(node, null);
                    return source.jjtAccept(this, data);
                }
                
                // look for bounded ranges which will prevent propagation
                List<JexlNode> otherNodes = new ArrayList<>();
                if (JexlASTHelper.findRange().notDelayed().isRange(node)) {
                    // bounded range, can't do it
                    return data;
                }
                
                // flip it
                JexlNode newNode = applyDeMorgans(node, false);
                state.setNegated(false);
                
                return newNode.jjtAccept(this, data);
            }
        }
        
        // keep going
        return super.visit(node, data);
    }
    
    @Override
    public Object visit(ASTNENode node, Object data) {
        if (data instanceof NegationState) {
            NegationState state = (NegationState) data;
            
            if (state.isNegated()) {
                // replace this node with an EQ node
                JexlNode eqNode = JexlNodeFactory.buildEQNode(JexlASTHelper.getIdentifier(node), JexlASTHelper.getLiteral(node).image);
                
                JexlNodes.swap(node.jjtGetParent(), node, eqNode);
                state.setNegated(false);
                return state;
            }
        }
        
        // keep going
        return super.visit(node, data);
    }
    
    @Override
    public Object visit(ASTNRNode node, Object data) {
        if (data instanceof NegationState) {
            NegationState state = (NegationState) data;
            
            if (state.isNegated()) {
                // replace this node with an ER node
                JexlNode eqNode = JexlNodeFactory.buildERNode(JexlASTHelper.getIdentifier(node), JexlASTHelper.getLiteral(node).image);
                
                JexlNodes.swap(node.jjtGetParent(), node, eqNode);
                state.setNegated(false);
                return state;
            }
        }
        
        // keep going
        return super.visit(node, data);
    }
    
    /**
     * Apply De Morgan's law to a node, splicing out the old root and putting a replacement in its place. De Morgans law states that the negation of a
     * disjunction is the conjunction of the negations; and the negation of a conjunction is the disjunction of the negations
     *
     * By generalizing De Morgans duality, an ASTAndNode can be converted to an ASTOrNode by applying a negation to the root, flipping the operator (AND to OR
     * vice versa), and negating each child then splicing the new root into the parent
     * 
     * @param root
     *            the node to apply De Morgan's law to either an ASTAndNode or ASTOrNode, must not be null
     * @param negateRoot
     *            apply the negation of the parent here, or assume that will be done eventually elsewhere
     *
     * @return the replacement for root which was replaced, will always be an ASTNotNode
     */
    public static JexlNode applyDeMorgans(JexlNode root, boolean negateRoot) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        
        if (root.jjtGetNumChildren() == 0) {
            throw new IllegalStateException("root must have at least one child");
        }
        
        // build the child list, while negating each child
        List<JexlNode> negatedChildren = new ArrayList<>(root.jjtGetNumChildren());
        for (int i = 0; i < root.jjtGetNumChildren(); i++) {
            negatedChildren.add(JexlNodes.negate(root.jjtGetChild(i)));
        }
        
        // flip the root operator and finish validating the root
        JexlNode newRoot;
        if (root instanceof ASTAndNode) {
            newRoot = JexlNodeFactory.createOrNode(negatedChildren);
        } else if (root instanceof ASTOrNode) {
            newRoot = JexlNodeFactory.createAndNode(negatedChildren);
        } else {
            throw new IllegalArgumentException("root must be either an ASTAndNode or ASTOrNode");
        }
        
        if (negateRoot) {
            // negate the new node
            newRoot = JexlNodes.negate(newRoot);
        }
        
        // if there is a parent to swap
        if (root.jjtGetParent() != null) {
            // replace root with the newly negated node, AND -> OR or OR -> AND
            JexlNodes.swap(root.jjtGetParent(), root, newRoot);
        }
        
        return newRoot;
    }
    
    private JexlNode dropNot(ASTNotNode toDrop, NegationState state) {
        JexlNode newNode = toDrop.jjtGetChild(0);
        
        // no need to swap nodes if toDrop is the root
        if (toDrop.jjtGetParent() != null) {
            // connect the child to the parent cutting this node out
            JexlNodes.swap(toDrop.jjtGetParent(), toDrop, newNode);
        }
        
        // negate the data so the parent Not knows it was successfully pushed down
        state.setNegated(false);
        
        return newNode;
    }
    
    private static class NegationState {
        private boolean negated;
        
        private NegationState(boolean negated) {
            this.negated = negated;
        }
        
        public boolean isNegated() {
            return negated;
        }
        
        public void setNegated(boolean negated) {
            this.negated = negated;
        }
    }
}
