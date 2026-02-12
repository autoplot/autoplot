package org.autoplot.util;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;

/**
 * java.util.logging Handler that writes each LogRecord as a CSV row.
 *
 * Default columns:
 *   timestamp_iso, epoch_millis, level, logger, thread, source_class, source_method, message, thrown
 */
public final class CsvFileLogHandler extends Handler implements Closeable, Flushable {

    private final BufferedWriter out;
    private final boolean writeHeader;
    private boolean headerWritten = false;

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    public CsvFileLogHandler() throws IOException {
        this(new File("/tmp/autoplot.log.csv").toPath());
    }
    
    public CsvFileLogHandler(Path file) throws IOException {
        this(file, StandardCharsets.UTF_8, true);
    }

    public CsvFileLogHandler(Path file, Charset charset, boolean writeHeader) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(charset, "charset");
        this.writeHeader = writeHeader;

        // Create parent dirs if needed
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        this.out = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(file,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE),
                charset
        ));

        // Default formatter is not used; we write CSV ourselves, but keep for compatibility.
        setFormatter(new Formatter() {
            @Override public String format(LogRecord record) { return record.getMessage(); }
        });
    }

    @Override
    public void publish(LogRecord r) {
        if (r == null || !isLoggable(r)) return;

        // Handler has an internal lock (get/set), but we’ll synchronize on it for atomic row writes.
        synchronized (this) {
            try {
                if (writeHeader && !headerWritten) {
                    writeRow(new String[] {
                            "timestamp_iso", "epoch_millis", "level", "thread", "logger", 
                            "source_class", "source_method", "message", "thrown"
                    });
                    headerWritten = true;
                }

                long millis= r.getMillis();
                String timestampIso = Units.ms1970.createDatum(millis).toString();
                String epochMillis = Long.toString(millis);
                String level = Integer.toString(r.getLevel().intValue());
                String logger = safe(r.getLoggerName());
                String thread = Integer.toString(r.getThreadID());
                String sourceClass = safe(r.getSourceClassName());
                String sourceMethod = safe(r.getSourceMethodName());
                String message = formatMessage(r);
                String thrown = throwableToString(r.getThrown());

                writeRow(new String[] {
                        timestampIso, epochMillis, level, thread, logger, 
                        sourceClass, sourceMethod, message, thrown
                });

            } catch (IOException e) {
                reportError("CSV log write failed", e, ErrorManager.WRITE_FAILURE);
            } catch (RuntimeException e) {
                reportError("CSV log write failed (runtime)", e, ErrorManager.GENERIC_FAILURE);
            }
        }
    }

    private String formatMessage(LogRecord r) {
        try {
            Formatter f = getFormatter();
            // If you later swap in a formatter that applies parameters, this will honor it.
            String s = (f != null) ? f.formatMessage(r) : r.getMessage();
            return s == null ? "" : s;
        } catch (Exception e) {
            // Fall back to raw message
            String s = r.getMessage();
            return s == null ? "" : s;
        }
    }

    private void writeRow(String[] cols) throws IOException {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) out.write(',');
            out.write(csvEscape(cols[i]));
        }
        out.write("\n");
    }

    private static String csvEscape(String s) {
        if (s == null) s = "";
        boolean mustQuote = false;

        // Check if we need quoting
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == ',' || c == '\n' || c == '\r') {
                mustQuote = true;
                break;
            }
        }

        if (!mustQuote) return s;

        // Quote and double embedded quotes
        StringBuilder b = new StringBuilder(s.length() + 16);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') b.append("\"\"");
            else b.append(c);
        }
        b.append('"');
        return b.toString();
    }

    private static String throwableToString(Throwable t) {
        if (t == null) return "";
        // Keep it compact for CSV; adjust if you want full stack traces.
        // If you want stack traces, you can render with StringWriter/PrintWriter here.
        String msg = t.getMessage();
        return (msg == null || msg.isEmpty())
                ? t.getClass().getName()
                : (t.getClass().getName() + ": " + msg);
    }

    private static String safe(Level lvl) { return (lvl == null) ? "" : lvl.getName(); }
    private static String safe(String s) { return (s == null) ? "" : s; }

    @Override
    public void flush() {
        synchronized (this) {
            try {
                out.flush();
            } catch (IOException e) {
                reportError("CSV log flush failed", e, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    @Override
    public void close() throws SecurityException {
        synchronized (this) {
            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                reportError("CSV log close failed", e, ErrorManager.CLOSE_FAILURE);
            }
        }
    }
}
