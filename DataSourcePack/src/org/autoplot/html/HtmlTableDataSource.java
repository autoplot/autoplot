
package org.autoplot.html;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.html.parser.ParserDelegator;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;

/**
 * Data source for extracting data from HTML tables.  This has been used
 * for looking at real estate sales and weather history.
 * @author jbf
 */
public class HtmlTableDataSource extends AbstractDataSource {

    /**
     * the parameter name (not label) to plot
     */
    public static final String PARAM_COLUMN= "column";
    public static final String PARAM_TABLE= "table";
    
    public HtmlTableDataSource(URI uri) {
        super(uri);
    }

    /**
     * read the table from the file.
     * @param mon
     * @return
     * @throws IOException 
     */
    public QDataSet getTable( ProgressMonitor mon ) throws IOException  {
        File f= getHtmlFile(resourceURI.toURL(),mon);

        try (BufferedReader reader = new BufferedReader( new FileReader(f))) {

            HtmlParserCallback callback = new HtmlParserCallback(  );

            String stable= (String)getParams().get( PARAM_TABLE );
            if ( stable!=null ) callback.setTable( stable );
            new ParserDelegator().parse( reader, callback, true );

            QDataSet ds= callback.getDataSet();

            return ds;
            
        }
    }

    @Override
    public QDataSet getDataSet( ProgressMonitor mon ) throws IOException {
        QDataSet ds = getTable( mon );

        String column=  (String) getParams().get(PARAM_COLUMN);
        if ( column==null ) {
            return ds;
        } else {
            try {
                int icol= Integer.parseInt(column);
                return DataSetOps.unbundle( ds, icol );
            } catch ( NumberFormatException ex ) {
                return DataSetOps.unbundle( ds, column );
            }
        }
        
    }

    /**
     * return a list of the tables, with column and human readable description after.
     * @return a list of the tables, with column and human readable description after.
     * @throws java.io.IOException 
     */
    public List<String> getTables() throws java.io.IOException {
        File f= getHtmlFile( resourceURI.toURL(), new NullProgressMonitor() );

        BufferedReader reader = new BufferedReader( new FileReader(f));

        HtmlParserCallback callback = new HtmlParserCallback(  );

        String stable= (String)getParams().get( PARAM_TABLE );
        if ( stable!=null ) callback.setTable( stable );
        new ParserDelegator().parse( reader, callback, true );

        List<String> tables= new ArrayList(callback.getTables());

        return tables;

    }
}
