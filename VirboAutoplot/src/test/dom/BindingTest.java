/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.dom;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Converter;
import org.virbo.autoplot.dom.DomNode;
import org.virbo.autoplot.dom.PlotElement;

/**
 * Attempt to measure performance problems with Bindings.  By binding many
 * properties.
 * @author jbf
 */
public class BindingTest {

    private static final Logger logger= Logger.getLogger("autoplot");
    
    public static void bind(DomNode master, String prop, DomNode p, String destProp, Converter c ) {
        Binding binding;
        BeanProperty bp = BeanProperty.create(prop);

        binding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, master, bp,
                p, BeanProperty.create(destProp));
        binding.bind();
    }

    private static PropertyChangeListener propListener( final Object p, final BeanProperty bp, final Converter c, final boolean forward ) {
        return new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                Object value= evt.getNewValue();
                if ( c==null ) {
                    bp.setValue( p, value );
                } else {
                    if ( forward ) {
                        bp.setValue( p, c.convertForward(value) );
                    } else {
                        bp.setValue( p, c.convertReverse(value) );
                    }
                }
            }
        };
    }

    public static void bind2( DomNode master, String prop, Object p, String destProp, Converter c ) {
        BeanProperty bp = BeanProperty.create(prop);
        BeanProperty dbp = BeanProperty.create(destProp);
        
        master.addPropertyChangeListener( prop, propListener(p,dbp,c,true) );
        try {
            Method apcl = p.getClass().getMethod("addPropertyChangeListener", String.class, PropertyChangeListener.class);
            apcl.invoke(p, destProp, propListener(master,bp,c,false) );
        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        //Object value= bp.getValue(master);
        //dbp.setValue( p, value );
    }


    public static void main(String[] args) {
        List<PlotElement> plotElements1 = new ArrayList();
        PlotElement master = new PlotElement();

        for (int i = 0; i < 40; i++) {
            plotElements1.add(new PlotElement());
        }

        long t0 = System.currentTimeMillis();


        for (PlotElement p : plotElements1) {
            bind2(master, PlotElement.PROP_LEGENDLABEL, p, PlotElement.PROP_LEGENDLABEL,null);
            System.err.printf("%5d: bind %s\n", System.currentTimeMillis() - t0, p);
        }

        System.err.printf("%5d: done bindings \n", System.currentTimeMillis() - t0);

        for (int i = 0; i < 10; i++) {
            master.setLegendLabel("foo" + i);
            System.err.println(plotElements1.get(20).getLegendLabel());
        }
        System.err.printf("%5d: done \n", System.currentTimeMillis() - t0);
    }
}
