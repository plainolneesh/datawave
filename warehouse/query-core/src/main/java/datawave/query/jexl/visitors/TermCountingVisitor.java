package datawave.query.jexl.visitors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.LiteralRange;
import datawave.webservice.common.logging.ThreadConfigurableLogger;

import org.apache.commons.jexl2.parser.ASTAndNode;
import org.apache.commons.jexl2.parser.ASTEQNode;
import org.apache.commons.jexl2.parser.ASTERNode;
import org.apache.commons.jexl2.parser.ASTFunctionNode;
import org.apache.commons.jexl2.parser.ASTGENode;
import org.apache.commons.jexl2.parser.ASTGTNode;
import org.apache.commons.jexl2.parser.ASTLENode;
import org.apache.commons.jexl2.parser.ASTLTNode;
import org.apache.commons.jexl2.parser.ASTNENode;
import org.apache.commons.jexl2.parser.ASTNRNode;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.log4j.Logger;

/**
 * Count the number of terms where bounded ranges count as 1 term
 */
public class TermCountingVisitor extends BaseVisitor {
    private static final Logger log = ThreadConfigurableLogger.getLogger(TermCountingVisitor.class);
    
    public static int countTerms(JexlNode script) {
        TermCountingVisitor visitor = new TermCountingVisitor();
        
        return ((MutableInt) script.jjtAccept(visitor, new MutableInt(0))).intValue();
    }
    
    @Override
    public Object visit(ASTAndNode node, Object data) {
        List<JexlNode> otherNodes = new ArrayList<>();
        if (JexlASTHelper.findRange().isRange(node)) {
            // count each bounded range as 1
            ((MutableInt) data).increment();
        } else {
            // otherwise recurse on the children
            super.visit(node, data);
        }
        
        return data;
    }
    
    @Override
    public Object visit(ASTEQNode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTNENode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTERNode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTNRNode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTLTNode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTLENode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTGTNode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTGENode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
    @Override
    public Object visit(ASTFunctionNode node, Object data) {
        ((MutableInt) data).increment();
        return data;
    }
    
}
