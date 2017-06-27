/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.util.logging.Logger;
import org.das2.datum.LoggerManager;

/**
 * ImageOp that draws the reflection as well as the perspective view of the data.
 * @author jbf
 */
public class ScalePerspectiveImageOp implements BufferedImageOp {

    private static final Logger logger= LoggerManager.getLogger("autoplot.pngwalk");
    
    final int w;
    final int h;
    // subsample
    final int ssx;
    final int ssy;
    // output offset
    final int x1;
    // output offset
    final int y1;
    final double p;
    int nw;
    int nh;
    final int maxidx;
    private int w1;
    private int h1;
    private int rh1; // reflection height
    private boolean reflect; // enable/disable reflections

    /**
     *
     * @param w original width
     * @param h original height
     * @param x1 upper left hand corner
     * @param y1 upper left hand corner ( when p=0 )
     * @param w1 new width
     * @param h1 new height at the middle
     * @param ssx subsampling factor.  =1 no subsampling.  -1 means subsampling allowed, and will be picked automatically.
     * @param ssy subsampling factor.  =1 no subsampling.  -1 means subsampling allowed, and will be picked automatically.
     * @param rh1 extra height for the reflection
     * @param p rockiness, similar to the tan of the angle of the top of the image. Zero means no perspective (flat).
     */
    public ScalePerspectiveImageOp(int w, int h, int x1, int y1, int w1, int h1, int rh1, int ssx, int ssy, double p, boolean reflect) {
        this.w = w;
        this.h = h;
        this.w1 = w1;
        this.h1= h1;
        this.reflect= reflect;
        this.rh1= rh1 * h / h1;
        this.x1= x1;
        this.y1= y1;
        this.p = p;
        this.nw = (int) w1;
        this.nh = (int) h1 + rh1;
        this.maxidx= nw*nh-1;
        if ( ssx==-1 ) {
            this.ssx= Math.max( 1, w / w1 / 2 );
        } else {
            this.ssx= ssx;
        }
        if ( ssy==-1 ) {
            if ( p!=0 ) {
               this.ssy= 1;
            } else {
               this.ssy= Math.max( 1, h / h1 );
            }
        } else {
            this.ssy= ssy;
        }
    }

//    private Shape getOutline( double x, double y ) {
//        GeneralPath path= new GeneralPath();
//        Point2D dst= new Point2D.Float();
//        dst= this.getPoint2D( new Point2D.Float(0,0), dst);
//        path.moveTo((float)dst.getX(), (float)dst.getY());
//        dst= this.getPoint2D( new Point2D.Float(w,0), dst);
//        path.lineTo((float)dst.getX(), (float)dst.getY());
//        dst= this.getPoint2D( new Point2D.Float(w,h), dst);
//        path.lineTo((float)dst.getX(), (float)dst.getY());
//        dst= this.getPoint2D( new Point2D.Float(0,h), dst);
//        path.lineTo((float)dst.getX(), (float)dst.getY());
//        dst= this.getPoint2D( new Point2D.Float(0,0), dst);
//        path.lineTo((float)dst.getX(), (float)dst.getY());
//        path.transform( AffineTransform.getTranslateInstance(x,y) );
//        return path;
//    }

    private int index(int i, int j) {
        int ii= i + j * nw;
        ii= ( ii>this.maxidx ) ? maxidx : ii;
        ii= ( ii<0 ) ? 0 : ii;
        return ii;
    }

    @Override
    public BufferedImage filter(BufferedImage src, BufferedImage dest) {

        if ( dest!=null && dest.getWidth()==nw && dest.getHeight()==nh ) {
            logger.fine("recycling old image");
        } else {
            dest = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        }

        int[] rr = new int[nw * nh];
        int[] gg = new int[nw * nh];
        int[] bb = new int[nw * nh];
        int[] aa = new int[nw * nh];
        int[] nn = new int[nw * nh];

        final int AVG = 0;
        final int BG_WEIGHT=1; // weight (1-255) applied when background color is detected.

        boolean hasBg = true;
        int bgColor = src.getRGB(3, 3);
        if ( ( bgColor >> 24 & 0xFF ) !=255 ) hasBg= false;
        if (src.getRGB(3, 3) != bgColor) hasBg = false;
        if (src.getRGB(3, h - 4) != bgColor) hasBg = false;
        if (src.getRGB(w - 4, 3) != bgColor) hasBg = false;
        if (src.getRGB(w - 4, h - 4) != bgColor) hasBg = false;

        // make sure that all the borders are included in the averages.
        int[] jj= new int[h/ssy+1];
        for ( int k1=0; k1<h/ssy; k1++ ) jj[k1]= k1*ssy;
        jj[h/ssy]= h-1;

        int[] ii= new int[w/ssx+1];
        for ( int k2=0; k2<w/ssx; k2++ ) ii[k2]= k2*ssx;
        ii[w/ssx]= w-1;

        for ( int k1 = 0; k1 < jj.length; k1++ ) {
            int j= jj[k1];
            for ( int k2 = 0; k2<ii.length; k2++ ) {
                int i= ii[k2];
                
                int color = src.getRGB(i,j);

                int i1 = x1 + (int) (i * w1 / w );
                double pp = p>0 ? ( i * p)  : ( ( i-w ) *p );
                double hai= h1 - 2 * pp;
                int j1= y1 + (int) ( pp + j * ( hai / h ) );
                int didx = index(i1, j1);
                int weight;
                if (hasBg) {
                    weight = color == bgColor ? BG_WEIGHT : 255;
                } else {
                    weight = (color >> 24 & 0xff);
                }

                rr[didx] += weight * ((color >> 16 & 0xff) - AVG);
                gg[didx] += weight * ((color >> 8  & 0xff) - AVG);
                bb[didx] += weight * ((      color & 0xff) - AVG);
                aa[didx] += 255 * weight;
                nn[didx] += weight;
                
                if ( this.reflect ) {

                    j1= (int) ( pp + hai + (h-j) * ( hai / h ) );
                    //j1 = ( y1 + (int) (((j * (1000 - pp * 2)) + pp * h) * h1 / h / 1000 ) ) + j/10;

                    didx = index(i1, j1);

                    weight = (color >> 24 & 0xff);

                    double	ww1 = Math.max( 1,weight * ( ( (0.4 ) / (rh1) ) *(j - (h-rh1) ) ) );

                    rr[didx] += 255*((color >> 16 & 0xff) - AVG);
                    gg[didx] += 255*((color >> 8  & 0xff) - AVG);
                    bb[didx] += 255*((      color  & 0xff) - AVG);
                    aa[didx] += 255* ww1;
                    nn[didx] += 255;

                }
            }
        }

        for (int i = 0; i < nw; i++) {
            for (int j = 0; j < nh; j++) {
                int didx = index(i, j);
                int n = nn[didx];
                if (n > 0) {
                    int weight = aa[didx] / nn[didx];
                    if (hasBg) {
                        int color;
                        if (weight == 0) {
                            color = bgColor;
                        } else if ( weight<255 ) {
                            color = (weight << 24) + ((rr[didx] / n + AVG) << 16) + ((gg[didx] / n + AVG) << 8) + (bb[didx] / n + AVG);
                        } else {
                            //int rr1= (rr[didx] / n + AVG);
                            color = (255 << 24) + ((rr[didx] / n + AVG) << 16) + ((gg[didx] / n + AVG) << 8) + (bb[didx] / n + AVG);
                        }
                        dest.setRGB(i, j, color);
                    } else {
                        int color = (weight << 24) + ((rr[didx] / n + AVG) << 16) + ((gg[didx] / n + AVG) << 8) + (bb[didx] / n + AVG);
                        dest.setRGB(i, j, color);
                    }
                } else {
                    //if ( hasBg && j<=h1 ) {  // if it's not part of the reflection
                    //    dest.setRGB(i, j, bgColor );
                    //}
                }
            }
        }

        return dest;
    }

    @Override
    public Rectangle2D getBounds2D(BufferedImage src) {
        return new Rectangle2D.Double(0, 0, src.getWidth(), src.getHeight());
    }

    @Override
    public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
        return new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    }

    @Override
    public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {

        double i1 = x1 + (int) (srcPt.getX() * w1 / w );
        double pp = (int) Math.round( p>0 ? ( srcPt.getX() * p)  : ( ( srcPt.getX()-w ) *p ) );
        double j1 = y1 + (int) (((srcPt.getY() * (1000 - pp * 2)) + pp * h) * h1 / h / 1000 );

        if (dstPt == null) {
            dstPt = new Point2D.Double(i1, j1);
        } else {
            dstPt.setLocation(i1, j1);
        }
        return dstPt;
    }

    @Override
    public RenderingHints getRenderingHints() {
        return new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
