
package org.autoplot.cdaweb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.components.DasProgressPanel;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.fsm.FileStorageModel;
import org.das2.util.AboutUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.CancelledOperationException;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Class for encapsulating the functions of the database
 * @author jbf
 */
public class CDAWebDB {

    private static final Logger logger= LoggerManager.getLogger("apdss.cdaweb");
    
    private static CDAWebDB instance=null;
    
    public static final String CDAWeb;
    static {
        if ( System.getProperty("cdawebHttps","true").equals("false") ) {
            CDAWeb = "http://cdaweb.gsfc.nasa.gov/";
        } else {
            // Note modern Javas are needed for https support.  
            // https will be required by Spring 2017.
            CDAWeb = "https://cdaweb.gsfc.nasa.gov/";
        }
    }

    public static final String dbloc= CDAWeb + "pub/catalogs/all.xml";
    //public static final String dbloc= "https://cdaweb.sci.gsfc.nasa.gov/%7Ecgladney/all.xml";
    
    //private String version;
    private Document document; // should consume ~ 2 MB
    private Map<String,String> ids;  // serviceproviderId,Id
    private long refreshTime=0;
    private final Map<String,String> bases= new HashMap();
    private final Map<String,String> tmpls= new HashMap();
    private Boolean online= null;

    public static synchronized CDAWebDB getInstance() {
        if ( instance==null ) {
            instance= new CDAWebDB();
        }
        return instance;
    }
    
    /**
     * returns true if the CDAWeb is on line.
     * @return true if the CDAWeb is on line.
     */
    public synchronized boolean isOnline() {
        if ( online==null ) {
            try {
                // get a file via http so we get a filesystem offline if we are at a hotel.
                // Note the file is small, and if the file is already downloaded, this will only result in a head request.
                DataSetURI.getFile( CDAWeb + "pub/software/cdawlib/AAREADME.txt", false, new NullProgressMonitor() );        
                online= true;
            } catch ( IOException ex ) {
                try {
                    if ( !AboutUtil.isJreVersionAtLeast("1.8.0_102") ) {
                        logger.warning("Java version is probably too old to connect to CDAWeb");
                    }
                } catch (ParseException ex1) {
                    logger.warning("Java version may be too old to connect to CDAWeb");
                }
                
                online= false;
            }
        }
        return online;
    }

    /**
     * refresh no more often than once per 10 minutes.  We don't need to refresh
     * often.  Note it only takes a few seconds to refresh, plus download time,
     * but we don't want to pound on the CDAWeb server needlessly.
     * @param mon
     * @throws java.io.IOException when reading dbloc file.
     */
    public synchronized void maybeRefresh( ProgressMonitor mon ) throws IOException {
        long t= System.currentTimeMillis();
        if ( t - refreshTime > 600000 ) { // 10 minutes
            refresh(mon);
            refreshTime= t;
        }
    }
//
//    public synchronized void refreshViaWebServices( ProgressMonitor mon ) throws IOException {
//        try {
//            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
//            mon.setProgressMessage("refreshing database");//TODO: is this working
//            mon.started();
//            mon.setTaskSize(30);
//            mon.setProgressMessage("call WS for listing" );
//            
//        } catch (SAXException ex) {
//            logger.log(Level.SEVERE, ex.getMessage(), ex);
//        } catch (ParserConfigurationException | URISyntaxException ex) {
//            logger.log(Level.SEVERE, ex.getMessage(), ex);
//        }
//        
//    }
//    
    /**
     * Download and parse the all.xml to create a database of available products.
     * @param mon progress monitor for the task
     * @throws IOException when reading dbloc file.
     */
    public synchronized void refresh( ProgressMonitor mon ) throws IOException {
        try {

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            mon.setProgressMessage("refreshing database");
            mon.started();
            mon.setTaskSize(30);
            mon.setProgressMessage("downloading file "+dbloc );

            //String lookfor=  "ftp://cdaweb.gsfc.nasa.gov/pub/istp/";
            //String lookfor2= "ftp://cdaweb.gsfc.nasa.gov/pub/cdaweb_data";

            logger.log( Level.FINE, "downloading file {0}", dbloc);
            File f= DataSetURI.getFile( new URI(dbloc), SubTaskMonitor.create( mon, 0, 10 ) ) ; // bug 3055130 okay
            FileInputStream fin=null;
            InputStream altin= null;
            try {
                fin= new FileInputStream( f );

                InputSource source = new InputSource( fin );

                mon.setTaskProgress(10);
                mon.setProgressMessage("parsing file "+dbloc );
                document = builder.parse(source);

                //XPath xp = XPathFactory.newInstance().newXPath();
                //version= xp.evaluate( "/sites/datasite/@version", document );

                mon.setTaskProgress(20);
                mon.setProgressMessage("reading IDs");

                altin= CDAWebDB.class.getResourceAsStream("/org/autoplot/cdaweb/filenames_alt.txt") ;
                if ( altin==null ) {
                    throw new RuntimeException("Unable to locate /org/autoplot/cdaweb/filenames_alt.txt");
                }
                try (BufferedReader rr = new BufferedReader( new InputStreamReader( altin ) )) {
                    String ss= rr.readLine();
                    while ( ss!=null ) {
                        int i= ss.indexOf("#");
                        if ( i>-1 ) ss= ss.substring(0,i);
                        if ( ss.trim().length()>0 ) {
                            String[] sss= ss.split("\\s+");
                            String naming= sss[2];
                            naming= naming.replaceAll("\\%", "\\$");
                            naming= naming.replaceAll("\\?","."); //TODO: this . happens to match one character.  This may change.
                            tmpls.put( sss[0], naming );
                            if ( sss[1].length()>1 ) bases.put( sss[0], sss[1] );
                        }
                        ss= rr.readLine();
                    }
                }

                refreshServiceProviderIds(mon.getSubtaskMonitor(20,30,"process document"));
                mon.setTaskProgress(30);

            } finally {
                if ( fin!=null ) fin.close();
                if ( altin!=null ) altin.close();
                mon.finished();
            }
        //} catch (XPathExpressionException ex) {
        //    logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } catch (ParserConfigurationException | URISyntaxException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }

    /**
     * isolate the code that resolves which files need to be accessed, so that
     * we can use the web service when it is available.
     * @param spid the service provider id, like "AC_H2_CRIS"
     * @param tr the timerange
     * @param useWebServiceHint null means no preference, or "T", or "F" means use file template found in all.xml.
     * @param mon progress monitor for the download
     * @return array of strings, with filename|startTime|endTime
     * @throws java.io.IOException 
     * @throws org.das2.util.monitor.CancelledOperationException 
     */
    public String[] getFiles( String spid, DatumRange tr, String useWebServiceHint, ProgressMonitor mon ) throws IOException, CancelledOperationException {
        boolean useService= !( "F".equals(useWebServiceHint) );
        String[] result;
        logger.log(Level.FINE, "getFiles {0} {1} ws={2}", new Object[]{spid, tr, useService});
        if ( useService ) {
            String[] ff;
            try {
                ff = getOriginalFilesAndRangesFromWebService(spid, tr, mon );
            } catch ( IOException ex ) {
                return getFiles( spid, tr, "F", mon );
            }
            List<String> resultList= new ArrayList(ff.length);
            for ( String ff1 : ff ) {
                try {
                    String[] ss = ff1.split("\\|");
                    DatumRange dr= DatumRangeUtil.parseTimeRange( ss[1]+ " to "+ ss[2] );
                    if (dr.intersects(tr)) {
                        resultList.add(ff1);
                    }
                }catch (ParseException ex) {
                    Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            result= resultList.toArray(new String[resultList.size()]);
                    
        } else {
            try {
                String tmpl= getNaming( spid );
                String base= getBaseUrl( spid );
                
                logger.log( Level.FINE, "tmpl={0}", tmpl);
                logger.log( Level.FINE, "base={0}", base);
                logger.log(Level.FINE, "{0}/{1}", new Object[]{base, tmpl});
                
                FileSystem fs= FileSystem.create( new URI( base ) ); // bug3055130 okay
                FileStorageModel fsm= FileStorageModel.create( fs, tmpl );
                
                String[] ff= fsm.getBestNamesFor(tr,mon);
                result= new String[ ff.length ];
                TimeParser tp= TimeParser.create("$Y-$m-$dT$H:$M:$SZ");
                for ( int i=0; i<ff.length; i++ ) {
                    DatumRange tr1= fsm.getRangeFor(ff[i]);
                    result[i]= base + "/" + ff[i] + "|" + tp.format(tr1.min()) + "|" + tp.format(tr1.max());
                }
                
            } catch (URISyntaxException ex) {
                throw new IOException(ex);
            }
        }
        logger.log(Level.FINER, "found {0} files.", result.length);
        return result;
    }
    
    /**
     * get the list of files from the web service
     * @param spid the service provider id, like "AC_H2_CRIS"
     * @param tr the timerange constraint
     * @param mon progress monitor
     * @return  filename|startTime|endTime
     * @throws java.io.IOException
     * @throws org.das2.util.monitor.CancelledOperationException
     */    
    public static String[] getOriginalFilesAndRangesFromWebService(String spid, DatumRange tr, ProgressMonitor mon ) throws IOException, CancelledOperationException {
        TimeParser tp= TimeParser.create("$Y$m$dT$H$M$SZ");
        String tstart= tp.format(tr.min(),tr.min());
        String tstop= tp.format(tr.max(),tr.max());

        InputStream ins= null;

        try {
            
            long t0= System.currentTimeMillis();
            
            URL url = new URL(String.format( CDAWeb + "WS/cdasr/1/dataviews/sp_phys/datasets/%s/orig_data/%s,%s", spid, tstart, tstop));
            logger.fine(url.toString());
            Logger loggerUrl= org.das2.util.LoggerManager.getLogger( "das2.url" );
            URLConnection urlc;

            urlc = url.openConnection();
            urlc.setConnectTimeout(60000);
            urlc.setReadTimeout(60000);
            //urlc= HttpUtil.checkRedirect(urlc);
            loggerUrl.log(Level.FINE,"GET data from CDAWeb {0}", urlc.getURL() );

            Document doc;
            
            synchronized ( CDAWebDB.class ) { 
                ins= urlc.getInputStream();
                InputSource source = new InputSource( ins );
            
                DocumentBuilder builder;
                builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                doc = builder.parse(source);
            
                ins.close();
            }
            
            XPath xp = XPathFactory.newInstance().newXPath();

            NodeList set = (NodeList) xp.evaluate( "/DataResult/FileDescription", doc.getDocumentElement(), javax.xml.xpath.XPathConstants.NODESET );

            mon.setTaskSize( set.getLength() );
            mon.started();
            
            // 2019-04-29 suddenly getting dumplicate entries from 
            // https://cdaweb.gsfc.nasa.gov/WS/cdasr/1/dataviews/sp_phys/datasets/THA_L2_ESA/orig_data/20190405T000000Z,20190406T000000Z
            // See http://jfaden.net/jenkins/job/autoplot-test142.
            
            ArrayList<String> r= new ArrayList<>();
            for ( int i=0; i<set.getLength(); i++ ) {
                if ( mon.isCancelled() ) throw new CancelledOperationException("cancel during parse");
                mon.setTaskProgress(i);
                Node item= set.item(i);
                String s= xp.evaluate("Name/text()",item) + "|"+ xp.evaluate("StartTime/text()",item)+ "|" + xp.evaluate("EndTime/text()",item );
                if ( !r.contains(s) ) r.add(s);
            }
            
            String[] result= r.toArray( new String[ r.size() ] );
            
            long ms= System.currentTimeMillis() - t0;            
            logger.log(Level.FINE, "get files and ranges for {0} for {1} in {2}ms", new Object[]{spid, tr, ms});
     
            return result;

        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw ex;
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException | XPathExpressionException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } finally {
            mon.finished();
            if ( ins!=null ) ins.close();
        }        
    }
    
    /**
     * get the list of files from the web service
     * @param spid the id like "AC_H2_CRIS"
     * @param tr the timerange constraint
     * @return  filename|startTime|endTime
     * @throws java.io.IOException
     */
    public static String[] getFilesAndRangesFromWebService(String spid, DatumRange tr) throws IOException {
        TimeParser tp= TimeParser.create("$Y$m$dT$H$M$SZ");
        String tstart= tp.format(tr.min(),tr.min());
        String tstop= tp.format(tr.max(),tr.max());

        InputStream ins= null;

        long t0= System.currentTimeMillis();
        
        try {
            URL url = new URL(String.format( CDAWeb + "WS/cdasr/1/dataviews/sp_phys/datasets/%s/data/%s,%s/ALL-VARIABLES?format=cdf", spid, tstart, tstop));
            URLConnection urlc;

            Logger loggerUrl= org.das2.util.LoggerManager.getLogger( "das2.url" );
            loggerUrl.log(Level.FINE,"openConnection {0}", url);
            
            urlc = url.openConnection();
            urlc.setConnectTimeout(FileSystem.settings().getConnectTimeoutMs());
            urlc.setReadTimeout(FileSystem.settings().getConnectTimeoutMs()*2);
            //urlc= HttpUtil.checkRedirect(urlc);
            
            loggerUrl.log(Level.FINE,"getInputStream {0}", url);
            
            Document doc;
            synchronized ( CDAWebDB.class ) {
                ins= urlc.getInputStream();
                InputSource source = new InputSource( ins );
            
                DocumentBuilder builder;
                builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                doc = builder.parse(source);
            
                ins.close();
            }            
            
            XPath xp = XPathFactory.newInstance().newXPath();

            NodeList set = (NodeList) xp.evaluate( "/DataResult/FileDescription", doc.getDocumentElement(), javax.xml.xpath.XPathConstants.NODESET );

            String[] result= new String[ set.getLength() ];
            for ( int i=0; i<set.getLength(); i++ ) {
                Node item= set.item(i);
                result[i]= xp.evaluate("Name/text()",item) + "|"+ xp.evaluate("StartTime/text()",item)+ "|" + xp.evaluate("EndTime/text()",item );
            }
            
            //((HttpURLConnection)urlc).disconnect(); // https://sourceforge.net/p/autoplot/bugs/1754/
            //Do not call after trivial calls, so that if all the data is downloaded, then the connections are reused.

            long ms= System.currentTimeMillis() - t0;            
            logger.log(Level.FINE, "get files and ranges for {0} for {1} in {2}ms", new Object[]{spid, tr, ms});
            
            return result;

        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw ex;
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException | XPathExpressionException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        } finally {
            if ( ins!=null ) ins.close();
        }
    }

    /**
     * Matlab uses net.sf.saxon.xpath.XPathEvaluator by default, so we explicitly look for the Java 6 one.
     * @return com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl, probably.
     */
    private static XPathFactory getXPathFactory() {
        return DataSourceUtil.getXPathFactory();
    }
    
    /**
     * returns the filename convention for spid, found in all.xml at /sites/datasite/dataset[@serviceprovider_ID='%s']/access
     * For AC_H2_CRIS, this combines the subdividedby and filenaming properties to get %Y/ac_h2_cris_%Y%m%d_?%v.cdf
     * @param spid the id like "AC_H2_CRIS"
     * @return URI template like "%Y/ac_h2_cris_%Y%m%d_?%v.cdf"
     * @throws IOException
     */
    public String getNaming( String spid ) throws IOException {
        if ( document==null ) {
            throw new IllegalArgumentException("document has not been read, refresh must be called first");
        }

        try {
            spid= spid.toUpperCase();
            if ( tmpls.containsKey(spid) ) {
                return tmpls.get(spid);
            }
            XPathFactory xpf= getXPathFactory();
            XPath xp = xpf.newXPath();
            
            logger.log( Level.FINER, "getting node for {0}", spid );
            Node node = (Node) xp.evaluate( String.format( "/sites/datasite/dataset[@serviceprovider_ID='%s']/access", spid), document, XPathConstants.NODE );
            if ( node==null ) {
                throw new IOException("unable to find node for "+spid + " in "+ dbloc );
            }
            NamedNodeMap attrs= node.getAttributes();
            String subdividedby=attrs.getNamedItem("subdividedby").getTextContent();
            String filenaming= attrs.getNamedItem("filenaming").getTextContent();
            
            logger.log( Level.FINER, "subdividedby={0}", subdividedby);
            logger.log( Level.FINER, "filenaming={0}", filenaming);
            
            if ( filenaming.contains("%Q") ) {
                filenaming= filenaming.replaceFirst("%Q.*\\.cdf", "?%(v,sep).cdf"); // templates don't quite match
            }
            
            String naming;
            if ( subdividedby.equals("None") ) {
                naming= filenaming;
            } else {
                naming= subdividedby + "/" + filenaming;
            }
            naming= naming.replaceAll("\\%", "\\$");
            return naming;

        } catch (XPathExpressionException ex) {
            throw new IOException("unable to read node "+spid);
        }
    }

    /**
     * returns the base URL.  FTP urls in the all.xml file are converted to HTTP by replacing
     * "ftp://cdaweb.gsfc.nasa.gov/pub/istp/" with  "https://cdaweb.gsfc.nasa.gov/sp_phys/data/"
     * @param spid the id like "AC_H2_CRIS"
     * @return the base URL like https://cdaweb.gsfc.nasa.gov/sp_phys/data/ace/cris/level_2_cdaweb/cris_h2

     * @throws IOException
     */
    public String getBaseUrl( String spid ) throws IOException {
        if ( document==null ) {
            throw new IllegalArgumentException("document has not been read, refresh must be called first");
        }
        try {
            spid= spid.toUpperCase();
            if ( bases.containsKey(spid) ) {
                return bases.get(spid);
            }
            XPathFactory xpf= getXPathFactory();
            XPath xp = xpf.newXPath();
            String url= (String)xp.evaluate( String.format( "/sites/datasite/dataset[@serviceprovider_ID='%s']/access/URL/text()", spid), document, XPathConstants.STRING );
            url= url.trim();
            if ( url.contains(" ") ) {
                String[] ss= url.split("\\s+"); // See saber/l1bv7
                url= ss[ss.length-1];
            }
            if ( url.startsWith("/tower3/public/pub/istp/") ) {
                url= "ftp://cdaweb.gsfc.nasa.gov/" + url.substring("/tower3/public/".length() );
            }
            if ( url.startsWith("/tower4/public/pub/istp/") ) {
                url= "ftp://cdaweb.gsfc.nasa.gov/" + url.substring("/tower4/public/".length() );
            }
            if ( url.startsWith("/tower5/public/pub/istp/") ) {
                url= "ftp://cdaweb.gsfc.nasa.gov/" + url.substring("/tower5/public/".length() );
            }
            if ( url.startsWith("/tower6/public/pub/istp/") ) {
                url= "ftp://cdaweb.gsfc.nasa.gov/" + url.substring("/tower6/public/".length() );
            }
            if ( url.startsWith("/tower3/private/cdaw_data/cluster_private/st") ) {  //get all the cluster stuff
                url= "ftp://cdaweb.gsfc.nasa.gov/" + url.substring("/tower3/private/".length() );
            }            
            String lookfor= "ftp://cdaweb.gsfc.nasa.gov/pub/";
            if ( url.startsWith(lookfor) ) {
                url= CDAWeb + "pub/" + url.substring(lookfor.length());
            }            
            return url;

        } catch (XPathExpressionException ex) {
            throw new IOException("unable to read node "+spid);
        }

    }

    /**
     * return a range of a file that could be plotted.  Right now, this
     * just creates a FSM and gets a file.
     * @param spid the id like "AC_H2_CRIS"
     * @return
     * @throws IOException
     */
    public String getSampleTime( String spid ) throws IOException {
        try {
            String last = getTimeRange(spid);
            int i = last.indexOf(" to ");
            last = last.substring(i + 4);

            String tmpl= getNaming( spid );
            String base= getBaseUrl( spid );

            Datum width;
            FileSystem fs;
            try {
                fs = FileSystem.create(new URI(base));
                FileStorageModel fsm= FileStorageModel.create( fs, tmpl );
                String ff= fsm.getRepresentativeFile( new NullProgressMonitor() );
                if ( ff!=null ) {
                    return fsm.getRangeFor(ff).toString();
                } else {
                    width= Units.hours.createDatum(24);
                }

            } catch (URISyntaxException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                width= Units.hours.createDatum(24);

            }

            Datum d = TimeUtil.prevMidnight(TimeUtil.create(last)); // TODO: getFilename, when $v is handled
            d= d.subtract( width );
            Datum d1= d.add( width );
            DatumRange dr= new DatumRange( d, d1 );
            return dr.toString();
            
        } catch (ParseException ex) {
            throw new IOException(ex.toString());
        }
    }

    /**
     * return a sample file from the dataset.
     * @param spid the id like "AC_H2_CRIS"
     * @return a downloadable file like http://cdaweb.gsfc.nasa.gov/pub/data/ace/cris/level_2_cdaweb/cris_h2/2015/ac_h2_cris_20151115_v06.cdf
     * @throws IOException 
     */
    public String getSampleFile( String spid ) throws IOException {

        String tmpl= getNaming( spid );
        String base= getBaseUrl( spid );

        FileSystem fs;
        try {
            fs = FileSystem.create(new URI(base));
            FileStorageModel fsm= FileStorageModel.create( fs, tmpl );
            String ff= fsm.getRepresentativeFile( new NullProgressMonitor() );
            if ( ff!=null ) {
                return base + "/" + ff;
            } else {
                throw new IllegalArgumentException("unable to find sample file");
            }

        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);

        }
    }
    
    /**
     * return the timerange spanning the availability of the dataset.
     * @param spid service provider id.
     * @return the time range (timerange_start, timerange_stop) for the dataset.
     * @throws IOException
     */
    public String getTimeRange( String spid ) throws IOException {
        if ( document==null ) {
            throw new IllegalArgumentException("document has not been read, refresh must be called first");
        }
        try {
            spid= spid.toUpperCase();
            XPath xp = getXPathFactory().newXPath();
            Node node = (Node) xp.evaluate( String.format( "/sites/datasite/dataset[@serviceprovider_ID='%s']", spid), document, XPathConstants.NODE );
            if ( node==null ) {
                throw new IllegalArgumentException("unable to find node for serviceprovider_ID="+spid);
            }
            NamedNodeMap attrs= node.getAttributes();
            String start=attrs.getNamedItem("timerange_start").getTextContent();
            String stop= attrs.getNamedItem("timerange_stop").getTextContent();

            return start + " to " + stop;

        } catch (XPathExpressionException ex) {
            throw new IOException("unable to read node "+spid);
        }
    }

    /**
     * return the name of a master file, which is used to override the metadata
     * of the daily files.
     * @param ds the name, like A1_K0_MPA
     * @param p progress monitor
     * @return the name (http://...) of the master file to use, which may be one of the data files.
     * @throws IOException 
     */
    public String getMasterFile( String ds, ProgressMonitor p ) throws IOException {
        logger.log(Level.FINE, "getMasterFile for {0}, looking for v02 then v01.", ds);
        p.started();
        String master; //= CDAWeb + "pub/software/cdawlib/0MASTERS/"+ds.toLowerCase()+"_00000000_v01.cdf";
        
        FileSystem mastersFs= FileSystem.create(CDAWeb + "pub/software/cdawlib/0MASTERS/" );
        //String s= ds.toLowerCase()+"_$x_v$v.cdf"; //FSM method takes 500+ milliseconds.

        FileObject fo= mastersFs.getFileObject( ds.toLowerCase()+"_00000000_v02.cdf" );
        if ( !fo.exists() ) fo= mastersFs.getFileObject( ds.toLowerCase()+"_00000000_v01.cdf" );
        if ( fo.exists() ) {
            master= mastersFs.getRootURI().toString() + fo.getNameExt();
        } else {
            master= null;
        }
        
        if ( master!=null ) {
            logger.log(Level.FINER, "found master file: {0}", master );
            p.finished();
        } else {
            // datasets don't have to have masters.  In this case we use the default timerange to grab a file as the master.

            String tmpl= getNaming(ds.toUpperCase());
            String base= getBaseUrl(ds.toUpperCase());
            URI baseUri;
            baseUri = DataSetURI.toUri(base);
            FileSystem fs= FileSystem.create( baseUri );
            
            FileStorageModel fsm= FileStorageModel.create( fs, tmpl );

            String avail= CDAWebDB.getInstance().getSampleTime(ds);
            DatumRange dr;
            try {
                dr = DatumRangeUtil.parseTimeRange(avail);
            } catch (ParseException ex1) {
                logger.log(Level.SEVERE, ex1.getMessage(), ex1);
                master= fsm.getRepresentativeFile(p.getSubtaskMonitor("get representative file"));
                dr= fsm.getRangeFor(master);
            }
            //String[] files1= getFilesAndRangesFromWebService( ds, dr );

            String[] files= fsm.getBestNamesFor( dr, p.getSubtaskMonitor("get best names for") );
            if ( files.length==0 ) {
                master= fsm.getRepresentativeFile(p.getSubtaskMonitor("get representative file"));
                if ( master==null ) {
                    throw new FileNotFoundException("unable to find any files to serve as master file in "+fsm );
                } else {
                    master= fs.getRootURI().toString() + master;
                }
            } else {
                master= fs.getRootURI().toString() + files[0];
            }
            logger.log(Level.FINER, "using arbitary representative as master file: {0}", master );
            p.finished();
        }
        p.setProgressMessage(" ");
        return master;
    }

    /**
     * return the URL used to access the files, or null if none exists.
     * @param id the service provider id, such as "mms1_edi_srvy_l2_amb-pm2"
     * @param dataset the node from the all.xml file.
     * @return the URL or null.
     */
    private String getURL( String id, Node dataset ) {
        NodeList kids= dataset.getChildNodes();
        String lookfor= "ftp://cdaweb.gsfc.nasa.gov/pub/istp/";
        String lookfor2= "ftp://cdaweb.gsfc.nasa.gov/pub/cdaweb_data";
//  http://cdaweb.gsfc.nasa.gov/pub/catalogs/all.xml  Sept18 changeover
        for ( int j=0; j<kids.getLength(); j++ ) {
            Node childNode= kids.item(j);
            if ( childNode.getNodeName().equals("access") ) {
                NodeList kids2= childNode.getChildNodes();
                for ( int k=0; k<kids2.getLength(); k++ ) {
                    if ( kids2.item(k).getNodeName().equals("URL") ) {
                        if ( kids2.item(k).getFirstChild()==null ) {
                            logger.log(Level.FINE, "URL is missing for {0}, data cannot be accessed.", id);
                            return null;
                        }
                        String url= kids2.item(k).getFirstChild().getTextContent().trim();
                        if ( url.startsWith( lookfor ) ) {
                            // "ftp://cdaweb.gsfc.nasa.gov/pub/istp/ace/mfi_h2"
                            //  http://cdaweb.gsfc.nasa.gov/istp_public/data/
                            url= CDAWeb + "sp_phys/data/" + url.substring(lookfor.length());
                        }
                        if ( url.startsWith(lookfor2) ) {
                            url= CDAWeb + "sp_phys/data/" + url.substring(lookfor2.length());
                        }
                        return url;
                    }
                }
            }
        }
        return null;
    }

    private String getDescription( Node dataset ) {
        NodeList kids= dataset.getChildNodes();
        for ( int j=0; j<kids.getLength(); j++ ) {
            Node childNode= kids.item(j);
            if ( childNode.getNodeName().equals("description") ) {
                NamedNodeMap kids2= childNode.getAttributes();
                Node shortDesc= kids2.getNamedItem("short");
                if ( shortDesc!=null ) {
                    return shortDesc.getNodeValue();
                } else {
                    return null;
                }
            }
        }
        return null;
    }
    
    /**
     * return the filenames for the dataset, so we can check for .cdf.
     * @param dataset dataset node, such as &lt;dataset ID="ac_h2_cris_cdaweb" ...&gt;
     * @return the filename, such as ac_h2_cris_%Y%m%d_%Q.cdf
     */
    private String getFilenaming( Node dataset ) {
        NodeList kids= dataset.getChildNodes();
        for ( int j=0; j<kids.getLength(); j++ ) {
            Node childNode= kids.item(j);
            if ( childNode.getNodeName().equals("access") ) {
                NamedNodeMap kids2= childNode.getAttributes();
                Node shortDesc= kids2.getNamedItem("filenaming");
                if ( shortDesc!=null ) {
                    return shortDesc.getNodeValue();
                } else {
                    return null;
                }
            }
        }
        return null;
    }
    
    /**
     * @return Map from serviceproviderId to description
     */
    public Map<String,String> getServiceProviderIds() {
        return ids;
    }

    /**
     * return the list of IDs that this reader can consume.
     * We apply a number of constraints: <ol>
     * <li> files must end in .cdf
     * <li> timerange_start and timerange_stop must be ISO8601 times.
     * <li> URL must start with a /, and may not be another website.
     * </ol>
     * @throws IOException
     */
    private void refreshServiceProviderIds( ProgressMonitor mon ) throws IOException {
        if ( document==null ) {
            throw new IllegalArgumentException("document has not been read, refresh must be called first");
        }
        try {
            XPath xp = getXPathFactory().newXPath();
            NodeList nodes = (NodeList) xp.evaluate( "//sites/datasite/dataset", document, XPathConstants.NODESET );

            Map<String,String> result= new LinkedHashMap<>();

            mon.setTaskSize(nodes.getLength());
            mon.started();
            
            for ( int i=0; i<nodes.getLength(); i++ ) {
                mon.setTaskProgress(i);
                Node node= nodes.item(i);
                NamedNodeMap attrs= node.getAttributes();
                try {
                    String st= attrs.getNamedItem("timerange_start").getTextContent();
                    String en= attrs.getNamedItem("timerange_stop").getTextContent();
                    //String nssdc_ID= attrs.getNamedItem("nssdc_ID").getTextContent();                       
                    if ( st.length()>1 && Character.isDigit(st.charAt(0))
                            && en.length()>1 && Character.isDigit(en.charAt(0))
                            //&& nssdc_ID.contains("None") ) {
                             ) {
                        String name= attrs.getNamedItem("serviceprovider_ID").getTextContent();
                        String url= getURL(name,node);
                        if ( url!=null && 
                                ( url.startsWith( CDAWeb ) ||
                                url.startsWith("ftp://cdaweb.gsfc.nasa.gov" ) ) && !url.startsWith("/tower3/private" ) ) {
                            String filenaming= getFilenaming(node);
                            String s=attrs.getNamedItem("serviceprovider_ID").getTextContent();
                            if ( filenaming.endsWith(".cdf") ) {
                                String desc= getDescription(node);
                                //String sid=attrs.getNamedItem("ID").getTextContent();
                                result.put(s,desc);
                            } else if ( filenaming.endsWith(".nc" ) ) {
                                if ( !name.contains("FORMOSAT") ) { // GOLD_L2_ON2 missing visad library -- not sure why.
                                    logger.log(Level.FINE, "ignoring {0} because .nc file is not supported", s);
                                }
                                String desc= getDescription(node);
                                //String sid=attrs.getNamedItem("ID").getTextContent();
                                result.put(s,desc);                                
                            } else {
                                logger.log(Level.FINE, "ignoring {0} because files do not end in .cdf or .nc", s);
                            }
                        }
                    }
                } catch ( DOMException ex2 ) {
                    logger.log( Level.WARNING, "exception", ex2 );
                }
            }
            mon.finished();

            ids= result;

        } catch (XPathExpressionException ex) {
            logger.log( Level.WARNING, "serviceprovider_IDs exception", ex );
            throw new IOException("unable to read serviceprovider_IDs");
        }

    }

    /**
     * 4.2 seconds before getting description.  After too!
     * @param args
     * @throws IOException
     * @throws java.text.ParseException
     */
    public static void main( String [] args ) throws IOException, ParseException {
        CDAWebDB db= getInstance();

        long t0= System.currentTimeMillis();

        String[] files;

        db.refresh( DasProgressPanel.createFramed("refreshing database") );

        System.err.println( db.getBaseUrl("AC_H3_CRIS") );
        System.err.println( db.getNaming("AC_H3_CRIS") );
        FileStorageModel fsm= FileStorageModel.create( FileSystem.create(db.getBaseUrl("AC_H3_CRIS")), db.getNaming("AC_H3_CRIS") );
        files= fsm.getBestNamesFor( DatumRangeUtil.parseTimeRange( "20110601-20110701" ), new NullProgressMonitor() );
        for ( String s: files ) {
            System.err.println(s); //logger ok
        }

        db.getSampleTime("I1_AV_OTT"); // empty trailing folder 1984 caused problem before 20120525.
        db.getSampleTime("IA_K0_ENF"); // no files...

        files= db.getFilesAndRangesFromWebService( "AC_H0_MFI", DatumRangeUtil.parseTimeRange( "20010101T000000Z-20010131T000000Z" ) );
        for ( String s: files ) {
            System.err.println(s); //logger ok
        }

        files= db.getFilesAndRangesFromWebService( "TIMED_L1B_SABER", DatumRangeUtil.parseTimeRange( "2002-01-26" ) );
        for ( String s: files ) {
            System.err.println(s); //logger ok
        }
        
        Map<String,String> ids= db.getServiceProviderIds( );

        for ( Entry<String,String> e: ids.entrySet() ) {
            System.err.println( e.getKey() + ":\t" + e.getValue() ); //logger ok
        }
        System.err.println( ids.size() ); //logger ok
        System.err.println( db.getNaming( "AC_H0_MFI" )  ); //logger ok
        System.err.println( db.getTimeRange( "AC_H0_MFI" )  ); //logger ok
        
        System.err.println( "Timer: " + ( System.currentTimeMillis() - t0 ) ); //logger ok

    }
}
