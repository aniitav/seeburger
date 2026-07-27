package com.seeburger.rag.document;

import com.seeburger.rag.document.dto.UploadResponse;
import com.seeburger.rag.error.ApiExceptionHandler;
import com.seeburger.rag.error.UnsupportedDocumentTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentControllerTest {

    @Test
    void acceptsRawPlainText() {
        var service = mock(DocumentIngestionService.class);
        var controller = new DocumentController(service);
        var response = new UploadResponse(
                UUID.randomUUID(), "plain-text-input", "text/plain", 1, "INDEXED"
        );
        when(service.ingestText("Policy text")).thenReturn(new UploadResult(response, true));

        var result = controller.uploadText("Policy text");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
        verify(service).ingestText("Policy text");
    }

    @Test
    void rejectsBlankPlainTextAtControllerBoundary() throws Exception {
        var service = mock(DocumentIngestionService.class);
        var controller = new DocumentController(service);
        var mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(post("/upload")
                        .contentType("text/plain")
                        .content("   \n"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsOnlyPdfMultipartFiles() {
        var service = mock(DocumentIngestionService.class);
        var controller = new DocumentController(service);
        var file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );
        var response = new UploadResponse(
                UUID.randomUUID(), "policy.pdf", "application/pdf", 1, "INDEXED"
        );
        when(service.ingestPdf(file)).thenReturn(new UploadResult(response, true));

        var result = controller.uploadPdf(file);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(service).ingestPdf(file);
    }

    @Test
    void rejectsTxtMultipartFilesAtControllerBoundary() {
        var service = mock(DocumentIngestionService.class);
        var controller = new DocumentController(service);
        var file = new MockMultipartFile(
                "file",
                "policy.txt",
                "text/plain",
                "Policy text".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> controller.uploadPdf(file))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        verifyNoInteractions(service);
    }
}
