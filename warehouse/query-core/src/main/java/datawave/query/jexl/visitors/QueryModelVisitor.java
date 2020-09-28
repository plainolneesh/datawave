package datawave.query.jexl.visitors;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import datawave.query.jexl.LiteralRange;
import datawave.query.jexl.nodes.BoundedRange;
import datawave.query.model.QueryModel;
import datawave.query.exceptions.DatawaveFatalQueryException;
import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.JexlNodeFactory;
import datawave.query.jexl.JexlNodeFactory.ContainerType;
import datawave.webservice.common.logging.ThreadConfigurableLogger;
import datawave.webservice.query.exception.DatawaveErrorCode;
import datawave.webservice.query.exception.QueryException;
import org.apache.commons.jexl2.parser.ASTAndNode;
import org.apache.commons.jexl2.parser.ASTEQNode;
import org.apache.commons.jexl2.parser.ASTERNode;
import org.apache.commons.jexl2.parser.ASTFunctionNode;
import org.apache.commons.jexl2.parser.ASTGENode;
import org.apache.commons.jexl2.parser.ASTGTNode;
import org.apache.commons.jexl2.parser.ASTIdentifier;
import org.apache.commons.jexl2.parser.ASTJexlScript;
import org.apache.commons.jexl2.parser.ASTLENode;
import org.apache.commons.jexl2.parser.ASTLTNode;
import org.apache.commons.jexl2.parser.ASTMethodNode;
import org.apache.commons.jexl2.parser.ASTNENode;
import org.apache.commons.jexl2.parser.ASTNRNode;
import org.apache.commons.jexl2.parser.ASTNullLiteral;
import org.apache.commons.jexl2.parser.ASTOrNode;
import org.apache.commons.jexl2.parser.ASTReference;
import org.apache.commons.jexl2.parser.ASTReferenceExpression;
import org.apache.commons.jexl2.parser.ASTSizeMethod;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.JexlNodes;
import org.apache.commons.jexl2.parser.Node;
import org.apache.commons.jexl2.parser.ParserTreeConstants;
import org.apache.log4j.Logger;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.apache.commons.jexl2.parser.JexlNodes.id;

/**
 * Apply the forward mapping
 */
public class QueryModelVisitor extends RebuildingVisitor {
    private static final Logger log = ThreadConfigurableLogger.getLogger(QueryModelVisitor.class);
    
    private QueryModel queryModel;
    private HashSet<ASTAndNode> expandedNodes;
    private Set<String> validFields;
    private SimpleQueryModelVisitor simpleQueryModelVisitor;
    
    public QueryModelVisitor(QueryModel queryModel, Set<String> validFields) {
        this.queryModel = queryModel;
        this.expandedNodes = Sets.newHashSet();
        this.validFields = validFields;
        this.simpleQueryModelVisitor = new SimpleQueryModelVisitor(queryModel, validFields);
    }
    
    /**
     * Get the aliases for the field, and retain only those in the "validFields" set.
     * 
     * @param field
     * @return the list of field aliases
     */
    protected Collection<String> getAliasesForField(String field) {
        List<String> aliases = new ArrayList<>(this.queryModel.getMappingsForAlias(field));
        aliases.retainAll(validFields);
        return aliases;
    }
    
    public static ASTJexlScript applyModel(ASTJexlScript script, QueryModel queryModel, Set<String> validFields) {
        QueryModelVisitor visitor = new QueryModelVisitor(queryModel, validFields);
        
        return (ASTJexlScript) script.jjtAccept(visitor, null);
    }
    
    @Override
    public Object visit(ASTEQNode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTNENode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTERNode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTNRNode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTLTNode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTLENode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTGTNode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTGENode node, Object data) {
        return expandBinaryNodeFromModel(node, data);
    }
    
    @Override
    public Object visit(ASTFunctionNode node, Object data) {
        return node.jjtAccept(this.simpleQueryModelVisitor, data);
    }
    
    @Override
    public Object visit(ASTReference node, Object data) {
        if (JexlASTHelper.HasMethodVisitor.hasMethod(node)) {
            // this reference has a child that is a method
            return (ASTReference) node.jjtAccept(this.simpleQueryModelVisitor, null);
        } else {
            return super.visit(node, data);
        }
    }
    
    @Override
    public Object visit(ASTMethodNode node, Object data) {
        return node.jjtAccept(this.simpleQueryModelVisitor, data);
    }
    
    @Override
    public Object visit(ASTSizeMethod node, Object data) {
        return node.jjtAccept(this.simpleQueryModelVisitor, data);
    }
    
    @Override
    public Object visit(ASTAndNode node, Object data) {
        if (this.expandedNodes.contains(node)) {
            return node;
        }
        
        ASTAndNode smashed = TreeFlatteningRebuildingVisitor.flatten(node);
        
        LiteralRange range = JexlASTHelper.findRange().getRange(smashed);
        if (range != null) {
            return expandRangeNodeFromModel(range, smashed, data);
        } else {
            return super.visit(smashed, data);
        }
    }
    
    public Object expandRangeNodeFromModel(LiteralRange range, ASTAndNode node, Object data) {
        
        // this is the set of fields that have an upper and a lower bound operand
        // make a copy of the intersection, as I will be modifying lowererBounds and upperBounds below
        List<JexlNode> aliasedBounds = Lists.newArrayList();
        
        Collection<String> aliases = getAliasesForField(range.getFieldName());
        if (aliases.isEmpty()) {
            aliases = Lists.newArrayList(range.getFieldName());
        }
        
        for (String alias : aliases) {
            if (alias != null) {
                BoundedRange rangeNode = BoundedRange.create(JexlNodes.children(new ASTAndNode(ParserTreeConstants.JJTANDNODE),
                                JexlASTHelper.setField(RebuildingVisitor.copy(range.getLowerNode()), alias),
                                JexlASTHelper.setField(RebuildingVisitor.copy(range.getUpperNode()), alias)));
                aliasedBounds.add(rangeNode);
                this.expandedNodes.add((ASTAndNode) JexlASTHelper.dereference(rangeNode));
            }
        }
        
        JexlNode nodeToAdd;
        if (1 == aliasedBounds.size()) {
            nodeToAdd = JexlASTHelper.dereference(aliasedBounds.get(0));
        } else {
            ASTOrNode unionOfAliases = new ASTOrNode(ParserTreeConstants.JJTORNODE);
            nodeToAdd = JexlNodes.children(unionOfAliases, aliasedBounds.toArray(new JexlNode[aliasedBounds.size()]));
        }
        
        return nodeToAdd;
    }
    
    /**
     * Applies the forward mapping from the QueryModel to a node, expanding the node into an Or if needed.
     * 
     * @param node
     * @param data
     * @return
     */
    protected JexlNode expandBinaryNodeFromModel(JexlNode node, Object data) {
        
        // Count the immediate children:
        int childCount = node.jjtGetNumChildren();
        
        if (childCount != 2) {
            QueryException qe = new QueryException(DatawaveErrorCode.BINARY_NODE_TOO_MANY_CHILDREN, MessageFormat.format("Node: {0}",
                            PrintingVisitor.formattedQueryString(node)));
            throw new DatawaveFatalQueryException(qe);
        }
        
        // Find identifiers
        List<ASTIdentifier> allidentifiers = JexlASTHelper.getIdentifiers(node);
        
        // If we don't have any identifiers, we have nothing to expand
        if (allidentifiers.isEmpty()) {
            return node;
        }
        
        JexlNode leftNode = node.jjtGetChild(0);
        JexlNode rightNode = node.jjtGetChild(1);
        if (log.isTraceEnabled()) {
            log.trace("leftNode:" + PrintingVisitor.formattedQueryString(leftNode));
            log.trace("leftNodeQuery:" + JexlStringBuildingVisitor.buildQuery(leftNode));
            log.trace("rightNode:" + PrintingVisitor.formattedQueryString(rightNode));
            log.trace("rightNodeQuery:" + JexlStringBuildingVisitor.buildQuery(rightNode));
        }
        // this will expand identifiers that have a method connected to them
        boolean leftState = JexlASTHelper.HasMethodVisitor.hasMethod(leftNode);
        if (leftState) {
            // there is a method under leftNode
            leftNode = (JexlNode) leftNode.jjtAccept(this.simpleQueryModelVisitor, null);
        }
        boolean rightState = JexlASTHelper.HasMethodVisitor.hasMethod(rightNode);
        if (rightState) {
            // there is a method under rightNode
            rightNode = (JexlNode) rightNode.jjtAccept(this.simpleQueryModelVisitor, null);
        }
        
        // expand any identifiers inside of methods/functions in the left and right nodes
        leftNode = (JexlNode) leftNode.jjtAccept(this, null);
        rightNode = (JexlNode) rightNode.jjtAccept(this, null);
        if (log.isTraceEnabled()) {
            log.trace("after expansion, leftNode:" + PrintingVisitor.formattedQueryString(leftNode));
            log.trace("after expansion, leftNodeQuery:" + JexlStringBuildingVisitor.buildQuery(leftNode));
            log.trace("after expansion, rightNode:" + PrintingVisitor.formattedQueryString(rightNode));
            log.trace("after expansion, rightNodeQuery:" + JexlStringBuildingVisitor.buildQuery(rightNode));
        }
        
        // if state == true on either side, then there is a method on one side and we are done applying the model
        if (leftState || rightState) {
            JexlNode toReturn = JexlNodeFactory.buildUntypedBinaryNode(node, leftNode, rightNode);
            if (log.isTraceEnabled()) {
                log.trace("done early. returning:" + JexlStringBuildingVisitor.buildQuery(toReturn));
            }
            return toReturn;
        }
        
        Object leftSeed = null, rightSeed = null;
        Set<Object> left = Sets.newHashSet(), right = Sets.newHashSet();
        boolean isNullEquality = false;
        
        if (node instanceof ASTEQNode && (leftNode instanceof ASTNullLiteral || rightNode instanceof ASTNullLiteral)) {
            isNullEquality = true;
        }
        
        // the query has been previously groomed so that identifiers are on the left and literals are on the right
        // an identifier with a method attached will have already been substituted above (and will return null for the IdentifierOpLiteral)
        // The normal case of `IDENTIFIER op 'literal'`
        JexlASTHelper.IdentifierOpLiteral op = JexlASTHelper.getIdentifierOpLiteral(node);
        if (op != null) {
            // One identifier
            leftSeed = op.getIdentifier();
            
            rightSeed = op.getLiteral();
            if (rightSeed instanceof ASTNullLiteral && node instanceof ASTEQNode) {
                isNullEquality = true;
            }
        } else if (1 <= childCount && childCount <= 2) {
            // I could have a reference on both sides of the expression
            leftSeed = node.jjtGetChild(0);
            rightSeed = node.jjtGetChild(1);
        } else {
            QueryException qe = new QueryException(DatawaveErrorCode.BINARY_NODE_TOO_MANY_CHILDREN, MessageFormat.format("Node: {0}",
                            PrintingVisitor.formattedQueryString(node)));
            throw new DatawaveFatalQueryException(qe);
        }
        
        if (leftSeed instanceof ASTReference) {
            // String fieldName = JexlASTHelper.getIdentifier((JexlNode)leftSeed);
            List<ASTIdentifier> identifiers = JexlASTHelper.getIdentifiers((ASTReference) leftSeed);
            if (identifiers.size() > 1) {
                log.warn("I did not expect to see more than one Identifier here for " + JexlStringBuildingVisitor.buildQuery((ASTReference) leftSeed)
                                + " from " + JexlStringBuildingVisitor.buildQuery(leftNode));
            }
            for (ASTIdentifier identifier : identifiers) {
                for (String fieldName : getAliasesForField(JexlASTHelper.deconstructIdentifier(identifier))) {
                    left.add(JexlNodeFactory.buildIdentifier(fieldName));
                }
            }
        } else if (leftSeed instanceof ASTIdentifier) {
            for (String fieldName : getAliasesForField(JexlASTHelper.deconstructIdentifier((ASTIdentifier) leftSeed))) {
                left.add(JexlNodeFactory.buildIdentifier(fieldName));
            }
        } else {
            // Not an identifier, therefore it's probably a literal
            left.add(leftSeed);
        }
        
        if (rightSeed instanceof ASTReference) {
            List<ASTIdentifier> identifiers = JexlASTHelper.getIdentifiers((ASTReference) rightSeed);
            if (identifiers.size() > 1) {
                log.warn("I did not expect to see more than one Identifier here for " + JexlStringBuildingVisitor.buildQuery((ASTReference) rightSeed)
                                + " from " + JexlStringBuildingVisitor.buildQuery(rightNode));
            }
            for (ASTIdentifier identifier : identifiers) {
                for (String fieldName : getAliasesForField(JexlASTHelper.deconstructIdentifier(identifier))) {
                    right.add(JexlNodeFactory.buildIdentifier(fieldName));
                }
            }
        } else if (rightSeed instanceof ASTIdentifier) {
            for (String fieldName : getAliasesForField(JexlASTHelper.deconstructIdentifier((ASTIdentifier) rightSeed))) {
                right.add(JexlNodeFactory.buildIdentifier(fieldName));
            }
            
        } else {
            // Not an identifier, therefore it's probably a literal
            right.add(rightSeed);
        }
        boolean requiresAnd = isNullEquality || node instanceof ASTNENode;
        
        if (leftSeed == null) {
            leftSeed = leftNode;
        }
        if (rightSeed == null) {
            rightSeed = rightNode;
        }
        
        @SuppressWarnings("unchecked")
        // retrieve the cartesian product
        Set<List<Object>> product = Sets.cartesianProduct(left, right);
        
        /**
         * use the product transformer to shallow copy the jexl nodes. We've created new nodes that will be embedded within an ast reference. As a result, we
         * need to ensure that if we create a logical structure ( such as an or ) -- each literal references a unique identifier from the right. Otherwise,
         * subsequent visitors will reference incorrection sub trees, and potentially negate the activity of the query model visitor
         */
        Set<List<Object>> newSet = Sets.newHashSet(FluentIterable.from(product).transform(new ProductTransformer()));
        
        if (product.size() > 1) {
            if (requiresAnd) {
                JexlNode expanded = JexlNodeFactory.createNodeTreeFromPairs(ContainerType.AND_NODE, node, newSet);
                if (log.isTraceEnabled())
                    log.trace("expanded:" + PrintingVisitor.formattedQueryString(expanded));
                return expanded;
            } else {
                JexlNode expanded = JexlNodeFactory.createNodeTreeFromPairs(ContainerType.OR_NODE, node, newSet);
                if (log.isTraceEnabled())
                    log.trace("expanded:" + PrintingVisitor.formattedQueryString(expanded));
                return expanded;
            }
        } else if (1 == product.size()) {
            List<Object> pair = product.iterator().next();
            JexlNode expanded = JexlNodeFactory.buildUntypedBinaryNode(node, pair.get(0), pair.get(1));
            if (log.isTraceEnabled())
                log.trace("expanded:" + PrintingVisitor.formattedQueryString(expanded));
            return expanded;
        }
        
        // If we couldn't map anything, return a copy
        if (log.isTraceEnabled())
            log.trace("just returning the original:" + PrintingVisitor.formattedQueryString(node));
        return node;
    }
    
    /**
     * Ensures that each object created as a result of the cartesian product of the literal and identifiers gives us unique references within the tree. Without
     * this functional transformation you may have subsequent methods that use your objects to create nodes, referencing the embedded literals
     */
    protected static class ProductTransformer implements Function<List<Object>,List<Object>> {
        @Override
        public List<Object> apply(List<Object> objects) {
            List<Object> newObjectList = Lists.newArrayListWithCapacity(objects.size());
            for (Object obj : objects) {
                Object newObj = obj;
                if (obj instanceof JexlNode) {
                    newObj = RebuildingVisitor.copy((JexlNode) obj);
                }
                newObjectList.add(newObj);
            }
            return newObjectList;
            
        }
    }
    
    /**
     * The SimpleQueryModelVisitor will only change identifiers into a disjunction of their aliases: FOO becomes (ALIASONE||ALIASTWO) It is used within function
     * and method node arguments and in the reference that a method is called on
     */
    protected static class SimpleQueryModelVisitor extends RebuildingVisitor {
        
        private static final Logger log = ThreadConfigurableLogger.getLogger(SimpleQueryModelVisitor.class);
        private QueryModel queryModel;
        private Set<String> validFields;
        
        public SimpleQueryModelVisitor(QueryModel queryModel, Set<String> validFields) {
            this.queryModel = queryModel;
            this.validFields = validFields;
        }
        
        @Override
        public Object visit(ASTIdentifier node, Object data) {
            JexlNode newNode;
            String fieldName = JexlASTHelper.getIdentifier(node);
            
            Collection<String> aliases = Sets.newLinkedHashSet(getAliasesForField(fieldName)); // de-dupe
            
            Set<ASTIdentifier> nodes = Sets.newLinkedHashSet();
            
            if (aliases.isEmpty()) {
                return super.visit(node, data);
            }
            for (String alias : aliases) {
                ASTIdentifier newKid = new ASTIdentifier(ParserTreeConstants.JJTIDENTIFIER);
                newKid.image = JexlASTHelper.rebuildIdentifier(alias);
                nodes.add(newKid);
            }
            if (nodes.size() == 1) {
                newNode = JexlNodeFactory.wrap(nodes.iterator().next());
            } else {
                newNode = JexlNodeFactory.createOrNode(nodes);
            }
            newNode.jjtSetParent(node.jjtGetParent());
            
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                newNode.jjtAddChild((Node) node.jjtGetChild(i).jjtAccept(this, data), i);
            }
            return newNode;
        }
        
        /**
         * Get the aliases for the field, and retain only those in the "validFields" set.
         *
         * @param field
         * @return the list of field aliases
         */
        protected Collection<String> getAliasesForField(String field) {
            List<String> aliases = new ArrayList<>(this.queryModel.getMappingsForAlias(field));
            aliases.retainAll(validFields);
            return aliases;
        }
    }
}
