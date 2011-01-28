/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.ascii;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * class to experiment with JSON library and to make prototype
 * preprocess that makes using JSON more attractive.
 * @author jbf
 */
public class JsonIntro {

    private static void test1() throws JSONException {
        String ss = "# {\n"
                + "#   \"LABEL\": [ \"Time,UTC\", \"Density\" ],\n"
                + "#   \"UNITS\": [ \"Time_UTC\", \"cc-3\" ],\n"
                + "#   \"VALID_MIN\": 0,\n"
                + "# }\n";
        System.err.println(ss);
        System.err.println(prep(ss));

        JSONObject jo;
        jo = new JSONObject(prep(ss));

        System.err.println(jo.toString());
    }

    private static void test2() throws JSONException {
        String ss = "# {\n"
                + "#   LABEL: [ \"Time,UTC\", \"Density\" ],\n"
                + "#   UNITS: [ \"Time_UTC\", \"cc-3\" ],\n"
                + "#   VALID_MIN: 0,\n"
                + "# }\n";

        System.err.println(ss);
        System.err.println(prep(ss));

        JSONObject jo;
        jo = new JSONObject(prep(ss));

        System.err.println(jo.toString());
    }

    /**
     * test that commas are added.
     * @throws JSONException
     */
    private static void test3() throws JSONException {
        String ss = "# \n"
                + "#   LABEL: [ \"Time,UTC\", \"Density\" ]\n"
                + "#   UNITS: [ \"Time_UTC\", \"cc-3\" ]\n"
                + "#   VALID_MIN: 0\n"
                + "# \n";

        System.err.println(ss);
        System.err.println(prep(ss));

        JSONObject jo;
        jo = new JSONObject(prep(ss));

        System.err.println(jo.toString());
    }

    /**
     * test that commas are added.
     * @throws JSONException
     */
    private static void test3_1() throws JSONException {
        String ss = "# \n"
                + "#   LABEL: [ \"Time,UTC\", \"Density\" ]\n"
                + "#   UNITS: [ \"Time_UTC\",\n"
                + "#            \"cc-3\" \n"
                + "#          ]\n"
                + "#   VALID_MIN \n"
                + "# \n"
                + "#       :0\n"
                + "# \n";

        System.err.println(ss);
        System.err.println(prep(ss));

        JSONObject jo;
        jo = new JSONObject(prep(ss));

        System.err.println(jo.toString());
    }

    private static void test4() throws JSONException {
        String ss = "# {\n"
                + "#   LABEL: [ \"Time,UTC\", \"Density\" ],\n"
                + "#   UNITS: [ \"Time_UTC\", \"cc-3\" ],\n"
                + "#   VALID_MIN: 0\n"
                + "# }\n";

        System.err.println(ss);
        System.err.println(prep(ss));

        JSONObject jo;
        jo = new JSONObject(prep(ss));

        System.err.println(jo.toString());
    }

    /**
     * test to see that non-formatted records don't mess us up.
     * @throws JSONException
     */
    private static void test5() throws JSONException {
        String ss = "# \n"
                + "# LABEL=\"Density\"\n"
                + "# UNITS=\"cc-3\"\n"
                + "# ";

        System.err.println(ss);
        System.err.println(prep(ss));

        if (isJsonHeader(ss)) {
            JSONObject jo;
            jo = new JSONObject(prep(ss));

            System.err.println(jo.toString());
        } else {
            System.err.println("Not a JSON header");
        }
    }

    private static void test6() throws JSONException {
        String ssa = "# { \n"
                + "# TIME:{ LABEL: \"Time_UTC\" }\n"
                + "# DENSITY:{ LABEL: \"Density\", \n"
                + "#   SCALE_MIN:1E-2, SCALE_MAX:1e2, \n"
                + "#   SCALE_TYPE:\"LOG\" } \n"
                + "# }\n"
                + "# Legacy supports following line:\n"
                + "# TIME DENSITY \n"
                + "2011-01-01T00:00 0.12\n"
                + "2011-01-01T00:01 0.14\n";

        String ss= extractJsonHeader( ssa );
        System.err.println(ss);
        System.err.println(prep(ss));

        if (isJsonHeader(ss)) {
            JSONObject jo;
            jo = new JSONObject(prep(ss));

            System.err.println(jo.toString());
        } else {
            System.err.println("Not a JSON header");
        }
    }

    /**
     * we'll need a method to detect if the string is actually a JSON
     * header that can be parsed.
     * @param ss
     * @return
     */
    private static boolean isJsonHeader(String ss) {
        try {
            new JSONObject(prep(ss));
        } catch (JSONException ex) {
            return false;
        }
        return true;
    }

    /**
     * from the text, trim off the data and parts that don't appear to be 
     * part of the header.  For now let this be commented lines.
     * @param ss
     * @return
     */
    private static String extractJsonHeader( String ss ) {
        
        StringBuilder build= new StringBuilder();

        BufferedReader buf= new BufferedReader( new StringReader(ss) );
        try {
            String s = buf.readLine();
            while ( s!=null ) {
                if ( s.startsWith("#") ) {
                    build.append(s).append("\n");
                    s= buf.readLine();
                } else {
                    break;
                }
            }
            buf.close();
            return build.toString();

        } catch (IOException ex) {
            throw new RuntimeException(ex);  // shouldn't happen--StringReader
        }

    }
    /**
     * Preprocess the string to make more valid JSON.
     * 1. pop off comment character (#) from line.
     * 2. add leading and trailing braces if the first char is not an opening brace.
     * 3. add implicit comma at line breaks unless the next line starts with comma.
     * @param s
     * @return
     */
    private static String prep(String s) {
        boolean dontHaveOpeningBrace = true;
        boolean addClosingBrace = false;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new StringReader(s));

            String line = reader.readLine();
            if (line != null && line.startsWith("#")) {
                line = line.substring(1);
            }

            int iline = 1;
            while (line != null) {

                String trimLine = line.trim();

                if (dontHaveOpeningBrace) {
                    if (!trimLine.startsWith("{")) {
                        line = "{" + line;
                        addClosingBrace = true;
                    }
                    dontHaveOpeningBrace = false;
                }

                // read ahead to get the next line, so we can avoid adding comma to dangling text.  See test3_1.
                String nextLine = reader.readLine();
                if (nextLine != null && nextLine.startsWith("#")) {
                    nextLine = nextLine.substring(1);
                }

                // we can add a comma at the end of a line to make it valid.
                if (trimLine.length() > 0) {
                    char lastChar = trimLine.charAt(trimLine.length() - 1);
                    if (lastChar == '"' || Character.isDigit(lastChar) || lastChar == ']' || lastChar == '}') {
                        char nextChar;
                        if (nextLine != null && nextLine.trim().length() > 0) {
                            nextChar = nextLine.trim().charAt(0);
                            if (nextChar != ',' && nextChar != ']') {
                                line = line + ",";
                            }
                        }
                    }
                }

                sb.append(line).append("\n");

                line = nextLine;
                iline++;

            }

            if (addClosingBrace) {
                sb.append("}");
            }
            return sb.toString();

        } catch (IOException ex) {
            throw new RuntimeException(ex);

        }

    }

    public static void main(String[] arg) throws JSONException {
        JSONObject jo;
        jo = new JSONObject("{ \"fOO\":\"MY_FIRST_JSON\" }");
        System.err.println(jo.toString());
        jo = new JSONObject("{ fOO:\"MY_FIRST_JSON\" }");
        System.err.println(jo.toString());

        System.err.println("\n== test1 ==");
        test1();
        System.err.println("\n== test2 ==");
        test2();
        System.err.println("\n== test3 ==");
        test3();
        System.err.println("\n== test3_1 ==");
        test3_1();
        System.err.println("\n== test4 ==");
        test4();
        System.err.println("\n== test5 ==");
        test5();
        System.err.println("\n== test6 ==");
        test6();
    }
}
