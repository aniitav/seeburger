package com.seeburger.rag.document;

import com.seeburger.rag.chunking.ChunkingStrategy;
import com.seeburger.rag.common.Hashing;
import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.chunking.TokenEstimator;
import com.seeburger.rag.document.dto.UploadResponse;
import com.seeburger.rag.embedding.EmbeddingGateway;
import com.seeburger.rag.error.BadRequestException;
import com.seeburger.rag.error.PayloadTooLargeException;
import com.seeburger.rag.error.UnprocessableDocumentException;
import com.seeburger.rag.vector.IndexedDocument;
import com.seeburger.rag.vector.VectorRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class DocumentIngestionService {
    private final DocumentParserFactory parserFactory;
    private final ChunkingStrategy chunker;
    private final TokenEstimator tokenEstimator;
    private final EmbeddingGateway embeddingGateway;
    private final VectorRepository vectorRepository;
    private final RagProperties properties;

    public DocumentIngestionService(
            DocumentParserFactory parserFactory,
            ChunkingStrategy chunker,
            TokenEstimator tokenEstimator,
            EmbeddingGateway embeddingGateway,
            VectorRepository vectorRepository,
            RagProperties properties
    ) {
        this.parserFactory = parserFactory;
        this.chunker = chunker;
        this.tokenEstimator = tokenEstimator;
        this.embeddingGateway = embeddingGateway;
        this.vectorRepository = vectorRepository;
        this.properties = properties;
    }

    public UploadResult ingestText(String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        validateSize(bytes.length);
        return ingest(bytes, "plain-text-input", MediaType.TEXT_PLAIN_VALUE);
    }

    public UploadResult ingestPdf(MultipartFile file) {
        validatePresenceAndSize(file);
        var fileName = safeFileName(file.getOriginalFilename());
        var bytes = read(file);
        validatePdfSignature(bytes);
        return ingest(bytes, fileName, MediaType.APPLICATION_PDF_VALUE);
    }

    private UploadResult ingest(byte[] bytes, String fileName, String contentType) {
        var contentHash = Hashing.sha256(bytes);
        var existing = vectorRepository.findIndexedByContentHash(contentHash);
        if (existing.isPresent()) {
            return new UploadResult(toResponse(existing.get()), false);
        }

        var parsed = parserFactory.get(contentType, fileName).parse(bytes);
        if (parsed.pages().stream().allMatch(page -> page.text().isBlank())) {
            throw new UnprocessableDocumentException(
                    "No extractable text was found. Scanned PDFs require OCR, which is not supported yet."
            );
        }
        var documentTokens = parsed.pages().stream()
                .mapToInt(page -> tokenEstimator.estimate(page.text()))
                .sum();
        if (documentTokens > properties.upload().maxDocumentTokens()) {
            throw new PayloadTooLargeException(
                    "The extracted document exceeds the configured maximum of "
                            + properties.upload().maxDocumentTokens() + " estimated tokens."
            );
        }
        var chunks = chunker.chunk(parsed);
        if (chunks.isEmpty()) {
            throw new UnprocessableDocumentException("No indexable text was found in the document.");
        }
        var embeddingContent = chunks.stream()
                .map(chunk -> chunk.heading() == null
                        ? chunk.content()
                        : "Heading: " + chunk.heading() + "\nContent: " + chunk.content())
                .toList();
        var embeddings = embeddingGateway.embedDocuments(embeddingContent);

        try {
            var stored = vectorRepository.store(
                    UUID.randomUUID(),
                    fileName,
                    contentType,
                    contentHash,
                    chunks,
                    embeddings
            );
            return new UploadResult(toResponse(stored), true);
        } catch (DuplicateKeyException race) {
            var winner = vectorRepository.findIndexedByContentHash(contentHash).orElseThrow(() -> race);
            return new UploadResult(toResponse(winner), false);
        }
    }

    private void validatePresenceAndSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A non-empty TXT or PDF file is required.");
        }
        if (file.getSize() > properties.upload().maxBytes()) {
            throw new PayloadTooLargeException(properties.upload().maxBytes());
        }
    }

    private void validateSize(long size) {
        if (size > properties.upload().maxBytes()) {
            throw new PayloadTooLargeException(properties.upload().maxBytes());
        }
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new BadRequestException("The uploaded file must have a name.");
        }
        var normalized = original.replace('\\', '/');
        var fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 512) {
            throw new BadRequestException("The uploaded file name is invalid or too long.");
        }
        return fileName;
    }

    private byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UnprocessableDocumentException("The uploaded file could not be read.", exception);
        }
    }

    private void validatePdfSignature(byte[] bytes) {
        var valid = bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-';
        if (!valid) {
            throw new UnprocessableDocumentException("The uploaded content is not a valid PDF file.");
        }
    }

    private UploadResponse toResponse(IndexedDocument document) {
        return new UploadResponse(
                document.id(),
                document.fileName(),
                document.contentType(),
                document.chunksCount(),
                "INDEXED"
        );
    }
}
