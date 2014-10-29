/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import static org.autoplot.pngwalk.PngWalkView.logger;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.das2.util.monitor.AlertNullProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.dsops.Ops;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author jbf
 */
public class ClickDigitizer {
    
    //WalkImageSequence seq;
    PngWalkView view;
    
    public ClickDigitizer( PngWalkView view ) {
        this.view= view;
    }
    
    /**
     * return the node containing JSON metadata showing where the plots are.
     * @param file
     * @return null or the JSON describing the image.  See 
     * @throws IOException 
     */
    private String getJSONMetadata( File file ) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(file);
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

        if (readers.hasNext()) {

            // pick the first available ImageReader
            ImageReader reader = readers.next();

            // attach source to the reader
            reader.setInput(iis, true);

            // read metadata of first image
            IIOMetadata metadata = reader.getImageMetadata(0);
            //PNGMetadata m= (PNGMetadata)metadata;
            IIOMetadataNode n= (IIOMetadataNode)metadata.getAsTree("javax_imageio_png_1.0");
            NodeList nl= n.getElementsByTagName("tEXtEntry");
            for ( int i=0; i<nl.getLength(); i++ ) {
                Element e= (Element)nl.item(i);
                String n3= e.getAttribute("keyword");
                if ( n3.equals("plotInfo") ) {
                    return e.getAttribute("value");
                }
            }
        }
        return null;
    }
    
    private JSONObject getPlotContaining( JSONArray plots, int x, int y ) throws JSONException {
        for ( int i=0; i<plots.length(); i++ ) {
            JSONObject plot= plots.getJSONObject(i);
            JSONObject yaxis= plot.getJSONObject("yaxis");
            int t1= yaxis.getInt("top");
            int t2= yaxis.getInt("bottom");
            if ( t1>t2 ) { // swap
                t2= yaxis.getInt("top"); 
                t1= yaxis.getInt("bottom");
            }
            if ( t1<=y && y<t2 ) {
                JSONObject xaxis= plot.getJSONObject("xaxis");
                t1= xaxis.getInt("left");
                t2= xaxis.getInt("right");
                if ( t1>t2 ) { // swap
                    t2= yaxis.getInt("left");
                    t1= yaxis.getInt("right");
                }
                if ( t1<=x && x<t2) {
                    return plot;
                }
            }
        }
        return null;
    }
    
    /**
     * returns rank 0 datum, with SCALE_TYPE and LABEL set.
     * @param xaxis the axis
     * @return 
     */
    private QDataSet lookupDatum( JSONObject xaxis, int p, String smaller, String bigger ) throws JSONException, ParseException {
        boolean xlog= xaxis.get("type").equals("log");
        DatumRange xrange;

        if ( "UTC".equals( xaxis.getString("units") ) ) {
            xrange= DatumRangeUtil.parseISO8601Range( xaxis.getString("min")+"/"+xaxis.getString("max") );

        } else {
            String sunits= "";
            sunits= xaxis.getString("units"); 
            Units units= Units.lookupUnits(sunits);
            xrange= new DatumRange(units.parse(xaxis.getString("min")),
                  units.parse(xaxis.getString("max")) );
            
        }
        double nn= ( p - xaxis.getInt(smaller) ) / ((double) ( xaxis.getInt(bigger) - xaxis.getInt(smaller) ) );
        
        Datum result;
        if ( xlog ) {
            DatumRange rr= DatumRangeUtil.rescaleLog( xrange, nn, nn );
            result= rr.min();
        } else {
            DatumRange rr= DatumRangeUtil.rescale( xrange, nn, nn );
            result= rr.min();            
        }
        QDataSet r= DataSetUtil.asDataSet(result);
        r= Ops.putProperty( r, QDataSet.LABEL, xaxis.getString("label") );
        r= Ops.putProperty( r, QDataSet.SCALE_TYPE, xlog ? "log" : "linear" );
        
        return r;
        
    }
    
    /**
     * look up the richPng metadata within the png images.
     * @param x
     * @param y 
     */
    protected void doLookupMetadata( int x, int y ) throws IOException, ParseException {
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= getJSONMetadata( file );
        if ( json!=null ) {
            try {
                JSONObject jo = new JSONObject( json );
                JSONArray plots= jo.getJSONArray("plots");
                JSONObject plot= getPlotContaining( plots, x, y );
                if ( plot!=null ) {
                    JSONObject xaxis= plot.getJSONObject("xaxis");
                    QDataSet xx= lookupDatum( xaxis, x, "left", "right" );
                    JSONObject yaxis= plot.getJSONObject("yaxis");
                    QDataSet yy= lookupDatum( yaxis, y, "bottom", "top" );
                    view.seq.setStatus(  "" + xx + ", "+ yy );
                }
            } catch (JSONException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            logger.log(Level.SEVERE, null, "no json available");
        }
        
    }


}
