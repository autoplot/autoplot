package org.autoplot.pngwalk;

import javax.swing.JFrame;
import org.das2.util.ArgumentList;

/**
 *
 * @author ed
 */
public class TestPngWalk2 extends JFrame {

    public TestPngWalk2(String title) {
        super(title);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        //WalkImageSequence seq = new WalkImageSequence("file:/tmp/pngwalk/product_%Y%m%d.png");
        //PngWalkView v = new ViewSingleImage(seq);
        //setLayout(new BorderLayout());
        PngWalkTool2 tool = new PngWalkTool2();
        add(tool);
        pack();
    }

    public static void main(String[] args) {

        final ArgumentList alm = new ArgumentList("AutoPlotUI");
        alm.addBooleanSwitchArgument("nativeLAF", "n", "nativeLAF", "use the system look and feel");
        alm.addOptionalPositionArgument(0, "template",  "file:/tmp/pngwalk/product_$Y$m$d.png", "initial template to use.");

        alm.process(args);

        if (alm.getBooleanValue("nativeLAF")) {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Runnable r = new Runnable() {
            public void run() {
                JFrame win = new TestPngWalk2("PNGWalk Test");
                win.setVisible(true);
            }
        };
        new Thread(r).start();
    }
}
