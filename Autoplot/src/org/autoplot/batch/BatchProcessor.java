
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
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
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
 * will be one "host" machine and multiple "worker" machines.  The host will
 * set up the batchDirectory to contain the subdirectories:
 * <ul>
 * <li>jobs - jobs to be run
 * <li>pending - job claimed by workers
 * <li>complete - job completed by workers
 * </ul>
 * A job is moved from one directory to the next as each job is performed.  Note
 * this is a guess at how this should work, and may change.
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
     * @param f1
     * @param f2
     * @param uri
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
            for ( String s: ss ) {
                argList.add(s);
            }
        } else {
            if ( f1.trim().length()>0 ) {
                argList.add(f1);
            }
        }
        if ( f2.contains(";") ) {
            String[] ss= f2.split("\\;",-2);
            for ( String s: ss ) {
                argList.add(s);
            }
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
                        args[i]= Integer.parseInt(argList.get(i));
                        break;
                    case 'f':
                    case 'e':
                        args[i]= Double.parseDouble(argList.get(i));
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
            String[] paramNames1= maybeSplitMultiParam( param1Name );

            if ( paramNames1!=null ) {
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
                if ( !parameterDescriptions.containsKey(param1Name) ) {
                    if ( param1Name.length()==0 ) {
                        throw new IllegalArgumentException("param1Name not set");
                    }
                }
                // set all the default values, and values set for all runs
                for ( Map.Entry<String,String> e: params.entrySet() ) {
                    String pname= e.getKey();
                    if ( parameterDescriptions.get(pname)!=null ) { //TODO: When does this happen?  See file:/Users/jbf/Desktop/git/dev/demos/2025/20250130/brokenParamDescription.jy?resourceURI='https://pds-ppi.igpp.ucla.edu/data/JNO-J_SW-JAD-5-CALIBRATED-V1.0/DATA/2018/2018091/ELECTRONS/JAD_L50_HRS_ELC_TWO_DEF_2018091_V01.LBL'&doplot=False
                        setParam( interp, pwd, parameterDescriptions.get(pname), pname, e.getValue() );
                    }
                }
                setParam( interp, pwd, parameterDescriptions.get(param1Name), param1Name, param1Value );
                runResults.put(param1Name,param1Value);
                scriptParams.put(param1Name,param1Value);                
                
            }
            
            if ( param2Name!=null && param2Name.length()>0 ) {
                String[] paramNames2= maybeSplitMultiParam( param2Name );

                if ( paramNames2!=null ) {
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
                    if ( !parameterDescriptions.containsKey(param2Name) ) {
                        if ( param2Name.length()==0 ) {
                            throw new IllegalArgumentException("param1Name not set");
                        }
                    }
                    // set all the default values, and values set for all runs
                    for ( Map.Entry<String,String> e: params.entrySet() ) {
                        String pname= e.getKey();
                        if ( parameterDescriptions.get(pname)!=null ) { //TODO: When does this happen?  See file:/Users/jbf/Desktop/git/dev/demos/2025/20250130/brokenParamDescription.jy?resourceURI='https://pds-ppi.igpp.ucla.edu/data/JNO-J_SW-JAD-5-CALIBRATED-V1.0/DATA/2018/2018091/ELECTRONS/JAD_L50_HRS_ELC_TWO_DEF_2018091_V01.LBL'&doplot=False
                            setParam( interp, pwd, parameterDescriptions.get(pname), pname, e.getValue() );
                        }
                    }
                    setParam( interp, pwd, parameterDescriptions.get(param2Name), param2Name, param2Value );
                    runResults.put(param2Name,param2Value);
                    scriptParams.put(param2Name,param2Value);                

                }                
            }

            long t0= System.currentTimeMillis();
            
            OutputStream outs;
            ByteArrayOutputStream outbaos;
            if ( batchDirectory!=null ) {
                File outf= new File( new File( batchDirectory, "stdout" ), String.format("%06d",jobNumber) );
                if ( !outf.getParentFile().exists() ) {
                    outf.getParentFile().mkdirs();
                } 
                outbaos= new ByteArrayOutputStream();
                outs= new TeeOutputStream( new FileOutputStream( outf ), outbaos );
            } else {
                outbaos= new ByteArrayOutputStream();
                outs= outbaos;
            }
            
            //ByteArrayOutputStream outbaos= 
            try {
                interp.setOut(outs);
                interp.execfile( new ByteArrayInputStream(script.getBytes("US-ASCII")), name );
                String uri= URISplit.format( "script", split.resourceUri.toString(), scriptParams );
                String doWriteTemplate= this.writePngTemplate;
                if ( doWriteTemplate.length()>0 ) {
                    runResults.put("writeFile", doWrite( doWriteTemplate, param1Value, "", uri, myDom ) );
                }

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
        
            ThreadFactory tf= (Runnable r) -> new Thread( r, "run-batch-"+threadCounter.incrementAndGet());
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(initialThreadCount,tf);
            Deque<Long> durationsMillis= new ArrayDeque<>(); 
            
            final AtomicInteger jobNumber= new AtomicInteger(0);
            
            boolean showEta= "true".equals( System.getProperty("RunBatchTool.eta","true") ); 
            
            int i1=0;
            
            Map<String,Object> env= new HashMap<>();
            env.put("dom",dom);
            env.put("PWD",pwd);
            
            final Map<String,Param> fparameterDescriptions=Util.getParams( env, script, params, new NullProgressMonitor() );
           
            int numberOfJobs;
            
            File lbatchQueueDirectory= null;
            File lbatchPendingDirectory= null;
            File lbatchCompleteDirectory= null;
            File lbatchStdoutDirectory= null;
            
            boolean batchWorker= false; // a batchWorker is a machine which works on a batch but does not set it up.
            
            if ( batchDirectory!=null ) {
                if ( !batchDirectory.exists() ) {
                    if ( !batchDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+batchDirectory);
                    }
                }
                lbatchQueueDirectory= new File( batchDirectory, "jobs" );
                if ( !lbatchQueueDirectory.exists() ) {
                    if ( !lbatchQueueDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchQueueDirectory);
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
                lbatchStdoutDirectory= new File( batchDirectory, "stdout" );
                if ( !lbatchStdoutDirectory.exists() ) {
                    if ( !lbatchStdoutDirectory.mkdirs() ) {
                        throw new IllegalArgumentException("Unable to make directory: "+lbatchStdoutDirectory);
                    }
                }
                
                File specificationFile = new File( batchDirectory, "main.batch" );
                
                if ( specificationFile.exists() ) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(lbatchQueueDirectory.toPath())) {
                        if ( !stream.iterator().hasNext() ) {
                            specificationFile.delete();
                        }
                    }
                }
                if ( specificationFile.exists() ) {
                    batchWorker= true;
                    logger.info("Running batch as worker");
                } else {
                    try ( PrintWriter write= new PrintWriter( specificationFile ) ) {
                        write.append(batchFileJson);
                    }
                    emptyDirectory(lbatchQueueDirectory);
                    emptyDirectory(lbatchPendingDirectory);
                    emptyDirectory(lbatchCompleteDirectory);
                    emptyDirectory(lbatchStdoutDirectory);
                    logger.info("Running batch as manager");
                }
                
            }
            
            final File batchQueueDirectory= lbatchQueueDirectory;
            final File batchPendingDirectory= lbatchPendingDirectory;
            final File batchCompletedDirectory= lbatchCompleteDirectory;
            
            JSONObject batchResults= new JSONObject();
            JSONArray resultsStats= new JSONArray();
            
            batchResults.put( "results", resultsStats );
            
            JSONArray paramsJson= new JSONArray();
            paramsJson.put(0,jo.getString("param1"));
            if ( jo.getString("param2").length()>0 ) {
                paramsJson.put(1,jo.getString("param2"));
            }
            
            batchResults.put("params", paramsJson );
            
            // If using a batchDirectory and we are the manager, then queue up all the jobs.
            if ( batchPendingDirectory!=null && !batchWorker ) {
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
                        File file= new File( batchQueueDirectory,String.format("%06d",fi) );
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
                    throw new IllegalArgumentException("second parameter not supported");
                    //TODO: second parameter
                }
            }
            
            if ( param2Values==null || param2Values.length==0 ) {
                numberOfJobs= param1Values.length;
                monitor.setTaskSize(numberOfJobs);
                monitor.started();
                for ( int i=0; i<param1Values.length; i++ ) {
                    final int fi= i;
                    final String fparam1= jo.getString("param1");
                    final String fparam1Value= param1Values[i];
                    final Map<String,String> fscriptParams= scriptParams;
                    final JSONObject frunResults= new JSONObject();
                    
                    Runnable runOne= () -> {
                        if ( monitor.isCancelled() ) return;
                        
                        try {
                            if ( batchQueueDirectory!=null ) {
                                synchronized (BatchProcessor.this) {
                                    // Note even though this is synchronized, 
                                    // the idea is that other machines might also 
                                    // be working on this.
                                    File queueFile= new File( batchQueueDirectory,String.format("%06d",fi) );
                                    if ( !queueFile.exists() ) {
                                        logger.log(Level.FINE, "someone else grabbed {0}", queueFile);
                                        return; // someone else grabbed the task
                                    }
                                    File pendingFile= new File( batchPendingDirectory,String.format("%06d",fi) );
                                    if ( !queueFile.renameTo(pendingFile) ) {
                                        if ( queueFile.exists() ) {
                                            logger.log(Level.WARNING, "there was an issue when moving {0}", queueFile);
                                        } else {
                                            logger.log(Level.FINE, "someone else grabbed {0}", queueFile);
                                            return; // someone else grabbed the task
                                        }
                                    }
                                    try {
                                        Path path= pendingFile.toPath();
                                        String text= "workerHost: " + InetAddress.getLocalHost().getHostName() + "\n" + 
                                            "workerPid: " + AutoplotUtil.getProcessId("XXX") +"\n" +
                                            "workerThread: " + Thread.currentThread().getName() + "\n";
                                        Files.write(path, text.getBytes(), StandardOpenOption.APPEND );
                                    } catch (IOException ex) {
                                        logger.log(Level.SEVERE, null, ex);
                                    }
                                    
                                }
                            }
                            
                            long t0= System.currentTimeMillis();
                            JSONObject runResults= 
                                    doOneJob( fi, pwd,
                                            fscriptUri,
                                            script, 
                                            fparameterDescriptions, 
                                            fscriptParams,
                                            fparam1, 
                                            fparam1Value, 
                                            null,
                                            null,
                                            monitor.getSubtaskMonitor(fparam1) );
                            long timeToComplete= System.currentTimeMillis()-t0;
                            if ( showEta ) {
                                try{
                                    durationsMillis.addLast(timeToComplete);
                                } catch ( Exception ex ) {
                                    logger.warning("Exception...");
                                }                                
                            }
                            
                            if ( batchQueueDirectory!=null ) {
                                File pendingFile= new File( batchPendingDirectory,String.format("%06d",fi) );
                                File completedFile= new File( batchCompletedDirectory,String.format("%06d",fi) );
                                if ( !pendingFile.renameTo(completedFile) ) {
                                    throw new IllegalArgumentException("couldn't rename "+pendingFile);
                                }
                                try {
                                    Path path= completedFile.toPath();
                                    String exception= runResults.optString("result","").replaceAll("\n"," ");
                                    String text= "runTimeMs: " + timeToComplete + "\n" + "exception: "+exception;
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

                            int icount= jobNumber.get();

                            try {
                                resultsStats.put( icount, runResults );
                            } catch (JSONException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                            
                        } catch ( RuntimeException ex ) {
                            ex.printStackTrace(); //TODO: do something with this.
                        } finally {
                            if ( monitor.isFinished() ) {
                                logger.fine("monitor reports being finished though it shouldn't have been.");
                            } else {
                                monitor.setTaskProgress(jobNumber.incrementAndGet());

                            }
                        }
                    };
                    executor.execute(runOne);
                    i1=i1+1;                    
                }
            } else {
                throw new IllegalArgumentException("second parameter not supported");
            }
            
            long lastWrite= System.currentTimeMillis();
            long lastReport= System.currentTimeMillis();
            long messageNumber= 0; // toggle between messages
            
            int exportResultsWritten= 0;
            
            while ( true ) {
                if ( executor.getActiveCount()==0 && jobNumber.intValue()==numberOfJobs ) {
                    break;
                }
                if ( monitor.isCancelled() ) {
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
                        long removed = durationsMillis.removeFirst();
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
                
            
            if ( monitor.isCancelled() ) executor.shutdownNow();

            
            
        } catch (JSONException ex) {
            Logger.getLogger(BatchProcessor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            
            if ( !monitor.isFinished() ) monitor.finished();
            
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
        processor.addPropertyChangeListener( BatchProcessor.PROP_STATUSMESSAGE, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                System.err.println(evt.getNewValue());
            }
        });
        System.err.println("Done!");
        System.exit(0);
    }
}
