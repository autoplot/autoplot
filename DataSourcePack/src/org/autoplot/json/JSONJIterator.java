package org.autoplot.json;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.json.JSONException;

public class JSONJIterator implements Iterator<Object>, AutoCloseable {
    private final BufferedReader reader;
    private String nextLine;

    public JSONJIterator(InputStream inputStream) {
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
        advance();
    }

    private void advance() {
        try {
            nextLine = reader.readLine();
            if (nextLine == null || ( nextLine=nextLine.trim() ).isEmpty() ) {
                reader.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return nextLine != null;
    }

    @Override
    public Object next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        
        String jsonText = nextLine.trim();
        Object parsed=null;
        try {
            if (jsonText.startsWith("{")) {
                parsed = new JSONObject(jsonText);
            } else if (jsonText.startsWith("[")) {
                parsed = new JSONArray(jsonText);
            } else {
                throw new IllegalArgumentException("Invalid JSONJ record: " + jsonText);
            }
        } catch ( JSONException ex ) {
            
        }
        advance();
        return parsed;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public static void main(String[] args) {
        String jsonj = "                {\"name\": \"Alice\", \"age\": 30}\n"+
                "{\"name\": \"Bob\", \"age\": 25}\n" +
                "{\"name\": \"Charlie\", \"age\": 35}\n " +
                "[1, 2, 3, 4, 5]\n" + 
                "";

        try (JSONJIterator iterator = new JSONJIterator(new ByteArrayInputStream(jsonj.getBytes()))) {
            while (iterator.hasNext()) {
                Object record = iterator.next();
                System.out.println(record.getClass().getSimpleName() + ": " + record);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
