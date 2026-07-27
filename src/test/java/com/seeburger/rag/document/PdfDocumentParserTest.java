package com.seeburger.rag.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentParserTest {

    @Test
    void extractsTextWithPageNumbers() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var font = new PDType1Font(FontName.HELVETICA);
            for (var text : new String[]{"First page policy", "Second page details"}) {
                var page = new PDPage();
                document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        var parsed = new PdfDocumentParser().parse(pdf);

        assertThat(parsed.pages()).hasSize(2);
        assertThat(parsed.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(parsed.pages().get(0).text()).contains("First page policy");
        assertThat(parsed.pages().get(1).pageNumber()).isEqualTo(2);
        assertThat(parsed.pages().get(1).text()).contains("Second page details");
    }
}
