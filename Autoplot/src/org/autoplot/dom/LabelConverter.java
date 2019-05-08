
package org.autoplot.dom;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.OrbitDatumRange;
import org.das2.datum.Orbits;
import org.das2.datum.TimeParser;
import org.das2.datum.UnitsUtil;
import org.das2.util.LoggerManager;
import org.jdesktop.beansbinding.Converter;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;

/**
 * Class for containing the logic of how macros are implemented.
 * https://sourceforge.net/p/autoplot/feature-requests/426/
 * 
 * This currently supports just the plotElement and plot.  When 
 * multiple plotElements are attached to a plot, then the 
 * first is used.
 * 
 * @author faden@cottagesystems.com
 */
public class LabelConverter extends Converter {
    
    private static Logger logger= LoggerManager.getLogger("autoplot.dom.labelConverter");
            
    Application dom=null;
    PlotElement plotElement=null;
    Plot plot=null;
    Annotation annotation=null;
    Axis axis= null;
    
    boolean multiplePEWarning= false;
    
    private LabelConverter() {   
    }
    
    public LabelConverter( Application dom, Plot plot, Axis axis, PlotElement pe, Annotation an ) {
        this();
        this.dom= dom;
        this.plot= plot;
        this.axis= axis;
        this.plotElement= pe;
        this.annotation= an;
    }
    
    private PlotElement getFocusPlotElement() {
        PlotElement pe;
        multiplePEWarning= false;
        if ( plotElement!=null ) {
            pe= plotElement;
        } else {
            List<PlotElement> pes= DomUtil.getPlotElementsFor(dom, plot);
            if ( pes.size()==1 ) {
                pe= pes.get(0);
            } else if ( pes.isEmpty() ) {
                pe= null;
            } else {
                pe= pes.get(0);
                multiplePEWarning= true;
            }
        }
        return pe;
    }
    
    @Override
    public Object convertForward(Object value) {
        PlotElement pe= getFocusPlotElement();
        
        String title= (String)value;
        boolean done= false;
        
        while ( !done ) {
            int unresolvedIndex= title.indexOf("%{");
            
            if ( title.contains("CONTEXT" ) ) {
                if ( pe!=null ) {
                    int loopCount=0;
                    if ( pe.getController().isPendingChanges() ) {
                        loopCount++;
                        if ( loopCount>1000 ) {
                            break;
                        }
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(LabelConverter.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    if ( loopCount>1000 ) {
                        logger.warning("wait for isPendingChanges reached limit");
                    }
                    QDataSet dataSet= pe.getController().getDataSet();
                    if ( dataSet!=null ) {
                        if ( plot!=null && plot.getXaxis()==axis ) {  // crazy kludge, sure to cause problems.  This assumes that DEPEND_0 is the dataset causing the variation in X.
                            logger.finer("getting the CONTEXT property from DEPEND_0");
                            QDataSet d= (QDataSet) dataSet.property(QDataSet.DEPEND_0);
                            if ( d!=null && SemanticOps.getUnits(d).isConvertibleTo(axis.getRange().getUnits()) ) {
                                dataSet= d;
                            }
                        }
                        String contextStr= DataSetUtil.contextAsString(dataSet);
                        logger.log(Level.FINEST, "bug1814: context substitution success. {0}", Thread.currentThread().getName());
                        title= insertString( title, "CONTEXT", contextStr );
                    } else {
                        logger.log(Level.FINEST, "bug1814: ds is null. {0}", Thread.currentThread().getName());
                        title= insertString( title, "CONTEXT", "" ); 
                    }
                } else {
                    logger.log(Level.FINEST, "bug1814: pe is null.{0}", Thread.currentThread().getName());
                    title= insertString( title, "CONTEXT", "" );// https://sourceforge.net/p/autoplot/bugs/1814/
                }
            }
            if ( title.contains("PLOT_CONTEXT") ) {
                title= insertString( title, "PLOT_CONTEXT", "%{CONTEXT}");
            }            
            if ( title.contains("USER_PROPERTIES" ) ) {
                if ( pe!=null ) {
                    QDataSet dataSet= pe.getController().getDataSet();
                    if ( dataSet!=null ) {
                        Map<String,Object> props= (Map<String, Object>) dataSet.property(QDataSet.USER_PROPERTIES);
                        title= DomUtil.resolveProperties( title, "USER_PROPERTIES", props );
                    }
                }
            }

            if ( title.contains("METADATA" ) ) {
                if ( pe!=null ) {
                    DataSourceFilter dsf= (DataSourceFilter) DomUtil.getElementById( dom, pe.getDataSourceFilterId() );
                    if ( dsf!=null ) { // ought not to be!
                        Map<String,Object> props= (Map<String, Object>) dsf.getController().getRawProperties(); //TODO: this is a really old name that needs updating...
                        title= DomUtil.resolveProperties( title, "METADATA", props );
                    }
                }
            }
            if ( title.contains("{PROPERTIES" ) ) {  // ${PROPERTIES.DEPEND_0.UNITS}
                if ( pe!=null ) {
                    QDataSet dataSet= pe.getController().getDataSet();
                    int i1= title.indexOf("{PROPERTIES")+12; // 12 includes the dot following
                    int i2= title.indexOf('}',i1);
                    String prop= title.substring(i1,i2);
                    String[] ss= prop.split("\\.",-2);
                    Object o=null;
                    if ( dataSet!=null ) {
                        for (String s : ss) {
                            o = dataSet.property(s);
                            if ( o instanceof QDataSet ) {
                                dataSet= (QDataSet)o;
                            }
                            if ( o==null ) break;
                        }
                    }
                    if ( o==null ) o="";
                    title= insertString( title, title.substring(i1-11,i2), o.toString() );
                }
            }

            if ( title.contains("TIMERANGE") ) {
                DatumRange tr= PlotElementControllerUtil.getTimeRange( dom, pe );
                if ( tr==null ) {
                    if ( plot!=null && UnitsUtil.isTimeLocation( plot.getContext().getUnits() ) ) {
                        tr= plot.getContext();
                    } else if ( axis!=null && UnitsUtil.isTimeLocation( axis.getRange().getUnits() ) ) {
                        tr= axis.getRange();
                    }
                }
                
                Pattern pop= Pattern.compile("(.*)\\%\\{TIMERANGE(.*?)\\}(.*)");
                String insert= ( tr==null ? "(no timerange)" : tr.toString() );
                Matcher m= pop.matcher(title);
                if ( m.matches() ) {
                    String control= m.group(2).trim();
                    Map<String,String> controls= new HashMap<>();
                    if ( control.length()>0 ) {
                        char delim= control.charAt(0);
                        String[] ss;
                        ss= control.substring(1).split( "\\"+delim );
                        for ( String s: ss ) {
                            int i= s.indexOf("=");
                            if ( i==-1 ) {
                                controls.put(s,"");
                            } else {
                                controls.put(s.substring(0,i),s.substring(i+1));
                            }
                        }
                    }
                    if ( controls.size()>0 ) {
                        if ( controls.containsKey("CONTEXT") && tr!=null ) {
                            String context= controls.get("CONTEXT");
                            if ( context!=null ) { // the context can be an orbit file or orbit identifier.
                                Orbits o= Orbits.getOrbitsFor(context);
                                String s= o.getOrbit(tr.middle());
                                if ( s!=null ) {
                                    try {
                                        // convert to orbit datum range for the same time.
                                        DatumRange drtest= o.getDatumRange(s);
                                        if ( Math.abs( DatumRangeUtil.normalize( tr, drtest.min() ) ) < 0.05 &&
                                             Math.abs( DatumRangeUtil.normalize( tr, drtest.max() ) - 1.0 ) < 0.05 ) {
                                            tr= DatumRangeUtil.parseTimeRange("orbit:"+context+":"+s);
                                        }
                                    } catch (ParseException ex) {
                                        logger.log(Level.SEVERE, null, ex);
                                    }

                                }
                            }                            
                        }
                        if ( controls.containsKey("NOORBIT") ) {
                            if ( tr!=null ) {
                                if ( tr instanceof OrbitDatumRange ) {
                                    insert= DatumRangeUtil.formatTimeRange(tr,false) + " (Orbit "+((OrbitDatumRange)tr).getOrbit()+")";
                                } else {
                                    insert= DatumRangeUtil.formatTimeRange(tr,false);
                                }
                            }                            
                        } else if ( controls.containsKey("FORMAT") ) {
                            String format= controls.get("FORMAT");
                            if ( format.equals("$o") || format.equals("%o") ) {
                                if ( tr instanceof OrbitDatumRange ) {
                                    insert= ((OrbitDatumRange)tr).getOrbit();
                                } else {
                                    insert= "???";
                                }
                            } else {
                                TimeParser tp= TimeParser.create(format);
                                if ( tr!=null ) {
                                    insert= tp.format(tr);
                                }                        
                            }
                        }
                    }
                }
                title= insertString( title, "TIMERANGE", insert );

            }
            //logger.fine("<--"+value + "-->"+title );
            //see convertReverse, which must be done as well.
            if ( title.contains("COMPONENT") ) {
                String ss="";
                if ( pe!=null ) {
                    ss= pe.getComponent();
                }
                title= insertString( title, "COMPONENT", ss );
            }
            
            int newUnresolvedIndex= title.indexOf("%{");
            
            done= ( newUnresolvedIndex==-1 || newUnresolvedIndex==unresolvedIndex );
            
        }
        
        if ( multiplePEWarning && ! title.equals(value) ) {
            logger.log(Level.FINE, "multiple plot elements found, using first to resolve: {0}", value);
        }
        
        return title;
    }

    @Override
    public Object convertReverse(Object value) {
        String title= (String)value;
        
        String ptitle;
        if ( annotation!=null ) {
            ptitle= annotation.getText();
        } else if ( plotElement!=null ) {
            PlotElement pe= getFocusPlotElement();
            ptitle= pe.getLegendLabel();
        } else if ( axis!=null ) {
            ptitle= axis.getLabel();
        } else {
            ptitle= plot.getTitle();
        }
        
        if ( containsString( ptitle, "CONTEXT", title) ) {
            title= ptitle;
        } else if ( ptitle.contains( "%{PLOT_CONTEXT}" ) ) {
            title= ptitle;
        } else if ( ptitle.contains( "%{USER_PROPERTIES" ) ) { //kludgy
            title= ptitle;
        } else if ( ptitle.contains( "%{PROPERTIES" ) ) { //kludgy
            title= ptitle;
        } else if ( ptitle.contains( "%{METADATA" ) ) { //kludgy
            title= ptitle;
        } else if ( containsString( ptitle, "TIMERANGE", title ) ) {
            title= ptitle;
        } else if ( containsString( ptitle, "COMPONENT", title ) ) {
            title= ptitle;
        }
        
        if ( multiplePEWarning && !title.equals(value) ) {
            logger.log(Level.FINE, "multiple plot elements found, using first to resolve: {0}", value);              
        }
        return title;
    }

    /**
     * replace %{LABEL} or $(LABEL) with value.
     * @param title the string containing the macro.
     * @param label the label to replace, such as METADATA.SPACECRAFT.
     * @param value the value to insert
     * @return the new string with the value inserted.
     */
    protected static String insertString( String title, String label, String value ) {
        Pattern p= Pattern.compile("(\\%\\{"+label+"(,.*?)?\\})");
        Matcher m= p.matcher(title);
        if ( m.find() ) {
            return title.substring(0,m.start()) + value + title.substring(m.end());
        } else {
            p= Pattern.compile("(\\$\\("+label+"(,.*?)?\\))");
            m= p.matcher(title);
            if ( m.find() ) {
                return title.substring(0,m.start()) + value + title.substring(m.end());
            }
        }
        return title;
    }

    /**
     * return true if %{LABEL} or $(LABEL) is found in ptitle, or 
     * %{LABEL,args}.
     * @param title the string which contains the macro.
     * @param label the macro name to look for in %{macro} or $(macro)
     * @param value the string with the macro inserted.
     * @return true if ptitle is consistent.
     */
    protected static boolean containsString( String title, String label, String value ) {
        String search;
        String[] ss=null;
        Pattern p= Pattern.compile("(\\%\\{"+label+"(,.*)?\\})");
        Matcher m= p.matcher(title);
        if ( m.find() ) {
            ss= new String[2];
            ss[0]= title.substring(0,m.start());
            ss[1]= title.substring(m.end());
        } else {
            search= "$("+label+")";
            if ( title.contains( search ) ) {
                ss= title.split("\\$\\("+label+"\\)",-2);
            }
        }
        if ( ss!=null && value.startsWith(ss[0]) && value.endsWith(ss[1]) ) {
            return true;
        } else {
            return false;
        }
    }    
}
