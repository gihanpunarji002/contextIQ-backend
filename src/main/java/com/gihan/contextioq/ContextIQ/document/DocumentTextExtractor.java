package com.gihan.contextioq.ContextIQ.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.tika.Tika;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Validates uploaded bytes and extracts text before embeddings or database writes. */
@Component
public class DocumentTextExtractor {
    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    public static final int MAX_TEXT_BYTES = 65536;
    public static final int MAX_EXTRACTED_CHARS = 100000;
    private static final Map<String, String> FORMATS = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final Tika detector;

    public DocumentTextExtractor() {
        // TikaDocumentReader does not expose ParseContext/parser injection. Its supported
        // JVM configuration hook restricts parsers and disables OCR for all readers here.
        System.setProperty("tika.config", "contextiq-tika.xml");
        detector = new Tika();
    }

    public String extract(MultipartFile file, String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean plain = extension.equals("txt") || extension.equals("md");
        if (!plain && !FORMATS.containsKey(extension))
            throw failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Supported formats: TXT, Markdown, PDF, DOC and DOCX.");
        if (file.isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "The document is empty.");
        int limit = plain ? MAX_TEXT_BYTES : MAX_UPLOAD_BYTES;
        if (file.getSize() > limit)
            throw failure(HttpStatus.PAYLOAD_TOO_LARGE, plain
                    ? "Text files must be at most 64 KiB." : "PDF and Word files must be at most 10 MiB.");
        try {
            // Enforce the limit while reading too; do not trust multipart metadata alone.
            byte[] bytes;
            try (var input = file.getInputStream()) { bytes = input.readNBytes(limit + 1); }
            if (bytes.length > limit) throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "Document exceeds the upload size limit.");
            String text = plain ? decodeText(bytes) : readWithTika(bytes, filename, extension);
            text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
            if (text.isBlank()) throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No searchable text was found. Scanned or image-only documents need OCR; OCR is not enabled. Export a text-based document and retry.");
            if (text.length() > MAX_EXTRACTED_CHARS) throw tooMuchText();
            return text;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "The file could not be read. Upload a valid, unencrypted document.");
        }
    }

    private String decodeText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
                throw failure(HttpStatus.BAD_REQUEST, "Text files must contain text, not binary data.");
            return text;
        } catch (CharacterCodingException e) {
            throw failure(HttpStatus.BAD_REQUEST, "TXT and Markdown files must contain valid UTF-8 text.");
        }
    }

    private String readWithTika(byte[] bytes, String filename, String extension) throws IOException {
        // Bound ZIP expansion before container-aware type detection, including renamed ZIP files.
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') checkZipExpansion(bytes);
        // Inspect content without trusting the filename or the client-supplied MIME header.
        String mediaType;
        try (var input = TikaInputStream.get(bytes)) { mediaType = detector.detect(input); }
        if (mediaType.contains("encrypted")) throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                "Password-protected documents are not supported. Upload an unencrypted copy.");
        if (!FORMATS.get(extension).equals(mediaType)) throw failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "The file contents do not match its extension. Upload a valid PDF, DOC or DOCX file.");
        var resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };
        try {
            // A new handler per request: do not accumulate text across uploads or silently truncate it.
            var reader = new TikaDocumentReader(resource, new BodyContentHandler(MAX_EXTRACTED_CHARS),
                    ExtractedTextFormatter.defaults());
            return reader.get().stream().map(document -> document.getText()).collect(java.util.stream.Collectors.joining("\n"));
        } catch (RuntimeException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof WriteLimitReachedException) throw tooMuchText();
                if (cause instanceof EncryptedDocumentException) throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Password-protected documents are not supported. Upload an unencrypted copy.");
            }
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Text extraction failed. The document may be damaged, encrypted or unsupported. No document was indexed.");
        }
    }

    private void checkZipExpansion(byte[] bytes) throws IOException {
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        // DOCX is a ZIP container. Bound expanded bytes, including entries the parser may ignore.
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) {
                if (++entries > 1000) throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "The Word document contains too many embedded parts.");
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > 32L * 1024 * 1024) throw failure(HttpStatus.PAYLOAD_TOO_LARGE,
                            "The expanded Word document exceeds 32 MiB. Split or simplify the document.");
                }
            }
        }
    }

    private ResponseStatusException tooMuchText() {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE,
                "The document exceeds 100,000 extracted characters. Split it into smaller documents; no partial text was indexed.");
    }

    private ResponseStatusException failure(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }
}
