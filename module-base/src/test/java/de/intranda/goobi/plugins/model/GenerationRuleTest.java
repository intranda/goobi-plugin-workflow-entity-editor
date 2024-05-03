package de.intranda.goobi.plugins.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class GenerationRuleTest {

    @Test
    public void testSingleNumber() {
        String value = "lkl1";
        GenerationRule rule = new GenerationRule(value, "0000");
        String res = rule.generate(null);
        assertEquals("lkl0001", res);
    }
    
    @Test
    public void testTwoNumbers() {
        String value = "2lkl1";
        GenerationRule rule = new GenerationRule(value, "0000");
        String res = rule.generate(null);
        assertEquals("0002lkl0001", res);
    }
    
    @Test
    public void testMultipleOccurances() {
        String value = "1lkl1";
        GenerationRule rule = new GenerationRule(value, "0000");
        String res = rule.generate(null);
        assertEquals("0001lkl0001", res);
    }

}
