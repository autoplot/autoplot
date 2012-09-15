/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import org.virbo.datasource.DataSetURI;
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

    private static CDAWebDB instance=null;
    private static String dbloc= "http://cdaweb.gsfc.nasa.gov/pub/catalogs/all.xml";

    //private String version;
    private Document document; // should consume ~ 2 MB
    private Map<String,String> ids;  // serviceproviderId,Id
    private long refreshTime=0;
    private Map<String,String> bases= new HashMap();
    private Map<String,String> tmpls= new HashMap();

    public static synchronized CDAWebDB getInstance() {
        if ( instance==null ) {
            instance= new CDAWebDB();
        }
        return instance;
    }

    /**
     * refresh no more often than once per 10 minutes.  We don't need to refresh
     * often.  Note it only takes a few seconds to refresh, plus download time,
     * but we don't want to pound on the CDAWeb server needlessly.
     * @param mon
     */
    public synchronized void maybeRefresh( ProgressMonitor mon ) throws IOException {
        long t= System.currentTimeMillis();
        if ( t - refreshTime > 600000 ) { // 10 minutes
            refresh(mon);
            refreshTime= t;
        }
    }

    public synchronized void refresh( ProgressMonitor mon ) throws IOException {
        try {

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            mon.setProgressMessage("refreshing database");//TODO: is this working
            mon.started();
            mon.setTaskSize(3);
            mon.setProgressMessage("downloading file "+dbloc );

            String lookfor=  "ftp://cdaweb.gsfc.nasa.gov/pub/istp/";
            String lookfor2= "ftp://cdaweb.gsfc.nasa.gov/pub/cdaweb_data";
            File f= DataSetURI.getFile( new URI(dbloc), SubTaskMonitor.create( mon, 0, 1 ) ) ;
            FileInputStream fin=null;
            InputStream altin= null;
            try {
                fin= new FileInputStream( f );

                InputSource source = new InputSource( fin );

                mon.setTaskProgress(1);
                mon.setProgressMessage("parsing file "+dbloc );
                document = builder.parse(source);

                //XPath xp = XPathFactory.newInstance().newXPath();
                //version= xp.evaluate( "/sites/datasite/@version", document );

                mon.setTaskProgress(2);
                mon.setProgressMessage("reading IDs");

                altin= CDAWebDB.class.getResourceAsStream("/org/autoplot/cdaweb/filenames_alt.txt") ;
                if ( altin==null ) {
                    throw new RuntimeException("Unable to locate /org/autoplot/cdaweb/filenames_alt.txt");
                }
                BufferedReader rr= new BufferedReader( new InputStreamReader( altin ) );
                String ss= rr.readLine();
                while ( ss!=null ) {
                    int i= ss.indexOf("#");
                    if ( i>-1 ) ss= ss.substring(0,i);
                    if ( ss.trim().length()>0 ) {
                        String[] sss= ss.split("\\s+");
                        if ( sss[1].startsWith(lookfor) ) {
                            sss[1]= "http://cdaweb.gsfc.nasa.gov/sp_phys/data/" + sss[1].substring(lookfor.length()); //TODO: this location will change eventually
                        }
                        if ( sss[1].startsWith(lookfor2) ) {
                            sss[1]= "http://cdaweb.gsfc.nasa.gov/sp_phys/data/" + sss[1].substring(lookfor2.length());
                        }
                        bases.put( sss[0], sss[1] );
                        tmpls.put( sss[0], sss[2] );
                    }
                    ss= rr.readLine();
                }
                rr.close();

                refreshServiceProviderIds();
                mon.setTaskProgress(3);

            } finally {
                if ( fin!=null ) fin.close();
                if ( altin!=null ) altin.close();
                mon.finished();
            }
        //} catch (XPathExpressionException ex) {
        //    Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
        } catch (URISyntaxException ex) {
            Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * get the list of files from the web service
     * @param toUpperCase
     * @param tr
     * @return  <filename>|<startTime>|<endTime>
     */
    String[] getFilesAndRanges(String spid, DatumRange tr) throws IOException {
        TimeParser tp= TimeParser.create("$Y$m$dT$H$M$SZ");
        String tstart= tp.format(tr.min(),tr.min());
        String tstop= tp.format(tr.max(),tr.max());

        InputStream ins= null;

        try {
            URL url = new URL(String.format("http://cdaweb.gsfc.nasa.gov/WS/cdasr/1/dataviews/sp_phys/datasets/%s/data/%s,%s/ALL-VARIABLES?format=cdf", spid, tstart, tstop));
            URLConnection urlc;
            urlc = url.openConnection();
            urlc.setConnectTimeout(300);

            ins= urlc.getInputStream();
            InputSource source = new InputSource( ins );

            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc;
            doc = builder.parse(source);

            XPath xp = XPathFactory.newInstance().newXPath();

            NodeList set = (NodeList) xp.evaluate( "/DataResult/FileDescription", doc.getDocumentElement(), javax.xml.xpath.XPathConstants.NODESET );

            String[] result= new String[ set.getLength() ];
            for ( int i=0; i<set.getLength(); i++ ) {
                Node item= set.item(i);
                result[i]= xp.evaluate("Name/text()",item) + "|"+ xp.evaluate("StartTime/text()",item)+ "|" + xp.evaluate("EndTime/text()",item );
            }

            return result;

        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw ex;
        } catch (SAXException ex) {
            Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } catch (ParserConfigurationException ex) {
            Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } catch (XPathExpressionException ex) {
            Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } finally {
            if ( ins!=null ) ins.close();
        }
    }

    public String getNaming( String spid ) throws IOException {
        if ( document==null ) {
            throw new IllegalArgumentException("document has not been read, refresh must be called first");
        }
        try {
            spid= spid.toUpperCase();
            if ( tmpls.containsKey(spid) ) {
                return tmpls.get(spid);
            }
            XPath xp = XPathFactory.newInstance().newXPath();
            Node node = (Node) xp.evaluate( String.format( "/sites/datasite/dataset[@serviceprovider_ID='%s']/access", spid), document, XPathConstants.NODE );
            NamedNodeMap attrs= node.getAttributes();
            String subdividedby=attrs.getNamedItem("subdividedby").getTextContent();
            String filenaming= attrs.getNamedItem("filenaming").getTextContent();
            filenaming= filenaming.replace("%Q", "?%v"); // templates don't quite match
            if ( subdividedby.equals("None") ) {
                return filenaming;
            } else {
                return subdividedby + "/" + filenaming;
            }

        } catch (XPathExpressionException ex) {
            throw new IOException("unable to read node "+spid);
        }
    }

    public String getBaseUrl( String spid ) throws IOException {
        if ( document==null ) {
            throw new IllegalArgumentException("document has not been read, refresh must be called first");
        }
        try {
            spid= spid.toUpperCase();
            if ( bases.containsKey(spid) ) {
                return bases.get(spid);
            }
            XPath xp = XPathFactory.newInstance().newXPath();
            String url= (String)xp.evaluate( String.format( "/sites/datasite/dataset[@serviceprovider_ID='%s']/access/URL/text()", spid), document, XPathConstants.STRING );
            url= url.trim();
            if ( url.contains(" ") ) {
                String[] ss= url.split("\\s+"); // See saber/l1bv7
                url= ss[ss.length-1];
            }
            if ( url.startsWith("/tower3/public/pub/istp/") ) {
                url= "ftp://cdaweb.gsfc.nasa.gov/" + url.substring("/tower3/public/".length() );
            }
            if ( url.startsWith("/tower3/private/cdaw_data/cluster_private/st") ) {  //get all the cluster stuff
                url= "ftp://cdaweb.gsfc.nasa.gov/" + url.substring("/tower3/private/".length() );
            }
            String lookfor= "ftp://cdaweb.gsfc.nasa.gov/pub/istp/";
            if ( url.startsWith(lookfor) ) {
                url= "http://cdaweb.gsfc.nasa.gov/sp_phys/data/" + url.substring(lookfor.length());
            }
            String lookfor2= "ftp://cdaweb.gsfc.nasa.gov/pub/cdaweb_data";
            if ( url.startsWith(lookfor2) ) {
                url= "http://cdaweb.gsfc.nasa.gov/sp_phys/data/" + url.substring(lookfor2.length());
            }
            return url;

        } catch (XPathExpressionException ex) {
            throw new IOException("unable to read node "+spid);
        }

    }

    /**
     * return a range of a file that could be plotted.  Right now, this
     * just creates a FSM and gets a file.
     * @param spid
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

            Datum width= null;
            FileSystem fs;
            try {
                fs = FileSystem.create(new URI(base));
                FileStorageModelNew fsm= FileStorageModelNew.create( fs, tmpl );
                String ff= fsm.getRepresentativeFile( new NullProgressMonitor() );
                if ( ff!=null ) {
                    return fsm.getRangeFor(ff).toString();
                } else {
                    width= Units.hours.createDatum(24);
                }

            } catch (URISyntaxException ex) {
                Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
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
            XPath xp = XPathFactory.newInstance().newXPath();
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

    public String getMasterFile( String ds, ProgressMonitor p ) throws IOException {
        String master= "http://cdaweb.gsfc.nasa.gov/pub/CDAWlib/0MASTERS/"+ds.toLowerCase()+"_00000000_v01.cdf";

        //DasProgressPanel p= DasProgressPanel.createFramed("loading master cdf");
        p.setProgressMessage("loading master cdf");
        
        try {
            try {
                DataSetURI.getFile(new URI(master), p );
            } catch (URISyntaxException ex) {
                Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch ( IOException ex ) {
            // datasets don't have to have masters.  In this case we use the default timerange to grab a file as the master.

            String tmpl= getNaming(ds.toUpperCase());
            String base= getBaseUrl(ds.toUpperCase());
            URI baseUri;
            try {
                baseUri = new URI(base);
            } catch (URISyntaxException ex1) {
                throw new IllegalArgumentException("unable to make URI from "+base );
            }
            FileSystem fs= FileSystem.create( baseUri );
            
            FileStorageModelNew fsm= FileStorageModelNew.create( fs, tmpl );

            String avail= CDAWebDB.getInstance().getSampleTime(ds);
            DatumRange dr;
            try {
                dr = DatumRangeUtil.parseTimeRange(avail);
            } catch (ParseException ex1) {
                Logger.getLogger(CDAWebDB.class.getName()).log(Level.SEVERE, null, ex1);
                master= fsm.getRepresentativeFile(p);
                dr= fsm.getRangeFor(master);
            }
            //String[] files1= getFilesAndRanges( ds, dr );

            String[] files= fsm.getBestNamesFor( dr, p );
            if ( files.length==0 ) {
                master= fsm.getRepresentativeFile(p);
                if ( master==null ) {
                    throw new FileNotFoundException("unable to find any files to serve as master file in "+fsm );
                } else {
                    master= fs.getRootURI().toString() + master;
                }
            } else {
                master= fs.getRootURI().toString() + files[0];
            }

        }
        p.setProgressMessage(" ");
        return master;
    }

    private String getURL( Node dataset ) {
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
                        String url= kids2.item(k).getFirstChild().getTextContent().trim();
                        if ( url.startsWith( lookfor ) ) {
                            // "ftp://cdaweb.gsfc.nasa.gov/pub/istp/ace/mfi_h2"
                            //  http://cdaweb.gsfc.nasa.gov/istp_public/data/
                            url= "http://cdaweb.gsfc.nasa.gov/sp_phys/data/" + url.substring(lookfor.length());
                        }
                        if ( url.startsWith(lookfor2) ) {
                            url= "http://cdaweb.gsfc.nasa.gov/sp_phys/data/" + url.substring(lookfor2.length());
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
     * @return Map from serviceproviderId to description
     */
    public Map<String,String> getServiceProviderIds() {
        return ids;
    }

    /**
     * return the list of IDs that this reader can consume.
     * We apply a number of constraints:
     * 1. files must end in .cdf
     * 2. timerange_start and timerange_stop must be ISO8601 times.
     * 3. URL must start with a /, and may not be another website.
     * @return
     * @throws IOException
     */
    public void refreshServiceProviderIds() throws IOException {
        if ( document==null ) {
            throw new IllegalArgumentException("document has not been read, refresh must be called first");
        }
        try {
            XPath xp = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xp.evaluate( "//sites/datasite/dataset", document, XPathConstants.NODESET );

            Map<String,String> result= new LinkedHashMap<String,String>();

            for ( int i=0; i<nodes.getLength(); i++ ) {
                Node node= nodes.item(i);
                NamedNodeMap attrs= node.getAttributes();
                try {
                    String st= attrs.getNamedItem("timerange_start").getTextContent();
                    String en= attrs.getNamedItem("timerange_stop").getTextContent();
                    String nssdc_ID= attrs.getNamedItem("nssdc_ID").getTextContent();
                    if ( st.length()>1 && Character.isDigit(st.charAt(0))
                            && en.length()>1 && Character.isDigit(en.charAt(0))
                            //&& nssdc_ID.contains("None") ) {
                             ) {
                        String url= getURL(node);
                        if ( url!=null && 
                                ( url.startsWith( "http://cdaweb.gsfc.nasa.gov/istp_public/data/" ) ||
                                url.startsWith( "http://cdaweb.gsfc.nasa.gov/sp_phys/data/" ) ||
                                url.startsWith("ftp://cdaweb.gsfc.nasa.gov" ) ) && !url.startsWith("/tower3/private" ) ) {
                            String desc= getDescription(node);
                            String s=attrs.getNamedItem("serviceprovider_ID").getTextContent();
                            //String sid=attrs.getNamedItem("ID").getTextContent();
                            result.put(s,desc);
                        }
                    }
                } catch ( Exception ex2 ) {
                    ex2.printStackTrace();
                }
            }

            ids= result;

        } catch (XPathExpressionException ex) {
            ex.printStackTrace();
            throw new IOException("unable to read serviceprovider_IDs");
        }

    }

    /**
     * 4.2 seconds before getting description.  After too!
     * @param args
     * @throws IOException
     */
    public static void main( String [] args ) throws IOException, ParseException {
        CDAWebDB db= getInstance();

        long t0= System.currentTimeMillis();

        db.refresh( DasProgressPanel.createFramed("refreshing database") );

        db.getSampleTime("I1_AV_OTT"); // empty trailing folder 1984 caused problem before 20120525.
        db.getSampleTime("IA_K0_ENF"); // no files...

        String[] files= db.getFilesAndRanges( "AC_H0_MFI", DatumRangeUtil.parseTimeRange( "20010101T000000Z-20010131T000000Z" ) );
        for ( String s: files ) {
            System.err.println(s); //logger ok
        }

        String[] files2= db.getFilesAndRanges( "TIMED_L1B_SABER", DatumRangeUtil.parseTimeRange( "2002-01-26" ) );
        for ( String s: files2 ) {
            System.err.println(s); //logger ok
        }
        
        Map<String,String> ids= db.getServiceProviderIds( );

        for ( String s: ids.keySet() ) {
            System.err.println(s + ":\t" + ids.get(s) ); //logger ok
        }
        System.err.println( ids.size() ); //logger ok
        System.err.println( db.getNaming( "AC_H0_MFI" )  ); //logger ok
        System.err.println( db.getTimeRange( "AC_H0_MFI" )  ); //logger ok
        
        System.err.println( "Timer: " + ( System.currentTimeMillis() - t0 ) ); //logger ok

    }
}
