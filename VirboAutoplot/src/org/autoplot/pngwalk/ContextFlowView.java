/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pngwalk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jbf
 */
public class ContextFlowView extends PngWalkView {

    List<Rectangle> imageBounds;
    int firstIndexPainted, lastIndexPainted;

    boolean useSquashedThumbs= true;

    public ContextFlowView( WalkImageSequence s ) {
        super(s);
        sequenceChanged();
        addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                if ( seq!=null && seq.size()!=0 ) seq.skipBy(e.getWheelRotation());
            }
        });

        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( imageBounds==null ) return;
                for ( int i=firstIndexPainted; i<lastIndexPainted; i++ ) {
                    if ( imageBounds.get(i).contains(e.getPoint()) ) {
                        seq.setIndex(i);
                        break;
                    }
                }
            }
        });
    }

    @Override
    protected void sequenceChanged() {
        if ( seq==null ) {
            imageBounds= null;
        } else {
            imageBounds= new ArrayList<Rectangle>(seq.size());
            for ( int i=0; i<seq.size(); i++ ) imageBounds.add(i,null);
        }
        repaint();
    }


    private static Rectangle bounds(int xpos, int ypos, int width, int height, int targetWidth, int targetHeight, double aspectFactor, boolean debug) {
        double aspect = 1. * width / height;
        // target is the limiting dimension.

        boolean heightIsLimiting= 1. * targetWidth / targetHeight > ( aspect * aspectFactor );

        if (debug)
            System.err.printf("tw=%d th=%d rat=%5.2f asp=%5.2f  heightIsLimiting=%s\n", targetWidth, targetHeight, 1. * targetWidth / targetHeight, aspect, ""+heightIsLimiting);

        int w, h;
        if ( heightIsLimiting ) {
            w = (int) ( targetHeight * ( aspect * aspectFactor ) );
            h= targetHeight;
        } else {
            w= targetWidth;
            h=  (int) ( targetWidth / ( aspect * aspectFactor ) );
        }
        int x = xpos - w / 2;
        int y = ypos - h / 2;
        return new Rectangle(x, y, w, h);
    }

    synchronized BufferedImage getRightImage( Image image, int width, int height, Rectangle bounds ) {

        double magp= useSquashedThumbs ? 0.5 : 0.05;
        
        //Image cacheImage = rightThumbsCache.get(image);
        Image cacheImage = null;

        if (cacheImage == null) {
            BufferedImage im;
            if (image instanceof BufferedImage) {
                im = (BufferedImage) image;
            } else {
                im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                if ( !im.getGraphics().drawImage(image, 0, 0, this) ) {
                    return null;
                }
            }
            //addBorder(im,0.1);
            ScalePerspectiveImageOp op = new ScalePerspectiveImageOp(im.getWidth(), im.getHeight(),
                    0, 0, bounds.width, bounds.height, bounds.height/4, 1, 1,
                    magp, true);
            cacheImage= op.filter( im, null );
            //rightThumbsCache.put( image, cacheImage );
            return (BufferedImage)cacheImage;
        } else {
            return (BufferedImage)cacheImage;
        }
    }

    synchronized BufferedImage getLeftImage( Image image, int width, int height, Rectangle bounds ) {
        double magp=  useSquashedThumbs ? 0.5 : 0.05;
        //Image cacheImage = leftThumbsCache.get(image);
        Image cacheImage = null;
        
        if (cacheImage == null) {
            BufferedImage im;
            if (image instanceof BufferedImage) {
                im = (BufferedImage) image;
            } else {
                im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                if ( !im.getGraphics().drawImage(image, 0, 0, this) ) {
                    return null;
                }
            }
            //addBorder(im,0.1);
            ScalePerspectiveImageOp op = new ScalePerspectiveImageOp(im.getWidth(), im.getHeight(),
                    0, 0, bounds.width, bounds.height, bounds.height/4, 1, 1,
                    -1*magp , true);
            cacheImage= op.filter( im, null );
            //leftThumbsCache.put( image, cacheImage );
            return (BufferedImage)cacheImage;
        } else {
            return (BufferedImage)cacheImage;
        }
    }

    private synchronized void maybeTimeStamp(Graphics2D g, Rectangle bounds, String s ) {
        int fmh = 14; // font metrics height
        if (s != null) {
            g.drawString(s, bounds.x, bounds.y + bounds.height + fmh);
        }
    }
    
    @Override
    protected synchronized void paintComponent(Graphics g1) {

        if (seq == null)
            return;

        Graphics2D g = (Graphics2D) g1;

        Rectangle clip = g.getClipBounds();
        g.clearRect(clip.x, clip.y, clip.width, clip.height);


        int shelfWidth = 40;    // width of the images to the right or left.
        int currentWidth = 400; // width of the current image in the center.

        int xm = getWidth() / 2;
        int y = Math.max(200, getHeight() / 2);

        int columns = (xm - currentWidth / 2) / shelfWidth;

        int currentIndex = seq.getIndex();

        firstIndexPainted = Math.max(0, currentIndex - columns);
        lastIndexPainted = Math.min(seq.size(), currentIndex + columns + 1); // exclusive

        double sh = useSquashedThumbs ? 1. : 10.;

        for (int i = 0; i < columns * 2 + 1; i++) {

            int index;
            int d = columns - i / 2;
            if (i % 2 == 0) {
                index = currentIndex - d;
            } else {
                index = currentIndex + d;
            }
            if (index < 0)
                continue;
            if (index >= seq.size())
                continue;

            boolean usedLastImage = false;
            BufferedImage image = useSquashedThumbs ? seq.imageAt(index).getSquishedThumbnail() : seq.imageAt(index).getThumbnail();

            if (image == null)
                continue;

            int height = image.getHeight();
            int width = image.getWidth();

            Rectangle bounds;

            if (index < currentIndex) {
                int x = xm - currentWidth / 2 + (index - currentIndex) * shelfWidth; // fudge

                bounds = bounds(x, y, width, height, shelfWidth, Math.min(height, shelfWidth * 10), 1 / sh, false);
                //bounds = bounds( x, y, width, height, shelfWidth, shelfWidth*10, 0.1, false);

                BufferedImage cacheImage = getRightImage(image, width, height, bounds);

                if (cacheImage == null) {
                    continue;
                }

                g.drawImage(cacheImage, bounds.x, bounds.y, this);
                //g.draw(op.getOutline(bounds.x, bounds.y));

            } else if (index == currentIndex) {
                int x = xm;

                image = seq.imageAt(index).getImage();
                if (image == null) {
                    image = seq.imageAt(index).getThumbnail();

                    height = image.getHeight();
                    width = image.getWidth();

                    if (width > height) {
                        //allowing such images to take full width reduces flicker
                        bounds = bounds(x, y, width, height, currentWidth, 10000, 1.0, false);
                    } else {
                        bounds = bounds(x, y, width, height, currentWidth, height, 1.0, false);
                    }

                } else {

                    height = image.getHeight();
                    width = image.getWidth();

                    bounds = bounds(x, y, width, height, currentWidth, height, 1.0, false);
                }
                //if (g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, this)) {
                //    lastImage = image;
                //}
                //g.fill(bounds);

                BufferedImage im;
                if (image instanceof BufferedImage) {
                    im = (BufferedImage) image;
                } else {
                    im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    if (!im.getGraphics().drawImage(image, 0, 0, this)) {
                        continue;
                    }
                }
                //addBorder(im,1);
                ScalePerspectiveImageOp op = new ScalePerspectiveImageOp(width, height,
                        0, 0, bounds.width, bounds.height, 100, -1, -1,
                        0., true);
                g.drawImage(im, op, bounds.x, bounds.y);
                //lastImage = image;

                maybeTimeStamp(g, bounds, seq.imageAt(index).getCaption());

                //if (usedLastImage) drawMomentStr(g, bounds);
            } else {
                int x = xm + currentWidth / 2 + (index - currentIndex) * shelfWidth;

                bounds = bounds(x, y, width, height, shelfWidth, Math.min(height, shelfWidth * 10), 1 / sh, false);

                Image cacheImage = getLeftImage(image, width, height, bounds);

                if (cacheImage == null) {
                    continue;
                }

                g.drawImage(cacheImage, bounds.x, bounds.y, this);
                //g.draw(op.getOutline(bounds.x, bounds.y));
                //if (usedLastImage) drawMomentStr(g, bounds);
                }

            imageBounds.set(index, bounds);

        }

    }


}



