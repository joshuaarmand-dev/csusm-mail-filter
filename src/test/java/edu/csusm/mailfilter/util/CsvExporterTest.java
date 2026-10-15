package edu.csusm.mailfilter.util;

import edu.csusm.mailfilter.model.Analysis;
import edu.csusm.mailfilter.model.Category;
import edu.csusm.mailfilter.model.Msg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void plainValuesAreUnchanged() {
        assertEquals("hello", CsvExporter.cell("hello"));
        assertEquals("", CsvExporter.cell(null));
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"a, b\"", CsvExporter.cell("a, b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.cell("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvExporter.cell("line1\nline2"));
    }

    @Test
    void formulaLikeValuesAreNeutralized() {
        assertEquals("'=1+1", CsvExporter.cell("=1+1"));
        assertEquals("\"'=HYPERLINK(\"\"http://evil\"\")\"", CsvExporter.cell("=HYPERLINK(\"http://evil\")"));
        assertEquals("'+1", CsvExporter.cell("+1"));
        assertEquals("'-2", CsvExporter.cell("-2"));
        assertEquals("'@SUM(A1)", CsvExporter.cell("@SUM(A1)"));
    }

    @Test
    void rowsAreWrittenInTabOrderWithHeader() {
        Analysis analysis = new Analysis();
        analysis.add(msg("Unsafe one", Category.UNSAFE, List.of("Redirector: bit.ly", "Risky TLD: .xyz")));
        analysis.add(msg("Trusted one", Category.TRUSTED, List.of()));

        String[] lines = CsvExporter.toCsv(analysis).split("\r\n");

        assertEquals(CsvExporter.HEADER, lines[0]);
        assertTrue(lines[1].startsWith("Filtered,"));
        assertTrue(lines[2].startsWith("Unsafe,"));
        assertTrue(lines[2].contains("Redirector: bit.ly; Risky TLD: .xyz"));
        assertEquals(3, lines.length);
    }

    @Test
    void fileIsUtf8WithBom() throws IOException {
        Analysis analysis = new Analysis();
        analysis.add(msg("Caf\u00E9 \u2615", Category.NORMAL, List.of()));
        Path file = tempDir.resolve("scan.csv");

        CsvExporter.write(analysis, file);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFF" + CsvExporter.HEADER));
        assertTrue(content.contains("Caf\u00E9 \u2615"));
    }

    private static Msg msg(String subject, Category category, List<String> reasons) {
        Msg msg = new Msg("id", subject, "a@example.com", "2026-10-13 15:00", "https://x");
        msg.category = category;
        msg.reasons = reasons;
        return msg;
    }
}