/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.html;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSourceFormat;

/**
 *
 * @author jbf
 */
public class HtmlTableFormat  extends AbstractDataSourceFormat {

    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        setUri(uri);
        File f= new File( getResourceURI() );
        BufferedWriter w= new BufferedWriter( new FileWriter(f) );
        QDataSet bds= (QDataSet) data.property(QDataSet.BUNDLE_1);
        w.write("<body><table>");
        for ( int j=0; j<bds.length(); j++ ) {
            w.append("<th>");
            Units u= (Units) bds.property(QDataSet.UNITS,j);
            if ( u==null ) u= Units.dimensionless;
            String h= (String)bds.property(QDataSet.LABEL,j);
            if ( h!=null ) {
                w.append(h);
            }
            if ( u!=Units.dimensionless ){
                w.append("(");
                w.append(u.toString());
                w.append(")");
            }
            w.append("</th>");
        }
        w.append("</tr>");

        for ( int i=0; i<data.length(); i++ ) {
            StringBuilder b= new StringBuilder();
            b.append("<tr>");
            for ( int j=0; j<data.length(0); j++ ) {
                b.append("<td>");
                Units u= (Units) bds.property(QDataSet.UNITS,j);
                if ( u==null ) u= Units.dimensionless;
                Datum d= u.createDatum(data.value(i,j));
                b.append( d.getFormatter().format(d,u) );
                b.append("</td>");
            }
            b.append("</tr>");
            w.write( b.toString() );
        }
        w.write("</table></body>");
        w.close();
    }

    public boolean canFormat(QDataSet ds) {
        return ds.rank()==2;
    }

    public String getDescription() {
        return "HTML Table";
    }
    
}
