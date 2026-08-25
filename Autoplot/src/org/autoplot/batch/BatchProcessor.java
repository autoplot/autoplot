
package org.autoplot.batch;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream;
import java.net.URI;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import org.apache.commons.io.output.TeeOutputStream;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUtil;
import org.autoplot.JythonUtil;
import org.autoplot.RunBatchTool;
import org.autoplot.ScriptContext2023;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.URISplit;
import org.autoplot.dom.Application;
import org.autoplot.jythonsupport.JythonRefactory;
import org.autoplot.jythonsupport.Param;
import org.autoplot.jythonsupport.ui.Util;
import org.das2.components.DasProgressPanel;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.DatumUtil;
import org.das2.datum.Units;
import org.das2.util.DasPNGConstants;
import org.das2.util.FileUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.python.util.InteractiveInterpreter;

/**
 * This is a redo of the useful Run Batch Tool, hoping to accomplish
 * several goals:
 * <ul>
 * <li>separate the Model from the View
 * <li>headless processing
 * <li>multi-computer processing
 * <li>improved batch file formatting (they are very hard to read and create from other programs)
 * </ul>
 * 
 * The property "batchDirectory" is used to coordinate multiple machines.  There
 * will be one "host" machine and multiple "guest" machines.  The host will
 * set up the batchDirectory to contain the subdirectories:
 * <ul>
 * <li>jobs - jobs to be run
 * <li>pending - job claimed by guests
 * <li>complete - job completed by guests
 * </ul>
 * A job is moved from one directory to the next as each job is performed.  Note
 * this is a guess at how this should work, and may change.
 * 
 * Output (stdout but not stderr) is written to the directory stdout.
 * 
 * If setWritePngTemplate is a non-empty string, then image files are written to this location, and a link from the images
 * directory to this image will be created.
 * 
 * Scripts are run without verifying them against known approved scripts, so only run this against scripts from
 * trusted sources.
 * 
 * @author jbf
 */

public class BatchProcessor {
    
    private static final Logger logger= LoggerManager.getLogger("jython.runbatch");    
    
    /**
     * if the parameter name contains a split character then return the names.
     * This is just so we can experiment with the feature.
     * @param param
     * @return null or the names
     */
    private static String[] maybeSplitMultiParam( String param ) {
        if ( param.contains("|") ) {
            return param.split( "\\|", -2 );
        } else if ( param.contains(",") ) {
            return param.split( ",", -2 );
        } else if ( param.contains(";") ) {
            return param.split( ";", -2 );
        } else {
            return null;
        }
    }
    
    /**
     * TODO: this is not complete!
     * @param interp
     * @param paramDescription
     * @param paramName
     * @param f1
     * @throws IOException 
     */
    private static void setParam( InteractiveInterpreter interp, String pwd, org.autoplot.jythonsupport.Param paramDescription, 
            String paramName, String f1 ) throws IOException {
        if ( paramDescription==null ) {
            throw new IllegalArgumentException("expected to see parameter description!");
        }
        switch (paramDescription.type) {
            case 'U':
            case 'R':
                if ( f1.startsWith("'") && f1.endsWith("'") && f1.length()>1 ) {
                    f1= f1.substring(1,f1.length()-1);
                }
                URI uri;
                URISplit split= URISplit.parse(f1);
                if ( split.path==null ) {
                    uri= DataSetURI.getResourceURI( pwd + f1 );
                } else {
                    uri= DataSetURI.getResourceURI(f1);
                }
                interp.set("_apuri", uri );
                interp.exec("autoplot2025.params[\'"+paramName+"\']=_apuri"); // JythonRefactory okay
                break;
            case 'L': 
                interp.exec("autoplot2025.params[\'"+paramName+"\']=URL(\'"+f1+"\')"); // JythonRefactory okay
                break;
            case 'M':
                interp.exec("from java.io import File");
                interp.exec("autoplot2025.params[\'"+paramName+"\']=File(\'"+f1+"\')"); // JythonRefactory okay
                break;
            case 'A':
                if ( f1.startsWith("'") && f1.endsWith("'") && f1.length()>1 ) {
                    f1= f1.substring(1,f1.length()-1);
                }
                interp.exec("autoplot2025.params[\'"+paramName+"\']=\'"+f1+"\'");// JythonRefactory okay
                break;
            case 'T':
                try {
                    DatumRange timeRange= DatumRangeUtil.parseTimeRange(f1);
                    interp.set("_apdr", timeRange );
                    interp.exec("autoplot2025.params[\'"+paramName+"\']=_apdr");// JythonRefactory okay
                } catch (ParseException ex) {
                    Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
                }   break;
            default:
                interp.exec("autoplot2025.params[\'"+paramName+"\']="+f1);// JythonRefactory okay
                break;
        }
        
    }
    
    /**
     * write the current canvas to a file.
     * @param f1 the parameter value of null.
     * @param f2 the parameter value or null.
     * @param uri the script URI which is embedded in the PNG (for the PNGWalkTool to use).
     * @param dom if non-null, use this application for the image.
     * @return the name of the file used.
     * @throws IOException 
     */
    private String doWrite( String template, String f1, String f2, String uri, Application dom) throws IOException {
                
        Preferences prefs= Preferences.userNodeForPackage( RunBatchTool.class );
        prefs.put( "lastTemplate", template );

        template= template.replaceAll("\\$x","%s");
        
        f1= f1.replaceAll("/", "_");
        f2= f2.replaceAll("/", "_");
        f1= f1.replaceAll(" ", "_");
        f2= f2.replaceAll(" ", "_");
        f1= f1.replaceAll(":", "_"); // times
        f2= f2.replaceAll(":", "_");
        
        List<String> argList= new ArrayList<>();
        if ( f1.contains(";") ) {
            String[] ss= f1.split("\\;",-2);
            argList.addAll(Arrays.asList(ss));
        } else {
            if ( f1.trim().length()>0 ) {
                argList.add(f1);
            }
        }
        if ( f2.contains(";") ) {
            String[] ss= f2.split("\\;",-2);
            argList.addAll(Arrays.asList(ss));
        } else {
            if ( f2.trim().length()>0 ) {
                argList.add(f2);
            }
        }
        
        // now the tricky part will be to pull out all the fields from the template.
        String[] ss= template.split("\\%");
        
        boolean packArgments=false;
        if ( argList.size() != ss.length-1 ) {
            if ( ss.length==3 ) {
                packArgments= true;
            } else {
                throw new IllegalArgumentException("PNG template and number of parameters don't match");
            }
        }

        Object[] args= new Object[argList.size()];
        for ( int i=0; i<argList.size(); i++ ) {

            String spec;
            if ( packArgments ) {
                spec= "x";
            } else {
                spec= ss[i+1];
            }
            int idx= 0; // find the first letter
            char c= spec.length()>0 ? spec.charAt(0) : ' ';
            while ( idx<spec.length() && ( c=='-' || c=='.' || Character.isDigit(c) ) ) {
                idx++;
                c= spec.length()>0 ? spec.charAt(idx) : ' ';
            }
            if ( idx==spec.length() ) {
                throw new IllegalArgumentException("expected to see non-digit in template after %");
            }
            char letter= spec.charAt(idx);
            if ( letter=='s' ) {
                args[i]= argList.get(i);
            } else {
                switch (letter) {
                    case 'd':
                        args[i]= Integer.valueOf(argList.get(i));
                        break;
                    case 'f':
                    case 'e':
                        args[i]= Double.valueOf(argList.get(i));
                        break;
                    default:
                        args[i]= argList.get(i);
                        break;
                }
            }
        }
                
        String s;
        if ( packArgments ) {
            s= String.format( template, f1, f2 );
        } else {
            s= String.format( template, args );
        }
        
        s= s.replaceAll(" ","_"); 

        if ( s.endsWith(".png") ) {
            BufferedImage bufferedImage = dom.getController().getScriptContext().writeToBufferedImage(); 
            Map<String,String> metadata= new LinkedHashMap<>();
            metadata.put( "ScriptURI",uri );
            metadata.put( DasPNGConstants.KEYWORD_PLOT_INFO, 
                dom.getController().getApplicationModel().getCanvas().getImageMetadata() );
            dom.getController().getScriptContext().writeToPng(bufferedImage,s,metadata);
        } else if ( s.endsWith(".pdf") ) {
            dom.getController().getScriptContext().writeToPdf(s);
        } 
        return s;

    }
    
    /**
     * write stats for results to file.
     * @param pendingFile
     * @param results
     * @param resultsArray
     * @param recordsWrittenAlready
     * @param count
     * @throws FileNotFoundException
     * @throws IOException 
     */
    private static void appendResultsPendingCSV( 
            File pendingFile, 
            JSONObject results, 
            JSONArray resultsArray, 
            int recordsWrittenAlready, 
            int count ) throws FileNotFoundException, IOException {
        
        boolean header= recordsWrittenAlready==0;
        
        synchronized (RunBatchTool.class) {
            
            try (PrintWriter out = new PrintWriter( new FileWriter( pendingFile, true ) ) ) {

                if ( resultsArray.length()==0 ) {
                    logger.warning("no records in results");
                    return;
                }

                JSONObject jo= resultsArray.getJSONObject(0);
                boolean hasOutputFile= jo.has("writeFile");
                JSONArray params= results.getJSONArray("params");

                StringBuilder record;

                if ( header ) {
                    record= new StringBuilder();
                    record.append("jobNumber");

                    for ( int j=0; j<params.length(); j++ ) {
                        record.append(",");
                        record.append(params.get(j));
                    }
                    record.append(",").append("executionTime(ms)");
                    if ( hasOutputFile ) {
                        record.append(",").append("writeFile");
                    }
                    record.append(",").append("exception");

                    out.println(record.toString());

                }

                int stop= recordsWrittenAlready + count;
                for ( int i=recordsWrittenAlready; i<stop; i++ ) {
                    Object o= resultsArray.opt(i);
                    if ( o==null ) {
                        continue;
                    }
                    if ( !( o instanceof JSONObject ) ) {
                        continue;
                    }
                    jo= (JSONObject)o;
                    record= new StringBuilder();
                    record.append(i);
                    for ( int j=0; j<params.length(); j++ ) {
                        record.append(",");
                        record.append( jo.get(params.getString(j)) );
                    }
                    record.append(",").append(jo.get("executionTime"));
                    if ( hasOutputFile ) {
                        record.append(",").append(jo.get("writeFile"));
                    }
                    String resultString= jo.optString("result","");
                    int inl= resultString.indexOf("\n");
                    if ( inl>=0 ) inl= resultString.indexOf("\n",inl+1);
                    if ( inl>=0 ) inl= resultString.indexOf("\n",inl+1);
                    if ( inl>=0 ) resultString= resultString.substring(0,inl).replaceAll("\n"," ").replaceAll(",","");
                    record.append(",").append(resultString);
                    out.println( record.toString() );
                }
                out.flush();

            } catch (JSONException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
    
    
    /**
     * Run the job.  If an exception occurs during the run, the exception
     * text will appear in the result.
     * @param jobNumber unique id for the job.
     * @param pwd the working directory of the script.
     * @param scriptUri the name of the script, possibly https://...jy
     * @param script the script to run
     * @param parameterDescriptions the set of parameters that the script accepts
     * @param params defaults for other parameters, redundant with scriptUri.
     * @param param1Name the name of the first parameter (or semicolon-delimited parameters)
     * @param param1Value the value of the first parameter (or semicolon-delimited values)
     * @param param2Name the name of the second parameter (or semicolon-delimited parameters)
     * @param param2Value the value of the second parameter (or semicolon-delimited values)
     * @param monitor monitor for the job
     * @return a JSONObject representing the run results.
     */
    private JSONObject doOneJob(
        int jobNumber,
        String pwd,
        String scriptUri,
        String script,
        Map<String,Param> parameterDescriptions, 
        Map<String,String> params,
        String param1Name, 
        String param1Value, 
        String param2Name,
        String param2Value,
        final ProgressMonitor monitor ) throws RuntimeException {
        
        URISplit split= URISplit.parse(scriptUri);
        String name= split.file;
        
        param1Value= param1Value.trim();
        param1Name= param1Name.trim();

        JSONObject runResults= new JSONObject();

        try {
            
            ApplicationModel appmodel = new ApplicationModel();
            appmodel.addDasPeersToAppAndWait();

            Application myDom= appmodel.getDom();
            ScriptContext2023 scriptContext= new ScriptContext2023();
            myDom.getController().setScriptContext( scriptContext );
        
            if ( !scriptContext.isModelInitialized() ) {
                scriptContext.setApplicationModel(appmodel);
            }
            
            myDom.getController().getScriptContext();
            
            ProgressMonitor myMonitor= new NullProgressMonitor() {
                @Override
                public boolean isCancelled() {
                    return monitor.isCancelled();
                }
            }; // subtask would reset indeterminate.
            
            InteractiveInterpreter interp = JythonUtil.createInterpreter( true, false, myDom, myMonitor );
            interp.exec(JythonRefactory.fixImports("import autoplot2025")); 
            Map<String,Object> scriptParams= new LinkedHashMap<>();
            scriptParams.putAll( params );

            if ( monitor.isCancelled() ) {
                return null;
            }
            
            interp.set( "PWD", split.path );
            
            // set all the defaults
            for ( Map.Entry<String,String> e: params.entrySet() ) {
                String pname= e.getKey();
                if ( parameterDescriptions.get(pname)!=null ) { //TODO: When does this happen?  See file:/Users/jbf/Desktop/git/dev/demos/2025/20250130/brokenParamDescription.jy?resourceURI='https://pds-ppi.igpp.ucla.edu/data/JNO-J_SW-JAD-5-CALIBRATED-V1.0/DATA/2018/2018091/ELECTRONS/JAD_L50_HRS_ELC_TWO_DEF_2018091_V01.LBL'&doplot=False
                    setParam( interp, pwd, parameterDescriptions.get(pname), pname, e.getValue() );
                }
            }                
            
            String[] paramNames1= maybeSplitMultiParam( param1Name );

            if ( paramNames1!=null ) { // v1;v2;v3 form used
                char splitc= param1Name.charAt(paramNames1[0].length());
                String[] paramValues= param1Value.trim().split("\\"+splitc);
                for ( int j= 0; j<paramNames1.length; j++ ) {
                    String p= paramNames1[j].trim();
                    String v= paramValues[j].trim();
                    if ( !parameterDescriptions.containsKey(p) ) {
                        if ( p.trim().length()==0 ) {
                            throw new IllegalArgumentException("param1Name not set");
                        } else {
                            throw new IllegalArgumentException("param not found: " + p );
                        }
                    }
                    setParam( interp, pwd, parameterDescriptions.get(p), p, v );
                    runResults.put( p, v );
                    scriptParams.put( p, v );
                }
            } else {
                String p= param1Name;
                String v= param1Value;
                if ( !parameterDescriptions.containsKey(p) ) {
                    if ( p.length()==0 ) {
                        throw new IllegalArgumentException("param1Name not set");
                    } else {
                        throw new IllegalArgumentException("param not found: " + p );
                    }
                }
                setParam( interp, pwd, parameterDescriptions.get(p), p, v );
                runResults.put( p, v );
                scriptParams.put( p, v );
            }
            
            if ( param2Name!=null && param2Name.length()>0 ) {
                String[] paramNames2= maybeSplitMultiParam( param2Name );

                if ( paramNames2!=null ) { // v1;v2;v3 form used
                    char splitc= param2Name.charAt(paramNames2[0].length());
                    String[] paramValues= param2Value.trim().split("\\"+splitc);
                    for ( int j= 0; j<paramNames2.length; j++ ) {
                        String p= paramNames2[j].trim();
                        String v= paramValues[j].trim();
                        if ( !parameterDescriptions.containsKey(p) ) {
                            if ( p.trim().length()==0 ) {
                                throw new IllegalArgumentException("param1Name not set");
                            } else {
                                throw new IllegalArgumentException("param not found: " + p );
                            }
                        }
                        setParam( interp, pwd, parameterDescriptions.get(p), p, v );
                        runResults.put( p, v );
                        scriptParams.put( p, v );
                    }
                } else {
                    String p= param2Name;
                    String v= param2Value;
                    if ( !parameterDescriptions.containsKey(p) ) {
                        if ( p.trim().length()==0 ) {
                            throw new IllegalArgumentException("param2Name not set");
                        } else {
                            throw new IllegalArgumentException("param not found: " + p );
                        }
                    }
                    setParam( interp, pwd, parameterDescriptions.get(p), p, v );
                    runResults.put( p, v );
                    scriptParams.put( p, v );
                }                
            }

            long t0= System.currentTimeMillis();
            
            OutputStream outs;
            OutputStream errs;
            
            ByteArrayOutputStream outbaos;
            if ( batchDirectory!=null ) {
                outbaos= new ByteArrayOutputStream();

                File outf= new File( new File( batchDirectory, "stdout" ), String.format("%06d",jobNumber) );
                outs= new TeeOutputStream( new FileOutputStream( outf ), outbaos );
                
                //File errf= new File( new File( batchDirectory, "stderr" ), String.format("%06d",jobNumber) );
                //errs= new TeeOutputStream( new FileOutputStream( errf ), outbaos );
                errs= outs;  //TODO: the above lines don't seem to catch the err channel.
                
            } else {
                outbaos= new ByteArrayOutputStream();
                outs= outbaos;
                errs= outbaos;
            }
            
            //ByteArrayOutputStream outbaos= 
            try {
                interp.setOut(outs);
                interp.setErr(errs);
                
                interp.execfile( new ByteArrayInputStream(script.getBytes("US-ASCII")), name );
                
                String uri= URISplit.format( "script", split.resourceUri.toString(), scriptParams );
                String doWriteTemplate= this.writePngTemplate;
                if ( doWriteTemplate.length()>0 ) {
                    String image;
                    image= doWrite( doWriteTemplate, param1Value, param2Value, uri, myDom );

                    File outf= new File( new File( batchDirectory, "images" ), String.format("%06d.png",jobNumber) );
                    Path target = Paths.get(image);
                    Path link   = outf.toPath();
                    Files.createSymbolicLink(link, target);
                    runResults.put("writeFile", image );
                }
            } catch ( NumberFormatException ex ) {
                ex.printStackTrace(); // TODO: need to get the word out.  This is a bug in the run batch tool, not the script.
                String msg= ex.toString();
                runResults.put("result",msg);
                
            } catch ( IOException | JSONException | RuntimeException ex ) {
                String msg= ex.toString();
                runResults.put("result",msg);

            } finally {
                outbaos.close();
                runResults.put("stdout", new String(outbaos.toByteArray(),"US-ASCII") );
                runResults.put("executionTime", System.currentTimeMillis()-t0);
            }

            JSONObject copy = new JSONObject(runResults, JSONObject.getNames(runResults));

            return copy;

                
        } catch ( RuntimeException ex ) {
            Logger.getLogger(RunBatchTool.class.getName()).log(Level.SEVERE, null, ex);
            throw ex;
        } catch ( IOException | JSONException ex ) {
            throw new RuntimeException(ex);
        }

    }
    
    private int threads = 8;

    public static final String PROP_THREADS = "threads";

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        int oldThreads = this.threads;
        this.threads = threads;
        propertyChangeSupport.firePropertyChange(PROP_THREADS, oldThreads, threads);
    }

    private String writePngTemplate = "";

    public static final String PROP_WRITEPNGTEMPLATE = "writePngTemplate";

    public String getWritePngTemplate() {
        return writePngTemplate;
    }

    public void setWritePngTemplate(String writePngTemplate) {
        String oldWritePngTemplate = this.writePngTemplate;
        this.writePngTemplate = writePngTemplate;
        propertyChangeSupport.firePropertyChange(PROP_WRITEPNGTEMPLATE, oldWritePngTemplate, writePngTemplate);
    }

    private String statusMessage = "";

    public static final String PROP_STATUSMESSAGE = "statusMessage";

    /**
     * get the last issues status message.
     * @return 
     */
    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
        propertyChangeSupport.firePropertyChange(PROP_STATUSMESSAGE, null, statusMessage);
    }
    
    private File resultsFile = null;

    public static final String PROP_RESULTSFILE = "resultsFile";

    public File getResultsFile() {
        return resultsFile;
    }

    /**
     * set the file where results will be written.  This must have a .csv
     * or .json extention.
     * @param resultsFile 
     */
    public void setResultsFile(File resultsFile) {
        if ( !( resultsFile.getName().endsWith(".csv") || resultsFile.getName().endsWith(".json") ) ) {
            throw new IllegalArgumentException("results file must end with .json or .csv");
        }
        File oldResultsFile = this.resultsFile;
        this.resultsFile = resultsFile;
        propertyChangeSupport.firePropertyChange(PROP_RESULTSFILE, oldResultsFile, resultsFile);
    }
    
    /**
     * null or the root of the batch directory used to coordinate the jobs.
     */
    private File batchDirectory = null;

    /**
     * property name of the batchDirectory, where the batch is coordinated.
     */    
    public static final String PROP_BATCHDIRECTORY = "batchDirectory";

    /**
     * null or the root of the batch directory used to coordinate the jobs.
     * @return null or the root of the batch directory used to coordinate the jobs.
     */    
    public File getBatchDirectory() {
        return batchDirectory;
    }

    /**
     * set the location where job files will be created in "jobs"
     * moved to "pending" as the job is in progress, and to "complete"
     * as each job is completed.
     * @param batchDirectory null or the directory.
     */
    public void setBatchDirectory(File batchDirectory) {
        File oldBatchDirectory = this.batchDirectory;
        this.batchDirectory = batchDirectory;
        propertyChangeSupport.firePropertyChange(PROP_BATCHDIRECTORY, oldBatchDirectory, batchDirectory);
    }


    private transient final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
    
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName,listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName,listener);
    }    
    
    /**
     * delete all files in the directory.
     * @param directory
     * @throws IOException 
     */
    private static void emptyDirectory( File directory ) throws IOException {
        if ( !directory.exists() ) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory.toPath())) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    Files.delete(entry);
                }
            }
        }
    }

    /**
     * run the batch script at the location.  This will block until all jobs
     * are completed or the monitor is cancelled.
     * @param dom the original dom.
     * @param batchFile the batch file specification
     * @param monitor monitor for the process.
     * @throws java.io.IOException 
     * @throws IllegalArgumentException if the batchFile cannot be parsed.
     */
    public void runBatchScript( Application dom, String batchFile, ProgressMonitor monitor ) throws IOException {

        int initialThreadCount= threads;
        
        try {
            
            if ( SwingUtilities.isEventDispatchThread() ) throw new IllegalArgumentException("don't call from event thread");
            
            URISplit split= URISplit.parse(batchFile);
            
            String pwd= split.path;
            
            File f= DataSetURI.getFile(split.file);
            
            String batchFileJson= FileUtil.readFileToString(f);
            JSONObject jo= new JSONObject(batchFileJson);
            
            final Map<String,String> params= new HashMap();
            String scriptUri= jo.getString("script");
            if ( scriptUri.startsWith("script:") ) { // kludge where "script:" prefix gets into .batch file
                scriptUri= scriptUri.substring(7);
            }
            scriptUri= scriptUri.replaceAll("\\%\\{PWD\\}",pwd );
            
            final String fscriptUri= scriptUri;
            
            URISplit scriptSplit= URISplit.parse(scriptUri);
            Map<String,String> scriptParams= URISplit.parseParams(scriptSplit.params);
            
            final File scriptFile= DataSetURI.getFile(fscriptUri);
            
            final String script= FileUtil.readFileToString(scriptFile);
                    
            params.put( "script", fscriptUri );

            params.put( "param1", jo.getString("param1"));
            params.put( "param2", jo.getString("param2"));
            
            String[] param1Values;
            String[] param2Values;
            
            String param1Name= jo.getString("param1");
            Object oparam1Values= jo.get("param1Values");
            if ( oparam1Values instanceof String ) {
                param1Values= ((String)oparam1Values).split("\n");
            } else if ( oparam1Values instanceof JSONArray ) {
                JSONArray jv1= (JSONArray)oparam1Values;
                param1Values= new String[jv1.length()];
                for ( int i=0; i<jv1.length(); i++ ) {
                    param1Values[i]= jv1.getString(i);
                }
            } else {
                throw new IllegalArgumentException("param1Values must be a string or string array");
            }
            
            String param2Name= jo.getString("param2");
            Object oparam2Values= jo.get("param2Values");
            if ( oparam2Values instanceof String ) {
                param2Values= ((String)oparam2Values).split("\n");
                if ( param2Values.length==1 && param2Values[0].equals("") ) {
                    param2Values= null;
                }
            } else if ( oparam2Values instanceof JSONArray ) {
                JSONArray jv2= (JSONArray)oparam2Values;
                param2Values= new String[jv2.length()];
                for ( int i=0; i<jv2.length(); i++ ) {
                    param2Values[i]= jv2.getString(i);
                }
            } else {
                throw new IllegalArgumentException("param2Values must be a string or string array");
            }
            
            final AtomicInteger threadCounter= new AtomicInteger(0);

            final String pid= AutoplotUtil.getProcessId("???");
        
            ThreadFactory tf= (Runnable r) -> new Thread( r, "run-batch-"+threadCounter.incrementAndGet());
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(initialThreadCount,tf);
            Deque<Long> durationsMillis= new LinkedBlockingDeque<>(); 
            
            final AtomicInteger jobNumber= new AtomicInteger(0);
            
            boolean showEta= "true".equals( System.getProperty("RunBatchTool.eta","true") ); 
            
            Map<String,Object> env= new HashMap<>();
            env.put("dom",dom);
            env.put("PWD",pwd);
            
            final Map<String,Param> fparameterDescriptions=Util.getParams( env, script, params, new NullProgressMonitor() );
           
            int numberOfJobs;
            
            File lbatchJobsDirectory= null;
            File lbatchPendingDirectory= null;
            File lbatchCompleteDirectory= null;
            File lbatchExceptionsDirectory= null;
            File lbatchStdoutDirectory;
            File lbatchStderrDirectory;
            File lbatchImagesDirectory;
            
            File specificationFile = new File( batchDirectory, "main.batch" );
            File specificationPendingFile = new File( batchDirectory, "main.batch.pending" );
            
            boolean batchGuest= false; // a batchGuest is a machine which works on a batch but does not set it up.
            
            if ( batchDirectory!=null ) {
                if ( !batchDirectory.exists() ) {
                    if ( !batchDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+batchDirectory);
                    }
                }
                lbatchJobsDirectory= new File( batchDirectory, "jobs" );
                if ( !lbatchJobsDirectory.exists() ) {
                    if ( !lbatchJobsDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchJobsDirectory);
                    }
                }
                lbatchPendingDirectory= new File( batchDirectory, "pending" );
                if ( !lbatchPendingDirectory.exists() ) {
                    if ( !lbatchPendingDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchPendingDirectory);
                    }
                }
                lbatchCompleteDirectory= new File( batchDirectory, "complete" );
                if ( !lbatchCompleteDirectory.exists() ) {
                    if ( !lbatchCompleteDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchCompleteDirectory);
                    }
                }
                lbatchExceptionsDirectory= new File( batchDirectory, "exceptions" );
                if ( !lbatchExceptionsDirectory.exists() ) {
                    if ( !lbatchExceptionsDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchExceptionsDirectory);
                    }
                }
                lbatchStdoutDirectory= new File( batchDirectory, "stdout" );
                if ( !lbatchStdoutDirectory.exists() ) {
                    if ( !lbatchStdoutDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchStdoutDirectory);
                    }
                }

                lbatchStderrDirectory= new File( batchDirectory, "stderr" );
                //if ( !lbatchStderrDirectory.exists() ) {
                //    if ( !lbatchStderrDirectory.mkdirs() ) {
                //        throw new IllegalArgumentException("Unable to make directory: "+lbatchStderrDirectory);
                //    }
                //}

                lbatchImagesDirectory= new File( batchDirectory, "images" );
                if ( !lbatchImagesDirectory.exists() ) {
                    if ( !lbatchImagesDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchImagesDirectory);
                    }
                }
                
                if ( specificationFile.exists() ) {
                    if ( isDirectoryEmpty(lbatchJobsDirectory) ) {
                        if ( specificationFile.delete() ) {
                            logger.fine("Cleared specification file for old run, because the jobs directory is empty.");
                        } else {
                            logger.info("Saw the specification file, but someone else must have deleted it.");
                        }
                    }
                }
                
                while ( specificationPendingFile.exists() ) {
                    long t0= System.currentTimeMillis();
                    while ( specificationPendingFile.exists() && System.currentTimeMillis()-t0 < 60000 ) { // we'll wait for the file to go away for 1 minute.
                        logger.warning("Another thread or workstation appears to be setting up the run, sleeping for 10 seconds.");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                    if ( specificationPendingFile.exists() ) {
                        throw new IllegalArgumentException("The other thread is still working on the setup, check on its status and run this again later.");
                    }
                }
                
                if ( specificationFile.exists() ) {
                    //make sure the JSON files are identical.
                    String hostBatchFileJson= FileUtil.readFileToString(specificationFile);
                    JSONObject hostJson= new JSONObject(hostBatchFileJson); 
                    String diff= batchTasksDifferent(jo,hostJson);
                    if ( diff!=null ) {
                        throw new IllegalArgumentException("Guest runBatch JSON doesn't match host runBatch JSON in "+specificationFile+", "+diff);
                    }
                    batchGuest= true;
                    logger.log(Level.INFO, "Running batch as GUEST, pid is {0}", pid);
                } else {
                    Files.createFile( specificationPendingFile.toPath() );
                    try ( PrintWriter write= new PrintWriter( specificationFile ) ) {
                        write.append(batchFileJson);
                    }
                    emptyDirectory(lbatchJobsDirectory);
                    emptyDirectory(lbatchPendingDirectory);
                    emptyDirectory(lbatchCompleteDirectory);
                    emptyDirectory(lbatchExceptionsDirectory);
                    emptyDirectory(lbatchStdoutDirectory);
                    emptyDirectory(lbatchStderrDirectory);
                    emptyDirectory(lbatchImagesDirectory);
                    logger.log(Level.INFO, "Running batch as HOST, pid is {0}", pid);
                }
                
            }
            
            final File batchJobsDirectory= lbatchJobsDirectory;
            final File batchPendingDirectory= lbatchPendingDirectory;
            final File batchCompletedDirectory= lbatchCompleteDirectory;
            final File batchExceptionsDirectory= lbatchExceptionsDirectory;
            
            JSONObject batchResults= new JSONObject();
            JSONArray resultsStats= new JSONArray();
            
            batchResults.put( "results", resultsStats );
            
            JSONArray paramsJson= new JSONArray();
            paramsJson.put(0,jo.getString("param1"));
            if ( jo.getString("param2").length()>0 ) {
                paramsJson.put(1,jo.getString("param2"));
            }
            
            batchResults.put("params", paramsJson );
            
            // If using a batchDirectory and we are the "host", then queue up all the jobs.
            if ( batchPendingDirectory!=null && !batchGuest ) {
                if ( param2Values==null || param2Values.length==0 ) {
                    URISplit split1= URISplit.parse(fscriptUri);
                    Map<String,String> scriptParams1= URISplit.parseParams(split1.params);
                    String param1s= jo.getString("param1");
                    scriptParams1.remove(param1s);
                    String baseScriptUriMyName1= URISplit.format( null, split1.file, (Map) scriptParams1 );
                    final String hostAndPid= "managerPid: " +AutoplotUtil.getProcessId("XXX") +"\n" 
                            + "managerHost: " + InetAddress.getLocalHost().getHostName();
                    for ( int i=0; i<param1Values.length; i++ ) {
                        final int fi= i;
                        File file= new File( batchJobsDirectory,String.format("%06d",fi) );
                        try ( PrintWriter write= new PrintWriter( file) ) {
                            String scriptURI;
                            if ( baseScriptUriMyName1.endsWith("?") ) {
                                scriptURI= baseScriptUriMyName1 + param1s + "=" + param1Values[i];
                            } else{
                                scriptURI= baseScriptUriMyName1 + "&"+ param1s + "=" + param1Values[i];
                            }
                            write.println( "script: " + scriptURI );
                            write.println( hostAndPid );
                        }
                    }
                } else {
                    URISplit split1= URISplit.parse(fscriptUri);
                    Map<String,String> scriptParams1= URISplit.parseParams(split1.params);
                    scriptParams1.remove(param1Name);
                    scriptParams1.remove(param2Name);
                    String baseScriptUriMyName1= URISplit.format( null, split1.file, (Map) scriptParams1 );
                    final String hostAndPid= "managerPid: " +AutoplotUtil.getProcessId("XXX") +"\n" 
                            + "managerHost: " + InetAddress.getLocalHost().getHostName();
                    int i=0;

                    for (String param1Value : param1Values) {
                        for (String param2Value : param2Values) {
                            File file= new File( batchJobsDirectory,String.format("%06d",i) );
                            try (PrintWriter write = new PrintWriter( file)) {
                                String scriptURI;
                                if (baseScriptUriMyName1.endsWith("?")) {
                                    scriptURI = baseScriptUriMyName1 + param1Name + "=" + param1Value + "&" + param2Name + "=" + param2Value;
                                } else {
                                    scriptURI = baseScriptUriMyName1 + "&"+ param1Name + "=" + param1Value + "&" + param2Name + "=" + param2Value;
                                }
                                write.println( "script: " + scriptURI );
                                write.println( hostAndPid );
                            }
                            i=i+1;
                        }
                    }
                }
                Files.delete( specificationPendingFile.toPath() );
            }
            
            Settings s= new Settings();
            s.batchJobsDirectory= batchJobsDirectory;
            s.batchPendingDirectory= batchPendingDirectory;
            s.batchCompletedDirectory= batchCompletedDirectory;
            s.batchExceptionsDirectory= batchExceptionsDirectory;
            s.pwd= pwd;
            s.fscriptUri= fscriptUri;
            s.script= script;
            s.fparameterDescriptions= fparameterDescriptions;
            s.showEta= showEta;
            s.durationsMillis= durationsMillis;
            s.jobNumber= jobNumber;
            s.resultsStats= resultsStats;

            if ( param2Values==null || param2Values.length==0 ) {
                numberOfJobs= param1Values.length;
                monitor.setTaskSize(numberOfJobs);
                monitor.started();
                
                int ijob=0;
                                       
                for (String param1Value : param1Values) {
                    Runnable runOne = setUpOneRun(ijob, param1Name, param1Value, null, null, scriptParams, monitor, s);
                    executor.execute(runOne);
                    ijob=ijob+1;                    
                }
            } else {
                numberOfJobs= param1Values.length * param2Values.length;
                monitor.setTaskSize(numberOfJobs);
                monitor.started();
                
                int ijob=0;
                                       
                for (String param1Value : param1Values) {
                    for (String param2Value : param2Values) {
                        Runnable runOne = setUpOneRun(ijob, param1Name, param1Value, param2Name, param2Value, scriptParams, monitor, s);
                        executor.execute(runOne);
                        ijob=ijob+1;
                    }
                }
            }
            
            long lastWrite= System.currentTimeMillis();
            long lastReport= System.currentTimeMillis();
            
            int exportResultsWritten= 0;
            
            // is shutdownFile exists, then exit the batch job gracefully.
            File shutdownFile= new File( batchDirectory, "shutdown.txt" );
                    
            while ( true ) {
                if ( executor.getActiveCount()==0 && jobNumber.intValue()==numberOfJobs ) {
                    if ( isDirectoryEmpty(batchPendingDirectory) ) {
                        break;
                    }
                }
                if ( monitor.isCancelled() ) {
                    break;
                }
                if ( shutdownFile.exists() ) {
                    logger.info("shutting down after seeing shutdown.txt");
                    
                    break;
                }
                
                long t= System.currentTimeMillis();
                if ( resultsFile!=null && ( ( t-lastWrite )>1000 ) ) { // write to pending file every ten seconds.
                    if ( resultsFile.getName().endsWith(".json") ) {
                        
                    } else {
                        File pendingResultsFile= new File( resultsFile.getAbsolutePath()+".pending" );
                        int completed= jobNumber.intValue();
                        int count= completed - exportResultsWritten;
                        
                        appendResultsPendingCSV( pendingResultsFile, batchResults, resultsStats, exportResultsWritten, count);
                        String msg= "wrote records "+exportResultsWritten+"-"+completed + " to " + resultsFile.getAbsolutePath()+".pending";
                        setStatusMessage(msg);
                        exportResultsWritten= completed;
                    }
                    lastWrite= t;
                }
                
                if ( showEta && ( t - lastReport ) > 3000 ) {
                    String report;
                    while ( durationsMillis.size()>12 ) {
                        durationsMillis.removeFirst();
                    }
                    long timeFor12Jobs=0;
                    double jobCount=0.0;
                    if ( durationsMillis.size()>=12 ) {
                        try {
                            Iterator<Long> it= durationsMillis.descendingIterator();
                            for ( int i=0; i<12; i++ ) {
                                timeFor12Jobs+= it.next();
                                jobCount++;
                            }
                        } catch ( ConcurrentModificationException ex ) {
                            
                        }
                    }

                    long jobsRemaining= executor.getTaskCount() - executor.getCompletedTaskCount();
                    if ( jobCount>0 ) {
                        Datum eta= Units.milliseconds.createDatum( 
                            jobsRemaining * timeFor12Jobs / jobCount / executor.getCorePoolSize() );
                        eta= DatumUtil.asOrderOneUnits(eta);
                        String seta= String.format("%.2f%s", eta.value(), eta.getUnits() );
                        Datum avgDuration= Units.milliseconds.createDatum( timeFor12Jobs / jobCount );
                        avgDuration= DatumUtil.asOrderOneUnits(avgDuration);
                        String savgDuration= String.format("%.2f%s", avgDuration.value(), avgDuration.getUnits() );
                            
                        report= String.format( "%d remaining, avg %s, eta %s", jobsRemaining, savgDuration, seta );
                    } else {
                        report= String.format( "%d remaining", jobsRemaining );
                    }
                    
                    logger.fine(report);
                    setStatusMessage(report);
                    
                    lastReport= t;
                }
                
                //JSONObject pendingResults= new JSONObject( jo.toString() );
                //pendingResults.put( "results", new JSONArray( ja.toString() ) );
            }
            
            if ( resultsFile!=null ) { // write to pending file every ten seconds.
                if ( resultsFile.getName().endsWith(".json") ) {

                } else {
                    File pendingResultsFile= new File( resultsFile.getAbsolutePath()+".pending" );
                    int completed= jobNumber.intValue();
                    int count= completed - exportResultsWritten;

                    appendResultsPendingCSV( pendingResultsFile, batchResults, resultsStats, exportResultsWritten, count);
                    String msg= "wrote records "+exportResultsWritten+"-"+completed + " to " + resultsFile.getAbsolutePath()+".pending";
                    setStatusMessage(msg);
                    pendingResultsFile.renameTo( resultsFile );
                    
                }
            }
                
            
            if ( monitor.isCancelled() || shutdownFile.exists() ) {
                executor.shutdownNow();
            }

            
            
        } catch (JSONException ex) {
            Logger.getLogger(BatchProcessor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            
            if ( !monitor.isFinished() ) monitor.finished();
            
        }
        
    }

    private String batchParamsIdentical(Object p1,Object p2,String which) throws JSONException {
        String[] pp1;
        String[] pp2;
        if ( p1 instanceof String ) {
            pp1= ((String)p1).split("\n");
        } else {
            JSONArray ja= (JSONArray)p1;
            pp1= new String[ja.length()];
            for ( int i=0; i<ja.length(); i++ ) {
                pp1[i]= ja.getString(i);
            }
        }
        if ( p2 instanceof String ) {
            pp2= ((String)p2).split("\n");
        } else {
            JSONArray ja= (JSONArray)p2;
            pp2= new String[ja.length()];
            for ( int i=0; i<ja.length(); i++ ) {
                pp2[i]= ja.getString(i);
            }
        }
        if ( pp1.length!=pp2.length ) return which + " are different lengths";
        for ( int i=0; i<pp1.length; i++ ) {
            if ( !pp1[i].equals(pp2[i]) ) return which + " are different at index "+i;
        }
        return null;
    }
    
    /**
     * return a string indicating a different part of the JSON batch file, or null if they are the same.
     * @param jo
     * @param hostJson
     * @return
     * @throws JSONException 
     */
    private String batchTasksDifferent(JSONObject jo, JSONObject hostJson) throws JSONException {
        if ( !jo.getString("param1").equals(hostJson.getString("param1") ) ) return "param1 is different";
        if ( !jo.getString("param2").equals(hostJson.getString("param2") ) ) return "param2 is different";
        if ( !jo.getString("script").equals(hostJson.getString("script") ) ) return "script is different";
        Object p1= jo.get("param1Values");
        Object p2= hostJson.get("param1Values");
        String s= batchParamsIdentical(p1,p2,"param1Values");
        if ( s!=null ) return s;
        p1= jo.get("param2Values");
        p2= hostJson.get("param2Values");
        s= batchParamsIdentical(p1,p2,"param2Values");
        if ( s!=null ) return s;
        return null;
    }

    private static class Settings {
        File batchJobsDirectory;
        File batchPendingDirectory;
        File batchCompletedDirectory;
        File batchExceptionsDirectory;
        String pwd;
        String fscriptUri;
        String script;
        Map<String, Param> fparameterDescriptions;
        boolean showEta;
        Deque<Long> durationsMillis;
        AtomicInteger jobNumber;
        JSONArray resultsStats;
    }
    
    private Runnable setUpOneRun(int ijob, 
            String param1Name, String param1Value, 
            String param2Name, String param2Value,
            Map<String, String> scriptParams, ProgressMonitor monitor,
            Settings s ) throws JSONException {
        final int fijob= ijob;
        final String fparam1Name= param1Name;
        final String fparam2Name= param2Name;
        final String fparam1Value= param1Value;
        final String fparam2Value= param2Value;
        final Map<String,String> fscriptParams= scriptParams;
        final JSONObject frunResults= new JSONObject();
        Runnable runOne= () -> {
            if ( monitor.isCancelled() ) return;
            
            try {
                if ( s.batchJobsDirectory!=null ) {
                    synchronized (BatchProcessor.this) {
                        // Note even though this is synchronized,
                        // the idea is that other machines might also
                        // be working on this.
                        File jobFile= new File( s.batchJobsDirectory,String.format("%06d",fijob) );
                        if ( !jobFile.exists() ) {
                            logger.log(Level.FINE, "someone else grabbed {0}", jobFile);
                            return; // someone else grabbed the task
                        }
                        File pendingFile= new File( s.batchPendingDirectory,String.format("%06d",fijob) );
                        try {
                            Files.move( jobFile.toPath(), pendingFile.toPath(), StandardCopyOption.ATOMIC_MOVE );
                        } catch ( IOException ex ) {
                            if ( jobFile.exists() ) {
                                logger.log(Level.WARNING, "there was an issue when moving {0}", jobFile);
                            } else {
                                logger.log(Level.FINE, "someone else grabbed {0}", jobFile);
                                return; // someone else grabbed the task
                            }                            
                        }
                        
                        try {
                            Path path= pendingFile.toPath();
                            String text= "guestHost: " + InetAddress.getLocalHost().getHostName() + "\n" +
                                    "guestPid: " + AutoplotUtil.getProcessId("XXX") +"\n" +
                                    "guestThread: " + Thread.currentThread().getName() + "\n";
                            Files.write(path, text.getBytes(), StandardOpenOption.APPEND );
                        } catch (IOException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                        
                    }
                }
                
                long t0= System.currentTimeMillis();
                JSONObject runResults=
                        doOneJob( fijob, s.pwd,
                                s.fscriptUri,
                                s.script,
                                s.fparameterDescriptions,
                                fscriptParams,
                                fparam1Name,
                                fparam1Value,
                                fparam2Name,
                                fparam2Value,
                                monitor.getSubtaskMonitor(fparam1Name) );
                long timeToComplete= System.currentTimeMillis()-t0;
                if ( s.showEta ) {
                    try{
                        s.durationsMillis.addLast(timeToComplete);
                    } catch ( Exception ex ) {
                        logger.warning("Exception...");
                    }
                }
                
                if ( s.batchJobsDirectory!=null ) {
                    String exception= runResults.optString("result","").replaceAll("\n"," ").trim();
                    if ( exception.length()>240 ) exception= exception.substring(0,237)+"...";
                    File pendingFile= new File( s.batchPendingDirectory,String.format("%06d",fijob) );
                    File completeOrExceptionFile;
                    if ( exception.length()>0 ) {
                        completeOrExceptionFile= new File( s.batchExceptionsDirectory,String.format("%06d",fijob) );
                    } else {
                        completeOrExceptionFile= new File( s.batchCompletedDirectory,String.format("%06d",fijob) );
                    }
                    if ( !pendingFile.renameTo(completeOrExceptionFile) ) {
                        throw new IllegalArgumentException("couldn't rename "+pendingFile);
                    }
                    try {
                        Path path= completeOrExceptionFile.toPath();
                        String text= "runTimeMs: " + timeToComplete + "\n" + "exception: "+exception + "\n";
                        Files.write(path, text.getBytes(), StandardOpenOption.APPEND );
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
                if ( runResults==null ) return; // Cancel pressed
                Iterator keyIterator= runResults.keys();
                while ( keyIterator.hasNext() ) {
                    String k= (String) keyIterator.next();
                    try {
                        frunResults.put( k, runResults.get(k) );
                    } catch (JSONException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
                
                try {
                    synchronized ( BatchProcessor.this ) {
                        s.resultsStats.put( fijob, runResults );
                    }
                } catch (JSONException ex) {
                    logger.log(Level.SEVERE, null, ex);
                } catch ( ArrayIndexOutOfBoundsException ex ) {
                    logger.log(Level.SEVERE, null, ex);
                }
                
            } catch ( RuntimeException ex ) {
                ex.printStackTrace(); //TODO: do something with this.
            } finally {
                if ( monitor.isFinished() ) {
                    logger.fine("monitor reports being finished though it shouldn't have been.");
                } else {
                    monitor.setTaskProgress(s.jobNumber.incrementAndGet());
                    
                }
            }
        };
        return runOne;
    }

    /**
     * return true if the directory is empty
     * @param lbatchQueueDirectory
     * @return
     * @throws IOException 
     */
    private boolean isDirectoryEmpty(File lbatchQueueDirectory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(lbatchQueueDirectory.toPath())) {
            return !stream.iterator().hasNext();
        }
    }
    
    public static void main( String[] args ) throws IOException {
        Application dom= new ScriptContext2023().getDocumentModel();
        String batchFile= "https://github.com/autoplot/dev/blob/master/demos/2019/20190726/runBatch2.batch"; 
        ProgressMonitor monitor= DasProgressPanel.createFramed("Run Batch");
        BatchProcessor processor= new BatchProcessor();
        processor.setWritePngTemplate("/tmp/ap/mypng_%08.3f.png");
        processor.setResultsFile( new File("/tmp/ap/results.csv"));
        processor.setThreads(6);
        processor.runBatchScript(dom, batchFile, monitor);
        processor.addPropertyChangeListener(BatchProcessor.PROP_STATUSMESSAGE, (PropertyChangeEvent evt) -> {
            System.err.println(evt.getNewValue());
        });
        System.err.println("Done!");
        System.exit(0);
    }
}
