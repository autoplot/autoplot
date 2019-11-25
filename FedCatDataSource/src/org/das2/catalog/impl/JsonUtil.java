package org.das2.catalog.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.das2.catalog.DasProp;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Helper functions for catalog nodes that happen to be defined using JSON data
 *
 * @author cwp
 */
class JsonUtil {
	
	private static final DasProp nullProp = new DasProp(null);
	
	private static DasProp JdoToProp(Object obj){
		// Handle the static types first
		String sType = obj.getClass().getSimpleName();
		switch(sType){
		case "Boolean":
		case "Integer":
		case "Long":
		case "Float":
		case "Double": 
		case "String":  return new DasProp(obj);
		}
		
		if(sType.equals("JSONArray")){
			JSONArray ja = (JSONArray)obj;
			List<DasProp> list = new ArrayList<>();
			
			for(int i = 0; i < ja.length(); ++i ){
				DasProp subProp;
				if(ja.isNull(i)) subProp = new DasProp(null);
				else{
					try { subProp = JdoToProp(ja.get(i)); } 
					catch (JSONException ex) {
						// Can't happen unless some other thread is messing with our Json
						throw new AssertionError("Do you have a thread sync problem? (JSONArray)");
					}
				}
				
				list.add(subProp);
			}
			return new DasProp(list);
		}
		
		if(sType.equals("JSONObject")){
			JSONObject jo = (JSONObject)obj;
			JSONArray ja = jo.names();
			
			Map<String, DasProp> map = new HashMap<>();
			
			for(int i = 0; i < ja.length(); ++i){
				DasProp subProp;
				String sKey;
				try {
					sKey = ja.getString(i); 
					if(jo.isNull(sKey)) subProp = new DasProp(null);
					else{
						subProp = JdoToProp(jo.get(sKey));
					}
				} catch (JSONException ex) {
					// Can't happen unless some other thread is messing with our Json
					throw new AssertionError("Do you have a thread sync problem? (JSONobject)");
				}
				
				map.put(sKey, subProp);
			}
			return new DasProp(map);
		}
		
		throw new UnsupportedOperationException(
			"Conversion for org.json type "+sType+" not supported yet."
		);
	}
		
	private static DasProp propAtPath(Object data, String[] aFragPath){
		
		// Now we have two major branches, the JSON object branch or the JSON array branch.
		if(data instanceof JSONObject){
			JSONObject jo = (JSONObject)data;
			
			if(!jo.has(aFragPath[0])) return nullProp;
			if(jo.isNull(aFragPath[0])) return new DasProp(null);
			
			Object item;
			try { item = jo.get(aFragPath[0]); } 
			catch (JSONException ex) { return nullProp; }
			
			if(aFragPath.length == 1) return JdoToProp(item);
			else return propAtPath(item, Arrays.copyOfRange(aFragPath, 1, aFragPath.length));	
		}
		
		if(data instanceof JSONArray){
			JSONArray ja = (JSONArray)data;
			int i = Integer.parseInt(aFragPath[0]);
			
			if((i < 0 )||(i >= ja.length())) return nullProp;
			if(ja.isNull(i)) return new DasProp(null);
			
			Object item;
			try { item = ja.get(i); }
			catch (JSONException ex) { return nullProp; }
			
			if(aFragPath.length == 1) return JdoToProp(item);
			else return propAtPath(item, Arrays.copyOfRange(aFragPath, 1, aFragPath.length));	
		}
		
		// fragment lookup rules are pretty relaxed.  If the caller asks for sub-items
		// from something that can't have sub-items, just return null instead of throwing.
		// This sticks with the Map.get() style interface we're trying to emulate.
		return nullProp;
	}
	
	/** Get a give property given an internal object fragment string.
	 * 
	 * This uses the same semantics as the Map interface.
	 * 
	 * @param data A JSONArray or JSONObject value.
	 * @param sFragment
	 * @return The property at the given fragment path or null if no object exists at
	 * the given path
	 */
	public static DasProp prop(Object data, String sFragment){
		
		// Ignore separators at beginning or end of the path
		String sTmp = sFragment.replaceAll("^/+","").replaceAll("/+$","");
		
		// Treat repeated separaters as one
		String[] aFragPath = sTmp.split("/+");
		
		if(aFragPath.length == 0) return nullProp;
		
		return propAtPath(data, aFragPath);
	}
	
	/** Find a data in a json object give a DasProp fragment path 
	 * 
	 * @param data Either a JSONObject or JSONArray.  Since these don't share a 
	 *             base type, we have to use the moral equivalent of void* pointers.
	 * @param sFragment
	 * @param oDefault
	 * @return 
	 */
	public static DasProp prop(Object data, String sFragment, Object oDefault) 
	{
		DasProp prop = prop(data, sFragment);
		if(prop == null) return new DasProp(oDefault);
		else return prop;
	}
}
