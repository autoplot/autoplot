/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.html;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.util.List;
import javax.swing.text.html.parser.ParserDelegator;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;

/**
 *
 * @author jbf
 */
public class HtmlTableParser extends AbstractDataSource {

    /**
     * the parameter name (not label) to plot
     */
    public static final String PARAM_COLUMN= "column";
    public static final String PARAM_TABLE= "table";

    public HtmlTableParser(URI uri) {
        super(uri);
    }

    public QDataSet getTable( ProgressMonitor mon ) throws Exception {
        File f= getHtmlFile(resourceURI.toURL(),mon);

        BufferedReader reader = new BufferedReader( new FileReader(f));

        HtmlParserCallback callback = new HtmlParserCallback(  );

        String stable= (String)getParams().get( PARAM_TABLE );
        if ( stable!=null ) callback.setTable( stable );
        new ParserDelegator().parse( reader, callback, true );

        QDataSet ds= callback.getDataSet();

        return ds;
    }

    public QDataSet getDataSet( ProgressMonitor mon ) throws Exception {
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

    public List<String> getTables() throws java.io.IOException {
        File f= getHtmlFile( resourceURI.toURL(), new NullProgressMonitor() );

        BufferedReader reader = new BufferedReader( new FileReader(f));

        HtmlParserCallback callback = new HtmlParserCallback(  );

        String stable= (String)getParams().get( PARAM_TABLE );
        if ( stable!=null ) callback.setTable( stable );
        new ParserDelegator().parse( reader, callback, true );

        List<String> tables;
        tables= callback.getTables();

        return tables;

    }
}
