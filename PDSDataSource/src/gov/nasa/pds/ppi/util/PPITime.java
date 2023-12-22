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
package gov.nasa.pds.ppi.util;

import java.util.*;
import java.text.*;

/**
 * PPITime is a class that contains a methods for parsing, comparing and
 * generating time strings.
 *
 * @author      Todd King
 * @author      Planetary Data System
 * @version     1.0, 04/21/03
 * @since       1.0
 */
public class PPITime implements Comparable<PPITime>{
 	// Enumeration of possible value type
	/** 1/19/83 11:45:30.234 */	public static final String AMERDATE = "MM/dd/yy HH:mm:ss.SSS";      
	/** 19.1.83 11:45:30.234 */	public static final String EURODATE = "dd-MM-yy HH:mm:ss.SSS";
	/** jan 19, 1983 11:45:30.234 */ public static final String AMER = "MMM dd, yyyy HH:mm:ss.SSS";
	/** 19 jan 1983 11:45:30.234 */	public static final String EURO = "dd MMM yyyy HH:mm:ss.SSS";
	/** 1983 303 11:45:30.234 */ public static final String DOY = "yyyy DD HH:mm:ss.SSS";
	/** 83.1.19 11:45:30.234 */ public static final String JAPANDATE = "yyyy.MM.dd HH:mm:ss.SSS";
	/** 83.19.1 11:45:30.234 */ public static final String NIPPONDATE = "yyyy.dd.MM yyyy HH:mm:ss.SSS";
	/** 83 01 19 00 11 45 30.234 */ public static final String HIGHLOW = "yyyy MM dd HH:mm:ss.SSS";
	/** 83 019 JAN 19 11 45 30.234 */ public static final String ISEEDATE = "yyyy DDD MMM dd HH:mm:ss.SSS";
	/** 1989-JAN-19 11:45:30.234 */	public static final String DFS = "yyyy-MMM-dd HH:mm:ss.SSS";
	/** 1989/01/19 11:45:30.234 */ public static final String ABBRDFS = "yyyy/MM/dd HH:mm:ss.SSS";
	/** 1989-01-19T11:45:30.234 or 1989-019T11:45:30.234 with omissions */	public static final String PDS = "T";
	/** 19890119T114530.234 */ public static final String ISO = "yyyyMMddThhmmss.SSSS";
	/** 758979930.234 */ public static final String BINARY = "B1966";
	/** 19-01-1989 11:45:30.234 */ public static final String CLUSTER = "dd-MM-yyy HH:mm:ss.SSS";

	/*
	 * Default timezone (GMT - no offset)
	 */
	public TimeZone mTimeZone = TimeZone.getTimeZone("GMT-0:00");
		
	/** The Date variable where parsed values are stored. Also the value
	 * of this element is used when generating formatted output.
	 */
	public Calendar	mDate = Calendar.getInstance(mTimeZone);
	
    /** 
     * Creates an instance of a time value.
     * The instance is not initialized and must be either using
     * now() convert(), or copy().
	 *
     * @since           1.0
     */
 	public PPITime() {
	}
		
    /** 
     * Creates an instance of a time value.
     * The instance is initialized as a copy of the
     * passed argument.
     *
     * @param item		the instance of a PPITime value 
     *					to initialize this instance with.
	 *
     * @since           1.0
     */
 	public PPITime(PPITime item) {
		mDate = (Calendar) item.mDate.clone();
	}
	
	/** Execute the class from the command line
	 * @param args make javadoc shutup
	 */	
	public static void main(String[] args) {
		PPITime	time = new PPITime();
		String buffer;
		
		// Check arguments
		if(args.length < 3) {
			System.out.println("Usage: PPITime TimeString InFormat OutFormat");
			return;
		}

		// Process arguments
		System.out.println("args[0]: " + args[0]);
		time.convert(time.findSpec(args[1]), args[0]);
		
		System.out.println("Binary: " + time.format(BINARY));		
		
		System.out.print(args[2] + ": ");
		buffer = time.format(time.findSpec(args[2]));
		System.out.println(buffer);		
	}
	
    /** 
     * Parses a string into a Date using the given pattern.
     * The pattern can be specified using one of the predefined
     * formats or it can be specified using the syntax used
     * by  @see SimpleDateFormat.
	 * 
	 * @param pattern	the text containing the pattern to parse
	 *                  buffer with.
	 * @param buffer	the text containing the string to parse.
	 *
	 * @return			<code>true</code> if the string could be parsed.
	 *					<code>false</code> if any error occured.
	 *
     * @since           1.0
     */
	public boolean convert(String pattern, String buffer) 
	{
	
		long	milli;
		SimpleDateFormat	parser;
		Date	date;
		String	part[];
		String	piece[];
		int		n;
		
		if(buffer.length() == 0) return false;
		if(pattern.length() == 0) return false;
		
		try {
			if(pattern.charAt(0) == 'B') {	// Special case - binary seconds from reference year
				int		year;
				double	seconds;
				
				if(pattern.length() < 5) year = 1966;	// Cline Time
				else year = Integer.parseInt(pattern.substring(1));
				
				seconds = Double.parseDouble(buffer);
				milli = (long) (seconds * 1000);
				mDate.setTimeInMillis(milli);
				mDate.add(Calendar.YEAR, year - 1970);
			} else if(pattern.charAt(0) == 'T') {	// Special case - PDS style time
				int year = 0;
				int doy = 1;
				int month = 0;
				int day = 1;
				int hour = 0;
				int minute = 0;
				double	seconds = 0.0;
	
				if(buffer.compareToIgnoreCase("EOM") == 0) {
					eternity();
					return true;
				}
								
				if(buffer.compareToIgnoreCase("LAUNCH") == 0) {
					dawn();
					return true;
				}
				
				// Handle old PDS times with a Z at the end.
				if(buffer.charAt(buffer.length()-1) == 'Z'){
					if(buffer.length() < 2)
						return false;
					buffer = buffer.substring(0,buffer.length()-1);
				}
				
				part = buffer.split("T");
				piece = part[0].split("-");
				if(piece.length > 0) year = Integer.parseInt(piece[0]);
				
				if(piece.length == 2) doy = Integer.parseInt(piece[1]);
				if(piece.length > 2) {
					month = Integer.parseInt(piece[1]); day = Integer.parseInt(piece[2]);
				}
				if(part.length > 1) {	// Time portion
					piece = part[1].split(":");
					if(piece.length > 0) hour = Integer.parseInt(piece[0]);
					if(piece.length > 1) minute = Integer.parseInt(piece[1]);
					if(piece.length > 2) seconds = Double.parseDouble(piece[2]);
				}
				// Reformat string
				if(month == 0) {
					buffer = year + " " + doy + " ";
					pattern = "yyyy DDD HH:mm:ss.SSS";
				} else {
					buffer = year + " " + month + " " + day + " ";
					pattern = "yyyy MM dd HH:mm:ss.SSS";
				}
				buffer	+=hour + ":" + minute + ":"	+ seconds;
				parser = new SimpleDateFormat(pattern);
				parser.setTimeZone(mTimeZone);
				mDate.setTime(parser.parse(buffer));
			} else {
				parser = new SimpleDateFormat(pattern);	
				parser.setTimeZone(mTimeZone);
				mDate.setTime(parser.parse(buffer));
				n = mDate.get(Calendar.YEAR);
				if(n >= 20 && n < 100) mDate.add(Calendar.YEAR, 1900);
				if(n >= 0 && n < 20) mDate.add(Calendar.YEAR, 2000);
			}
		} catch(Exception e) {
			return false;
		}
		
		return true;
	}
	
    /** 
     * Formats the time in the requested format and returns the string.
	 * 
	 * @param pattern	the text containing the pattern to format
	 *					the time as.
	 *
	 * @return			a string containing the formatted time value.
	 *
     * @since           1.0
     */
	public String format(String pattern) {
		SimpleDateFormat	parser;
		String		buffer;
		String		temp;
		int			n;
		
		if(pattern == null) return "Invalid pattern";
		
		if(pattern.charAt(0) == 'B') {	// Special case - binary seconds from reference year
			int		year;
			long	seconds;
			long	milli;
			long	diff;
			
			if(pattern.length() < 5) year = 1966;	// Cline Time
			else year = Integer.parseInt(pattern.substring(1));
			
			Calendar refYear = Calendar.getInstance(mTimeZone);
			refYear.set(year, 1, 1, 0, 0, 0);
			Calendar sysYear = Calendar.getInstance(mTimeZone);
			sysYear.set(1970, 1, 1, 0, 0, 0);

			milli = mDate.getTimeInMillis();
			milli -= refYear.getTimeInMillis();
			milli += sysYear.getTimeInMillis();
			seconds = milli / 1000;
			diff = milli - (seconds * 1000);
			buffer = Long.toString(seconds);
			buffer += ".";
			temp = Long.toString(diff);
			for(int i = 0; i < 3 - temp.length(); i++) buffer += "0";
			n = temp.length();
			if(n > 3) n = 3;
			buffer += temp.substring(0, n);
		} else if((n = pattern.indexOf('T')) != -1) {	// Special case - PDS style time
			if(pattern.compareTo("T") == 0) {	// Fule time format
				parser = new SimpleDateFormat("yyyy-MM-dd");
				parser.setTimeZone(mTimeZone);
				buffer = parser.format(mDate.getTime()) + "T";
				parser = new SimpleDateFormat("HH:mm:ss.SSS");
				parser.setTimeZone(mTimeZone);
				buffer += parser.format(mDate.getTime());
			} else {	// Treat as two part pattern split by "T"
				buffer = "";
				temp = pattern.substring(0, n);
				if(temp.length() > 0) {
					parser = new SimpleDateFormat(pattern.substring(0, n));
					parser.setTimeZone(mTimeZone);
					buffer += parser.format(mDate.getTime());
				}
				buffer += "T";
				temp = pattern.substring(n+1);
				if(temp.length() > 0) {
					parser = new SimpleDateFormat(temp);
					parser.setTimeZone(mTimeZone);
					buffer += parser.format(mDate.getTime());
				}
			}
		} else {
			parser = new SimpleDateFormat(pattern);
			parser.setTimeZone(mTimeZone);
			buffer = parser.format(mDate.getTime());
		}
		return buffer;
	}
	
    /** 
     * Returns the time format specification that matches the given
     * standard format name. If no match is found the passed string is
     * returned.
	 * 
	 * @param name		the name of the standard format.
	 *
	 * @return			a string containing the format specification that matches
	 * 					the passed name. If no match is found the passed string
	 *					is returned.
	 *
     * @since           1.0
     */
	static public String findSpec(String name) {
		// Placed in order of most commonly used
		if(name.compareToIgnoreCase("PDS") == 0)		return PDS;
		if(name.compareToIgnoreCase("BINARY") == 0)		return BINARY;
		if(name.compareToIgnoreCase("CLUSTER") == 0)	return CLUSTER;
		if(name.compareToIgnoreCase("DFS") == 0)		return DFS;
		if(name.compareToIgnoreCase("ABBRDFS") == 0)	return ABBRDFS;
		if(name.compareToIgnoreCase("ISO") == 0)		return ISO;
		if(name.compareToIgnoreCase("DOY") == 0)		return DOY;
		if(name.compareToIgnoreCase("AMERDATE") == 0)	return AMERDATE;      
		if(name.compareToIgnoreCase("EURODATE") == 0)	return EURODATE;
		if(name.compareToIgnoreCase("AMER") == 0) 		return AMER;
		if(name.compareToIgnoreCase("EURO") == 0)		return EURO;
		if(name.compareToIgnoreCase("JAPANDATE") == 0)	return JAPANDATE;
		if(name.compareToIgnoreCase("NIPPONDATE") == 0)	return NIPPONDATE;
		if(name.compareToIgnoreCase("HIGHLOW") == 0)	return HIGHLOW;
		if(name.compareToIgnoreCase("ISEEDATE") == 0)	return ISEEDATE;

		return name;	// Return passed string - assume its a time spec.
	}
	
    /** 
     * Sets the time to the earliest possible time.
	 * 
     * @since           1.0
     */
	public void dawn() {
		Calendar calendar = Calendar.getInstance(mTimeZone);
		
		calendar.set(Calendar.YEAR, calendar.getMinimum(Calendar.YEAR));
		calendar.set(Calendar.MONTH, calendar.getMinimum(Calendar.MONTH));
		calendar.set(Calendar.DAY_OF_MONTH, calendar.getMinimum(Calendar.DAY_OF_MONTH));
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getMinimum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getMinimum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getMinimum(Calendar.SECOND));
		
		mDate.setTime(calendar.getTime());
	}
	
    /** 
     * Sets the time to the latest possible time.
	 * 
     * @since           1.0
     */
	public void eternity() {
		Calendar calendar = Calendar.getInstance(mTimeZone);
		
		calendar.set(Calendar.YEAR, 9000); // Using getMaximum does not work properly
		calendar.set(Calendar.MONTH, calendar.getMaximum(Calendar.MONTH));
		calendar.set(Calendar.DAY_OF_MONTH, calendar.getMaximum(Calendar.DAY_OF_MONTH));
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getMaximum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getMaximum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getMaximum(Calendar.SECOND));
		
		mDate.setTime(calendar.getTime());
	}
	
    /** 
     * Compare a time to this instance for ordering.
	 * 
	 * @param anotherTime	the PPITime to compare to this time.
	 *
	 * @return			the value 0 if the passed argument is equal to 
	 *					this instance; a value less than 0 if the this instance
	 *					is before the passed argument; and a value greater than 0 if 
	 *					the this instance is after the passed argument.
	 *
     * @since           1.0
     */
	public int compareTo(PPITime anotherTime) {
		return mDate.getTime().compareTo(anotherTime.mDate.getTime());
	}
	
	/** Override of equals to make sure that it is consistant with compareTo i.e.:
	 *    (x.compareTo(y) == 0) == (x.equals(y))
	 */
	public boolean equals(Object obj){
		if(this == obj) return true;
		if(!(obj instanceof PPITime)) return false;
		PPITime other = (PPITime)obj;
		
		return (mDate.compareTo(other.mDate) == 0);
	}
	
	/** Override of hashCode to make sure it is consistant with equals */
	public int hashCode(){ return mDate.hashCode(); }
	
    /** 
     * Makes a copy of a PPITime item.
	 *
	 * @param item	the instance of the PPITime item to copy.
	 *
     * @since           1.0
     */
	public void copy(PPITime item) {
		mDate = (Calendar) item.mDate.clone();
	}
	
    /** 
     * Advances the time by a specified number of minutes.
	 *
	 * @param minutes	the number of minutes to advance the time by.
	 *					The value may include a fractional minute.
	 *
     * @since           1.0
     */
	public void advance(double minutes) {
		int min = (int) minutes;
		double seconds = (minutes - min) * 60.0;
		int sec = (int) seconds;
		int milli = (int) ((seconds - sec) * 1000);
		
		mDate.add(Calendar.MINUTE, min);
		mDate.add(Calendar.SECOND, sec);
		mDate.add(Calendar.MILLISECOND, milli);
	}
	
    /** 
     * Sets the date to the current system time.
	 *
     * @since           1.0
     */
	public void now() {
		mDate.setTime(Calendar.getInstance(mTimeZone).getTime());
	}
	
    /** 
     * Returns the number of milliseconds between a time and this time.
	 *
	 * @param other the instance of the PPITime item to compare to.
	 *
     * @since           1.0
     */
	public long span(PPITime other) {
		return (mDate.getTimeInMillis() - other.mDate.getTimeInMillis());
	}
	
	/** Print the time in ISO/DIS 8601 day of month standard format. */
	public String toString(){
		// Since the calendar is constructed via getInstance the fields should always
		// be available.
		DecimalFormat df = new DecimalFormat("00");
		DecimalFormat dfm = new DecimalFormat("000");
		
		return mDate.get(Calendar.YEAR)+"-"+df.format(mDate.get(Calendar.MONTH)+1)+"-"+
			df.format(mDate.get(Calendar.DAY_OF_MONTH))+"T"+
			df.format(mDate.get(Calendar.HOUR_OF_DAY))+":"+
			df.format(mDate.get(Calendar.MINUTE))+":"+
			df.format(mDate.get(Calendar.SECOND))+"."+
			dfm.format(mDate.get(Calendar.MILLISECOND));
	}
}
