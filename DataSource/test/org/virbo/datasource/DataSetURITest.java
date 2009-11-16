/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.virbo.datasource.datasource.DataSourceFormat;

/**
 *
 * @author jbf
 */
public class DataSetURITest {

    public DataSetURITest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    /**
     * Test of getExt method, of class DataSetURI.
     */
    @Test
    public void testGetExt() {
        System.out.println("getExt");
        String surl = "";
        String expResult = "";
        String result = DataSetURI.getExt(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getExplicitExt method, of class DataSetURI.
     */
    @Test
    public void testGetExplicitExt() {
        System.out.println("getExplicitExt");
        String surl = "";
        String expResult = "";
        String result = DataSetURI.getExplicitExt(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of parse method, of class DataSetURI.
     */
    @Test
    public void testParse() {
        System.out.println("parse");
        String surl = "";
        URISplit expResult = null;
        URISplit result = DataSetURI.parse(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of format method, of class DataSetURI.
     */
    @Test
    public void testFormat() {
        System.out.println("format");
        URISplit split = null;
        String expResult = "";
        String result = DataSetURI.format(split);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getDataSource method, of class DataSetURI.
     */
    @Test
    public void testGetDataSource_URI() throws Exception {
        System.out.println("getDataSource");
        URI uri = null;
        DataSource expResult = null;
        DataSource result = DataSetURI.getDataSource(uri);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getDataSource method, of class DataSetURI.
     */
    @Test
    public void testGetDataSource_String() throws Exception {
        System.out.println("getDataSource");
        String surl = "";
        DataSource expResult = null;
        DataSource result = DataSetURI.getDataSource(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getDataSourceUri method, of class DataSetURI.
     */
    @Test
    public void testGetDataSourceUri() {
        System.out.println("getDataSourceUri");
        DataSource ds = null;
        String expResult = "";
        String result = DataSetURI.getDataSourceUri(ds);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of isAggregating method, of class DataSetURI.
     */
    @Test
    public void testIsAggregating() {
        System.out.println("isAggregating");
        String surl = "";
        boolean expResult = false;
        boolean result = DataSetURI.isAggregating(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getResourceURI method, of class DataSetURI.
     */
    @Test
    public void testGetResourceURI_URI() {
        System.out.println("getResourceURI");
        URI uri = null;
        URI expResult = null;
        URI result = DataSetURI.getResourceURI(uri);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getResourceURI method, of class DataSetURI.
     */
    @Test
    public void testGetResourceURI_String() {
        System.out.println("getResourceURI");
        String surl = "";
        URI expResult = null;
        URI result = DataSetURI.getResourceURI(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getWebURL method, of class DataSetURI.
     */
    @Test
    public void testGetWebURL() {
        System.out.println("getWebURL");
        URI url = null;
        URL expResult = null;
        URL result = DataSetURI.getWebURL(url);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getDataSourceFormat method, of class DataSetURI.
     */
    @Test
    public void testGetDataSourceFormat() {
        System.out.println("getDataSourceFormat");
        URI uri = null;
        DataSourceFormat expResult = null;
        DataSourceFormat result = DataSetURI.getDataSourceFormat(uri);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getDataSourceFactory method, of class DataSetURI.
     */
    @Test
    public void testGetDataSourceFactory() throws Exception {
        System.out.println("getDataSourceFactory");
        URI uri = null;
        ProgressMonitor mon = null;
        DataSourceFactory expResult = null;
        DataSourceFactory result = DataSetURI.getDataSourceFactory(uri, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of parseParams method, of class DataSetURI.
     */
    @Test
    public void testParseParams() {
        System.out.println("parseParams");
        String params = "";
        LinkedHashMap expResult = null;
        LinkedHashMap result = DataSetURI.parseParams(params);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of formatParams method, of class DataSetURI.
     */
    @Test
    public void testFormatParams() {
        System.out.println("formatParams");
        Map parms = null;
        String expResult = "";
        String result = DataSetURI.formatParams(parms);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getInputStream method, of class DataSetURI.
     */
    @Test
    public void testGetInputStream_URL_ProgressMonitor() throws Exception {
        System.out.println("getInputStream");
        URL url = null;
        ProgressMonitor mon = null;
        InputStream expResult = null;
        InputStream result = DataSetURI.getInputStream(url, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getInputStream method, of class DataSetURI.
     */
    @Test
    public void testGetInputStream_URI_ProgressMonitor() throws Exception {
        System.out.println("getInputStream");
        URI uri = null;
        ProgressMonitor mon = null;
        InputStream expResult = null;
        InputStream result = DataSetURI.getInputStream(uri, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of toURL method, of class DataSetURI.
     */
    @Test
    public void testToURL() throws Exception {
        System.out.println("toURL");
        String surl = "";
        URL expResult = null;
        URL result = DataSetURI.toURL(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of toUri method, of class DataSetURI.
     */
    @Test
    public void testToUri() {
        System.out.println("toUri");
        String suri = "";
        URI expResult = null;
        URI result = DataSetURI.toUri(suri);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of fromUri method, of class DataSetURI.
     */
    @Test
    public void testFromUri() {
        System.out.println("fromUri");
        URI uri = null;
        String expResult = "";
        String result = DataSetURI.fromUri(uri);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getFile method, of class DataSetURI.
     */
    @Test
    public void testGetFile_URL_ProgressMonitor() throws Exception {
        System.out.println("getFile");
        URL url = null;
        ProgressMonitor mon = null;
        File expResult = null;
        File result = DataSetURI.getFile(url, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getFile method, of class DataSetURI.
     */
    @Test
    public void testGetFile_URI_ProgressMonitor() throws Exception {
        System.out.println("getFile");
        URI uri = null;
        ProgressMonitor mon = null;
        File expResult = null;
        File result = DataSetURI.getFile(uri, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getURI method, of class DataSetURI.
     */
    @Test
    public void testGetURI() throws Exception {
        System.out.println("getURI");
        String surl = "";
        URI expResult = null;
        URI result = DataSetURI.getURI(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getURL method, of class DataSetURI.
     */
    @Test
    public void testGetURL() throws Exception {
        System.out.println("getURL");
        String surl = "";
        URL expResult = null;
        URL result = DataSetURI.getURL(surl);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getCompletions method, of class DataSetURI.
     */
    @Test
    public void testGetCompletions() throws Exception {
        System.out.println("getCompletions");
        String surl = "";
        int carotpos = 0;
        ProgressMonitor mon = null;
        List expResult = null;
        List result = DataSetURI.getCompletions(surl, carotpos, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getHostCompletions method, of class DataSetURI.
     */
    @Test
    public void testGetHostCompletions() throws Exception {
        System.out.println("getHostCompletions");
        String surl = "";
        int carotpos = 0;
        ProgressMonitor mon = null;
        List expResult = null;
        List result = DataSetURI.getHostCompletions(surl, carotpos, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getFileSystemCompletions method, of class DataSetURI.
     */
    @Test
    public void testGetFileSystemCompletions() throws Exception {
        System.out.println("getFileSystemCompletions");
        String surl = "file:/home/jbf/ct/hudson/data.backup/cdf/po_hyd/2000/";
        int carotpos = surl.length();
        ProgressMonitor mon = new NullProgressMonitor();
        List result = DataSetURI.getFileSystemCompletions(surl, carotpos, mon);
        assertEquals(9, result.size());
    }

    /**
     * Test of getFactoryCompletions method, of class DataSetURI.
     */
    @Test
    public void testGetFactoryCompletions() throws Exception {
        System.out.println("getFactoryCompletions");
        String surl1 = "";
        int carotPos = 0;
        ProgressMonitor mon = null;
        List expResult = null;
        List result = DataSetURI.getFactoryCompletions(surl1, carotPos, mon);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of init method, of class DataSetURI.
     */
    @Test
    public void testInit() {
        System.out.println("init");
        DataSetURI.init();
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of main method, of class DataSetURI.
     */
    @Test
    public void testMain() {
        System.out.println("main");
        String[] args = null;
        DataSetURI.main(args);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}