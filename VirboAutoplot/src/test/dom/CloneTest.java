/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.dom;

import org.virbo.autoplot.dom.*;
import org.das2.datum.Datum;
import org.das2.datum.Units;
import org.das2.graph.DasColorBar;

/**
 *
 * @author jbf
 */
public class CloneTest {
    public static void main(String[] args) {
        PanelStyle style= new PanelStyle();
        style.setReference(Datum.create(4.0, Units.kiloHertz ));

        PanelStyle cln= (PanelStyle) style.copy();

        System.err.println( cln.getReference());

        Panel myPanel= new Panel();

        myPanel.getStyle().setColortable( DasColorBar.Type.GRAYSCALE );

        Panel clonePanel= (Panel) myPanel.copy();
        System.err.println( clonePanel.getStyle().getColortable() );
    }
}
