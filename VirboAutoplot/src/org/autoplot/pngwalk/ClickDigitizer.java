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
import javax.swing.JOptionPane;
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
 * Quick-n-dirty class for picking off points from images.  The ClickDigitizer knows how to 
 * grab JSON metadata from the image (http://autoplot.org/richPng) and transform the pixel
 * location to a dataset.
 * @author jbf
 */
public class ClickDigitizer {
    
    //WalkImageSequence seq;
    PngWalkView view;
    PngWalkTool1 viewer;
    
    public ClickDigitizer( PngWalkView view ) {
        this.view= view;
    }
    
    void setViewer( PngWalkTool1 viewer ) {
        this.viewer= viewer;
    }
    
    /**
     * return the node containing JSON metadata showing where the plots are.
     * @param file the png file.
     * @return null or the JSON describing the image.  See http://autoplot.org/developer.richPng
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
            try {
                IIOMetadataNode n= (IIOMetadataNode)metadata.getAsTree("javax_imageio_png_1.0");
                NodeList nl= n.getElementsByTagName("tEXtEntry");
                for ( int i=0; i<nl.getLength(); i++ ) {
                    Element e= (Element)nl.item(i);
                    String n3= e.getAttribute("keyword");
                    if ( n3.equals("plotInfo") ) {
                        return e.getAttribute("value");
                    }
                }
            } catch ( IllegalArgumentException ex ) {
                logger.log( Level.FINE, ex.getMessage() );
                return null;
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
     * @param axis the axis
     * @return rank 0 dataset containing the point.
     */
    private QDataSet transform( JSONObject axis, int p, String smaller, String bigger ) throws JSONException, ParseException {
        boolean log= axis.get("type").equals("log");
        DatumRange range;

        if ( "UTC".equals( axis.getString("units") ) ) {
            range= DatumRangeUtil.parseISO8601Range( axis.getString("min")+"/"+axis.getString("max") );

        } else {
            String sunits= "";
            sunits= axis.getString("units"); 
            Units units= Units.lookupUnits(sunits);
            range= new DatumRange(units.parse(axis.getString("min")),
                  units.parse(axis.getString("max")) );
            
        }
        double nn= ( p - axis.getInt(smaller) ) / ((double) ( axis.getInt(bigger) - axis.getInt(smaller) ) );
        
        Datum result;
        if ( log ) {
            DatumRange rr= DatumRangeUtil.rescaleLog( range, nn, nn );
            result= rr.min();
        } else {
            DatumRange rr= DatumRangeUtil.rescale( range, nn, nn );
            result= rr.min();            
        }
        QDataSet r= DataSetUtil.asDataSet(result);
        r= Ops.putProperty( r, QDataSet.LABEL, axis.getString("label") );
        r= Ops.putProperty( r, QDataSet.SCALE_TYPE, log ? "log" : "linear" );
        
        return r;
        
    }
    
    /**
     * from data to pixel space.
     * @param axis
     * @param datum
     * @param smaller
     * @param bigger
     * @return Integer.MAX_VALUE or the valid transform.
     * @throws JSONException
     * @throws ParseException 
     * @throws 
     */
    int invTransform( JSONObject axis, Datum datum, String smaller, String bigger ) throws JSONException, ParseException  {
        boolean log= axis.get("type").equals("log");
        DatumRange range;
        
        if ( "UTC".equals( axis.getString("units") ) ) {
            range= DatumRangeUtil.parseISO8601Range( axis.getString("min")+"/"+axis.getString("max") );

        } else {
            String sunits;
            sunits= axis.getString("units"); 
            Units units= Units.lookupUnits(sunits);
            range= new DatumRange(units.parse(axis.getString("min")),
                  units.parse(axis.getString("max")) );
            
        }        
        
        if ( range.getUnits().isConvertableTo(datum.getUnits()) && range.contains(datum) ) {
            if ( log ) {
                double d= DatumRangeUtil.normalizeLog( range, datum );
                return (int)( axis.getInt(smaller) + d * (axis.getInt(bigger)-axis.getInt(smaller)) );
            } else {
                double d= DatumRangeUtil.normalize( range, datum );
                return (int)( axis.getInt(smaller) + d * (axis.getInt(bigger)-axis.getInt(smaller)) );
            }
        } else {
            return Integer.MAX_VALUE;
        }
        
    }
    
    
    
    /**
     * look up the richPng metadata within the png images.  If the metadata is not
     * available, then the x and y coordinates, with 0,0 in the lower-left corner, are used.
     * Note the output has y=0 at the bottom to be consistent with the ImageDataSource.
     * @param x x coordinate in image where 0 is the left side.
     * @param y y coordinate in image where 0 is the top.  Note the output has y=0 at the bottom.
     * @throws ParseException when the JSON cannot be parsed.
     * @throws IOException when the file cannot be read.
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
                    QDataSet xx= transform( xaxis, x, "left", "right" );
                    JSONObject yaxis= plot.getJSONObject("yaxis");
                    QDataSet yy= transform( yaxis, y, "bottom", "top" );
                    
                    if ( viewer!=null ) {
                        view.seq.setStatus(  "Plot Coordinates: " + xx + ", "+ yy );
                        if ( viewer.digitizer!=null ) {
                            try {
                                viewer.digitizer.addDataPoint( DataSetUtil.asDatum(xx), DataSetUtil.asDatum(yy) );
                            } catch ( RuntimeException ex ) { // units conversion
                                JOptionPane.showMessageDialog( viewer,ex.getMessage());
                            }
                        }
                    } else {
                        view.seq.setStatus(  "Plot Coordinates: " + xx + ", "+ yy + "  (Options->Start Digitizer to record)");
                    }
                }
            } catch (JSONException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
                Datum xx= Units.dimensionless.createDatum(x);
                Datum yy= Units.dimensionless.createDatum(h-y);
                view.seq.setStatus(  "Pixel Coordinates: " + xx + ", "+ yy + " (unable to use JSON) " );
                
            }
        } else {
            int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
            Datum xx= Units.dimensionless.createDatum(x);
            Datum yy= Units.dimensionless.createDatum(h-y);
            if ( viewer!=null ) {
                view.seq.setStatus( "Pixel Coordinates: " + xx + ", "+ yy );                
                if ( viewer.digitizer!=null ) {
                    try {
                        viewer.digitizer.addDataPoint( xx, yy );
                    } catch ( RuntimeException ex ) { // units conversion
                        JOptionPane.showMessageDialog( viewer,ex.getMessage());
                    }
                } else {
                    
                }
            } else {
                view.seq.setStatus(  "Pixel Coordinates: " + xx + ", "+ yy + "  (Options->Start Digitizer to record)");
            }
        }
        
    }

    /**
     * return rank 2 bundle dataset that is ds[n;i,j]
     */
    QDataSet doTransform() throws IOException {
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= getJSONMetadata( file );
        QDataSet ds = viewer.digitizer.getDataSet();
        if ( ds==null ) return null;
        if ( ds.length()==0 ) return null;
        QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
        if ( ds.rank()>1 ) {
            ds= ds.slice(0);
        }
        if (dep0.rank()>1 ){
            dep0= dep0.slice(0);
        }
        
        if ( json==null ) {
            return ds;
        } else {
            QDataSet result=null;
            for ( int ii= 0; ii<ds.length(); ii++ ) {
                Datum x= DataSetUtil.asDatum( dep0.slice(ii) ); //TODO: make this a bundle!
                Datum y= DataSetUtil.asDatum( ds.slice(ii) );
                try {
                    JSONObject jo = new JSONObject( json );
                    JSONArray plots= jo.getJSONArray("plots");
                    for ( int i= 0; i<plots.length(); i++ ) {
                        JSONObject plot= plots.getJSONObject(i);
                        JSONObject xaxis= plot.getJSONObject("xaxis");
                        JSONObject yaxis= plot.getJSONObject("yaxis");
                        int ix= invTransform( xaxis, x, "left", "right");
                        int iy= invTransform( yaxis, y, "bottom", "top" );
                        if ( ix!=Integer.MAX_VALUE && iy!=Integer.MAX_VALUE ) {
                            result= Ops.join( result, Ops.join( DataSetUtil.asDataSet(ix), DataSetUtil.asDataSet(iy) ) );
                        }
                    }
                } catch ( ParseException ex ){

                } catch ( JSONException ex ){
                }                
            }
            return result;
        }
    }


}
