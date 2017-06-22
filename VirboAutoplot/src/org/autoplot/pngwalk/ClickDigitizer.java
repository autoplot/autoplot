/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.util.ImageUtil;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.AlertNullProgressMonitor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.das2.qds.ops.Ops;

/**
 * Quick-n-dirty class for picking off points from images.  The ClickDigitizer knows how to 
 * grab JSON metadata from the image (http://autoplot.org/richPng) and invTransform the pixel
 * location to a dataset.
 * @author jbf
 */
public class ClickDigitizer {
    
    //WalkImageSequence seq;
    PngWalkView view;
    PngWalkTool viewer;
    
    private static final Logger logger= LoggerManager.getLogger("autoplot.pngwalk");
    
    public ClickDigitizer( PngWalkView view ) {
        this.view= view;
    }
    
    void setViewer( PngWalkTool viewer ) {
        this.viewer= viewer;
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
     * invTransform from pixel space to data space.
     * returns rank 0 datum, with SCALE_TYPE and LABEL set.
     * @param axis the axis
     * @param p the pixel location
     * @param smaller the location of the smaller pixel row/column (e.g. "top" or "right" )
     * @param bigger the location of the bigger pixel row/column (e.g. "bottom" or "left" )
     * @return rank 0 dataset containing the point, and labels and scale type.
     */
    private QDataSet invTransform( JSONObject axis, int p, String smaller, String bigger ) throws JSONException, ParseException {
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
        double nn= ( ( p + 0.5 ) - axis.getInt(smaller) ) / ((double) ( axis.getInt(bigger) - axis.getInt(smaller) ) );
        
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
     * @param axis axis description in JSON
     * @param datum the point in data space of the axis.
     * @param smaller the location of the smaller pixel row/column (e.g. "top" or "right" )
     * @param bigger the location of the bigger pixel row/column (e.g. "bottom" or "left" )
     * @return Integer.MAX_VALUE or the valid transform
     * @throws JSONException
     * @throws ParseException 
     */
    private int transform1D( JSONObject axis, Datum datum, String smaller, String bigger ) throws JSONException, ParseException  {
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
        
        if ( range.getUnits().isConvertibleTo(datum.getUnits()) && range.contains(datum) ) {
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
        doLookupMetadata( x, y, false );
    }
    
    /**
     * look up the richPng metadata within the png images.  If the metadata is not
     * available, then the x and y coordinates, with 0,0 in the lower-left corner, are used.
     * Note the output has y=0 at the bottom to be consistent with the ImageDataSource.
     * @param x x coordinate in image where 0 is the left side.
     * @param y y coordinate in image where 0 is the top.  Note the output has y=0 at the bottom.
     * @param release true if the mouse is released and PngWalkTool.PROP_MOUSERELEASELOCATION change should be fired.
     * @throws ParseException when the JSON cannot be parsed.
     * @throws IOException when the file cannot be read.
     */
    protected void doLookupMetadata( int x, int y, boolean release ) throws IOException, ParseException {
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= ImageUtil.getJSONMetadata( file );
        Map meta= new HashMap();
        meta.put( "image", view.seq.getSelectedName() );
        
        if ( json!=null ) {
            try {
                JSONObject jo = new JSONObject( json );
                JSONArray plots= jo.getJSONArray("plots");
                JSONObject plot= getPlotContaining( plots, x, y );
                if ( plot!=null ) {
                    JSONObject xaxis= plot.getJSONObject("xaxis");
                    QDataSet xx= invTransform( xaxis, x, "left", "right" );
                    JSONObject yaxis= plot.getJSONObject("yaxis");
                    QDataSet yy= invTransform( yaxis, y, "bottom", "top" );
                    
                    if ( viewer!=null ) {
                        view.seq.setStatus(  "Plot Coordinates: " + xx + ", "+ yy );
                        if ( release==false && viewer.digitizer!=null && viewer.digitizerRecording ) {
                            try {
                                viewer.digitizer.addDataPoint( DataSetUtil.asDatum(xx), DataSetUtil.asDatum(yy), meta );
                            } catch ( RuntimeException ex ) { // units conversion
                                String msg= DataSourceUtil.getMessage(ex);
                                JOptionPane.showMessageDialog( viewer, msg );
                            }
                        }
                        QDataSet q= Ops.bundle( xx, yy );
                        if ( release ) {
                            viewer.firePropertyChange( PngWalkTool.PROP_MOUSERELEASELOCATION, null, q );
                        } else {
                            viewer.firePropertyChange( PngWalkTool.PROP_MOUSEPRESSLOCATION, null, q );
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
                if ( release==false && viewer.digitizer!=null ) {
                    try {
                        viewer.digitizer.addDataPoint( xx, yy, meta );
                        //viewer.digitizer.addDataPoint( xx, yy );
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
     * 
     * @param json null or the JSON
     * @param x x in the canvas frame.
     * @param y y in the canvas frame.
     * @return rank 1 bundle x,y.
     * @throws IOException
     * @throws ParseException 
     */
    private QDataSet doTransformPoint( String json, int x, int y ) throws IOException, ParseException {

        if ( json!=null ) {
            try {
                JSONObject jo = new JSONObject( json );
                JSONArray plots= jo.getJSONArray("plots");
                JSONObject plot= getPlotContaining( plots, x, y );
                if ( plot!=null ) {
                    JSONObject xaxis= plot.getJSONObject("xaxis");
                    QDataSet xx= invTransform( xaxis, x, "left", "right" );
                    JSONObject yaxis= plot.getJSONObject("yaxis");
                    QDataSet yy= invTransform( yaxis, y, "bottom", "top" );
                    return Ops.bundle( xx, yy );
                } else {
                    return null;
                }
             } catch (JSONException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
                Datum xx= Units.dimensionless.createDatum(x);
                Datum yy= Units.dimensionless.createDatum(h-y);
                return  Ops.bundle( Ops.dataset(xx), Ops.dataset(yy) );
             }
        } else {
            int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
            Datum xx= Units.dimensionless.createDatum(x);
            Datum yy= Units.dimensionless.createDatum(h-y);
            return Ops.bundle( Ops.dataset(xx), Ops.dataset(yy) );
        }
    }
            
    /**
     * return rank 2 bundle dataset that is ds[n;i,j]
     * or null if there are no points.  The point 0,0 is in 
     * the upper right corner of the image.
     * This is [N;x,y] where N is the number of points.
     * @return null or rank 2 bundle.
     * @throws java.io.IOException
     */
    protected QDataSet doTransform() throws IOException {
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= ImageUtil.getJSONMetadata( file ); // json might be null after
        QDataSet ds = viewer.digitizer.getDataSet();
        if ( ds==null ) return null;
        if ( ds.length()==0 ) return null;
        if ( ds.rank()>1 ) {
            QDataSet images= Ops.slice1(ds,1);
            EnumerationUnits eu= (EnumerationUnits)SemanticOps.getUnits(images);
            QDataSet r= Ops.where( Ops.eq( images, eu.createDatum(view.seq.getSelectedName()) ) );
            if ( r.length()==0 ) {
                return null;
            }
            ds= DataSetOps.applyIndex( ds, 0, r, true );
            ds= Ops.slice1(ds,0);
        }
        QDataSet dep0= (QDataSet) ds.property(QDataSet.DEPEND_0);
        if (dep0.rank()>1 ){
            dep0= dep0.slice(0);
        }
        
        if ( json==null ) {
            BufferedImage im= view.seq.imageAt( view.seq.getIndex() ).getImageIfLoaded();
            if ( im==null ) return null;
            return Ops.bundle( dep0, Ops.subtract( Ops.dataset(im.getHeight()), ds ) );
            
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
                        int ix= transform1D( xaxis, x, "left", "right");
                        int iy= transform1D( yaxis, y, "bottom", "top" );
                        if ( ix!=Integer.MAX_VALUE && iy!=Integer.MAX_VALUE ) {
                            result= Ops.join( result, Ops.join( DataSetUtil.asDataSet(ix), DataSetUtil.asDataSet(iy) ) );
                        }
                    }
                } catch ( ParseException | JSONException ex ){
                    logger.log(Level.SEVERE, "error parsing rich png JSON metadata", ex );
                }                
            }
            return result;
        }
    }

    /**
     * select the datapoint that is near the click position, based on the annoTypeChar
     * @param p
     * @return
     * @throws IOException
     * @throws ParseException 
     */
    protected int maybeSelect(Point p) throws IOException, ParseException {
        
        if ( viewer==null || viewer.digitizer==null ) {
            return -1;
        }
        
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= ImageUtil.getJSONMetadata( file );
        QDataSet ds1= doTransformPoint(json, p.x-2, p.y-2 );
        QDataSet ds2= doTransformPoint(json, p.x+2, p.y+2 );
        if ( ds1==null ) return -1;
        
        DatumRange xrange= DatumRangeUtil.union( DataSetUtil.asDatum(ds1.slice(0)), DataSetUtil.asDatum(ds2.slice(0)) );
        DatumRange yrange= DatumRangeUtil.union( DataSetUtil.asDatum(ds1.slice(1)), DataSetUtil.asDatum(ds2.slice(1)) );

        if ( !UnitsUtil.isTimeLocation( SemanticOps.getUnits(ds2) ) ) {
            return -1;
        } 
        
        int isel;
        
        if ( viewer.digitizer!=null ) {
            switch (viewer.annoTypeChar) {
                case '.':
                    isel= viewer.digitizer.select(xrange, yrange );
                    break;
                case '+':
                    isel= viewer.digitizer.select(xrange, yrange, true );
                    break;
                case '|':
                    isel= viewer.digitizer.select(xrange, null);
                    break;
                default:
                    throw new RuntimeException("can't find annoTypeChar");
            }
            return isel;
            
        } else {
            return -1;
        }
        
        
    }
}
