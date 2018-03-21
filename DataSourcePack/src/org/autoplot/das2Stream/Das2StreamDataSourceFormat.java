
package org.autoplot.das2Stream;

import java.io.File;
import java.io.FileOutputStream;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;
import org.das2.qstream.QdsToD2sStream;
import org.das2.qstream.StreamException;

/**
 * Format the data into das2streams.
 * @author jbf
 */
public class Das2StreamDataSourceFormat implements DataSourceFormat {

    @Override
    public void formatData( String url, QDataSet data, ProgressMonitor mon) throws Exception {

        URISplit split = URISplit.parse(url);
        java.util.Map<String, String> params= URISplit.parseParams(split.params);

        boolean binary= "binary".equals( params.get( "type" ) );
         if (split.ext.equals(".qds")) {
            FileOutputStream fo=null;
            try {
                fo= new FileOutputStream( new File( split.resourceUri ) );
                if ( SemanticOps.isBundle(data) ) {
                    new org.das2.qstream.BundleStreamFormatter().format( data, fo, !binary );
                } else {
                    new org.das2.qstream.SimpleStreamFormatter().format( data, fo, !binary );
                }
            } finally {
                if ( fo!=null ) fo.close();
            }
        } else {
			  FileOutputStream fo = null;
           try {
				  fo= new FileOutputStream( new File( split.resourceUri ) );
				  QdsToD2sStream writer;
				  if(binary)  writer = new QdsToD2sStream(QdsToD2sStream.FORMAT_2_2);
				  else writer = new QdsToD2sStream(QdsToD2sStream.FORMAT_2_2, 5, 3);  
				  
				  if(!writer.write(data, fo)){
					  throw new StreamException("Dataset is rank 3 or otherwise incompatiable "
						                         + "with the das2 basic stream foramt");
				  }
				  
			  }
			  finally {
				   if ( fo!=null ) fo.close();
			  }
        }
    }

    @Override
    public boolean canFormat(QDataSet ds) {
		 // Can't answer this question until we know the output format, which is not
		 // given here.  The Das2 stream source and QStream source need to be split 
		 // since they have different capabilities.
		 // writer = new QdsToD2sStream();
		 // writer.canWrite(ds);  Answers the question.
		 //
        return true; // at least it should, so if it can't it's a bug elsewhere.
    }

    @Override
    public String getDescription() {
        return "Das2Stream data transfer format";
    }

}
