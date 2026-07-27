package com.seeburger.rag.document;

import com.seeburger.rag.document.dto.UploadResponse;
import com.seeburger.rag.error.UnsupportedDocumentTypeException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@RestController
public class DocumentController {
    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<UploadResponse> uploadText(@RequestBody @NotBlank String text) {
        return response(ingestionService.ingestText(text));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadPdf(@RequestPart("file") MultipartFile file) {
        var fileName = file.getOriginalFilename();
        var isPdfName = fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
        var isPdfContent = MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(file.getContentType());
        if (!isPdfName || !isPdfContent) {
            throw new UnsupportedDocumentTypeException();
        }
        return response(ingestionService.ingestPdf(file));
    }

    private ResponseEntity<UploadResponse> response(UploadResult result) {
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }
}
