
package org.autoplot.jythonsupport;

import java.util.List;
import java.util.Map;

/**
 * Class for representing a Parameter.  This has been buried within the JythonUtil
 * class and will be useful on its own.
 */
public class Param {

    public String name;
    public String label; // the label for the variable used in the script
    public Object deft;
    public Object value; // the value if available, null means not present.
    public String doc;
    public List<Object> enums; // the allowed values
    public List<Object> examples; // example values
    /**
     * constraints for the value, such as:<ul>
     * <li>'labels':Arrays.asList(['RBSP-A','RBSP-B']) labels for each enum.
     * </ul>
     */
    public Map<String, Object> constraints;
    /**
     * The parameter type:<ul>
     * <li>T (TimeRange),
     * <li>A (String, but note a string with the values enumerated either T
     * or F is treated as a boolean.)
     * <li>F (Double or Integer, but note the values [0,1] imply it's a
     * boolean.),
     * <li>D (Datum),
     * <li>S (DatumRange),
     * <li>U (Dataset URI),
     * <li>L (URL), a file location, not a URI with parameters,
     * <li>M (local file or directory),
     * <li>or R (the resource URI)
     * </ul>
     */
    public char type;
    /**
     * List&lt;String&gt; of labels.
     */
    public static final String CONSTRAINT_LABELS = "labels";
    /**
     * Number for the minimum, inclusive
     */
    public static final String CONSTRAINT_MIN = "min";
    /**
     * Number for the maximum, inclusive
     */
    public static final String CONSTRAINT_MAX = "max";
    /**
     * List&lt;Object&gt; example values, which will be the same as the examples field.
     */
    public static final String CONSTRAINT_EXAMPLES = "examples";

    @Override
    public String toString() {
        return name + "=" + deft;
    }
    
}
