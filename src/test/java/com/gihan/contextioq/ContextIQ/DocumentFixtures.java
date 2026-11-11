package com.gihan.contextioq.ContextIQ;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** Small generated fixtures: no external files or running services are needed. */
final class DocumentFixtures {
    static byte[] pdf(String text, boolean encrypted) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            if (text != null) {
                try (var content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            if (encrypted) {
                var protection = new StandardProtectionPolicy("owner-test", "reader-test", new AccessPermission());
                protection.setEncryptionKeyLength(128);
                document.protect(protection);
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    static byte[] docx(String text) throws Exception {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Launch owner");
            table.getRow(0).getCell(1).setText("Morgan");
            document.write(output);
            return output.toByteArray();
        }
    }

    static byte[] legacyDoc() throws Exception {
        try (var input = DocumentFixtures.class.getResourceAsStream("/documents/apache-tika-testWORD.doc")) {
            if (input == null) throw new IllegalStateException("Missing DOC fixture");
            return input.readAllBytes();
        }
    }
}
