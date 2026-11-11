package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import com.gihan.contextioq.ContextIQ.document.DocumentTextExtractor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DocumentTextExtractorTests {
    final DocumentTextExtractor extractor = new DocumentTextExtractor();

    String extract(String filename, byte[] bytes) {
        // A false client MIME header must not determine the parser.
        return extractor.extract(new MockMultipartFile("file", filename, "application/octet-stream", bytes), filename);
    }

    @Test void readsPdfDocxAndLegacyDocUsingRealParsers() throws Exception {
        assertTrue(extract("launch.PDF", DocumentFixtures.pdf("Juniper launches on May 14.", false)).contains("May 14"));
        var word = extract("launch.docx", DocumentFixtures.docx("Juniper launches on May 14."));
        assertTrue(word.contains("May 14"));
        assertTrue(word.contains("Morgan"));
        assertTrue(word.contains("Launch owner"));
        assertTrue(extract("legacy.doc", DocumentFixtures.legacyDoc()).contains("Sample Word Document"));
    }

    @Test void rejectsRenamedAndUnsupportedFiles() throws Exception {
        assertEquals(415, assertThrows(ResponseStatusException.class,
                () -> extract("fake.pdf", "not a PDF".getBytes(StandardCharsets.UTF_8))).getStatusCode().value());
        assertEquals(415, assertThrows(ResponseStatusException.class,
                () -> extract("fake.docx", DocumentFixtures.pdf("Hello", false))).getStatusCode().value());
        assertEquals(415, assertThrows(ResponseStatusException.class,
                () -> extract("a.exe", new byte[]{65})).getStatusCode().value());
    }

    @Test void rejectsEncryptedUnreadableAndTextlessDocuments() throws Exception {
        var encrypted = assertThrows(ResponseStatusException.class,
                () -> extract("locked.pdf", DocumentFixtures.pdf("Secret", true)));
        assertEquals(422, encrypted.getStatusCode().value());
        assertTrue(encrypted.getReason().contains("Password"));
        var empty = assertThrows(ResponseStatusException.class,
                () -> extract("scan.pdf", DocumentFixtures.pdf(null, false)));
        assertEquals(422, empty.getStatusCode().value());
        assertTrue(empty.getReason().contains("OCR"));
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> extract("broken.pdf", "%PDF-1.7\ncorrupt".getBytes(StandardCharsets.UTF_8))).getStatusCode().value());
    }

    @Test void refusesTooMuchTextWithoutReturningTruncatedContent() throws Exception {
        var error = assertThrows(ResponseStatusException.class,
                () -> extract("large.docx", DocumentFixtures.docx("word ".repeat(21000))));
        assertEquals(413, error.getStatusCode().value());
        assertTrue(error.getReason().contains("100,000"));
    }

    @Test void rejectsOversizeBinaryAndExcessiveZipExpansionBeforeParsing() throws Exception {
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> extract("large.pdf", new byte[DocumentTextExtractor.MAX_UPLOAD_BYTES + 1])).getStatusCode().value());
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("large.xml"));
            byte[] block = new byte[1024 * 1024];
            for (int i = 0; i < 33; i++) zip.write(block);
            zip.closeEntry();
        }
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> extract("compressed.docx", output.toByteArray())).getStatusCode().value());
    }

    @Test void separateUploadsDoNotShareHandlerText() throws Exception {
        extract("first.pdf", DocumentFixtures.pdf("FIRST UNIQUE PASSAGE", false));
        String second = extract("second.pdf", DocumentFixtures.pdf("SECOND PASSAGE", false));
        assertTrue(second.contains("SECOND"));
        assertFalse(second.contains("FIRST"));
    }
}
