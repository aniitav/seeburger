package com.seeburger.rag.document;

import com.seeburger.rag.error.UnprocessableDocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String fileName) {
        return "application/pdf".equalsIgnoreCase(contentType) || fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        try (var pdf = Loader.loadPDF(content)) {
            if (pdf.isEncrypted()) {
                throw new UnprocessableDocumentException("Encrypted PDF files are not supported.");
            }
            var pages = new ArrayList<ParsedPage>(pdf.getNumberOfPages());
            var stripper = new PDFTextStripper();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new ParsedPage(page, stripper.getText(pdf)));
            }
            return new ParsedDocument(pages);
        } catch (IOException exception) {
            throw new UnprocessableDocumentException("The PDF could not be read.", exception);
        }
    }
}
