package org.das2.catalog.impl;

import org.das2.catalog.DasResolveException;
import org.json.JSONObject;

/** Helper functions for catalog nodes that happen to be defined using JSON data
 *
 * @author cwp
 */
class JsonUtil {
	
	/** 
	 * 
	 * @param data
	 * @param sFragment
	 * @param oDefault
	 * @return 
	 */
	static Object property(JSONObject data, String sFragment, Object oDefault) 
	{
		throw new UnsupportedOperationException("Not supported yet.");
		
	}
	
	/** Get a give property given an internal object fragment string
	 * 
	 * @param data
	 * @param sFragment
	 * @param expect
	 * @param oDefault
	 * @return 
	 */
	static Object property(
		JSONObject data, String sFragment, Class expect, Object oDefault
	){
		
		throw new UnsupportedOperationException("Not supported yet.");
		
	}

	static Object property(JSONObject data, String sFragment) 
		throws DasResolveException 
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	static Object property(JSONObject data, String sFragment, Class expect) 
		throws DasResolveException 
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}
}
