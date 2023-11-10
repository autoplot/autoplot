package org.autoplot.jythonsupport.ui;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.das2.components.propertyeditor.PropertyEditor;

/**
 *
 * @author jbf@iowa.uiowa.edu
 */
public class SyntaxColorBean {

    private Color tokenMarker = Color.WHITE;

    public static final String PROP_TOKENMARKER = "tokenMarker";

    public Color getTokenMarker() {
        return tokenMarker;
    }

    public void setTokenMarker(Color tokenMarker) {
        Color oldTokenMarker = this.tokenMarker;
        this.tokenMarker = tokenMarker;
        propertyChangeSupport.firePropertyChange(PROP_TOKENMARKER, oldTokenMarker, tokenMarker);
    }

    private Color pairMarker = Color.WHITE;

    public static final String PROP_PAIRMARKER = "pairMarker";

    public Color getPairMarker() {
        return pairMarker;
    }

    public void setPairMarker(Color pairMarker) {
        Color oldPairMarker = this.pairMarker;
        this.pairMarker = pairMarker;
        propertyChangeSupport.firePropertyChange(PROP_PAIRMARKER, oldPairMarker, pairMarker);
    }

    private Color carotColor = Color.WHITE;

    public static final String PROP_CAROTCOLOR = "carotColor";

    public Color getCarotColor() {
        return carotColor;
    }

    public void setCarotColor(Color carotColor) {
        Color oldCarotColor = this.carotColor;
        this.carotColor = carotColor;
        propertyChangeSupport.firePropertyChange(PROP_CAROTCOLOR, oldCarotColor, carotColor);
    }

    private Color backgroundColor = Color.WHITE;

    public static final String PROP_BACKGROUNDCOLOR = "backgroundColor";

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        Color oldBackgroundColor = this.backgroundColor;
        this.backgroundColor = backgroundColor;
        propertyChangeSupport.firePropertyChange(PROP_BACKGROUNDCOLOR, oldBackgroundColor, backgroundColor);
    }

    private Color selectionColor = Color.WHITE;

    public static final String PROP_SELECTIONCOLOR = "selectionColor";

    public Color getSelectionColor() {
        return selectionColor;
    }

    public void setSelectionColor(Color selectionColor) {
        Color oldSelectionColor = this.selectionColor;
        this.selectionColor = selectionColor;
        propertyChangeSupport.firePropertyChange(PROP_SELECTIONCOLOR, oldSelectionColor, selectionColor);
    }

    private Color styleOperator = Color.decode("0x000000");

    public static final String PROP_STYLEOPERATOR = "styleOperator";

    public void setStyleOperator(Color c) {
        Color oldColor = styleOperator;
        styleOperator = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEOPERATOR, oldColor, c);
    }

    public Color getStyleOperator() {
        return styleOperator;
    }

    private Color styleDelimiter = Color.decode("0x000000");

    public static final String PROP_STYLEDELIMITER = "styleDelimiter";

    public void setStyleDelimiter(Color c) {
        Color oldColor = styleDelimiter;
        styleDelimiter = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEDELIMITER, oldColor, c);
    }

    public Color getStyleDelimiter() {
        return styleDelimiter;
    }

    private Color styleKeyword = Color.decode("0x3333ee");

    public static final String PROP_STYLEKEYWORD = "styleKeyword";

    public void setStyleKeyword(Color c) {
        Color oldColor = styleKeyword;
        styleKeyword = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEKEYWORD, oldColor, c);
    }

    public Color getStyleKeyword() {
        return styleKeyword;
    }

    private Color styleKeyword2 = Color.decode("0x3333ee");

    public static final String PROP_STYLEKEYWORD2 = "styleKeyword2";

    public void setStyleKeyword2(Color c) {
        Color oldColor = styleKeyword2;
        styleKeyword2 = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEKEYWORD2, oldColor, c);
    }

    public Color getStyleKeyword2() {
        return styleKeyword2;
    }

    private Color styleType = Color.decode("0x000000");

    public static final String PROP_STYLETYPE = "styleType";

    public void setStyleType(Color c) {
        Color oldColor = styleType;
        styleType = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLETYPE, oldColor, c);
    }

    public Color getStyleType() {
        return styleType;
    }

    private Color styleType2 = Color.decode("0x000000");

    public static final String PROP_STYLETYPE2 = "styleType2";

    public void setStyleType2(Color c) {
        Color oldColor = styleType2;
        styleType2 = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLETYPE2, oldColor, c);
    }

    public Color getStyleType2() {
        return styleType2;
    }

    private Color styleType3 = Color.decode("0x000000");

    public static final String PROP_STYLETYPE3 = "styleType3";

    public void setStyleType3(Color c) {
        Color oldColor = styleType3;
        styleType3 = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLETYPE3, oldColor, c);
    }

    public Color getStyleType3() {
        return styleType3;
    }

    private Color styleString = Color.decode("0xcc6600");

    public static final String PROP_STYLESTRING = "styleString";

    public void setStyleString(Color c) {
        Color oldColor = styleString;
        styleString = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLESTRING, oldColor, c);
    }

    public Color getStyleString() {
        return styleString;
    }

    private Color styleString2 = Color.decode("0xcc6600");

    public static final String PROP_STYLESTRING2 = "styleString2";

    public void setStyleString2(Color c) {
        Color oldColor = styleString2;
        styleString2 = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLESTRING2, oldColor, c);
    }

    public Color getStyleString2() {
        return styleString2;
    }

    private Color styleNumber = Color.decode("0x999933");

    public static final String PROP_STYLENUMBER = "styleNumber";

    public void setStyleNumber(Color c) {
        Color oldColor = styleNumber;
        styleNumber = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLENUMBER, oldColor, c);
    }

    public Color getStyleNumber() {
        return styleNumber;
    }

    private Color styleRegex = Color.decode("0xcc6600");

    public static final String PROP_STYLEREGEX = "styleRegex";

    public void setStyleRegex(Color c) {
        Color oldColor = styleRegex;
        styleRegex = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEREGEX, oldColor, c);
    }

    public Color getStyleRegex() {
        return styleRegex;
    }

    private Color styleIdentifier = Color.decode("0x000000");

    public static final String PROP_STYLEIDENTIFIER = "styleIdentifier";

    public void setStyleIdentifier(Color c) {
        Color oldColor = styleIdentifier;
        styleIdentifier = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEIDENTIFIER, oldColor, c);
    }

    public Color getStyleIdentifier() {
        return styleIdentifier;
    }

    private Color styleComment = Color.decode("0x339933");

    public static final String PROP_STYLECOMMENT = "styleComment";

    public void setStyleComment(Color c) {
        Color oldColor = styleComment;
        styleComment = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLECOMMENT, oldColor, c);
    }

    public Color getStyleComment() {
        return styleComment;
    }

    private Color styleComment2 = Color.decode("0x339933");

    public static final String PROP_STYLECOMMENT2 = "styleComment2";

    public void setStyleComment2(Color c) {
        Color oldColor = styleComment2;
        styleComment2 = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLECOMMENT2, oldColor, c);
    }

    public Color getStyleComment2() {
        return styleComment2;
    }

    private Color styleDefault = Color.decode("0x000000");

    public static final String PROP_STYLEDEFAULT = "styleDefault";

    public void setStyleDefault(Color c) {
        Color oldColor = styleDefault;
        styleDefault = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEDEFAULT, oldColor, c);
    }

    public Color getStyleDefault() {
        return styleDefault;
    }

    private Color styleWarning = Color.decode("0xCC0000");

    public static final String PROP_STYLEWARNING = "styleWarning";

    public void setStyleWarning(Color c) {
        Color oldColor = styleWarning;
        styleWarning = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEWARNING, oldColor, c);
    }

    public Color getStyleWarning() {
        return styleWarning;
    }

    private Color styleError = Color.decode("0xCC0000");

    public static final String PROP_STYLEERROR = "styleError";

    public void setStyleError(Color c) {
        Color oldColor = styleError;
        styleError = c;
        propertyChangeSupport.firePropertyChange(PROP_STYLEERROR, oldColor, c);
    }

    public Color getStyleError() {
        return styleError;
    }

    public void readFromConfig(File file) throws IOException {
        Pattern p = Pattern.compile("([a-zA-Z\\.]+)\\s*=\\s*([0-9a-fA-Fx]+)(\\s*\\,\\s*([0123])\\s*)?");

        try (Stream<String> linesStream = Files.lines(file.toPath())) {
            linesStream.forEach(line -> {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    String prop = m.group(1);
                    String value = m.group(2);
                    Color c = Color.decode(value);
                    switch (prop) {
                        case "TokenMarker.Color":
                            setTokenMarker(c);
                            break;
                        case "PairMarker.Color":
                            setPairMarker(c);
                            break;
                        case "CaretColor":
                            setCarotColor(c);
                            break;
                        case "Background":
                            setBackgroundColor(c);
                            break;
                        case "SelectionColor":
                            setSelectionColor(c);
                            break;
                        case "Style.OPERATOR":
                            setStyleOperator(c);
                            break;
                        case "Style.DELIMITER":
                            setStyleDelimiter(c);
                            break;
                        case "Style.KEYWORD":
                            setStyleKeyword(c);
                            break;
                        case "Style.KEYWORD2":
                            setStyleKeyword2(c);
                            break;
                        case "Style.TYPE":
                            setStyleType(c);
                            break;
                        case "Style.TYPE2":
                            setStyleType2(c);
                            break;
                        case "Style.TYPE3":
                            setStyleType3(c);
                            break;
                        case "Style.STRING":
                            setStyleString(c);
                            break;
                        case "Style.STRING2":
                            setStyleString2(c);
                            break;
                        case "Style.NUMBER":
                            setStyleNumber(c);
                            break;
                        case "Style.REGEX":
                            setStyleRegex(c);
                            break;
                        case "Style.IDENTIFIER":
                            setStyleIdentifier(c);
                            break;
                        case "Style.COMMENT":
                            setStyleComment(c);
                            break;
                        case "Style.COMMENT2":
                            setStyleComment2(c);
                            break;
                        case "Style.DEFAULT":
                            setStyleDefault(c);
                            break;
                        case "Style.WARNING":
                            setStyleWarning(c);
                            break;
                        case "Style.ERROR":
                            setStyleError(c);
                            break;
                        default:
                    }
                }
            });
        }
    }

    private String toHex(Color c) {
        return String.format("0x%06x",(c.getRGB()&0xFFFFFF));
    }

    /**
     * write the current state to the configuration file
     * @param f
     * @throws FileNotFoundException 
     */
    public void writeToConfig(File f) throws FileNotFoundException {
        PrintWriter w = new PrintWriter(f);
        w.println("TokenMarker.Color = " + toHex(getTokenMarker()));
        w.println("PairMarker.Color = " + toHex(getPairMarker()));
        w.println("LineNumbers.Foreground = 0x333300");
        w.println("LineNumbers.Background = 0xeeeeff");
        w.println("LineNumbers.CurrentBack = 0xccccee");
        w.println("CaretColor = " + toHex(getCarotColor()));
        w.println("Background = " + toHex(getBackgroundColor()));
        w.println("SelectionColor = " + toHex(getSelectionColor()));
        w.println("# These are the various Attributes for each TokenType.");
        w.println("# The keys of this map are the TokenType Strings, and the values are:");
        w.println("# color (hex, or integer), Font.Style attribute");
        w.println("# Style is one of: 0 = plain, 1=bold, 2=italic, 3=bold/italic");
        w.println("Style.OPERATOR = " + toHex(getStyleOperator()) + ", 0");
        w.println("Style.DELIMITER = " + toHex(getStyleDelimiter()) + ", 1");
        w.println("Style.KEYWORD = " + toHex(getStyleKeyword()) + ", 0");
        w.println("Style.KEYWORD2 = " + toHex(getStyleKeyword2()) + ", 3");
        w.println("Style.TYPE = " + toHex(getStyleType()) + ", 2");
        w.println("Style.TYPE2 = " + toHex(getStyleType2()) + ", 1");
        w.println("Style.TYPE3 = " + toHex(getStyleType3()) + ", 3");
        w.println("Style.STRING = " + toHex(getStyleString()) + ", 0");
        w.println("Style.STRING2 = " + toHex(getStyleString2()) + ", 1");
        w.println("Style.NUMBER = " + toHex(getStyleNumber()) + ", 1");
        w.println("Style.REGEX = " + toHex(getStyleRegex()) + ", 0");
        w.println("Style.IDENTIFIER = " + toHex(getStyleIdentifier()) + ", 0");
        w.println("Style.COMMENT = " + toHex(getStyleComment()) + ", 2");
        w.println("Style.COMMENT2 = " + toHex(getStyleComment2()) + ", 3");
        w.println("Style.DEFAULT = " + toHex(getStyleDefault()) + ", 0");
        w.println("Style.WARNING = " + toHex(getStyleWarning()) + ", 0");
        w.println("Style.ERROR = " + toHex(getStyleError()) + ", 3");
        w.close();

    }

    public static void main(String[] args) throws IOException {
        SyntaxColorBean bean = new SyntaxColorBean();
        bean.readFromConfig(new File("/home/jbf/autoplot_data/config/jsyntaxpane.properties"));
        PropertyEditor edit = new org.das2.components.propertyeditor.PropertyEditor(bean);
        edit.showModalDialog(null);
        bean.writeToConfig(new File("/home/jbf/autoplot_data/config/jsyntaxpane.xxx.properties"));

    }

    private transient final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

}
