/* Copyright (c) 2003. All Rights Reserved.
 * 
 * This is the distribution of classes developed at IGPP/UCLA.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *  
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 * 
 * See the COPYING file located in the top-level-directory of
 * the archive of this library for complete text of license.
 *
 */
package gov.nasa.pds.ppi.label;

import java.io.PrintStream;

/**
 * PDSValue is a class that contains a value which can be associated
 * with an element. A value also has a units description and a potential
 * data type.
 *
 * @author      Todd King
 * @author      Planetary Data System
 * @version     1.0, 02/21/03
 * @since       1.0
 */
public class PDSValue {
 	/** Data type is unspecified */	public static final int	TYPE_NONE		= 0;
 	/** A quoted (") string. */		public static final int	TYPE_STRING		= 1;
 	/** A literal (') string. */	public static final int	TYPE_LITERAL	= 2;
 	
 	/** The value assocated with the instance */
 	public String	mValue = "";
 	/** The units of the value */
 	public String	mUnits = "";
 	/** The data type for the value */
 	public int		mType = TYPE_NONE;
 	
 	/** Creates and instance of a PDSValue */
 	public PDSValue() {
 	}
 	
    /** 
     * Return the length of the value when formated using PDS standards. 
     * This length includes any delimiters which may surround the value
     * as well as any units (if specified)
     *
     * @since           1.0
     */
	public int length() {
		int len = 0;
		
		switch(mType) {
			case TYPE_STRING:
			case TYPE_LITERAL:
				len += 2;
			break;
		}
		len += mValue.length();
			
		if(mUnits.length() > 0) {
			len += mUnits.length() + 3;
		}
		
		return len;
	}
	
    /** 
     * Output a value formated using PDS standards. 
     * This will output the value delimited with the properly
     * quotation marks for the data type, followed by the 
     * units. If no units are specified no units will be output.
     *
     * @since           1.0
     */
	public void print() {
		print(System.out, 0, 4, 78);
	}
	
    /** 
     * Output a value formated using PDS standards. 
     * This will output the value delimited with the properly
     * quotation marks for the data type, followed by the 
     * units. If no units are specified no units will be output.
     *
     * @param out    	the stream to print the element to.
     * @param offset    the number of spaces the first character of the string
     *                  is to be offset. 
     * @param indentLength the number of spaces to indent each line not on the 
     * 					same line as the keyword.
     * @param maxLength the maximum length for any line.
     *
     * @since           1.0
     */
	public void print(PrintStream out, int offset, int indentLength, int maxLength) {
		String	buffer;
		
		switch(mType) {
			case TYPE_STRING:
				out.print("\"");
			break;
			case TYPE_LITERAL:
				out.print("'");
			break;
		}
		// out.print(mValue);
		if(isQuoted()) buffer = mValue;
		else buffer = wrapPad(mValue, offset, indentLength, maxLength);
		
		out.print(buffer);
		switch(mType) {
			case TYPE_STRING:
				out.print("\"");
			break;
			case TYPE_LITERAL:
				out.print("'");
			break;
		}
			
		if(mUnits.length() > 0) {
			out.print(" <" + mUnits + ">");
		}
	}
	
    /** 
     * Format the value and return a string containing it.
     *
     * @return		a String contining a formatted representation of the value.
     *
     * @since           1.0
     */
	public String formatValue() {
		return formatValue(false);
	}
	
    /** 
     * Format the value and return a string containing it.
     *
     * @param plain		flag indicating if the value is not to be adorned
 	 *                  with appropriate quotation marks.
 	 *
	 * @return		a String contining a formatted representation of the value.
     *
     * @since           1.0
     */
	public String formatValue(boolean plain) {
		String	buffer = "";
		
		buffer = mValue;
		
		if(!plain) {
			switch(mType) {
				case TYPE_STRING:
					buffer = "\"" + buffer + "\"";
				break;
				case TYPE_LITERAL:
					buffer = "'" + buffer + "'";
				break;
			}
		}
			
		if(mUnits.length() > 0) {
			buffer += " <" + mUnits + ">";
		}
		
		return buffer;
	}

    /** 
     * Returns true is the data type of the value is quoted.
     *
     * @return          true if a quoted data type, false otherwise
     *
     * @since           1.0
     */
	public boolean isQuoted() {
		switch(mType) {
			case TYPE_STRING:
			case TYPE_LITERAL:
				return true;
		}
		return false;
	}
		
    /** 
     * Reformats a string so that it does not exceed 
     * a given length. Also pads the beginning of the line
     * with some number of spaces.
     *
     * @param text    	the string of text to wrap and pad.
     * @param offset    the number of spaces the first character of the string
     *                  is to be offset. 
     * @param indentLength the number of spaces to indent each line not on the 
     * 					same line as the keyword.
     * @param maxLength the maximum length for any line.
     *
     * @return          the newly formatted string.
     *
     * @since           1.0
     */
	public String wrapPad(String text, int offset, int indentLength, int maxLength) {
		String	buffer;
		String[]	line;
		String	indent = "";
		String	result = "";
		String	newline = "\r\n";
		int		i, j, n;
		
		for(i = 0; i < indentLength; i++) indent += " ";
		
		buffer = "";
		line = text.split(newline);
		for(i = 0; i < line.length; i++) {
			line[i] = line[i].trim();
			if(line[i].length() > 0) {
				if(buffer.length() > 0) buffer += " ";
				buffer += line[i];
				n = buffer.length();
				while(n+offset+indentLength > maxLength) {
					for(j = maxLength-offset-indentLength; j > 0; j--) {
						if(buffer.charAt(j) == ' ') {	// Break it
							if(offset == 0) result += indent;
							result += buffer.substring(0, j);
							result += newline;
							buffer = buffer.substring(j+1);
							break;
						}
					}
					n = buffer.length();
					offset = 0;
				}
			} else {	// Blank line 
				if(buffer.length() > 0) {
					if(offset == 0) result += indent;
					result += buffer + newline;
				}
				if(line.length > 1) result += newline;
				buffer = "";
				offset = 0;
			}
		}
		if(buffer.length() > 0) {	// Something left to add
			if(offset == 0) result += indent;
			result += buffer;
		}
		
		return result;
	}
	
    /** 
     * Dump all information about an element.
 	 * 
     * @param out    	the stream to print the element to.
     *
     * @since           1.0
     */
	public void dump(PrintStream out) {
		out.print("Type: ");
		switch(mType) {
		case TYPE_LITERAL:
			 out.println("LITERAL");
			 break;
		case TYPE_STRING:
			 out.println("STRING");
			 break;
		case TYPE_NONE:
			 out.println("NONE");
			 break;
		}
			 
		out.println("Value: " + mValue);
		out.println("Units: " + mUnits);
	}
} 
 