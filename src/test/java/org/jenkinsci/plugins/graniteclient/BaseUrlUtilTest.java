package org.jenkinsci.plugins.graniteclient;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

/**
 * Created by madamcin on 3/27/17.
 */
public class BaseUrlUtilTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseUrlUtilTest.class);

    @Test
    public void testSplitByNewline() {

        String simpleValue = "some text ";
        assertEquals("simple text without newlines should be parsed as a single element",
                simpleValue, BaseUrlUtil.splitByNewline(simpleValue).get(0));

        String twoValues = "some text \n other text";
        assertEquals("two simple texts separated only by newline should be parsed as two elements",
                simpleValue, BaseUrlUtil.splitByNewline(twoValues).get(0));

        String twoValuesWithCarriage = "some text \r\n other text";
        assertEquals("two simple texts separated by cr+nl should be parsed as two elements",
                simpleValue, BaseUrlUtil.splitByNewline(twoValuesWithCarriage).get(0));

        String threeValues = "some text \n other text \nlast text ";
        assertEquals("three simple texts separated only by newline should be parsed as three elements",
                3, BaseUrlUtil.splitByNewline(threeValues).size());

        String twoValuesWithEscape = "some text \n other text \\nlast text ";
        assertEquals("two text separated only by newline but with one having an escaped newline, should be parsed as two elements",
                2, BaseUrlUtil.splitByNewline(twoValuesWithEscape).size());

        String threeValuesSepByEscape = "some text \\n other text \\nlast text ";
        assertEquals("three texts separated only by escaped newlines should be parsed as three elements",
                3, BaseUrlUtil.splitByNewline(threeValuesSepByEscape).size());

    }
}
