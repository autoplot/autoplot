/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package external;

import java.util.HashMap;
import java.util.Map;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyTuple;

/**
 *
 * @author eew
 */
public final class FunctionSupport {

	private static final String EXACT_PARAMETERS
			= "%s() takes exactly %d arguments (%d given)";
	private static final String AT_MOST_PARAMETERS
			= "%s() takes at most %d arguments (%d given)";
	private static final String AT_LEAST_PARAMETERS
			= "%s() takes at least %d arguments (%d given)";
	private static final String EXACT_KW_PARAMETERS
			= "%s() takes exactly %d non-keyword arguments (%d given)";
	private static final String AT_MOST_KW_PARAMETERS
			= "%s() takes at most %d non-keyword arguments (%d given)";
	private static final String AT_LEAST_KW_PARAMETERS
			= "%s() takes at least %d non-keyword arguments (%d given)";
	private static final String UNEXPECTED_KEYWORD
			= "%s() got an unexpected keyword argument '%s'";
	private static final String MULTIPLE_VALUES
			= "%s() got multiple values for keyword argument '%s'";
	private static final String DUPLICATE_ARGUMENT
			= "duplicate argument '%s' in function definition";
	private String name;
	private String[] formalParameters;
	private PyObject[] defaults;
	private String extraPositionalParameters;
	private String extraKeywordParameters;

	public FunctionSupport(String name, String[] parameters) {
		this(name, parameters, null, null, null);
	}

	public FunctionSupport(String name, String[] parameters, PyObject[] defaults) {
		this(name, parameters, defaults, null, null);
	}

	public FunctionSupport(
			String name,
			String[] parameters,
			PyObject[] defaults,
			String extraPositionalParameters,
			String extraKeywordParameters)
	{

		int nParameters = parameters == null ? 0 : parameters.length;
		int nDefaults = defaults == null ? 0 : defaults.length;

		if (nDefaults > nParameters) {
			throw new IllegalArgumentException("more defaults were specified than parameters");
		}

		this.name = name;

		if (parameters == null) {
			this.formalParameters = new String[0];
		}
		else {
			this.formalParameters = new String[parameters.length];
			System.arraycopy(parameters, 0, this.formalParameters, 0, nParameters);
		}
		if (defaults == null) {
			this.defaults = new PyObject[0];
		}
		else {
			this.defaults = new PyObject[defaults.length];
			System.arraycopy(defaults, 0, this.defaults, 0, nDefaults);
		}

		this.extraPositionalParameters = extraPositionalParameters;
		this.extraKeywordParameters = extraKeywordParameters;

		//extraPositionalParameters and extraKeywordParameters cannot occur
		//in the formalParameters array
		if (findParameter(extraPositionalParameters) >= 0) {
			String message = String.format(DUPLICATE_ARGUMENT, extraPositionalParameters);
			throw new IllegalArgumentException(message);
		}
		if (findParameter(extraKeywordParameters) >= 0) {
			String message = String.format(DUPLICATE_ARGUMENT, extraKeywordParameters);
			throw new IllegalArgumentException(message);
		}

	}

//	private static void checkNull(PyObject[] defaults) {
//		for (int i = 0; i < defaults.length; i++) {
//			if (defaults[i] == null) {
//				throw new NullPointerException("Null default not allowed. Use Py.None instead.");
//			}
//		}
//	}


	/** Processes the arguments for a jython method invocation in a way that
	 * mimics the way arguments are process for native python defined method.
	 * This algorithm is based on the description of the process given in
	 * section 5.3.4 of the version 2.4.4 Python Reference Manual.
	 *
	 * http://www.python.org/doc/2.4.4/ref/calls.html
	 *
	 * @param args
	 * @param keywords
	 * @return
	 */
	public Map<String,PyObject> args(PyObject[] args, String[] keywords) {

		Map<String,PyObject> result;
		PyObject[] parameters;
		PyTuple extraParameters = null;
		PyDictionary extraKeywords = null;

		int nParams = formalParameters.length;
		int keywordOffset = args.length - keywords.length;
		int defaultsOffset = formalParameters.length - defaults.length;


		//Create n unfilled slots
		//(where n is the number of formal parameters specified)
		parameters = new PyObject[nParams];


		//Copy up to n positional parameters into the unfilled slots
		//(where n is the number of formal parameters specified)
		System.arraycopy(args, 0, parameters, 0, Math.min(keywordOffset,nParams));

		//Either copy extra parameters into the extraParameters array
		//or raise a TypeError
		if (keywordOffset > nParams && extraPositionalParameters != null) {
			int nExtraParameters = keywordOffset - nParams;
			PyObject[] tmp = new PyObject[nExtraParameters];
			System.arraycopy(args, nParams, tmp, 0, nExtraParameters);
			extraParameters = new PyTuple(tmp);
		}
		else if (keywordOffset > nParams) {
			String message;
			if (keywords.length == 0) {
				if (defaults.length == 0) {
					message = EXACT_PARAMETERS;
				}
				else {
					message = AT_MOST_PARAMETERS;
				}
			}
			else if (defaults.length == 0) {
				message = EXACT_KW_PARAMETERS;
			}
			else {
				message = AT_MOST_KW_PARAMETERS;
			}
			message = String.format(message, name, nParams, keywordOffset);
			throw Py.TypeError(message);
		}
		else if (extraPositionalParameters != null) {
			extraParameters = new PyTuple();
		}

		//Copy keyword arguments into the parameter slots or the extra
		//keyword parameters map
		if (extraKeywordParameters != null) {
			extraKeywords = new PyDictionary();
		}
		for (int i = 0; i < keywords.length; i++) {
			int iSlot = findParameter(keywords[i]);
			if (iSlot < 0 && extraKeywordParameters != null) {
				extraKeywords.__setitem__(new PyString(keywords[i]), args[i+keywordOffset]);
			}
			else if (iSlot < 0) {
				String message = String.format(UNEXPECTED_KEYWORD, name, keywords[i]);
				throw Py.TypeError(message);
			}
			else if (parameters[iSlot] == null) {
				parameters[iSlot] = args[i+keywordOffset];
			}
			else {
				String message = String.format(MULTIPLE_VALUES, name, keywords[i]);
				throw Py.TypeError(message);
			}
		}

		//Fill in default values
		for (int i = 0; i < defaults.length; i++) {
			if (parameters[i+defaultsOffset] == null) {
				parameters[i+defaultsOffset] = defaults[i];
			}
		}

		//Check for unfilled slots
		for (int i = 0; i < nParams; i++) {
			if (parameters[i] == null) {
				String message;
				if (keywords.length == 0) {
					if (defaults.length == 0) {
						message = EXACT_PARAMETERS;
					}
					else {
						message = AT_LEAST_PARAMETERS;
					}
				}
				else if (defaults.length == 0) {
					message = EXACT_KW_PARAMETERS;
				}
				else {
					message = AT_LEAST_KW_PARAMETERS;
				}
				message = String.format(message, name, defaultsOffset, keywordOffset);
				throw Py.TypeError(message);
			}
		}

		result = new HashMap();
		for (int i = 0; i < nParams; i++) {
			result.put(formalParameters[i], parameters[i]);
		}
		if (extraPositionalParameters != null) {
			result.put(extraPositionalParameters, extraParameters);
		}
		if (extraKeywordParameters != null) {
			result.put(extraKeywordParameters, extraKeywords);
		}
		return result;
	}

	private int findParameter(String keyword) {
		for (int i = 0; i < formalParameters.length; i++) {
			if (keyword != null && keyword.equals(formalParameters[i]))
				return i;
		}
		return -1;
	}

}
