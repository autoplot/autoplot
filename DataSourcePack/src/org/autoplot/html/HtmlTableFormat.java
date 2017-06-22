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
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSourceFormat;

/**
 *
 * @author jbf
 */
public class HtmlTableFormat  extends AbstractDataSourceFormat {

    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        if ( data.rank()==2 ) {
            formatDataRank2( uri, data, mon );
        } else if ( data.rank()==1 ) {
            formatDataRank1( uri, data, mon );
        }
    }
    
    public void formatDataRank2(String uri, QDataSet data, ProgressMonitor mon) throws Exception {    
        setUri(uri);
        File f= new File( getResourceURI() );
        BufferedWriter w= new BufferedWriter( new FileWriter(f) );
        QDataSet bds= (QDataSet) data.property(QDataSet.BUNDLE_1);
        w.write("<body><table>\n");
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
            w.append("</th>\n");
        }
        w.append("</tr>\n");

        for ( int i=0; i<data.length(); i++ ) {
            StringBuilder b= new StringBuilder();
            b.append("<tr>");
            for ( int j=0; j<data.length(0); j++ ) {
                b.append("<td>");
                Units u= (Units) bds.property(QDataSet.UNITS,j);
                if ( u==null ) u= Units.dimensionless;
                Datum d= u.createDatum(data.value(i,j));
                b.append( d.getFormatter().format(d,u) );
                b.append("</td>\n");
            }
            b.append("</tr>\n");
            w.write( b.toString() );
        }
        w.write("</table></body>");
        w.close();
    }

    public void formatDataRank1(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        QDataSet dep0= SemanticOps.xtagsDataSet(data);

        Units u;
        Datum d;

        setUri(uri);
        File f= new File( getResourceURI() );
        
        BufferedWriter w= new BufferedWriter( new FileWriter(f) );
        w.write("<body><table>\n");
        w.append("<th>");
        u= SemanticOps.getUnits(dep0);
        String h= (String)dep0.property(QDataSet.LABEL);
        if ( h!=null ) {
            w.append(h);
        }
        if ( u!=Units.dimensionless ){
            w.append("(");
            w.append(u.toString());
            w.append(")");
        }
        w.append("</th>\n");
        
        w.append("<th>");
        u= SemanticOps.getUnits(data);
        h= (String)data.property(QDataSet.LABEL);
        if ( h!=null ) {
            w.append(h);
        }
        if ( u!=Units.dimensionless ){
            w.append("(");
            w.append(u.toString());
            w.append(")");
        }
        w.append("</th>");
        
        w.append("</tr>\n");

        
        for ( int i=0; i<data.length(); i++ ) {
            StringBuilder b= new StringBuilder();
            b.append("<tr>");
            b.append("<td>");
            u= SemanticOps.getUnits(dep0);
            if ( u==null ) u= Units.dimensionless;
            d= u.createDatum(dep0.value(i));
            b.append( d.getFormatter().format(d,u) );
            b.append("</td>");
            b.append("<td>");
            u= SemanticOps.getUnits(data);
            d= u.createDatum(data.value(i));
            b.append( d.getFormatter().format(d,u) );
            b.append("</td>");
            b.append("</tr>\n");
            w.write( b.toString() );
        }
        w.write("</table></body>\n");
        w.close();
    }
    
    @Override
    public boolean canFormat(QDataSet ds) {
        return ds.rank()==2 || ds.rank()==1;
    }

    @Override
    public String getDescription() {
        return "HTML Table";
    }
    
}
