package edu.csusm.mailfilter.util;

import edu.csusm.mailfilter.model.Analysis;
import edu.csusm.mailfilter.model.Msg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes scan results as a CSV file that opens cleanly in Excel or Google Sheets. */
public final class CsvExporter {

    static final String HEADER = "Tab,Received,From,Subject,Sender check,Reasons,Links";

    /** Byte order mark so Excel reads the file as UTF-8 instead of guessing. */
    private static final String UTF8_BOM = "\uFEFF";

    private CsvExporter() {
    }

    public static void write(Analysis analysis, Path path) throws IOException {
        Files.writeString(path, UTF8_BOM + toCsv(analysis), StandardCharsets.UTF_8);
    }

    public static String toCsv(Analysis analysis) {
        StringBuilder out = new StringBuilder(HEADER).append("\r\n");

        appendRows(out, "Filtered", analysis.filtered);
        appendRows(out, "Unfiltered", analysis.unfiltered);
        appendRows(out, "Unsafe", analysis.unsafe);

        return out.toString();
    }

    private static void appendRows(StringBuilder out, String tab, List<Msg> messages) {
        for (Msg msg : messages) {
            out.append(cell(tab)).append(',')
                    .append(cell(msg.received)).append(',')
                    .append(cell(msg.from)).append(',')
                    .append(cell(msg.subject)).append(',')
                    .append(cell(msg.auth)).append(',')
                    .append(cell(String.join("; ", msg.reasons))).append(',')
                    .append(cell(String.join(" ", msg.links)))
                    .append("\r\n");
        }
    }

    /**
     * Escapes one CSV field.
     *
     * <p>Subjects come from strangers, so a subject like {@code =HYPERLINK(...)}
     * would run as a formula when the file is opened in a spreadsheet. Values
     * starting with a formula character are prefixed with an apostrophe so
     * they show as plain text.</p>
     */
    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        if ("=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }

        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;

        return needsQuotes
                ? '"' + value.replace("\"", "\"\"") + '"'
                : value;
    }
}