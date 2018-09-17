
package org.autoplot.pngwalk;

import java.awt.image.BufferedImage;
import java.util.logging.Logger;
import org.das2.util.ImageUtil;
import org.das2.util.LoggerManager;

/**
 * first from http://today.java.net/pub/a/today/2007/04/03/perils-of-image-getscaledinstance.html
 * resize taken from http://www.componenthouse.com/article-20
 * @author jbf
 */
public class ImageResize {

    public static final Logger logger= LoggerManager.getLogger("autoplot.pngwalk");
    
    /**
     * convenient typical use.
     * @param img image to resize.
     * @param thumbSize corner-to-corner size, preserving aspect ratio.
     * @return buffered image that is thumbSize across.
     */
    public static BufferedImage getScaledInstance( BufferedImage img, int thumbSize ) {
        return ImageUtil.getScaledInstance(img, thumbSize);
    }

    /**
     * Convenience method that returns a scaled instance of the
     * provided {@code BufferedImage}.
     *
     * @param img the original image to be scaled
     * @param targetWidth the desired width of the scaled instance,
     *    in pixels
     * @param targetHeight the desired height of the scaled instance,
     *    in pixels
     * @param hint one of the rendering hints that corresponds to
     *    {@code RenderingHints.KEY_INTERPOLATION} (e.g.
     *    {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
     *    {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
     *    {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
     * @param higherQuality if true, this method will use a multi-step
     *    scaling technique that provides higher quality than the usual
     *    one-step technique (only useful in downscaling cases, where
     *    {@code targetWidth} or {@code targetHeight} is
     *    smaller than the original dimensions, and generally only when
     *    the {@code BILINEAR} hint is specified)
     * @return a scaled version of the original {@code BufferedImage}
     */
    public static BufferedImage getScaledInstance(BufferedImage img,
                                           int targetWidth,
                                           int targetHeight,
                                           Object hint,
                                           boolean higherQuality)
    {
        return ImageUtil.getScaledInstance(img, targetWidth, targetHeight, hint, higherQuality);
    }

}

