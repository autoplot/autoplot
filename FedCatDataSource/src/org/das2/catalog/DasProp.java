/* This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org/>
 */
package org.das2.catalog;

import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.das2.datum.Datum;
import org.das2.datum.TimeUtil;

/** Properties retrieved from a das catalog node, a wrapper around Object.  
 *
 * DasNodes can have properties of different types.  Since Java reflection provides
 * methods for determining the actual return type of a property pulled from a catalog
 * node, introducing a property type seems on it's face to be an unnecessary 
 * complication.  However to avoid having a completely open ended interface that can
 * return just any type of object this class exists to put boundaries around the
 * value types an application program will have to deal with when reading from a das
 * catalog.  
 * 
 * Without this class, constructs like the following would be common in application
 * code:
 * 
 *    String sFragment = "interface/data/efield/units";
 *    Object obj = node.prop(sFragment, Map<String,Object>.class);
 *    Map<String,Object> map = (Map<String,Object>)obj;
 * 
 * Instead this becomes:
 *   
 *    Map<String,DasProp> map = node.prop("interface/data/efield/units").map();
 * 
 * @author cwp
 */
public class DasProp {
	
	public enum Type { NULL, BOOL, STR, LONG, DOUBLE, DATUM, TIME, LIST, MAP };
	
	private static final ZoneOffset UTC_ZONE = ZoneOffset.UTC;
	
	public static final Type NULL  =  Type.NULL;
	public static final Type BOOL  = Type.BOOL;
	public static final int STR   = 20;
	public static final int INT   = 30;
	public static final int DATUM = 40;
	public static final int TIME  = 50;  
	public static final int LIST  = 60;
	public static final int MAP   = 70;
	
	protected Object obj;
	protected Type type;
	
	/** Wrap a raw object and give it a type.
	 * 
	 * This constructor does not handle parsing of string objects to some
	 * other type.  To parse strings use the two-parameter constructor.
	 * 
	 * @param item The object to wrap.  Must be one of, null, String,
	 *             Integer, Double, Datum, List<DasProp>, Map<String, DasProp>
	 */
	public DasProp(Object item){
		if(item == null){ type = Type.NULL; 	obj = null; return; }
		if(item instanceof String){ type = Type.STR;    obj = item; return; }
		if(item instanceof Integer){ 
			type = Type.LONG;
			obj = new Long((Integer)item);
			return; 
		}
		if(item instanceof Long){   type = Type.LONG;   obj = item; return; }
		if(item instanceof Float){  
			type = Type.DOUBLE; 
			obj = new Double((Float)item); 
			return; 
		}
		if(item instanceof Double){ type = Type.DOUBLE; obj = item; return; }
		if(item instanceof Datum){type = Type.DATUM; obj = item; return;}
		
		if(item instanceof Double){ 
			type = Type.DATUM;
			obj = Datum.create((Double)item);
			return;
		}
		
		// instanceof doesn't work for generics, switching over to cast exceptions 
		// for the remainder...
		try{
			List<DasProp> list = (List<DasProp>)item;
			type = Type.LIST;
			obj = list;
		}
		catch(ClassCastException e1){
			try{
				Map<String, DasProp> map = (Map<String, DasProp>)item;
				type = Type.MAP;
				obj = map;
			}
			catch(ClassCastException e2){
				throw new IllegalArgumentException(
					"Object " + item.toString() + "cannot be wrapped as a Das Catalog Property"
				);		
			}
		}
	}
	
	public String str(){ 
		if(obj == null) return null;
		return obj.toString();
	}
	
	public ZonedDateTime time() throws ParseException{
		TimeUtil.TimeStruct ts = TimeUtil.parseTime(obj.toString());
		int nSec = (int)ts.seconds;
		int nNano = (int)((ts.seconds - nSec) * 1.0e+9);
		ZonedDateTime zdt = ZonedDateTime.of(
			ts.year, ts.month, ts.day, ts.hour, ts.minute, nSec, nNano, UTC_ZONE
		);
		return zdt;
	}
	
	public List<String> list(){
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	public Map<String, DasProp> map(){
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	public DasProp map(String sKey){
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	Datum datum(){
		throw new UnsupportedOperationException("Not supported yet.");
	} 
	
}
