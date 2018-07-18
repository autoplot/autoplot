
package org.autoplot.datasource;

import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;

/**
 * @author jbf
 */
public interface DataSourceFormat {

    /**
     * Format the dataset using the specified URI.  This should be parsed the same way 
     * read URIs are parsed, and arguments should reflect those of the reader 
     * when possible.  If the uri refers to a file and the folder which will contain
     * the file does not exist, it should be created.
     * @param uri
     * @param data
     * @param mon
     * @throws Exception
     */
    public void formatData( String uri, QDataSet data, ProgressMonitor mon  ) throws Exception;

    /**
     * return true if the dataset can be formatted
     * @param ds
     * @return 
     */
    public boolean canFormat( QDataSet ds );

//    /**
//     * stream the data.  
//     * Do not use this--it is likely to change.
//     * @param params parameters for streaming.
//     * @param data iterator of records to be streamed.
//     * @param out the output stream accepting the formatted data
//     * @return true if the data can be streamed.
//     * @throws Exception 
//     */
//    public boolean streamData( Map<String,String> params, Iterator<QDataSet> data, OutputStream out ) throws Exception;
        
    /**
     * return a description of this format
     * @return 
     */
    public String getDescription();
        
    
}
