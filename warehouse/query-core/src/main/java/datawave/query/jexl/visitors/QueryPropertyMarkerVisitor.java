package datawave.query.jexl.visitors;

import com.google.common.collect.Lists;
import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.nodes.ExceededOrThresholdMarkerJexlNode;
import datawave.query.jexl.nodes.ExceededTermThresholdMarkerJexlNode;
import datawave.query.jexl.nodes.ExceededValueThresholdMarkerJexlNode;
import datawave.query.jexl.nodes.IndexHoleMarkerJexlNode;
import datawave.query.jexl.nodes.QueryPropertyMarker;
import datawave.query.jexl.nodes.BoundedRange;
import org.apache.commons.jexl2.parser.ASTAndNode;
import org.apache.commons.jexl2.parser.ASTAssignment;
import org.apache.commons.jexl2.parser.ASTEvaluationOnly;
import org.apache.commons.jexl2.parser.ASTDelayedPredicate;
import org.apache.commons.jexl2.parser.ASTOrNode;
import org.apache.commons.jexl2.parser.ASTReference;
import org.apache.commons.jexl2.parser.ASTReferenceExpression;
import org.apache.commons.jexl2.parser.JexlNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.apache.commons.jexl2.parser.JexlNodes.children;

/**
 * This class is used to determine whether the specified node is an instance of a query marker. The reason for this functionality is that if the query is
 * serialized and deserialized, then only the underlying assignment will persist. This class will identify the Reference, ReferenceExpression, or And nodes,
 * created by the original QueryPropertyMarker instance, as the marked node. Children of the marker node will not be identified as marked.
 */
public class QueryPropertyMarkerVisitor extends BaseVisitor {
    
    private static final Set<String> TYPE_IDENTIFIERS;
    
    protected Set<String> typeIdentifiers = new HashSet<>();
    protected List<JexlNode> sourceNodes;
    
    private boolean identifierFound = false;
    
    protected Set<String> rejectedIdentifiers = new HashSet<>();
    private boolean rejectedIdentifiersFound = false;
    
    static {
        TYPE_IDENTIFIERS = new HashSet<>();
        TYPE_IDENTIFIERS.add(IndexHoleMarkerJexlNode.class.getSimpleName());
        TYPE_IDENTIFIERS.add(ASTDelayedPredicate.class.getSimpleName());
        TYPE_IDENTIFIERS.add(ASTEvaluationOnly.class.getSimpleName());
        TYPE_IDENTIFIERS.add(ExceededValueThresholdMarkerJexlNode.class.getSimpleName());
        TYPE_IDENTIFIERS.add(ExceededTermThresholdMarkerJexlNode.class.getSimpleName());
        TYPE_IDENTIFIERS.add(ExceededOrThresholdMarkerJexlNode.class.getSimpleName());
        TYPE_IDENTIFIERS.add(BoundedRange.class.getSimpleName());
    }
    
    private QueryPropertyMarkerVisitor() {}
    
    public static boolean instanceOfAny(JexlNode node) {
        return instanceOfAny(node, (List) null);
    }
    
    public static boolean instanceOfAny(JexlNode node, List<JexlNode> sourceNodes) {
        return instanceOf(node, (Set) null, sourceNodes);
    }
    
    public static boolean instanceOf(JexlNode node, Set<Class<? extends QueryPropertyMarker>> types, List<JexlNode> sourceNodes) {
        return instanceOf(node, types != null ? Lists.newArrayList(types) : null, sourceNodes);
    }
    
    public static boolean instanceOfAnyExcept(JexlNode node, List<Class<? extends QueryPropertyMarker>> except) {
        return instanceOfAnyExcept(node, except, null);
    }
    
    public static boolean instanceOfAnyExcept(JexlNode node, List<Class<? extends QueryPropertyMarker>> except, List<JexlNode> sourceNodes) {
        return instanceOf(node, null, except, sourceNodes);
    }
    
    public static boolean instanceOf(JexlNode node, Class<? extends QueryPropertyMarker> type, List<JexlNode> sourceNodes) {
        return instanceOf(node, type == null ? null : Collections.singletonList(type), null, sourceNodes);
    }
    
    public static boolean instanceOf(JexlNode node, List<Class<? extends QueryPropertyMarker>> types, List<JexlNode> sourceNodes) {
        return instanceOf(node, types, null, sourceNodes);
    }
    
    /**
     * Check a node for any QueryPropertyMarker in types as long as it doesn't have any QueryPropertyMarkers in except
     * 
     * @param node
     * @param types
     * @param except
     * @param sourceNodes
     * @return true if at least one of the types QueryPropertyMarkers exists and there are no QueryPropertyMarkers from except, false otherwise
     */
    public static boolean instanceOf(JexlNode node, List<Class<? extends QueryPropertyMarker>> types, List<Class<? extends QueryPropertyMarker>> except,
                    List<JexlNode> sourceNodes) {
        QueryPropertyMarkerVisitor visitor = new QueryPropertyMarkerVisitor();
        
        if (node != null) {
            if (types != null)
                types.stream().forEach(type -> visitor.typeIdentifiers.add(type.getSimpleName()));
            else
                visitor.typeIdentifiers.addAll(TYPE_IDENTIFIERS);
            
            if (except != null) {
                except.stream().forEach(e -> visitor.rejectedIdentifiers.add(e.getSimpleName()));
            }
            
            node.jjtAccept(visitor, null);
            
            if (visitor.identifierFound) {
                if (sourceNodes != null)
                    for (JexlNode sourceNode : visitor.sourceNodes)
                        sourceNodes.add(trimReferenceNodes(sourceNode));
                return !visitor.rejectedIdentifiersFound;
            }
        }
        
        return false;
    }
    
    private static JexlNode trimReferenceNodes(JexlNode node) {
        if ((node instanceof ASTReference || node instanceof ASTReferenceExpression) && node.jjtGetNumChildren() == 1)
            return trimReferenceNodes(node.jjtGetChild(0));
        return node;
    }
    
    @Override
    public Object visit(ASTAssignment node, Object data) {
        if (data != null) {
            Set foundIdentifiers = (Set) data;
            
            String identifier = JexlASTHelper.getIdentifier(node);
            if (identifier != null) {
                foundIdentifiers.add(identifier);
            }
            
            if (rejectedIdentifiers.contains(identifier)) {
                rejectedIdentifiersFound = true;
            }
        }
        return null;
    }
    
    @Override
    public Object visit(ASTOrNode node, Object data) {
        return null;
    }
    
    @Override
    public Object visit(ASTAndNode node, Object data) {
        // if this is an and node, and it is the first one we've
        // found, it is our potential candidate
        if (data == null) {
            List<JexlNode> siblingNodes = new ArrayList<>();
            
            Deque<JexlNode> siblings = new LinkedList<>();
            Deque<JexlNode> stack = new LinkedList<>();
            stack.push(node);
            
            // for the purposes of this method, nested and nodes are
            // ignored, and their children are handled as direct children
            // of the parent and node.
            while (!stack.isEmpty()) {
                JexlNode descendant = stack.pop();
                
                if (descendant instanceof ASTAndNode) {
                    for (JexlNode sibling : children(descendant))
                        stack.push(sibling);
                } else {
                    siblings.push(descendant);
                }
            }
            
            // check each child to see if we found our identifier, and
            // save off the siblings as potential source nodes
            for (JexlNode child : siblings) {
                
                // don't look for identifiers if we already found what we were looking for
                if (!identifierFound) {
                    Set<String> foundIdentifiers = new HashSet<>();
                    child.jjtAccept(this, foundIdentifiers);
                    
                    foundIdentifiers.retainAll(typeIdentifiers);
                    
                    // if we found our identifier, proceed to the next child node
                    if (!foundIdentifiers.isEmpty()) {
                        identifierFound = true;
                        continue;
                    }
                }
                
                siblingNodes.add(child);
            }
            
            if (identifierFound)
                sourceNodes = siblingNodes;
        }
        return null;
    }
}
