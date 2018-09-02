
package org.autoplot.imagedatasource;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DataSetIterator;
import org.das2.qds.DataSetOps;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetUtil;

/**
 * Format data to RGB images, or ARGB images.
 * Formatter presumes data is:<ul>
 * <li>(m,n,4) for ARGB 
 * <li>(3,m,n) RGB. (this should not be used)
 * <li>(m,n,3) RGB.
 * </ul>
 * When data is (m,n,4) then ds[:,:,0] should be the alpha channel,
 * [:,:,1] should be the red channel, and so on.
 * @author jbf
 */
public class ImageDataSourceFormat implements DataSourceFormat {

    /**
     * Converts the components of a color, as specified by the default RGB
     * model, to an equivalent set of values for hue, saturation, and
     * brightness that are the three components of the HSB model.
     * NOTE these values will be a bit different than those returned by 
     * ImageDataSource's hue, saturation, and value channels, because of slightly
     * different mappings.
     * @param rgb
     * @return hsv array [h,w,3]
     * @see       java.awt.Color#RGBtoHSB(int, int, int, float[]) 
     * @see #fromHSVtoRGB(org.das2.qds.QDataSet) 
     */
    public static QDataSet fromRGBtoHSV( QDataSet rgb ) {
        float hue, saturation, brightness;

        ArrayDataSet result= ArrayDataSet.create( float.class, DataSetUtil.qubeDims(rgb) );
        
        int rows= rgb.length();
        int cols= rgb.length(0);
        
        for ( int ii=0; ii<rows; ii++ ) {
            for ( int jj=0; jj<cols; jj++ ) {
                int r= (int)rgb.value( ii, jj, 0 );
                int g= (int)rgb.value( ii, jj, 1 );
                int b= (int)rgb.value( ii, jj, 2 );
                int cmax = (r > g) ? r : g;
                if (b > cmax) cmax = b;
                int cmin = (r < g) ? r : g;
                if (b < cmin) cmin = b;

                brightness = ((float) cmax) / 255.0f;
                if (cmax != 0)
                    saturation = ((float) (cmax - cmin)) / ((float) cmax);
                else
                    saturation = 0;
                if (saturation == 0)
                    hue = 0;
                else {
                    float redc = ((float) (cmax - r)) / ((float) (cmax - cmin));
                    float greenc = ((float) (cmax - g)) / ((float) (cmax - cmin));
                    float bluec = ((float) (cmax - b)) / ((float) (cmax - cmin));
                    if (r == cmax)
                        hue = bluec - greenc;
                    else if (g == cmax) {
                        hue = 2.0f + redc - bluec;
                    } else {
                        hue = 4.0f + greenc - redc;
                    }
                    hue = hue / 6.0f;
                    if (hue < 0) {
                        hue = hue + 1.0f;
                    }
                }
                result.putValue( ii, jj, 0, hue*360 );
                result.putValue( ii, jj, 1, saturation*100 );
                result.putValue( ii, jj, 2, brightness*100 );
            }
        }
        return result;
    }
    
    /**
     * convert HSV QDataSet to RGB.  The input should be a rank 3 dataset
     * with rows for the first index, columns for the second index, and 
     * 3 indeces for the last index:<ul>
     * <li>H, the hue, should vary from 0 to 360.
     * <li>S, the Saturation, should vary from 0 to 100.
     * <li>V, the Value, should vary from 0 to 100.
     * </ul>
     * @param hsv rank 3, [rows;columns;h,s,v] dataset.
     * @return rank 3, [rows;columns;r,g,b] dataset.
     * @see java.awt.Color#HSBtoRGB(float, float, float) where this code was found and converted to QDataSet.
     * @see #fromRGBtoHSV(org.das2.qds.QDataSet) 
     */
    public static QDataSet fromHSVtoRGB( QDataSet hsv ) {
        
        ArrayDataSet result= ArrayDataSet.create( float.class, DataSetUtil.qubeDims(hsv) );
        
        int rows= hsv.length();
        int cols= hsv.length(0);
        
        for ( int ii=0; ii<rows; ii++ ) {
            for ( int jj=0; jj<cols; jj++ ) {
                int r = 0, g = 0, b = 0;
                float saturation= (float)(hsv.value(ii,jj,1)/100);
                float brightness= (float)(hsv.value(ii,jj,2)/100);
                
                if (saturation == 0) {
                    r = g = b = (int) (brightness * 255.0f + 0.5f);
                } else {
                    float hue= ((float)hsv.value(ii,jj,0)/360);
                    
                    float h = (hue - (float)Math.floor(hue)) * 6.0f;
                    float f = h - (float)java.lang.Math.floor(h);
                    float p = brightness * (1.0f - saturation);
                    float q = brightness * (1.0f - saturation * f);
                    float t = brightness * (1.0f - (saturation * (1.0f - f)));
                    switch ((int) h) {
                    case 0:
                        r = (int) (brightness * 255.0f + 0.5f);
                        g = (int) (t * 255.0f + 0.5f);
                        b = (int) (p * 255.0f + 0.5f);
                        break;
                    case 1:
                        r = (int) (q * 255.0f + 0.5f);
                        g = (int) (brightness * 255.0f + 0.5f);
                        b = (int) (p * 255.0f + 0.5f);
                        break;
                    case 2:
                        r = (int) (p * 255.0f + 0.5f);
                        g = (int) (brightness * 255.0f + 0.5f);
                        b = (int) (t * 255.0f + 0.5f);
                        break;
                    case 3:
                        r = (int) (p * 255.0f + 0.5f);
                        g = (int) (q * 255.0f + 0.5f);
                        b = (int) (brightness * 255.0f + 0.5f);
                        break;
                    case 4:
                        r = (int) (t * 255.0f + 0.5f);
                        g = (int) (p * 255.0f + 0.5f);
                        b = (int) (brightness * 255.0f + 0.5f);
                        break;
                    case 5:
                        r = (int) (brightness * 255.0f + 0.5f);
                        g = (int) (p * 255.0f + 0.5f);
                        b = (int) (q * 255.0f + 0.5f);
                        break;
                    default:
                        // do nothing (gray)
                    }
                }
                
                result.putValue(ii,jj,0,r);
                result.putValue(ii,jj,1,g);
                result.putValue(ii,jj,2,b);
            }
        }
        return result;
    }
    
    
    
    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        BufferedImage im;

        QDataSet r,g,b;
        QDataSet alpha=null;

        int h, w;
        if ( data.length()==3 ) {
            im= new BufferedImage( data.length(0), data.length(0,0), BufferedImage.TYPE_INT_RGB );
            r= data.slice(0);
            g= data.slice(1);
            b= data.slice(2);
            w= data.length(0);
            h= data.length(0,0);

        } else if ( data.length(0,0)==4 ) {
            im= new BufferedImage( data.length(), data.length(0), BufferedImage.TYPE_INT_ARGB);
            alpha= DataSetOps.slice2( data, 0 );
            r= DataSetOps.slice2( data, 1 );
            g= DataSetOps.slice2( data, 2 );
            b= DataSetOps.slice2( data, 3 );
            
            w= data.length();
            h= data.length(0);

        } else {
            im= new BufferedImage( data.length(), data.length(0), BufferedImage.TYPE_INT_RGB);
            r= DataSetOps.slice2( data, 0 );
            g= DataSetOps.slice2( data, 1 );
            b= DataSetOps.slice2( data, 2 );
            w= data.length();
            h= data.length(0);
        }

        URISplit split= URISplit.parse(uri);

        DataSetIterator it= new QubeDataSetIterator(data);
        boolean warn= true;
        while ( it.hasNext() ) {
            it.next();
            double v= it.getValue(data);
            if ( warn && ( v<0 || v>=256. ) ) {
                System.err.println("element out of range 0-255: "+v );
                warn= false;
            }
        }

        if ( alpha==null ) {
            for ( int i=0; i<w; i++ ) {
                for ( int j=0; j<h; j++ ) {
                    im.setRGB( i, h-j-1, 
                            (int)(r.value(i,j))*256*256 
                            + (int)(g.value(i,j))*256 
                            + (int)b.value(i,j) );
                }
            }
        } else {
            for ( int i=0; i<w; i++ ) {
                for ( int j=0; j<h; j++ ) {
                    im.setRGB( i, h-j-1, 
                            (int)(alpha.value(i,j)) *256*256*256 
                            + (int)(r.value(i,j))*256*256 
                            + (int)(g.value(i,j))*256
                            + (int)(b.value(i,j)) );
                }
            }

        }

        int i= split.file.lastIndexOf(".");
        String ext= split.file.substring(i+1);

        if ( ImageIO.write( (RenderedImage)im, ext, new File( split.resourceUri ) ) ) {

        } else {
            throw new IOException("unable to find writer for "+ext);
        }

    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return ds.rank()==3 && ( ds.length()<5 || ds.length(0,0)<5 );
    }

    @Override
    public String getDescription() {
        return "Image Format";
    }

}
