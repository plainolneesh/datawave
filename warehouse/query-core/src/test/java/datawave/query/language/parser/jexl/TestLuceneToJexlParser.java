package datawave.query.language.parser.jexl;

import datawave.query.language.tree.QueryNode;

import org.junit.Assert;
import org.junit.Test;

public class TestLuceneToJexlParser {
    
    @Test
    public void test1() throws Exception {
        
        LuceneToJexlQueryParser parser = new LuceneToJexlQueryParser();
        
        QueryNode node = parser.parse("FIELD:SELECTOR AND #EXCLUDE(AND, F1, GB.*, F2, GB.*)");
        Assert.assertEquals("FIELD == 'SELECTOR' && (not(filter:includeRegex(F1, 'GB.*')) || not(filter:includeRegex(F2, 'GB.*')))", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #INCLUDE(AND, F1, GB.*, F2, GB.*)");
        Assert.assertEquals("FIELD == 'SELECTOR' && (filter:includeRegex(F1, 'GB.*') && filter:includeRegex(F2, 'GB.*'))", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #loaded(after, 20140101)");
        Assert.assertEquals("FIELD == 'SELECTOR' && filter:afterLoadDate(LOAD_DATE, '20140101')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #loaded(before, 20140101, yyyyMMdd)");
        Assert.assertEquals("FIELD == 'SELECTOR' && filter:beforeLoadDate(LOAD_DATE, '20140101', 'yyyyMMdd')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #loaded(between, 20140101, 20140102, yyyyMMdd)");
        Assert.assertEquals("FIELD == 'SELECTOR' && filter:betweenLoadDates(LOAD_DATE, '20140101', '20140102', 'yyyyMMdd')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #date(SOME_DATE, after, 20140101)");
        Assert.assertEquals("FIELD == 'SELECTOR' && filter:afterDate(SOME_DATE, '20140101')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #date(SOME_DATE, before, 20140101, yyyyMMdd)");
        Assert.assertEquals("FIELD == 'SELECTOR' && filter:beforeDate(SOME_DATE, '20140101', 'yyyyMMdd')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #date(SOME_DATE, between, 20140101, 20140102, yyyyMMdd)");
        Assert.assertEquals("FIELD == 'SELECTOR' && filter:betweenDates(SOME_DATE, '20140101', '20140102', 'yyyyMMdd')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #MATCHES_IN_GROUP(FIELD1, v1, FIELD2, v2)");
        Assert.assertEquals("FIELD == 'SELECTOR' && grouping:matchesInGroup(FIELD1, 'v1', FIELD2, 'v2')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #MATCHES_AT_LEAST_COUNT_OF(7, FIELD, v1, v2)");
        Assert.assertEquals("FIELD == 'SELECTOR' && filter:matchesAtLeastCountOf(7, FIELD, 'v1', 'v2')", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SOMETHING AND #EVALUATION_ONLY('NUM_FIELD: [0 TO 50}')");
        Assert.assertEquals("FIELD == 'SOMETHING' && ((ASTEvaluationOnly = true) && ((BoundedRange = true) && (NUM_FIELD >= '0' && NUM_FIELD < '50')))",
                        node.getOriginalQuery());
    }
    
    @Test
    public void test2() throws Exception {
        
        LuceneToJexlQueryParser parser = new LuceneToJexlQueryParser();
        
        QueryNode node = parser.parse("FIELD:SELECTOR AND #INCLUDE(F1, GB.*)");
        Assert.assertEquals("FIELD == 'SELECTOR' && (filter:includeRegex(F1, 'GB.*'))", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #INCLUDE(F1, GB.{3})");
        Assert.assertEquals("FIELD == 'SELECTOR' && (filter:includeRegex(F1, 'GB.{3}'))", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #INCLUDE(F1, GB\\.{3})");
        Assert.assertEquals("FIELD == 'SELECTOR' && (filter:includeRegex(F1, 'GB\\\\.{3}'))", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SELECTOR AND #INCLUDE(F1, GB\\.{3\\,1})");
        Assert.assertEquals("FIELD == 'SELECTOR' && (filter:includeRegex(F1, 'GB\\\\.{3,1}'))", node.getOriginalQuery());
        
        node = parser.parse("FIELD:SOMETHING AND #EVALUATION_ONLY('#INCLUDE(F1, GB\\.{3\\,1})')");
        Assert.assertEquals("FIELD == 'SOMETHING' && ((ASTEvaluationOnly = true) && (filter:includeRegex(F1, 'GB\\\\.{3,1}')))", node.getOriginalQuery());
    }
}
