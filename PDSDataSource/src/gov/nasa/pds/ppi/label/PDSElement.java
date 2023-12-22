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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.MalformedInputException;
import java.util.ArrayList;
import java.util.Iterator;
/**
 * PDSElement is a class that contains a single definition or line 
 * as specified in the PDS Object Defnition Language (ODL). 
 * In this context a line may be an simple line of text, a block of 
 * commented text, or a keyword/value pair. A value may extend 
 * over more than one physical line if it is quoted or part of 
 * a value set.
 *
 * @author      Todd King
 * @author      Planetary Data System
 * @version     1.0, 02/21/03
 * @since       1.0
 */
public class PDSElement {
 	// Enumeration of possible value type
 	/** Unspecified grouping type. */	public static final int	TYPE_NONE		= 0;
 	/** Ordered list of values. */		public static final int	TYPE_ORDERED	= 1;	
 	/** An unordered list of values. */	public static final int	TYPE_UNORDERED	= 2;
 	/** A blank line */					public static final int	TYPE_BLANK_LINE	= 3;
 	/** A comment */					public static final int	TYPE_COMMENT	= 4;
 	
 	/** The text found before the equal sign of an element. If the element does not
 	 * contain and equal sign the value is blank.
 	 */
 	public String		mKeyword = "";
 	
 	/** An array containing a PDSValue object for each value following
 	 * 						the equal sign in the element. There may be any number of
 	 *						values.
 	 */
 	public ArrayList	mValue = new ArrayList();
 	
 	/** The basic type of the value. Possible values include:
 	 *						<dt>TYPE_NONE</dt><dd>Unspecified grouping type</dd>
 	 *						<dt>TYPE_ORDERED</dt><dd>An ordered list of values</dd>
 	 *						<dt>TYPE_UNORDERED</dt><dd>A unordered list of values</dd>
 	 */
 	public int			mType = TYPE_NONE;
 	
 	/** The comment text found within the element. If the element does not contain a comment 
	 * the value is blank.
 	 */
 	public String		mComment = "";
 	
 	/** The raw line as read from the file.
 	 */
 	public char[]		mRaw = null;
 	
 	/** Maximum line length when printing. Really its 80 including the DOS style new line (2 bytes). 
 	 * All lines are wrapped if they exceed this length
 	 */
 	public int					mMaxLength = 78;	
 	
 	/** The count of the number of physical lines parsed into this element
 	 */
 	 public int				mLineCount = 0;
 	 
         
         public String  mSource= "<unknown>";
         
 	/** Indicates whether a symtax error occurred will parsing or reading a line.
 	 */
 	 public boolean			mSyntaxError = false;
 	 
 	/** Creates an instance of a PDSElement */
 	public PDSElement() {
 	}
 	
 	/** Creates an instance of a PDSElement */
 	public PDSElement(int line) {
 		mLineCount = line;
 	}
        
        public PDSElement( int line, String source ) {
            mSource= source;
        }
 	
 	/**
 	 * Clears an element and set it to an initial state
 	 **/
 	 public void clear()
 	 {
		mKeyword = "";
		mValue.clear();
		mType = TYPE_NONE;
		mComment = "";
		mRaw = null;
		mMaxLength = 78;	
		mLineCount = 0;
		mSyntaxError = false;
 	 	
 	 }
    /** 
     * Parses the next element from a file stream. An element
	 * is a single definition or line as specified in the PDS 
	 * Object Defnition Language (ODL). In this context a line 
	 * may be an simple line of text, a block of commented text, 
	 * or a keyword/value pair. A value may extend over more than 
	 * one physical line if it is quoted or part of a value set.
	 * 
     * @param reader    the input file stream.
     *
     * @return          <code>true</code> if an element was parsed from the stream;
     *                  <code>false</code> if the end of file or an error was encountered.
     * @since           1.0
     */
	 /* @see             FileInputStream */
     // Note: We use character arrays for optimization. Long strings
     //       are very slow to parse.
	public boolean parse(BufferedReader reader) 
		throws PDSException
	{
		char		lastC = 0;
		char		c = 0;
		int			quote = ' ';
		int			i;
		int 		total = 0;
		boolean		inQuote = false;
		boolean		inBlock = false;
		boolean		inComment = false;
		boolean		inUnits = false;
		boolean		add = true;
		int			commentStart;
		int			commentEnd;
		boolean		scanning = true;
		String		buffer;
		
		try {
			clear();
			while(scanning) {
				mRaw = readLine(reader);
				if(mRaw == null) return false;		// End of file
				
				total = strlen(mRaw);
				// if(total == 0) continue;	// Keep reading
						
				// Now parse the raw string
				// Extract comment from string
				commentStart = 0;
				commentEnd = 0;
				inComment = false;
				inQuote = false;
				lastC = 0;
				for(i = 0; i < total; i++) {
					c = mRaw[i];
					if(!inComment) {	// Check if in quote
						if(c == '"') { if(inQuote) inQuote = false; else inQuote = true; }
					}
					if(!inQuote) {	// Check if in comment
						if(lastC == '/' && c == '*') { commentStart = i-1; inComment = true; }
						if(lastC == '*' && c == '/') { commentEnd = i; inComment = false; add = false; }
					}
					lastC = c;
					
					if(inComment) { mComment += (char) c; continue; }
				}
				if(mComment.length() > 0) mComment = mComment.substring(1);	// Remove leading "*";
				if(mComment.length() > 0) mComment = mComment.substring(0, mComment.length() - 1);	// Remove trailing "*"
				
				// Remove comment from raw buffer;
				if(commentStart != commentEnd) {
					deleteString(mRaw, commentStart, commentEnd);
				}
				
				trimSpace(mRaw);
				total = strlen(mRaw);
				if(total == 0) {
					if(commentEnd > 0) {
						mType = TYPE_COMMENT;
					} else {
						mType = TYPE_BLANK_LINE;
					}
					return true;
				}
				
				scanning = false;
			}

			// Check for END statement
			if(total >= 3) {
				buffer = new String(mRaw, 0, 3);
				if(total > 3) c = mRaw[3];
				else c = ' ';
				if(buffer.compareTo("END") == 0 && Character.isWhitespace(c)) {
					mKeyword = "END";
					return false;
				}
			}
			
			// Extract keyword and parse value
			i = indexOf(mRaw, '=');
			if(i == -1) { // Just a word - check if "END"
				mKeyword = new String(mRaw, 0, total); 
				if(total > 14) {	// Check for CCSD tag
					if(mKeyword.substring(0, 14).compareTo("CCSD3ZF0000100") == 0) return true;
				}
				if(mKeyword.compareTo("END") == 0) return false; // End of label - no more keywords
				throw(new PDSException("Syntax error. Unrecognized plain word (" + mKeyword + ") at line " + mLineCount));
			}
		
			// Extract keyword	
			mKeyword = new String(mRaw, 0, i);
			mKeyword = mKeyword.trim();
	
			// Extract value
			return parseValue(mRaw, i+1);
		} catch(PDSException e) {
			throw(e);
		}

	}

	public int indexOf(char[] cbuff, char c)
	{
		for(int i = 0; i < cbuff.length; i++) { if(cbuff[i] == c) return i; }
		return -1;
	}
	
	public void deleteString(char[] cbuff, int start, int end) 
	{
		int	i;
		int	n = start;
		
		for(i = end+1; i < cbuff.length; i++) { cbuff[n++] = cbuff[i]; }
		for(i = n; i < cbuff.length; i++) { cbuff[i] = '\0'; }	
	}
	
	public int strlen(char[] cbuff)
	{
		return strlen(cbuff, 0);
	}
	
	public int strlen(char[] cbuff, int startAt)
	{
		int	total = cbuff.length;
		for(int i = startAt; i < total; i++) {if(cbuff[i] == '\0') return i-startAt; }
		return cbuff.length-startAt;
	}
	
    /** 
     * Read the next element definition from an input file stream.
	 * An element may extend over more than one physical line if it 
	 * is quoted or part of a value set.
	 * 
     * @param reader      the input file stream.
     *
     * @return          <code>true</code> if an element was parsed from the stream;
     *                  <code>false</code> if the end of file or an error was encountered.
     *
     * @since           1.0
     */
	/* @see             FileInputStream */
	public char[] readLine(BufferedReader reader) 
		throws PDSException
	{
		int			lastC	= 0;
		int			c		= 0;
		int			quote = ' ';
		boolean		inQuote = false;
		boolean		inLiteral = false;
		boolean		inList = false;
		boolean		inOrderedList = false;
		boolean		inComment = false;
		boolean		inUnits = false;
		boolean		inValue = false;
		boolean		foundValue = false;
		boolean		foundComment = false;
		
		int			n 			= 0;
		int			total		= 0;

		int			maxBuffer	= 4048;
		char[]		buffer = new char[maxBuffer];
		ArrayList	buffList = new ArrayList();

		mSyntaxError = false;
		try {
			while((c = reader.read()) != -1) {
				buffer[n] = (char) c;
				n++;
				total++;
				if(n == maxBuffer) {
					buffList.add(buffer);
					buffer = new char[4048];
					n = 0;
				}
				if(c == '\n') mLineCount++;
				if(!inComment) {	// Check for quoting
					if(c == '"') { if(inQuote) inQuote = false; else inQuote = true; }
				}
				if(!inQuote) {	// Check if in comment
					if(lastC == '/' && c == '*') { inComment = true; }
					if(lastC == '*' && c == '/') { inComment = false; foundComment = true; }
				}
				lastC = c;
				if(inQuote) continue;
				if(inComment) continue;
				if(inValue && !isSpace(c)) { foundValue = true; }
				
				if(c == '{') { if(inList) throw(new PDSException("List in a list") ); else inList = true; } 
				if(c == '}') { if(!inList) throw(new PDSException("End of list without start of list") ); else  inList = false; } 
				if(c == '(') { if(inOrderedList) throw(new PDSException("Ordered list in an ordered list") ); else inOrderedList = true; }
				if(c == ')') { if(!inOrderedList) throw(new PDSException("End of ordered list without start of ordered list") ); else inOrderedList = false; }
				
				if(c == '\'') { if(inLiteral) inLiteral = false; else inLiteral = true; }
				if(c == '<') { if(inUnits) { mSyntaxError = true; return null; } inUnits = true; }
				if(c == '>') { if(!inUnits) { mSyntaxError = true; return null; } inUnits = false; }
				if(c == '=') { inValue = true; }
				
				if(c == '\n' && !foundValue && foundComment) break;
				if(c == '\n' && foundValue && !inComment && !inQuote && !inList && !inOrderedList && !inLiteral && !inUnits) break;
				if(c == '\n' && !inValue && !inComment && !inQuote && !inList && !inOrderedList && !inLiteral && !inUnits) break;
			}
		}
		catch(MalformedInputException e){
			buffer = null;
			throw new PDSException( "Malformed Label Input, file is not 7-Bit ASCII safe");
		} catch(IOException e) {
			buffer = null;
			throw new PDSException(e);
		}
		
		if(c == -1 && total == 0) return null;	// End of file
		if(n > 0) {
			buffList.add(buffer);
		}
		
		// Build up return string
		int	i;
		int	start = 0;
		int	end;
		int	remaining = total;
		char[] cbuff = new char[total];
		Iterator t = buffList.iterator();
		while(t.hasNext()) {
			buffer = (char[]) t.next();
			n = remaining;
			if(n > maxBuffer) n = maxBuffer;
			for(i = 0; i < n; i++) cbuff[start+i] = buffer[i];
			start += n;
			remaining -= n;
		}

		return cbuff;
	}
	
	
    /** 
     * Trims the leading and trailing white space from a character array.
     * White space characters are tab, space, new line, return.
	 * 
     * @param cbuff    the character array to trim.
     *
     * @since           1.0
     */
	public void trimSpace(char[] cbuff) {
		trimSpace(cbuff, 0);
	}
		
    /** 
     * Trims the leading and trailing white space from a character array.
     * White space characters are tab, space, new line, return.
	  * 
     * @param cbuff    the character array to trim.
     *
     * @since           1.0
     */
	public void trimSpace(char[] cbuff, int startAt) {
		// Trim leading and trailing space
		int	start;
		int	end;
		int	i, n;
		int total = cbuff.length;
		
		for(end = total-1; end > -1; end--) {
			if(! isSpace(cbuff[end])) { break; }
		}
		for(start = startAt; start < end; start++) {
			if(! isSpace(cbuff[start])) { break; }
		}
		
		if(start > startAt || end < total-1) {
			n = startAt;
			for(i = start; i <= end; i++) cbuff[n++] = cbuff[i];
			for(i = end+1; i < total; i++) cbuff[n++] = '\0';	// Zero fill
		}
	}
		
    /** 
     * Determines if a character is a white space character.
     * White space characters are tab, space, new line, return.
	 * 
     * @param c    the character to check.
     *
     * @return          <code>true</code> if the character is white space;
     *                  <code>false</code> otherwise.
     *
     * @since           1.0
     */
	public boolean isSpace(int c) {
		if(c == ' ') return true;
		if(c == '\n') return true;
		if(c == '\r') return true;
		if(c == '\t') return true;
		
		return false;
	}
	
    /** 
     * Determines if an element is an "OBJECT".
     *		
     * @return          <code>true</code> if element is an OBJECT;
     *                  <code>false</code> otherwise.
     *
     * @since           1.0
     */
	public boolean isObject() {
		if(mKeyword.compareToIgnoreCase("OBJECT") == 0) return true;
		return false;
	}

    /** 
     * Parses a string as a value according to the PDS 
	 * Object Defnition Language (ODL). In this context a value
	 * can be a string, literal or list of values. Lists may
	 * be ordered (enclosed in parenthesis () )  or unordered (enclosed
	 * in curly braces {} ). A value may extend over more than 
	 * one physical line if it is quoted or part of a value set.
	 * 
     * @param cbuff    the string to parse as a value.
     *
     * @return          <code>true</code> if an value was parsed from properly;
     *                  <code>false</code> if the an error was encountered.
     *
     * @since           1.0
     */
	public boolean parseValue(char[] cbuff, int startAt) 
		throws PDSException
	{
		
		char		c = 0;
		int			quote = ' ';
		int			i;
		boolean		inQuote = false;
		boolean		inComment = false;
		boolean		inUnits = false;
		boolean		inList = false;
		PDSValue	value;
		int			maxValBuff = 8192;
		char[]		valBuff = new char[maxValBuff];
		int			valN = 0;

		inQuote = false;
		inUnits = false;
		
		mValue.clear();
		if(strlen(cbuff) <= startAt || startAt >= strlen(cbuff)) {	// No value
			throw(new PDSException("Empty value at line " + mLineCount));
		}
		
		value = new PDSValue();
		mValue.add(value);
		
		// Optimization for long quoted strings
		if(cbuff[startAt] == '"' && cbuff[startAt + strlen(cbuff, startAt) - 1] == '"') {
			value.mValue = new String(cbuff, startAt+1, strlen(cbuff, startAt)-2);
			value.mType = PDSValue.TYPE_STRING;
			return true;
		}
		
		// Parse all other types
		valN = 0;		
		for(i = startAt; i < cbuff.length; i++) {
			c = cbuff[i];
			if(c == '\0') break;	// End of line
			
			if(inQuote && c == quote) { inQuote = false; quote = ' '; continue; }
			if(!inQuote && isSpace(c)) continue;	// Ignore white space
			
			if(!inQuote && (c == '"' || c == '\'')) { 
				inQuote = true; 
				if(c == '"')	{ quote = '"'; value.mType = PDSValue.TYPE_STRING; }
				if(c == '\'')	{ quote = '\''; value.mType = PDSValue.TYPE_LITERAL; }
				continue;
			}
			if(inQuote) { if(valN < maxValBuff) {valBuff[valN++] = c;} continue; }
			
			if(c == '{' || c == '(') {
				if(inList) throw(new PDSException("Syntax error - list in list - at line: " + mLineCount));
				switch(c) {
				case '{': mType = TYPE_ORDERED; break;
				case '(': mType = TYPE_UNORDERED; break;
				}
				inList = true;
				continue;
			}
			if(c == '}' || c == ')') {	// End of list markers
				if(!inList) throw(new PDSException("Syntax error - end of list without start of list - at line: " + mLineCount + new String(cbuff)));
				inList = false;
				continue;	// List markers
			}
			
			if(c == '<') { inUnits = true; continue; }
			if(c == '>') { inUnits = false; continue; }
			
			if(!inUnits && c == ',') { 
				value.mValue = new String(valBuff, 0, valN);
				valN = 0;
				value = new PDSValue(); 
				mValue.add(value); 
				continue; 
			}
			
			if(inUnits) value.mUnits += c;
			else { if(valN < maxValBuff) valBuff[valN++] = c; }
		}
			
		if(valN > 0) {
			value.mValue = new String(valBuff, 0, valN);
		}
		
		return true;
	}
	
    /** 
     * Sets the value of an element. The type of the value is set to TYPE_NODE.
     * Clears any currently set values.
	 * 
     * @param buffer    the string to use as a value.
     *
     * @return          <code>true</code> if an value was parsed from properly;
     *                  <code>false</code> if the an error was encountered.
     *
     * @since           1.0
     */
	public boolean setValue(String buffer) 
		throws PDSException
	{
		return setValue(buffer, PDSValue.TYPE_NONE);
	}
	
    /** 
     * Sets the value of an element and set the type of the value. 
     * Clears any currently set values.
	 * 
     * @param buffer    the string to use as a value.
     * @param type    the type of the value.
     *
     * @return          <code>true</code> if an value was parsed from properly;
     *                  <code>false</code> if the an error was encountered.
     *
     * @since           1.0
     */
	public boolean setValue(String buffer, int type) 
		throws PDSException
	{
		mValue.clear();
		
		PDSValue value = new PDSValue();
		value.mValue = buffer;
		value.mType = type;
		
		mValue.add(value);
		
		return true;
	}
	
    /** 
     * Parses a string as a value according to the PDS 
	 * Object Defnition Language (ODL). In this context a value
	 * can be a stirng, literal or list of values. Lists may
	 * be ordered (enclosed in parenthesis () )  or unordered (enclosed
	 * in curly braces {} ). A value may extend over more than 
	 * one physical line if it is quoted or part of a value set.
	 * 
     * @param buffer    the string to parse as a value.
     *
     * @return          <code>true</code> if an value was parsed from properly;
     *                  <code>false</code> if the an error was encountered.
     *
     * @since           1.0
     */
	public boolean parseValue(String buffer) 
		throws PDSException
	{
		int			c = 0;
		int			quote = ' ';
		int			i;
		boolean		inQuote = false;
		boolean		inComment = false;
		boolean		inUnits = false;
		PDSValue	value;

		inQuote = false;
		inUnits = false;
		
		mValue.clear();
		if(buffer.length() == 0) {
			throw(new PDSException("Empty value at line " + mLineCount));
		}
		
		value = new PDSValue();
		mValue.add(value);
		
		c = buffer.charAt(0);
		switch(c) {
		case '{': mType = TYPE_ORDERED; break;
		case '(': mType = TYPE_UNORDERED; break;
		default: mType = TYPE_NONE; break;
		}
		
		
		for(i = 0; i < buffer.length(); i++) {
			c = buffer.charAt(i);
			if(c == '\0') break;	// End of line
			
			if(inQuote && c == quote) { inQuote = false; quote = ' '; continue; }
			if(!inQuote && Character.isWhitespace((char) c)) continue;	// Ignore white space
			
			if(!inQuote && (c == '"' || c == '\'')) { 
				inQuote = true; 
				if(c == '"')	{ quote = '"'; value.mType = PDSValue.TYPE_STRING; }
				if(c == '\'')	{ quote = '\''; value.mType = PDSValue.TYPE_LITERAL; }
				continue;
			}
			if(inQuote) { value.mValue += (char) c; continue; }
			
			if(c == '{' || c == '(' || c == '}' || c == ')') continue;	// List markers
			if(c == '<') { inUnits = true; continue; }
			if(c == '>') { inUnits = false; continue; }
			
			if(!inUnits && c == ',') { value = new PDSValue(); mValue.add(value); continue; }
			
			if(inUnits) value.mUnits += (char) c;
			else value.mValue += (char) c;
		}	
		
		return true;
	}
	
    /** 
     * Returns the number of values in the value list. 
     * An element may contain any number of values, including 0.
     * For example, an element that is just comment text will 
     * have a value size of 0. 
	 * 
     * @return          the number of values in the value list.
     * @since           1.0
     */
	public int valueSize() {
		return mValue.size();
	}
	
    /** 
     * Returns the value associated with the value item in the 
     * value array that is assocaited with the given index.
     * If the index is out of range a blank value is returned.
	 * 
     * @param index     the index of the value to return.
     * @return          a string containing the value associated with the given index. 
     * @since           1.0
     */
	public String value(int index) {
		PDSValue	value;
		
		if(index < 0 || index >= mValue.size()) return "";
		
		value = (PDSValue) mValue.get(index);
		return value.mValue;
	}
	
    /** 
     * Returns the units associated with the value item in the 
     * value array that is assocaited with the given index.
     * If the index is out of range a blank value is returned.
	 * 
     * @param index     the index of the value to return.
     * @return          a string containing the units associated with the given index. 
     * @since           1.0
     */
	public String units(int index) {
		PDSValue	value;
		
		if(index < 0 || index >= mValue.size()) return "";
		
		value = (PDSValue) mValue.get(index);
		return value.mUnits;
	}
	
    /** 
     * Create a copy of the element and return a new instance
     * of a PDSElement. 
	 * 
     * @return          a new instance of a PDSElement with the same content as this element.
     * @since           1.0
     */
	public PDSElement copy() {
		PDSElement	element = new PDSElement();
		
		element.mComment = mComment;
		element.mKeyword = mKeyword;
		element.mRaw = mRaw;
		element.mType = mType;
		element.mValue = mValue;
		
		return element;
	}

    /** 
     * Print the element according to PDS specifications for label
     * files to Syste.out. The line that is output can be indented with the equal sign
     * (when present) placed at a fixed position. The line is printed to System.out.
	 * 
     * @param indent    the number of spaces to indent for each level.
     * @param equal		the number of spaces from the end of the indent
     *					to align the equal sign for elements which have 
     *					a keyword and value.
     * @param level		the current level of indenting. The number of spaces
     *					the element will be indented is level*indent
     * @since           1.0
     */
	public void print(int indent, int equal, int level) {
		print(System.out, indent, equal, level);
	}
	
    /** 
     * Print the element according to PDS specifications for label
     * files. The line that is output can be indented with the equal sign
     * (when present) placed at a fixed position. The line is printed to System.out.
	 * 
     * @param out    	the stream to print the element to.
     * @param indent    the number of spaces to indent for each level.
     * @param equal		the number of spaces from the end of the indent
     *					to align the equal sign for elements which have 
     *					a keyword and value.
     * @param level		the current level of indenting. The number of spaces
     *					the element will be indented is level*indent
     * @since           1.0
     */
	public void print(PrintStream out, int indent, int equal, int level) {
		int			i, k, n;
		int			realIndent = 0;
		PDSValue	value;
		int			col;
		boolean		checkWrap;
		String		newline = "\r\n";
		
		switch(mType) {
		case TYPE_BLANK_LINE:
			out.print(newline);
			return;
		case TYPE_COMMENT:
			out.print("/*" + mComment + "*/" + newline);
			return;
		}
		
		// Print keyword
		if(mKeyword.length() != 0) {
			realIndent = indent * level;
			printSpaces(out, realIndent);
			out.print(mKeyword);
		}
		
				
		// Print value
		n = mValue.size();
		if(n != 0) {
			k = equal - mKeyword.length() - realIndent;
			if(k < 0) k = 1;
			printSpaces(out, k);
			out.print("= ");
			col = equal + 2;	// Start of value
			if(n > 1) {	// If more than one value in list
				switch(mType) {
					case TYPE_ORDERED:
						out.print("{");
						col++;
					break;
					case TYPE_UNORDERED:
						out.print("(");
						col++;
					break;
				}
			}
			
			// Print each value
			checkWrap = true;
			for(i = 0; i < n; i++) {
				value = (PDSValue) mValue.get(i);
				if(i != 0) {
					out.print(", ");
					col += 2;
				}
				if(mType != TYPE_NONE || !value.isQuoted()) checkWrap = true;
				else checkWrap = false;
				if(checkWrap && col + value.length() > mMaxLength) { out.print("\r\n"); col = equal + 2; printSpaces(out, col); }
				col += value.length();
				value.print(out, equal+2, 4, mMaxLength);
				checkWrap = true;
			}
			
			if(n > 1) {	// If more than one value in list
				switch(mType) {
					case TYPE_ORDERED:
						out.print("}");
					break;
					case TYPE_UNORDERED:
						out.print(")");
					break;
				}
			}
		}
		
		// Print comment
		if(mComment.length() > 0) {
			if(mKeyword.length() > 0) out.print(" ");
			out.print("/*" + mComment + "*/");
		}
		
		out.print(newline);
	} 

    /** 
     * Create a string representation of the value.
 	 * 
     * @return			a String containing the formatted value.
     *
     * @since           1.0
     *
     */
	public String valueString() {
		return valueString(false, false);
	}
	
    /** 
     * Create a string representation of the value.
 	 * 
 	 * @param plain		flag indicating if the value is not to be adorned
 	 *                  with appropriate quotation marks.
 	 *
     * @return			a String containing the formatted value.
     *
     * @since           1.0
     *
     */
	public String valueString(boolean plain) {
		return valueString(plain, true);
	}
	
    /** 
     * Create a string representation of the value.
 	 * 
 	 * @param plain		flag indicating if the value is not to be adorned
 	 *                  with appropriate quotation marks.
 	 * @param odl		flag indicating if the value is to be formated with
 	 *					ODL syntax for lists.
 	 *
     * @return			a String containing the formatted value.
     *
     * @since           1.0
     *
     */
	public String valueString(boolean plain, boolean odl) {
		String	buffer;
		PDSValue	value;
		int		i, n;
		
		// Format value
		buffer = "";
		n = mValue.size();
		if(n != 0) {
			// Format each value
			for(i = 0; i < n; i++) {
				if(i != 0) buffer += ", ";
				value = (PDSValue) mValue.get(i);
				buffer += value.formatValue(plain);
			}
			
			if(n > 1 && odl) {	// If more than one value in list
				switch(mType) {
					case TYPE_ORDERED:
						buffer = "{" + buffer + "}";
					break;
					case TYPE_UNORDERED:
						buffer = "(" + buffer + ")";
					break;
				}
			}
		}
		return buffer;
	}
	
    /** 
     * Dump all information about an element.
 	 * 
     * @param out    	the stream to print the element to.
     *
     * @since           1.0
     */
	public void dump(PrintStream out) {
		PDSValue	value;
		
		out.println("Keyword: " + mKeyword);
		out.println("Line Count: " + mLineCount);
		out.println("Type: " + mType);
		out.println("Comment: " + mComment);
		out.println("Raw line: " + mRaw);
		out.println("--- Parsed values ---");
		out.println("Number of values: " + mValue.size());
		for(int i = 0; i < mValue.size(); i++) {
			value = (PDSValue) mValue.get(i);
			value.dump(out);
		}
	}
	
    /** 
     * Print a string of spaces to an output stream. 
 	 * 
     * @param out    	the stream to print the element to.
     * @param count     the number of spaces to print.
     *
     * @since           1.0
     */
	public void printSpaces(PrintStream out, int count) {
		for(int i = 0; i < count; i++) out.print(" ");
	}

	/** Returns the keyword name if any and the first value.
	 *
	 * @todo For comments the entire comment is returned.  This
	 * should probably be length limited to about 40 characters.
	 */
	 @Override
	public String toString(){
		if(mType == TYPE_BLANK_LINE) return "<blank line>";
		if(mType == TYPE_COMMENT) return mComment;
		if(mValue.size() > 1)
			return mKeyword+" = ("+value(0)+", ...)";
		if(mValue.size() == 1)
			return mKeyword+" = "+value(0);
	
		return mKeyword+" = <blank>";
	} 
}
	
 