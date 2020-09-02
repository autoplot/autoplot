/*
 * DataSourceFactory.java
 *
 * Created on May 4, 2007, 6:34 AM
 */

package org.autoplot.datasource;

import java.net.URI;
import java.util.List;
import org.das2.util.monitor.ProgressMonitor;

/**
 * DataSourceFactories create data sources which resolve a URI into a QDataSet.
 * Some DataSourceFactories support discovery, meaning they will provide
 * a GUI to create URIs with no other information.
 * @author jbf
 */
public interface DataSourceFactory {
    
    /**
     * return a dataSource for the url
     * @param uri the URI
     * @return the DataSource
     * @throws java.lang.Exception any exception may occur.
     */
    DataSource getDataSource( URI uri ) throws Exception;
        
    /**
     * return a list of context-sensitive completions.  If an exception is thrown, then an
     * a error dialog is displayed for RuntimeExceptions.  Compile-time exceptions may be
     * displayed more gently, relying on getMessage to aid the human operator.
     * @param cc the context for the completion.
     * @param mon a progress monitor, for example to monitor a file download.
     * @return a set of completions
     * @throws java.lang.Exception
     */
    public List<CompletionContext> getCompletions( CompletionContext cc, ProgressMonitor mon ) throws Exception;

    /**
     * return additional tools for creating valid URIs, such as TimeSeriesBrowse.  This may soon include
     * a file selector, and an automatic GUI created from the completions model.
     * @param <T>
     * @param clazz the class, such as org.autoplot.datasource.capability.TimeSeriesBrowse
     * @return the capability, such as an instance of org.autoplot.datasource.capability.TimeSeriesBrowse
     */
    public <T> T getCapability( Class<T> clazz );
    
    /**
     * quick check to see that an uri looks acceptable.
     *
     * since 2012b, this should provide a list of objects marking reasons for rejecting.  Though each object
     * is simply a marker, toString method of each should provide some meaningful information to developers.
     * This list will be passed into the editor if available.
     * @param suri the uri.
     * @param problems list to which problems should be added. TODO: human readable or PROB_TIMERANGE?
     * @param mon a progress monitor, for example to monitor a file download.
     * @return true if the string cannot be used as a URI.
     */
    public boolean reject( String suri, List<String> problems, ProgressMonitor mon );
    
    /**
     * mark that this source has an editor that allows discovery of data,
     * and the GUI can be entered with "vap+ext:"  This must be consistent
     * with the reject method of the editor.
     * @return true if the data source factory supports discovery.
     */
    public boolean supportsDiscovery();
    
    /**
     * true if the data source is based on files.  For example, a CDF file
     * is true, since the URIs contain cdf file names, and while vap+cdaweb 
     * uses files to move data, it is not file based.  This is initially used
     * to limit the entries in file choosers.
     * @return true if the data source is based on files.
     */
    public boolean isFileResource();
    
    /**
     * return a short description of the factory, or empty string.  For example,
     * "NASA Common Data Format (CDF) Files" or "NASA CDAWeb".  This will 
     * be used to identify files or discovery sources.
     * @return a short description of the factory.
     */
    public String getDescription();
    
}
