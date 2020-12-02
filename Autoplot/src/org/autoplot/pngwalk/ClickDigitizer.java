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
    
//    /**
//     * invTransform from data space to pixel space.
//     * returns rank 0 datum, with SCALE_TYPE and LABEL set.
//     * @param axis the axis
//     * @param d the data location
//     * @param smaller the location of the smaller pixel row/column (e.g. "top" or "right" )
//     * @param bigger the location of the bigger pixel row/column (e.g. "bottom" or "left" )
//     * @return rank 0 dataset containing the point, and labels and scale type.
//     */
//    private QDataSet invInverseTransform( JSONObject axis, QDataSet d, String smaller, String bigger ) throws JSONException, ParseException {
//        boolean log= axis.get("type").equals("log");
//        DatumRange range;
//
//        if ( "UTC".equals( axis.getString("units") ) ) {
//            range= DatumRangeUtil.parseISO8601Range( axis.getString("min")+"/"+axis.getString("max") );
//
//        } else {
//            String sunits;
//            sunits= axis.getString("units"); 
//            Units units= Units.lookupUnits(sunits);
//            range= new DatumRange(units.parse(axis.getString("min")),
//                  units.parse(axis.getString("max")) );
//            
//        }
//        
//        DatumRangeUtil.rescaleInverse( range, d )
//        
//        double nn= ( ( p + 0.5 ) - axis.getInt(smaller) ) / ((double) ( axis.getInt(bigger) - axis.getInt(smaller) ) );
//        
//        Datum result;
//        if ( log ) {
//            DatumRange rr= DatumRangeUtil.rescaleLog( range, nn, nn );
//            result= rr.min();
//        } else {
//            DatumRange rr= DatumRangeUtil.rescale( range, nn, nn );
//            result= rr.min();            
//        }
//        QDataSet r= DataSetUtil.asDataSet(result);
//        r= Ops.putProperty( r, QDataSet.LABEL, axis.getString("label") );
//        r= Ops.putProperty( r, QDataSet.SCALE_TYPE, log ? "log" : "linear" );
//        
//        return r;
//        
//        
//    }
//    

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
        
        if ( range.getUnits().isConvertibleTo(datum.getUnits()) ) {
            double d;
            if ( log ) {
                d= DatumRangeUtil.normalizeLog( range, datum );
            } else {
                d= DatumRangeUtil.normalize( range, datum );
            }
            int result= (int)( axis.getInt(smaller) + d * (axis.getInt(bigger)-axis.getInt(smaller)) );
            if ( result<-10000 ) {
                result= -10000;
            } else if ( result>10000 ) {
                result= 10000;
            }
            return result;
        } else {
            return axis.getInt(smaller);
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
                        if ( viewer.digitizer!=null && UnitsUtil.isTimeLocation( SemanticOps.getUnits(xx) ) ) {
                            viewer.digitizer.setSorted(true);
                        }
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
                        viewer.digitizer.setSorted(false);
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
     * return the coordinates for the click in data coordinates if the JSON
     * Rich PNG metadata is available, or just the pixel coordinates if it
     * is not, with the property "PlotNumber" indicating which plot number
     * in the JSON is used.  The property "PlotNumber" 
     * will be an integer equal -1 if the rich png metadata is not found,
     * or zero or positive int for valid clicks.  If the x and y are not within
     * a plot, then -1 is returned for the plot number.
     * @param x the horizontal pixel coordinate
     * @param y the vertical pixel coordinate, with 0 at the top.
     * @return two-element bundle QDataSet with PlotNumber property.  -1 
     *   indicates no plot found at the location, and -99 means no rich png data.
     * @throws IOException
     * @throws ParseException 
     */
    public QDataSet pixelToDataTransform( int x, int y ) throws IOException, ParseException {
        if ( view==null ) {
            throw new IllegalArgumentException("view is not attached");
        }
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= ImageUtil.getJSONMetadata( file );
        return doTransformPoint(json, -1, x, y );
    }
    
    /**
     * return the coordinates for the click in data coordinates if the JSON
     * Rich PNG metadata is available, or just the pixel coordinates if it
     * is not, with the property "PlotNumber" indicating which plot number
     * in the JSON is used.  The property "PlotNumber" 
     * will be an integer equal -1 if the rich png metadata is not found,
     * or zero or positive int for valid clicks.  If the x and y are not within
     * a plot, then null (None) is returned.
     * @param iplot the plot number, or -1 to indicate whichever plot x and y are within.
     * @param x the horizontal position
     * @param y the vertical position, with 0 being the top of the plot.
     * @return two-element bundle QDataSet with PlotNumber property.  -1 
     *   indicates no plot found at the location, and -99 means no rich png data.
     * @throws IOException
     * @throws ParseException 
     */
    public QDataSet pixelToDataTransform( int iplot, int x, int y ) throws IOException, ParseException {
        if ( view==null ) {
            throw new IllegalArgumentException("view is not attached");
        }
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= ImageUtil.getJSONMetadata( file );
        return doTransformPoint(json, iplot, x, y );
    }
    
    /**
     * return the pixel coordinates for a given data coordinates.
     * @param iplot the plot number
     * @param p bundle of x and y data coordinates.
     * @return int[2] for the x and y pixel coordinates (0,0 is upper left).
     * @throws IOException
     * @throws ParseException 
     */
    public int[] dataToPixelTransform( int iplot, QDataSet p ) throws IOException, ParseException {
        if ( view==null ) {
            throw new IllegalArgumentException("view is not attached");
        }
        URI uri= view.seq.imageAt( view.seq.getIndex() ).getUri();
        File file = DataSetURI.getFile( uri, new AlertNullProgressMonitor("get image file") ); // assume it's local.
        String json= ImageUtil.getJSONMetadata( file );
        return doInvTransformPoint( json, iplot, p );
    }
    
    /**
     * This will transform the point x,y to data coordinates.  If iplot is
     * -1, then the x and y coordinates will pick the first plot which contains,
     * otherwise the plot number is used.  The result is a two-element bundle
     * QDataSet with the property "PlotNumber" which will indicate the plot 
     * number used.  If iplot is -1 and x and y are not within a plot, then 
     * the PlotNumber will be -1.  If the richPng metadata is not available
     * (null passed in for json), then -99 is returned for the plot number and
     * the pixel coordinate is returned.
     * 
     * @param json null or the JSON
     * @param iplot -1 or the plot number.
     * @param x x in the canvas frame.
     * @param y y in the canvas frame.
     * @return rank 1 bundle x,y.
     * @throws IOException
     * @throws ParseException 
     */
    private QDataSet doTransformPoint( String json, int iplot, int x, int y) throws IOException, ParseException {

        if ( json!=null ) {
            try {
                JSONObject jo = new JSONObject( json );
                JSONArray plots= jo.getJSONArray("plots");
                JSONObject plot;
                if ( iplot==-1 ) {
                    plot= getPlotContaining( plots, x, y );
                } else {
                    plot= plots.getJSONObject( iplot );
                }
                if ( plot!=null ) {
                    JSONObject xaxis= plot.getJSONObject("xaxis");
                    QDataSet xx= invTransform( xaxis, x, "left", "right" );
                    JSONObject yaxis= plot.getJSONObject("yaxis");
                    QDataSet yy= invTransform( yaxis, y, "bottom", "top" );
                    QDataSet result= Ops.bundle( xx, yy );
                    for ( int i=0; i<plots.length(); i++ ) {
                        if ( plots.getJSONObject(i)==plot ) {
                            result= Ops.putProperty( result, "PlotNumber",  i );
                        }
                    }
                    return result;
                } else {
                    QDataSet result= Ops.replicate( Double.NaN, 2 );
                    result= Ops.putProperty( result, "PlotNumber",  -1 );
                    return result;
                }
             } catch (JSONException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
                Datum xx= Units.dimensionless.createDatum(x);
                Datum yy= Units.dimensionless.createDatum(h-y);
                QDataSet result= Ops.bundle( Ops.dataset(xx), Ops.dataset(yy) );
                result= Ops.putProperty( result, "PlotNumber",  -1 );
                return result;
             }
        } else {
            int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
            Datum xx= Units.dimensionless.createDatum(x);
            Datum yy= Units.dimensionless.createDatum(h-y);
            QDataSet result= Ops.bundle( Ops.dataset(xx), Ops.dataset(yy) );
            result= Ops.putProperty( result, "PlotNumber",  -99 );
            return result;
        }
    }
    
    /**
     * 
     * @param json null or the JSON
     * @param iplot the plot number
     * @param x x in the canvas frame.
     * @param y y in the canvas frame.
     * @return rank 1 bundle x,y.
     * @throws IOException
     * @throws ParseException 
     */
    private int[] doInvTransformPoint( String json, int iplot, QDataSet ds ) throws IOException, ParseException {
        if ( json!=null ) {
            try {
                JSONObject jo = new JSONObject( json );
                JSONArray plots= jo.getJSONArray("plots");
                Object o= ds.property("PlotNumber");
                JSONObject plot= plots.getJSONObject(iplot);
                if ( plot!=null ) {
                    JSONObject xaxis= plot.getJSONObject("xaxis");
                    int ii= transform1D( xaxis, Ops.datum(ds.slice(0)), "left", "right" );
                    JSONObject yaxis= plot.getJSONObject("yaxis");
                    int jj= transform1D( yaxis, Ops.datum(ds.slice(1)), "bottom", "top" );
                    return new int[] { ii, jj };
                } else {
                    return null;
                }
             } catch (JSONException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
                Datum xx= Units.dimensionless.createDatum(ds.value(0));
                Datum yy= Units.dimensionless.createDatum(h-ds.value(1));
                return new int[] { (int)xx.value(), (int)yy.value() };
             }
        } else {
            int h= view.seq.imageAt( view.seq.getIndex() ).getImage().getHeight();
            Datum xx= Units.dimensionless.createDatum(ds.value(0));
            Datum yy= Units.dimensionless.createDatum(h-ds.value(1));
            return new int[] { (int)xx.value(), (int)yy.value() };
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
     * @return -1 if no selection is obvious, or the index in the digitized points dataset.
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
        QDataSet ds1= doTransformPoint(json, -1, p.x-2, p.y-2 );
        QDataSet ds2= doTransformPoint(json, -1, p.x+2, p.y+2 );
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
