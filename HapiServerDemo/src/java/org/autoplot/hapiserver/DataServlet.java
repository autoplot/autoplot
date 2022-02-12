package org.autoplot.hapiserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;
import org.das2.datum.Datum;
import org.autoplot.datasource.RecordIterator;
import org.das2.datum.DatumRange;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.fsm.FileStorageModel;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.filesystem.FileSystem;
import org.json.JSONArray;

/**
 * Servlet for data responses.  
 * @see https://github.com/hapi-server/data-specification/blob/master/hapi-2.0.0/HAPI-data-access-spec-2.0.0.md#data
 * @author jbf
 */
@WebServlet(urlPatterns = {"/DataServlet"})
public class DataServlet extends HttpServlet {

    private static final Logger logger= Logger.getLogger("hapi");

    private String getParam( Map<String,String[]> request, String name, String deft, String doc, Pattern constraints ) {
        String[] vs= request.remove(name);
        String v;
        if ( vs==null ) {
            v= deft;
        } else {
            v= vs[0];
        }
        if ( v==null ) v= deft;
        if ( constraints!=null ) {
            if ( !constraints.matcher(v).matches() ) {
                throw new IllegalArgumentException("parameter "+name+"="+v +" doesn't match pattern");
            }
        }
        if ( v==null ) throw new IllegalArgumentException("required parameter "+name+" is needed");
        return v;
    }
    
    /**
     * parse RFC 822, RFC 850, and asctime format.
     * @return the time in milliseconds since 1970-01-01T00:00Z.
     */
    private static long parseTime(String str) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat( "EEE, dd MMM yyyy HH:mm:ss z");
        Date result;
        try {
            result= dateFormat.parse(str);
        } catch ( ParseException ex ) {
            dateFormat = new SimpleDateFormat( "EEE MMM dd HH:mm:ss yyyy" );
            try {
                result= dateFormat.parse(str);
            } catch ( ParseException ex2 ) {
                dateFormat = new SimpleDateFormat( "E, dd-MMM-yyyy HH:mm:ss z" );
                try {
                    result= dateFormat.parse(str);
                } catch ( ParseException ex3 ) {
                    Datum d= Units.ms1970.parse(str);
                    return (long)d.doubleValue(Units.ms1970);
                }
            }
        }
        return result.getTime();
    }
    
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
                
        String HAPI_SERVER_HOME= getServletContext().getInitParameter("HAPI_SERVER_HOME");
        Util.setHapiHome( new File( HAPI_SERVER_HOME ) );
            
        Map<String,String[]> params= new HashMap<>( request.getParameterMap() );
        String id= getParam( params,"id",null,"The identifier for the resource.", null );
        String timeMin= getParam( params, "time.min", "", "The earliest value of time to include in the response.", null );
        String timeMax= 
            getParam( params, "time.max", "", "Include values of time up to but not including this time in the response.", null );
        if ( timeMin.length()==0 ) { // support 3.0
            timeMin= getParam( params, "start", null, "The earliest value of time to include in the response.", null );
            timeMax= 
                getParam( params, "stop", null, "Include values of time up to but not including this time in the response.", null );
        }
        String parameters= 
            getParam( params, "parameters", "", "The comma separated list of parameters to include in the response ", null );
        String include= getParam(params, "include", "", "include header at the top", PATTERN_INCLUDE);
        String format= getParam(params, "format", "", "The desired format for the data stream.", PATTERN_FORMAT);
        String stream= getParam(params, "_stream", "true", "allow/disallow streaming.", PATTERN_TRUE_FALSE);
        String timer= getParam(params, "_timer", "false", "service request with timing output stream", PATTERN_TRUE_FALSE);
        
        if ( !params.isEmpty() ) {
            throw new ServletException("unrecognized parameters: "+params);
        }
        
        logger.log(Level.FINE, "data request for {0} {1}/{2}", new Object[]{id, timeMin, timeMax});
        
        DataFormatter dataFormatter;
        if ( format.equals("binary") ) {
            response.setContentType("application/binary");
            dataFormatter= new BinaryDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename="
                + Ops.safeName(id) + "_"+timeMin+ "_"+timeMax + ".bin" );
        } else {
            response.setContentType("text/csv;charset=UTF-8");  
            dataFormatter= new CsvDataFormatter();
            response.setHeader("Content-disposition", "attachment; filename=" 
                + Ops.safeName(id) + "_"+timeMin+ "_"+timeMax + ".csv" ); 
        }
        
        
        response.setHeader("Access-Control-Allow-Origin", "* " );
        response.setHeader("Access-Control-Allow-Methods","GET" );
        response.setHeader("Access-Control-Allow-Headers","Content-Type" );
        
        DatumRange dr;
        try {
            // see https://github.com/autoplot/dev/blob/master/bugs/2020/20201018/demoBug.jy
            //dr = DatumRangeUtil.parseISO8601Range( String.format("%s/%s",timeMin,timeMax ) );
            dr = new DatumRange( Units.cdfTT2000.parse(timeMin), Units.cdfTT2000.parse(timeMax) ); 
        } catch (ParseException ex) {
            throw new IllegalArgumentException(ex);
        }

        RecordIterator dsiter;
        
        if ( !( HapiServerSupport.getCatalogIds().contains(id) ) ) {
            Util.raiseBadId(id, response, response.getWriter() );
            return;
        }
        
        boolean allowStream= !stream.equals("false");

        OutputStream out = response.getOutputStream();
        
        long t0= System.currentTimeMillis();
        
        if ( timer.equals("true") || Util.isTrustedClient(request) ) {
            out= new IdleClockOutputStream(out);
        }
                
        File[] dataFiles= null;
        dsiter= null;
        
        // Look to see if we can cover the time range using cached files.  These files
        // must: be csv, contain all data, cover all data within $Y$m$d
        boolean allowCache= false; //dataFormatter instanceof CsvDataFormatter;
        if ( allowCache ) {
            File dataFileHome= new File( Util.getHapiHome(), "cache" );
            dataFileHome= new File( dataFileHome, Util.fileSystemSafeName(id) );
            if ( dataFileHome.exists() ) {
                FileStorageModel fsm= FileStorageModel.create( FileSystem.create(dataFileHome.toURI()), "$Y/$m/$Y$m$d.csv.gz" );
                File[] files= fsm.getFilesFor(dr); 
                // make sure we have all files.
                if ( files.length>0 ) {
                    DatumRange dr1= fsm.getRangeFor(fsm.getNameFor(files[0]));
                    while ( dr1.min().gt(dr.min()) ) dr1= dr1.previous();
                    int nfiles= 0;
                    while ( dr1.min().lt(dr.max()) ) {
                        nfiles++;
                        dr1= dr1.next();
                    }
                    if ( nfiles==files.length ) { // we have all files.
                        dataFiles= files;
                    }
                }
            }
        }
        
        logger.log(Level.FINE, "dataFiles(one): {0}", dataFiles);
        
        if ( dataFiles==null ) {
            try {
                logger.log(Level.FINER, "data files is null at {0} ms.", System.currentTimeMillis()-t0);
                dsiter= checkAutoplotSource( id, dr, allowStream );
                logger.log(Level.FINER, "done checkAutoplotSource at {0} ms.", System.currentTimeMillis()-t0);
                if ( dsiter==null ) {
                    File dataFileHome= new File( Util.getHapiHome(), "data" );
                    File dataFile= new File( dataFileHome, Util.fileSystemSafeName(id)+".csv" );
                    if ( dataFile.exists() ) {
                        dataFiles= new File[] { dataFile };
                    } else {
                        if ( id.equals("0B000800408DD710.noStream") ) {
                            logger.log(Level.FINER, "noStream demo shows without streaming" );
                            dsiter= new RecordIterator( // allow Autoplot to select
                                "file:/home/jbf/public_html/1wire/data/$Y/$m/$d/0B000800408DD710.$Y$m$d.d2s", dr, false ); 
                        } else {
                            throw new IllegalArgumentException("bad id: "+id+", data file does not exist: "+dataFile );
                        }
                    }
                } else {
                    logger.log(Level.FINER, "have dsiter {0} ms.", System.currentTimeMillis()-t0);
                }
            } catch ( Exception ex ) {
                throw new IllegalArgumentException("Exception thrown by data read", ex);
            }
        }
        
        logger.log(Level.FINE, "dataFiles(two): {0}", dataFiles);
        logger.log(Level.FINE, "dsiter: {0}", dsiter);
        
        if ( dataFiles!=null ) {
            // implement if-modified-since logic, where a 302 can be used instead of expensive data response.
            String ifModifiedSince= request.getHeader("If-Modified-Since");
            logger.log(Level.FINE, "If-Modified-Since: {0}", ifModifiedSince);
            if ( ifModifiedSince!=null ) {
                try {
                    long requestIfModifiedSinceMs1970= parseTime(ifModifiedSince);
                    boolean can304= true;
                    for ( File f: dataFiles ) {
                        if ( f.lastModified()-requestIfModifiedSinceMs1970 > 0 ) {
                            logger.log(Level.FINER, "file is newer than ifModifiedSince header: {0}", f);
                            can304= false;
                        }
                    }
                    logger.log(Level.FINE, "If-Modified-Since allows 304 response: {0}", can304);
                    if ( can304 ) {
                        response.setStatus( HttpServletResponse.SC_NOT_MODIFIED ); //304
                        out.close();
                        return;
                    }
                } catch ( ParseException ex ) {
                    response.setHeader("X-WARNING-IF-MODIFIED-SINCE", "date cannot be parsed.");
                }

            }
        }
        
        response.setStatus( HttpServletResponse.SC_OK );
        
        try {
            if ( dsiter!=null ) dsiter.constrainDepend0(dr);
        } catch ( IllegalArgumentException ex ) {
            response.setHeader( "X-WARNING", "data is not monotonic in time, sending everything." );
        }
        
        JSONObject jo0, jo;
        
        try {

            jo0= InfoServlet.getInfo( id );
            int[] indexMap=null;
            
            if ( !parameters.equals("") ) {
                jo= Util.subsetParams( jo0, parameters );
                indexMap= (int[])jo.get("x_indexmap");
                if ( dsiter!=null ) {
                    dsiter.resortFields( indexMap );
                }
            } else {
                jo= jo0;
            }
            
            if ( include.equals("header") ) {
                ByteArrayOutputStream boas= new ByteArrayOutputStream(10000);
                PrintWriter pw= new PrintWriter(boas);
                jo.put( "format", format ); // Thanks Bob's verifier for catching this.
                pw.write( jo.toString(4) );
                pw.close();
                boas.close();
                String[] ss= boas.toString("UTF-8").split("\n");
                for ( String s: ss ) {
                    out.write( "# ".getBytes("UTF-8") );
                    out.write( s.getBytes("UTF-8") );
                    out.write( (char)10 );
                }
            }
            
            if ( dataFiles!=null ) {
                for ( File dataFile : dataFiles ) {
                    cachedDataCsv( jo, out, dataFile, dr, parameters, indexMap );
                }
                
                if ( out instanceof IdleClockOutputStream ) {
                    logger.log(Level.FINE, "request handled with cache in {0} ms, ", new Object[]{System.currentTimeMillis()-t0, 
                        ((IdleClockOutputStream)out).getStatsOneLine() });
                } else {
                    logger.log(Level.FINE, "request handled with cache in {0} ms.", System.currentTimeMillis()-t0);
                }
                return;
            }
            
        } catch (JSONException ex) {
            throw new ServletException(ex);
        }

        // To cache days, post a single-day request for CSV of all parameters.
        boolean createCache= true;
        if ( createCache && 
                dataFormatter instanceof CsvDataFormatter &&
                parameters.equals("") &&
                TimeUtil.getSecondsSinceMidnight(dr.min())==0 && 
                TimeUtil.getSecondsSinceMidnight(dr.max())==0 && 
                dr.width().doubleValue(Units.seconds)==86400 || 
                dr.width().doubleValue(Units.seconds)==86401 ) {
            boolean proceed= true;
            
            File dataFileHome= new File( Util.getHapiHome(), "cache" );
            dataFileHome= new File( dataFileHome, id );
            if ( !dataFileHome.exists() ) {
                if ( !dataFileHome.mkdirs() ) {
                    logger.log(Level.FINE, "unable to mkdir {0}", dataFileHome);
                    proceed=false;
                }
            }
            if ( proceed && dataFileHome.exists() ) {
                TimeParser tp= TimeParser.create( "$Y/$m/$Y$m$d.csv.gz");
                String s= tp.format(dr);
                File ff= new File( dataFileHome, s );
                if ( !ff.getParentFile().exists() ) {
                    if ( !ff.getParentFile().mkdirs() ) {
                        logger.log(Level.FINE, "unable to mkdir {0}", ff.getParentFile());
                        proceed= false;
                    }
                }
                if ( !ff.exists() && !ff.getParentFile().canWrite() ) {
                    proceed= false;
                }
                if ( proceed ) {
                    FileOutputStream fout= new FileOutputStream(ff);
                    GZIPOutputStream gzout= new GZIPOutputStream(fout);
                    org.apache.commons.io.output.TeeOutputStream tout= new TeeOutputStream( out, gzout );
                    out= tout;
                    logger.log( Level.FINE, "wrote cache file {0}", ff );
                } else {
                    logger.log( Level.FINE, "unable to write file {0}", ff );
                }
            }
        }
        
        try {
            assert dsiter!=null;
            if ( dsiter.hasNext() ) {
                logger.fine("dsiter has at least one record");
                            
                QDataSet first= dsiter.next();
            
                dataFormatter.initialize( jo, out, first );
        
                dataFormatter.sendRecord( out, first );
                while ( dsiter.hasNext() ) {
                    dataFormatter.sendRecord( out, dsiter.next() );
                }
            }
            
            dataFormatter.finalize(out);
            
        } finally {
            
            out.close();
            
        }
        
        if ( out instanceof IdleClockOutputStream ) {
            logger.log(Level.FINE, "request handled in {0} ms, {1}",
                    new Object[]{System.currentTimeMillis()-t0, 
                        ((IdleClockOutputStream)out).getStatsOneLine() });
        } else {
            logger.log(Level.FINE, "request handled in {0} ms.", System.currentTimeMillis()-t0);
        }
    }
    private static final Pattern PATTERN_TRUE_FALSE = Pattern.compile("(|true|false)");
    private static final Pattern PATTERN_FORMAT = Pattern.compile("(|csv|binary)");
    private static final Pattern PATTERN_INCLUDE = Pattern.compile("(|header)");

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost( req, resp );
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     * @see https://jfaden.net/git/jbfaden/workblog/blob/master/2021/hapi/20210213_upload.md
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Map<String,String[]> params= new HashMap<>( request.getParameterMap() );
        String id= getParam( params,"id",null,"The identifier for the resource.", null );
        String timeMin= getParam( params, "time.min", "", "The earliest value of time to include in the response.", null );
        String timeMax= getParam( params, "time.max", "", "The latest value of time to include in the response.", null );
        String parameters= getParam( 
            params, "parameters", "", "The comma separated list of parameters to include in the response ", null );
        String include= getParam(params, "include", "", "include header at the top", PATTERN_INCLUDE);
        String format= getParam(params, "format", "", "The desired format for the data stream.", PATTERN_FORMAT);
        
        String key= request.getParameter("key"); // key is authorization, not authentication
        if ( !Util.keyCanModify(id,key) ) {
            throw new ServletException("need key to modify items in catalog");
        }

        if ( !include.equals("") ) throw new IllegalArgumentException("include cannot be used");
        if ( !parameters.equals("") ) throw new IllegalArgumentException("parameters cannot be used");
        if ( !format.equals("") ) throw new IllegalArgumentException("format cannot be used");
        if ( !timeMin.equals("") ) throw new IllegalArgumentException("time.min cannot be used");
        if ( !timeMax.equals("") ) throw new IllegalArgumentException("time.max cannot be used");
            
        File dataFileHome= new File( Util.getHapiHome(), "data" );
        File dataFile= new File( dataFileHome, id+".csv" );

        int maxMemSize = 5000 * 1024;
        
        DiskFileItemFactory factory = new DiskFileItemFactory();

        factory.setSizeThreshold(maxMemSize);
        
        ServletFileUpload upload = new ServletFileUpload(factory);
        try {
            request.getContentType();
            // wget --post-data='data=2002-02-02T00:00Z,4.56' \
            //    'http://localhost:8084/HapiServerDemo/hapi/data?id=chickens&key=12345678'
            if ( request.getContentType().equals( "application/x-www-form-urlencoded" ) ) {
                String data= request.getParameter("data");
                try ( BufferedReader r = new BufferedReader( new StringReader(data) );
                            BufferedWriter fout= new BufferedWriter( new FileWriter(dataFile,true) ) ) { //TODO: merge
                    String s;
                    while ( (s=r.readLine())!=null ) {
                        fout.write(s);
                        fout.write("\n");
                    }
                }
            } else {
                ServletRequestContext xxx= new ServletRequestContext(request);
                List fileItems = upload.parseRequest(xxx);

                Iterator i = fileItems.iterator();
                while ( i.hasNext() ) {
                    FileItem fi = (FileItem) i.next();
                    String fieldName = fi.getFieldName();
                    if ( fieldName.equals("key") ) {
                        String skey= new String( fi.get(), "UTF-8" );
                        if ( !skey.equals(key) ) {
                            logger.warning("different key used!");
                        }
                    } else if ( fieldName.equals("data") ) {
                        try ( BufferedReader r = new BufferedReader( new InputStreamReader( fi.getInputStream() ) );
                            BufferedWriter fout= new BufferedWriter( new FileWriter(dataFile,true) ) ) { //TODO: merge
                            String s;
                            while ( (s=r.readLine())!=null ) {
                                fout.write(s);
                                fout.write("\n");
                            }
                        }
                    }

                }
            }
        } catch (FileUploadException ex) {
            Logger.getLogger(DataServlet.class.getName()).log(Level.SEVERE, null, ex);
        }

        
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    private RecordIterator checkAutoplotSource(String id, DatumRange dr, boolean allowStream) 
        throws IOException, JSONException, Exception {
        File configFile= new File( new File( Util.getHapiHome().toString(), "info" ), Util.fileSystemSafeName(id)+".json" );
        if ( !configFile.exists() ) {
            return null;
        }
        JSONObject o= HapiServerSupport.readJSON(configFile);
        if ( o.has("x_uri") ) {
            String suri= o.getString("x_uri");
            RecordIterator dsiter= new RecordIterator( suri, dr, allowStream ); 
            return dsiter;
        } else {
            return null;
        }
    }
    

    /**
     * we have the csv pre-calculated, so just read from it.
     * @param out the output stream accepting data.  This will not be closed.
     * @param dataFile file to send, which if ends in .gz, uncompress it, 
     * @param dr the range to which the data should be trimmed.
     * @param parameters if non-empty, then return just these parameters of the cached data.
     * @throws FileNotFoundException
     * @throws IOException 
     * 
     */
    private void cachedDataCsv( JSONObject info, OutputStream out, File dataFile, DatumRange dr, String parameters, int[] indexMap) 
        throws FileNotFoundException, IOException {

        long t0= System.currentTimeMillis();
        
        Reader freader;
        if ( dataFile.getName().endsWith(".gz") ) {
            freader= new InputStreamReader( new GZIPInputStream( new FileInputStream(dataFile) ) );
        } else {
            freader= new FileReader(dataFile);
        }
        
        logger.log(Level.FINE, "reading cache csv file: {0} ", dataFile);
        
        //TODO: handle parameters and format=binary, think about JSON
        int[] pmap=null;
        if ( parameters.length()>0 ) {
            pmap= indexMap;
        }
        
        boolean quickVerify= true; // verify cache entry
        
        int nrec=0;
        int nf= -1;
        try {
            try ( BufferedReader reader= new BufferedReader( freader ); 
                  BufferedWriter writer= new BufferedWriter( new NoCloseOutputStreamWriter(out) ) ) {
                String line= reader.readLine();
                while ( line!=null ) {
                    int i= line.indexOf(",");
                    try {
                        Datum t= TimeUtil.create(line.substring(0,i));
                        if ( dr.contains(t) ) {
                            nrec++;
                            if ( pmap==null ) {
                                writer.write(line);
                            } else {
                                String[] ss= Util.csvSplit(line,nf);
                                if ( nf==-1 ) nf= ss.length;
                                if ( quickVerify ) {
                                    try {
                                        JSONArray pps= info.getJSONArray("parameters");
                                        JSONObject time= pps.getJSONObject(0);
                                        int len= time.getInt("length");
                                        if ( ss[0].length()!=len ) {
                                            throw new IllegalArgumentException("cache field 0 length ("+ss[0].length()+") in file "
                                                +dataFile+" is incorrect, should be "+len);
                                        }
                                    } catch (JSONException ex) {
                                        throw new IllegalArgumentException(ex); // should have caught this already
                                    }
                                    quickVerify= false;
                                }
                                for ( int j=0; j<pmap.length; j++ ) {
                                    if ( j>0 ) writer.write(',');
                                    writer.write( ss[pmap[j]] );
                                }
                            }
                            writer.newLine();
                        }
                    } catch (ParseException ex) {
                        Logger.getLogger(DataServlet.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    line= reader.readLine();
                }
                logger.log(Level.FINER, "sent {0} records from {1}", new Object[]{nrec, dataFile});
            }
        } finally {
            freader.close();
            out.flush();
        }
        long timer= System.currentTimeMillis()-t0;
        
        if ( out instanceof IdleClockOutputStream ) {
            String s=  ((IdleClockOutputStream)out).getStatsOneLine();
            logger.log(Level.FINE, "done reading cache csv file ({0}ms, {1}): {2}", new Object[]{timer, s, dataFile});
        } else {
            logger.log(Level.FINE, "done reading cache csv file ({0}ms): {1}", new Object[]{timer, dataFile});
        }
    }
    
    
}
