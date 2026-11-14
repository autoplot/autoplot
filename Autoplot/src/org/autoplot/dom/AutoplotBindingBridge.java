package org.autoplot.dom;

import java.awt.Component;
import java.awt.Point;
import org.das2.graph.DasAnnotation;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.das2.graph.DasCanvasComponent;
import org.das2.graph.DasPlot;

/**
 * Connects DomBindingDialog to Autoplot's persistent DOM bindings.
 * @see DomBindingDialog
 */
public final class AutoplotBindingBridge implements DomBindingDialog.Bridge {

    private final Application application;

    public AutoplotBindingBridge(Application application) {
        if (application == null) {
            throw new IllegalArgumentException("application");
        }
        this.application = application;
    }

    @Override
    public Object resolve(Component clicked, Point pointInClicked) {
        DasCanvas dasCanvas = application.controller.getCanvas().getController().getDasCanvas();

        DasCanvasComponent dcc = dasCanvas.getCanvasComponentAt(pointInClicked.x, pointInClicked.y);

        // Swing's deepest component may be a child of a DasAxis/DasPlot.
        Component c = dcc;
        while (c != null) {
            if (c instanceof DasAxis) {
                for (Plot p : application.getPlots()) {
                    if (p.getXaxis().getController().getDasAxis() == c) {
                        return p.getXaxis();
                    }
                    if (p.getYaxis().getController().getDasAxis() == c) {
                        return p.getYaxis();
                    }
                    if (p.getZaxis().getController().getDasAxis() == c) {
                        return p.getZaxis();
                    }
                }
            }
            if (c instanceof DasPlot) {
                for (Plot p : application.getPlots()) {
                    if (p.getController().getDasPlot() == c) {
                        return p;
                    }
                }
            }
            if (c instanceof DasAnnotation) {
                for (Annotation ann : application.getAnnotations()) {
                    if (ann.getController().getDasAnnotation() == c) {
                        return ann;
                    }
                }
            }
            c = c.getParent();
        }
        return null;
    }

    @Override
    public void bind(Object source, String sourceProperty,
            Object target, String targetProperty) {
        if (!(source instanceof DomNode) || !(target instanceof DomNode)) {
            throw new IllegalArgumentException("Both components must be DOM nodes");
        }
        DomNode a = (DomNode) source;
        DomNode b = (DomNode) target;
        ApplicationController ac = application.getController();
        if (ac.findBinding(a, sourceProperty, b, targetProperty) != null) {
            throw new IllegalArgumentException("This binding already exists");
        }
        ac.bind(a, sourceProperty, b, targetProperty);
    }

    @Override
    public String describe(Object node) {
        if (node instanceof DomNode) {
            DomNode n = (DomNode) node;
            return n.getClass().getSimpleName() + " — " + n.getId();
        }
        return String.valueOf(node);
    }
}
