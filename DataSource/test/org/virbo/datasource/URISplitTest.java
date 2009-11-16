/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbf
 */
public class URISplitTest {

    public URISplitTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    /**
     * Test of maybeAddFile method, of class URISplit.
     */
    @Test
    public void testMaybeAddFile_String() {
        System.out.println("maybeAddFile");
        String surl = "";
        String expResult = "";
        String result = URISplit.maybeAddFile(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of maybeAddFile method, of class URISplit.
     */
    @Test
    public void testMaybeAddFile_String_int() {
        System.out.println("maybeAddFile");
        String surl = "";
        int carotPos = 0;
        URISplit expResult = null;
        URISplit result = URISplit.maybeAddFile(surl, carotPos);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of parse method, of class URISplit.
     */
    @Test
    public void testParse_String() {
        System.out.println("parse");

        String surl;
        URISplit split;

        surl= "vap+midl:?my_file_id&itsarg=5";

        split= URISplit.parse(surl);
        System.err.println(split);
        
        URISplit expResult = null;
        URISplit result = URISplit.parse(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of removeParam method, of class URISplit.
     */
    @Test
    public void testRemoveParam() {
        System.out.println("removeParam");
        String surl = "";
        String[] parm = null;
        String expResult = "";
        String result = URISplit.removeParam(surl, parm);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of putParam method, of class URISplit.
     */
    @Test
    public void testPutParam() {
        System.out.println("putParam");
        String surl = "";
        String name = "";
        String value = "";
        String expResult = "";
        String result = URISplit.putParam(surl, name, value);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getParam method, of class URISplit.
     */
    @Test
    public void testGetParam() {
        System.out.println("getParam");
        String surl = "";
        String name = "";
        String deft = "";
        String expResult = "";
        String result = URISplit.getParam(surl, name, deft);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of parse method, of class URISplit.
     */
    @Test
    public void testParse_3args() {
        System.out.println("parse");
        String surl = "";
        int carotPos = 0;
        boolean normalize = false;
        URISplit expResult = null;
        URISplit result = URISplit.parse(surl, carotPos, normalize);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of parseParams method, of class URISplit.
     */
    @Test
    public void testParseParams() {
        System.out.println("parseParams");
        String params = "";
        LinkedHashMap expResult = null;
        LinkedHashMap result = URISplit.parseParams(params);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of formatParams method, of class URISplit.
     */
    @Test
    public void testFormatParams() {
        System.out.println("formatParams");
        Map parms = null;
        String expResult = "";
        String result = URISplit.formatParams(parms);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of format method, of class URISplit.
     */
    @Test
    public void testFormat() {
        System.out.println("format");
        URISplit split = null;
        String expResult = "";
        String result = URISplit.format(split);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of uriEncode method, of class URISplit.
     */
    @Test
    public void testUriEncode() {
        System.out.println("uriEncode");
        String surl = "";
        String expResult = "";
        String result = URISplit.uriEncode(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of uriDecode method, of class URISplit.
     */
    @Test
    public void testUriDecode() {
        System.out.println("uriDecode");
        String s = "";
        String expResult = "";
        String result = URISplit.uriDecode(s);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of toString method, of class URISplit.
     */
    @Test
    public void testToString() {
        System.out.println("toString");
        URISplit instance = new URISplit();
        String expResult = "";
        String result = instance.toString();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}