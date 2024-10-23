
package org.autoplot.imagedatasource;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;

/**
 * Adapt a BufferedImage to a rank 2 or rank 3 QDataSet, using
 * ColorOp to extract red, green, or blue channels.  When mask
 * is null and op is null, then a rank 3 dataset [w,h,3] is returned, 
 * a bundle of red, green, blue channels.
 * @author jbf
 */
public class ImageDataSet extends AbstractDataSet {

    BufferedImage image;
    ColorOp op;
    int w,h;
    private int rank;
    
    public interface ColorOp {
        double value( int rgb );
    }

    private static class ChannelColorOp implements ColorOp {
        int mask;
        int rot;
        ChannelColorOp( int mask, int rot ) {
            this.mask= mask;
            this.rot= rot;
        }
        @Override
        public double value( int rgb ) {
            return ( rgb & mask ) >> rot;
        }
    }
    
    private double log2( double d ) {
        return Math.log(d)/Math.log(2);
    }
    
    
    /**
     * create a dataset from the image,
     * returning a rank 3 dataset ds[w,h,3].
     * @param image the image
     */
    public ImageDataSet( BufferedImage image ) {
        this( image, null, null );
    }
    
    /**
     * create a dataset from the image.  When mask and op are null,
     * then a rank 3 dataset ds[w,h,3] is returned.
     * @param image the image
     * @param mask null or the mask, where the image is ANDed with this color, and shifted by the lowest one bit location.
     * @param op null or an operation, such as grayscale, to convert the data to rank 2.
     */
    public ImageDataSet( BufferedImage image, Color mask, ColorOp op ) {
        this.image= image;
        this.h= image.getHeight();
        this.w= image.getWidth();
        this.rank= 2;
        if ( mask==null ) {
            if ( op==null ) {
                rank= 3;
                putProperty( QDataSet.DEPEND_2, Ops.labelsDataset(new String[] { "red", "green", "blue" } ) );
            } else {
                this.op= op;
            }
        } else {
            this.op= new ChannelColorOp( mask.getRGB() & 0xFFFFFF, (int)log2( Integer.lowestOneBit( mask.getRGB() ) ) );
        }
        putProperty( QDataSet.QUBE, Boolean.TRUE );
        putProperty( QDataSet.RENDER_TYPE, QDataSet.VALUE_RENDER_TYPE_COMPOSITE_IMAGE );
    }
    
    @Override
    public int rank() {
        return rank;
    }

    @Override
    public int length() {
        return w;
    }

    @Override
    public int length(int i) {
        return h;
    }

    @Override
    public int length(int i, int j) {
        return 3;
    }

    
    @Override
    public double value(int i0, int i1) {
        return op.value( image.getRGB( i0, h-i1-1 ) );
    }

    @Override
    public double value(int i0, int i1, int i2) {
        int rgb= image.getRGB( i0, h-i1-1 );
        switch (i2) {
            case 0: return ( rgb & 0xff0000 ) >> 16; 
            case 1: return ( rgb & 0x00ff00 ) >> 8; 
            case 2: return ( rgb & 0x0000ff ); 
            default: throw new IndexOutOfBoundsException( "i2=3" );
        }
    }
    
    
    

}
