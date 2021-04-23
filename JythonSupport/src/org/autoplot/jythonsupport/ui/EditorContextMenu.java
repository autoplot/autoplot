
package org.autoplot.jythonsupport.ui;

import ZoeloeSoft.projects.JFontChooser.JFontChooser;
import java.awt.Dimension;
import java.awt.Font;
import org.das2.components.propertyeditor.PropertyEditor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Element;
import jsyntaxpane.DefaultSyntaxKit;
import jsyntaxpane.SyntaxStyle;
import jsyntaxpane.SyntaxStyles;
import jsyntaxpane.actions.ActionUtils;
import jsyntaxpane.actions.IndentAction;
import org.das2.jythoncompletion.CompletionSettings;
import org.das2.jythoncompletion.JythonCompletionProvider;
import org.das2.util.LoggerManager;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.jythonsupport.JavaJythonConverter;
import org.autoplot.jythonsupport.JythonToJavaConverter;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.MathematicaJythonConverter;
import static org.das2.jythoncompletion.JythonCompletionTask.CLIENT_PROPERTY_INTERPRETER_PROVIDER;
import org.das2.jythoncompletion.JythonInterpreterProvider;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Name;

/**
 *
 * @author jbf
 */
public class EditorContextMenu {

    private static final Logger logger= Logger.getLogger("jython.editor");

    private EditorTextPane editor;
    private JPopupMenu menu;
    private DataSetSelector dataSetSelector;
    private JMenu examplesMenu;
    private JMenu jumpToMenu;
    private int jumpToMenuPosition;
    private JMenu actionsMenu;
    
    public EditorContextMenu( EditorTextPane edit  ) {
        this.editor = edit;
        maybeCreateMenu();

        JythonCompletionProvider.getInstance().settings().addPropertyChangeListener( new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                switch (evt.getPropertyName()) {
                    case CompletionSettings.PROP_EDITORFONT:
                        editor.setFont( Font.decode((String)evt.getNewValue() ) );
                        break;
                    case CompletionSettings.PROP_TABISSPACES:
                        {
                            boolean tabIsSpaces= (Boolean)evt.getNewValue();
                            Action get = ActionUtils.getAction( editor, IndentAction.class );
                            if ( get==null ) {
                                logger.warning("Expected to find IndentAction");
                            } else {
                                ((IndentAction)get).setInsertTab(!tabIsSpaces);
                            }       break;
                        }
                    case CompletionSettings.PROP_TAB_IS_COMPLETION:
                        {
                            boolean tabIsCompletion= (Boolean)evt.getNewValue();
                            Action get = ActionUtils.getAction( editor, IndentAction.class );
                            if ( get==null ) {
                                logger.warning("Expected to find IndentAction");
                            } else {
                                ((IndentAction)get).setInsertTab(!tabIsCompletion);
                            }       break;
                        }
                    case CompletionSettings.PROP_SHOWTABS:
                        SyntaxStyle deflt= SyntaxStyles.getInstance().getStyle(null);
                        boolean value= (Boolean)evt.getNewValue();
                        deflt.setDrawTabs(value);
                        editor.repaint();
                        break;
                    default:
                        break;
                }
            } 
        });

        editor.setComponentPopupMenu(menu); // override the default popup for the editor.
        
        menu.addPropertyChangeListener( "visible", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                doRebuildJumpToMenu();
            }
        });

    }
    
    /**
     * add the menu or menuitem to the actions menu.
     * @param menuitem
     */
    public void addMenuItem( JMenuItem menuitem ) {
        actionsMenu.add(menuitem);
    }
    
    private void doRebuildJumpToMenu() {
        final JMenu tree= new JMenu("Jump To");

        String[] ss= editor.jumpToList();
        for ( int i=0; i<ss.length; i++ ) {
            final String fs= ss[i];
            tree.add( new JMenuItem( new AbstractAction( ss[i] ) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    switch (fs) {
                        case "top":
                            editor.setCaretPosition(0);
                            break;
                        case "bottom":
                            editor.setCaretPosition(editor.getDocument().getLength()-1);
                            break;
                        default:
                            int i= fs.indexOf(":");
                            if ( i>-1 ) {
                                int line= Integer.parseInt(fs.substring(0,i));
                                Element ee= editor.getDocument().getDefaultRootElement().getElement(line-1);
                                editor.setCaretPosition(ee.getStartOffset());
                            }    break;
                    }
                }

            } ) );
        }
        Runnable run= new Runnable() {
            @Override
            public void run() {
                actionsMenu.remove(jumpToMenuPosition);
                actionsMenu.add( tree, jumpToMenuPosition );
            }
        };
        run.run();

    }
    
    public void setDataSetSelector( DataSetSelector sel ) {
        this.dataSetSelector= sel;
    }

    /**
     * create a menu item with a tool tip preview.
     * @param text
     * @return
     */
    private JMenuItem createInsertMenuItem( String label, final String text ) {
        JMenuItem result= new JMenuItem( new AbstractAction( label ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                LoggerManager.logGuiEvent(e);                                
                insertCode( text );
            }
        } );
        String htmlText= text;
        htmlText= htmlText.replaceAll("<", "&lt;") ;
        htmlText= htmlText.replaceAll(">", "&gt;") ;
        htmlText= htmlText.replaceAll("\n", "<br>");
        htmlText= htmlText.replaceAll(" ", "&nbsp;") ;
        result.setToolTipText( "<html><tt>" + htmlText + "</tt></html>" );
        return result;

    }

    /**
     * indent or dedent the code.  When amount is positive, we indent.  When it's negative, we remove so many spaces
     * from each line.
     * @param txt the block of text, starting but not including the newline, and ending just before a newline.
     * @param amount positive or negative number of spaces.
     * @return
     */
    private static String indent( String txt, int amount ) {
        try {
            StringBuilder result= new StringBuilder();
            BufferedReader r= new BufferedReader( new StringReader(txt) );
            String dedent= amount<0 ? "         ".substring(0,-1*amount) : "";
            String indent= amount>0 ? "         ".substring(0,   amount) : "";
            String line= r.readLine();
            while ( line!=null ) {
                if ( amount<0 && line.startsWith(dedent) ) line= line.substring(dedent.length());
                if ( amount>0 ) line= indent + line;
                result.append(line);
                line= r.readLine();
                if ( line!=null ) result.append("\n");
            }
            return result.toString();
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * comment or uncomment the code.  When amount is positive, we add the comment. 
     * When it's negative, we remove the comment from each line.
     * @param txt the block of text, starting but not including the newline, and ending just before a newline.
     * @param amount positive or negative number of spaces.
     * @return
     */
    private static String comment( String txt, int amount ) {
        try {
            StringBuilder result= new StringBuilder();
            BufferedReader r= new BufferedReader( new StringReader(txt) );
            String line= r.readLine();
            String hash= "#";
            while ( line!=null ) {
                String line1= line.trim();
                int ind= line.indexOf(line1);
                if ( amount<0 && line1.startsWith(hash) ) line= line.substring(0,ind)+line1.substring(1);
                if ( amount>0 ) line= hash + line.substring(0,ind) + line1;
                result.append(line);
                line= r.readLine();
                if ( line!=null ) result.append("\n");
            }
            return result.toString();
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }
    

    /**
     * return the index of the start and end of the selection, rounded out
     * to complete lines.
     * @return [start,len] where start is the index of the first character and len is the number of characters.
     */
    private int[] roundLines(  ) {
        int i= editor.getSelectionStart();  // note the netbeans source has all these operators, implemented correctly...
        int j= editor.getSelectionEnd();
        try {
            int limit= editor.getText().length();
            
            while ( i>0 && !editor.getText(i,1).equals("\n") ) i--;
            if ( i>=0 && i<limit-1 && editor.getText(i,1).equals("\n") && !editor.getText(i+1,1).equals("\n") ) i++;
            while ( j<limit && !editor.getText(j,1).equals("\n" ) ) j++;
                        
            return new int[] { i, j-i };
            
        } catch (BadLocationException ex) {
            throw new RuntimeException(ex);
        }
        
    }
    
    /**
     * encode font into string like "sans-12".  This is a copy of Autoplot's
     * DomUtil.encodeFont.
     * @param f
     * @return 
     */
    public static String encodeFont(java.awt.Font f) {
        String style="-";
        if ( f.isBold() ) style+="bold";
        if ( f.isItalic() ) style+="italic";
        String result= f.getFamily();
        if ( style.length()>1 ) result+= style;
        return result + "-" + f.getSize();
    }  
    
    /**
     * return "a" when line is a=...
     * @param editor
     * @return 
     */
    private String getVariableNameContext( EditorTextPane editor ) {
        String s= editor.getSelectedText();
        if ( s!=null ) {
            int i= s.indexOf("=");
            if ( i>0 ) {
                s= s.substring(0,i);
            }
        }
        if ( s==null ) {
            int i1= editor.getCaretPosition();
            int i0 = org.das2.jythoncompletion.Utilities.getRowStart( editor, i1 );
            if ( i1>i0 ) {
                try {
                    s= editor.getText( i0, i1-i0 ).trim();
                    int i= s.indexOf("=");
                    if ( i>0 ){
                        s= s.substring(0,i);
                    }
                } catch (BadLocationException ex) {
                    s= null;
                }
            }
        }
        if ( s!=null ) {
            s= s.trim();
        }           
        return s;
    }
    
    private synchronized void maybeCreateMenu() {
        if ( menu==null ) {
            menu= new JPopupMenu();
            Action a;
            JMenuItem item;
            JMenu insertCodeMenu= new JMenu("Insert Code");

            JMenu getParamMenu= new JMenu("Get Parameter");
            getParamMenu.setToolTipText("<html>Parameters provide a consistent and clean method for passing parameters into scripts.");

            a= new AbstractAction("getParam()") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    String var= getVariableNameContext( editor );
                    logger.log( Level.FINE, "editor.getdoc: {0}", editor.getDocument());
                    if ( var==null || var.length()==0 ) {
                        insertLine( "p1= getParam( 'p1', 0.0, 'parameter p1 (default=0.0)' )\n");
                    } else {
                        insertLine( var + "= getParam( '"+var+"', 0.0, 'parameter "+var+" (default=0.0)' )\n" );
                    }
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>get a parameter for the script, for example, from the URI or command line depending on context<br>The first argument is the parameter name,<br>second is the default value and type,<br>optional third is description</html>");
            getParamMenu.add( item );

            a= new AbstractAction("getParam() with enumeration") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);    
                    String var= getVariableNameContext( editor );
                    logger.log( Level.FINE, "editor.getdoc: {0}", editor.getDocument());
                    if ( var==null || var.length()==0 ) {
                        insertLine( "sc= getParam( 'sc', 'A', 'the spacecraft name', ['A','B'] )\n");
                    } else {
                        insertLine( var + "= getParam( '"+var+"', 'A', 'the spacecraft name', ['A','B'] )\n");
                    }
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>get a parameter for the script, constraining the list of values to an enumeration.</html>");
            getParamMenu.add( item );

            a= new AbstractAction("getParam() for boolean checkbox") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    String var= getVariableNameContext( editor );
                    logger.log( Level.FINE, "editor.getdoc: {0}", editor.getDocument());
                    if ( var==null || var.length()==0 ) {
                        insertLine( "filt= getParam( 'filter', 'F', 'filter data', ['T','F'] )\n");
                    } else {
                        insertLine( var + "= getParam( '"+var+"', 'F', 'filter data', ['T','F'] )\n");
                    }
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>get a parameter for the script, constraining the list of values to be True or False.  A checkbox is used when a GUI is generated.</html>");
            getParamMenu.add( item );


            a= new AbstractAction("getParam() for timerange to support time series browse") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    String var= getVariableNameContext( editor );
                    if ( var==null || var.length()==0 ) {
                        insertLine( "tr= getParam( 'timerange', '2014-01-09', 'timerange to load' )\n");
                    } else {
                        insertLine( var + "= getParam( 'timerange', '2014-01-09', 'timerange to load' )\n");
                    }   
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>When getParam timerange is read, then the script will the time axis to be set to any time.</html>");
            getParamMenu.add( item );

            a= new AbstractAction("getParam() to get the resource URI") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    insertLine( "resourceURI= getParam( 'resourceURI', 'http://autoplot.org/data/rainfall_KIOW_20120522_0252.html', 'example file to load' )\n" );
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>This special variable is the vap+jyds:&lt;resourceURI&gt;?script=&lt;script&gt;");
            getParamMenu.add( item );

            insertCodeMenu.add(getParamMenu);

            JMenu fragmentsMenu= new JMenu("Code Fragments");
            fragmentsMenu.add( createInsertMenuItem( "procedure", "def myproc(x,y):\n  z=x+y\n  return z\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "if block", "x=0\nif (x<0):\n  print 'x<0'\nelif (x==0):\n  print 'x==0'\nelse:\n  print 'x>0'\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "for loop with index", "a= sin( linspace(0,PI,100) )\nfor i in xrange(len(a)):\n  print i, a[i]\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "monitor for feedback", "from java.lang.Thread import sleep\nmonitor.setTaskSize(100)\nmonitor.started()\nfor i in xrange(100):\n  monitor.setTaskProgress(i)\n  if monitor.getTaskProgress()==80: monitor.setProgressMessage('almost done')\n  if monitor.isCancelled(): break\n  sleep(120)\nmonitor.finished()" ) );

            fragmentsMenu.add( createInsertMenuItem( "for loop over dataset", "a= sin( linspace(0,PI,100) )\nfor i in a:\n  print i\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "try-except", "try:\n  fil=downloadResourceAsTempFile(URL('http://autoplot.org/data/nofile.dat'),monitor)\nexcept java.io.IOException,ex:\n  print 'file not found'\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "except-traceback", "try:\n  fil=downloadResourceAsTempFile(URL('http://autoplot.org/data/nofile.dat'),monitor)\nexcept:\n  import traceback\n  traceback.print_exc()\n" ) );

            fragmentsMenu.add( createInsertMenuItem( "raise exception", "if ( ds.length()==0 ):\n  raise Exception('Dataset is empty')") );

            fragmentsMenu.add( createInsertMenuItem( "raise NoDataInIntervalException", "from org.das2.dataset import NoDataInIntervalException\nraise NoDataInIntervalException('no files found')") );

            fragmentsMenu.add( createInsertMenuItem( "documentation block", "# title: the one-line title\n# label: terse label") );

            fragmentsMenu.add( createInsertMenuItem( "multi-argument procedure", "# return a set of datasets which are synchronized to the same timetags.\n" +
"def mysynchronize( ds1, *dss ):\n" +
"    \"the first dataset's timetags are used to interpolate the list of datasets\"\n" +
"    tt= ds1.property( QDataSet.DEPEND_0 )\n" +
"    result= []\n" +
"    for ds in dss:\n" +
"        tt1= ds.property( QDataSet.DEPEND_0 )\n" +
"        ff= findex( tt1, tt )\n" +
"        ds= interpolate( ds, ff )\n" +
"        result.append( ds )\n" +
"    return result\n" +
"( mlat, MLT ) = mysynchronize( hfr_spectra, mlat, MLT )" ) );

			fragmentsMenu.add( createInsertMenuItem( "logger", "from org.das2.util import LoggerManager\n" +
															   "from java.util.logging import Level\n" +
															   "logger= LoggerManager.getLogger( 'aascript' )\n" +
															   "logger.log( Level.INFO, 'created logger for {0}', 'aascript')"));
            insertCodeMenu.add(fragmentsMenu);
            
            a= new AbstractAction("getDataSet()") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    String var= getVariableNameContext( editor );
                    String surl= dataSetSelector.getValue();
                    if ( var==null || var.length()==0 ) {
                        String name= DataSourceUtil.guessNameFor(surl);
                        insertLine( name + "= getDataSet('"+surl+"')\n");
                    } else {
                        insertLine( var + "= getDataSet('"+surl+"')\n");
                    }
                }
            };
            item= new JMenuItem( a );
            item.setToolTipText("<html>load the dataset from the specified URI into a variable.  An example URI is grabbed from the dataset selector.</html>");
            insertCodeMenu.add( item );

            menu.add( insertCodeMenu );
            JMenu submenu= new JMenu("Example Scripts");
            examplesMenu= submenu;
            menu.add( submenu );
            
            actionsMenu= new JMenu("Actions");
            
            jumpToMenu= new JMenu( "Jump To" );
            jumpToMenu.setToolTipText("Jump To Position in code");
            jumpToMenuPosition= actionsMenu.getItemCount();
            actionsMenu.add(jumpToMenu);

            JMenu developerMenu= new JMenu( "Developer" );
            developerMenu.setToolTipText("Special actions for developers");
            actionsMenu.add(developerMenu);

            JMenuItem printMenuItem= new JMenuItem( "Print" );
            printMenuItem.setToolTipText("Print to printer");
            printMenuItem.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        editor.print();
                    } catch (PrinterException ex) {
                        Logger.getLogger(EditorContextMenu.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
            actionsMenu.add(printMenuItem);

            JMenuItem runMenuItem= new JMenuItem( "Run Selected" );
            runMenuItem.setToolTipText("Run Selected Commands");
            runMenuItem.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final String doThis= editor.getSelectedText();
                    if ( doThis==null ) {
                        JOptionPane.showMessageDialog( editor, "Select portion of the code to execute");
                        return;
                    }
                    String[] sss= doThis.split("\n");
                    for ( String s: sss ) {
                        if ( s.length()>0 && Character.isWhitespace(s.charAt(0)) ) {
                            JOptionPane.showMessageDialog( menu, "Sorry no indents" );
                            return;
                        }
                    }
                    final JythonInterpreterProvider pp =
                            (JythonInterpreterProvider) editor.getClientProperty(CLIENT_PROPERTY_INTERPRETER_PROVIDER);
                    if ( pp==null ) {
                        JOptionPane.showMessageDialog( menu, "Sorry no Jython session to run commands" );
                        return;
                    }

                    Runnable run= new Runnable() {
                        @Override
                        public void run() {
                            try {
                                pp.createInterpreter().exec(doThis);
                            } catch (IOException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                        }                            
                    };

                    new Thread(run).start();

                }
            });
            actionsMenu.add(runMenuItem);
            
            JMenuItem mi;
            
            mi= new JMenuItem( new AbstractAction("Convert To Java") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);       
                    String doThis= editor.getSelectedText();
                    if ( doThis==null || doThis.length()==0 ) {
                        doThis= editor.getText();
                    }
                    try {
                        JavaJythonConverter cc= new JavaJythonConverter(editor,JavaJythonConverter.DIR_JYTHON_TO_JAVA);
                        cc.setPythonSource(doThis);
                        JDialog d= new JDialog();
                        d.setContentPane(cc);
                        d.pack();
                        d.setVisible(true);
                    } catch ( Exception ex ) {
                        JOptionPane.showMessageDialog( menu, ex.toString() );
                    }
                }                
            });
            developerMenu.add(mi);

            mi= new JMenuItem( new AbstractAction("Convert Java To Jython") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    String doThis= editor.getSelectedText();
                    if ( doThis==null || doThis.length()==0 ) {
                        doThis= editor.getText();
                    }
                    try {
                        JavaJythonConverter cc= new JavaJythonConverter(editor);
                        cc.setJavaSource(doThis);
                        JDialog d= new JDialog();
                        d.setContentPane(cc);
                        d.pack();
                        d.setVisible(true);
                    } catch ( Exception ex ) {
                        JOptionPane.showMessageDialog( menu, ex.toString() );
                    }
                }                
            });
            developerMenu.add(mi);

            mi= new JMenuItem( new AbstractAction("Convert Mathematica To Jython") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    String doThis= editor.getSelectedText();
                    if ( doThis==null || doThis.length()==0 ) {
                        doThis= editor.getText();
                    }
                    try {
                        MathematicaJythonConverter cc= new MathematicaJythonConverter(editor);
                        cc.setJavaSource(doThis);
                        JDialog d= new JDialog();
                        d.setContentPane(cc);
                        d.pack();
                        d.setVisible(true);
                    } catch ( Exception ex ) {
                        JOptionPane.showMessageDialog( menu, ex.toString() );
                    }
                }                
            });
            developerMenu.add(mi);
            
            mi= new JMenuItem( new AbstractAction("Show Simplified Script Used for Completions") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);       
                    editor.showCompletionsView();
                }                
            });
            mi.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_F12, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK ) ); 
            
            developerMenu.add(mi);
                        
            mi= new JMenuItem( new AbstractAction("Plot") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    editor.plotItem();
                }
            } );
            mi.setToolTipText("Plot dataset reference in a second Autoplot with its server port open");
            mi.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_C, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK ) ); 
            actionsMenu.add( mi );
                        
            mi= new JMenuItem( new AbstractAction("Inspect URI") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    editor.inspectURI();
                }
            } );
            mi.setIcon( new javax.swing.ImageIcon(getClass().getResource("/org/autoplot/datasource/fileMag.png") ) );
            mi.setToolTipText("Use the data source editor panel to modify URI");
            actionsMenu.add( mi );
            
            mi= new JMenuItem( new AbstractAction("Indent Block") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    int[] il= roundLines();
                    try {
                        String txt= editor.getText( il[0], il[1] );
                        txt= indent( txt, 4 );
                        editor.getDocument().remove( il[0], il[1] );
                        editor.getDocument().insertString( il[0], txt, null );
                        editor.setSelectionStart(il[0]);
                        editor.setSelectionEnd(il[0]+txt.length());
                    } catch ( BadLocationException ex ) {

                    }
                }
            } );
            mi.setToolTipText("indent the selected block of lines");
            actionsMenu.add( mi );
            mi= new JMenuItem( new AbstractAction("Unindent Block") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    int[] il= roundLines();
                    try {
                        String txt= editor.getText( il[0], il[1] );
                        txt= indent( txt, -4 );
                        editor.getDocument().remove( il[0], il[1] );
                        editor.getDocument().insertString( il[0], txt, null );
                        editor.setSelectionStart(il[0]);
                        editor.setSelectionEnd(il[0]+txt.length());
                    } catch ( BadLocationException ex ) {

                    }
                }
            } );
            mi.setToolTipText("indent the selected block of lines");
            actionsMenu.add( mi );
            mi= new JMenuItem( new AbstractAction("Comment Block") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    int[] il= roundLines();
                    try {
                        String txt= editor.getText( il[0], il[1] );
                        txt= comment( txt, 1 );
                        editor.getDocument().remove( il[0], il[1] );
                        editor.getDocument().insertString( il[0], txt, null );
                        editor.setSelectionStart(il[0]);
                        editor.setSelectionEnd(il[0]+txt.length());
                    } catch ( BadLocationException ex ) {

                    }
                }
            } );
            mi.setToolTipText("comment the selected block of lines");
            actionsMenu.add( mi );
            
            mi= new JMenuItem( new AbstractAction("Uncomment Block") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    int[] il= roundLines();
                    try {
                        String txt= editor.getText( il[0], il[1] );
                        txt= comment( txt, -1 );
                        editor.getDocument().remove( il[0], il[1] );
                        editor.getDocument().insertString( il[0], txt, null );
                        editor.setSelectionStart(il[0]);
                        editor.setSelectionEnd(il[0]+txt.length());
                    } catch ( BadLocationException ex ) {

                    }
                }
            } );
            mi.setToolTipText("uncomment the selected block of lines");
            actionsMenu.add( mi );

            mi= new JMenuItem( new AbstractAction("Show Usages") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    editor.showUsages();
                }
            } );
            mi.setToolTipText( "highlite use of name" );
            mi.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_U, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK ) );
            actionsMenu.add( mi );
            
            mi= new JMenuItem( new AbstractAction("Import Java Code") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    editor.doImports();
                }
            } );
            mi.setToolTipText( "search for and add import" );
            mi.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_I, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK ) );
            actionsMenu.add( mi );
            
            menu.add( actionsMenu );
            
            JMenu settingsMenu= new JMenu("Settings");
            mi= new JMenuItem( new AbstractAction("Edit Settings") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    CompletionSettings settings= JythonCompletionProvider.getInstance().settings();
                    PropertyEditor p= new PropertyEditor(settings);
                    p.showModalDialog(editor);
                }
            } );
            mi.setToolTipText( "Settings for the editor" );
            settingsMenu.add( mi );

            mi= new JMenuItem( new AbstractAction("Pick Font...") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);                
                    JFrame parent= (JFrame)SwingUtilities.getWindowAncestor( editor );
                    JFontChooser chooser = new JFontChooser( parent );
                    chooser.setLocationRelativeTo( editor );
                    chooser.setExampleText("ds= getDataSet('http://autoplot.org/data/data.dat')");
                    chooser.setFont( editor.getFont() );
                    if (chooser.showDialog() == JFontChooser.OK_OPTION) {
                       CompletionSettings settings= JythonCompletionProvider.getInstance().settings();
                       settings.setEditorFont( encodeFont( chooser.getFont() ) );
                    }
                }
            } );
            mi.setToolTipText("Pick Font for editor");
            settingsMenu.add( mi );

            mi= new JMenuItem( new AbstractAction("Reload Syntax Colors") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    String s= editor.getText();
                    editor.setEditorKit(null);
                    editor.getInitializeRunnable().run();
                    editor.setText(s);
                }
            } );
            mi.setToolTipText("Reload editor colors from autoplot_data/config/jsyntaxpane.properties");
            settingsMenu.add( mi );
            
            mi = new JMenuItem(new AbstractAction("Keyboard Shortcuts...") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    String msg = "<html><table><tr><td><b>Keyboard Shortcuts:</b></td><td></td><br></tr>"
                            + "<tr><td>META-Z</td><td> undo </td><br></tr>"
                            + "<tr><td>META-Y</td><td> redo</td>  <br></tr>"
                            + "<tr><td>META-EQUALS</td><td> biggerFont </td> <br></tr>"
                            + "<tr><td>META-MINUS</td><td> smallerFont </td> <br></tr>"
                            + "<tr><td>SHIFT-F5</td><td> settings </td> <br></tr>"
                            + "<tr><td>CTRL-SHIFT-C</td><td> plot expression via server mode (See [menubar]->Options->Enable Feature->Server)\" </td> <br></tr>"
                            + "<tr><td>CTRL-SHIFT-U</td><td> show usages of a variable<br></td></tr>"
                            + "<tr><td>ALT-SHIFT-U</td><td>import the symbol<br></td></tr>"
                            + "<tr><td>CTRL-S</td><td>  Save<br></td></tr>"
                            + "<tr><td>F6</td><td> Execute<br></td> </tr>"
                            + "<tr><td>SHIFT-F6</td><td> Execute with Parameters Dialog<br></td> </tr>"
                            + "<tr><td>CRTL-SHIFT-F12</td><td> Used for script editor development<br></td> </tr>"
                            + "<tr><td>CTRL-SPACE</td><td> Show completions<br></td> </tr>"
                            + "<tr><td>CTRL-F</td><td> Show Search bar<br></td> </tr>"
                            + "</table></html>";
                            
                    JOptionPane.showMessageDialog( actionsMenu, msg );
                }
            });
            mi.setToolTipText("Show shortcuts");
            settingsMenu.add( mi );
            
            menu.add( settingsMenu );
            
            menu.addSeparator();
            
            mi = new JMenuItem(new AbstractAction("Static Code Analysis") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    editor.doStaticAnalysis();
                }
            } );
            mi.setToolTipText("Run Static Code Analysis, looking for unused symbols which might hint at a semmantic error.");
            menu.add( mi );
            
            menu.addSeparator();
            mi = new JMenuItem(new AbstractAction("Search...") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    LoggerManager.logGuiEvent(e);
                    JOptionPane.showMessageDialog(menu,"Ctrl-F is Search");
                }
            } );
            JMenuItem searchItem = menu.add(mi);
            searchItem.setText("Find"); 
            
            menu.addSeparator();
            JMenuItem cutItem = menu.add(new DefaultEditorKit.CutAction());
            cutItem.setText("Cut");
            JMenuItem copyItem = menu.add(new DefaultEditorKit.CopyAction());
            copyItem.setText("Copy");
            JMenuItem pasteItem = menu.add(new DefaultEditorKit.PasteAction());
            pasteItem.setText("Paste");

        }
    }

    /**
     * delete the current line and insert the code.
     * Note this shows how to do a replacement as an atomic operation that properly supports undo.
     * @param code 
     */
    private void insertLine( String code ) {
        int i= editor.getCaretPosition();
        int i1 = org.das2.jythoncompletion.Utilities.getRowEnd( editor, i );
        int i0 = org.das2.jythoncompletion.Utilities.getRowStart( editor, i );
        editor.setSelectionStart(i0);
        editor.setSelectionEnd(i1);
        editor.replaceSelection(code);
    }
    
    private void insertCode( String code ) {
        try {
            editor.getDocument().insertString(editor.getCaretPosition(), code, null);
        } catch (BadLocationException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /**
     * adds an action to examples submenu.
     * @param a
     */
    public void addExampleAction( Action a ) {
        this.examplesMenu.add(a);
    }

}
