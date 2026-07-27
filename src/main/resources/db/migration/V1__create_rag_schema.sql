CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    file_name VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    embedding_provider VARCHAR(50) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dimensions INTEGER NOT NULL CHECK (embedding_dimensions > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('INDEXED')),
    chunks_count INTEGER NOT NULL CHECK (chunks_count > 0),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_documents_content_embedding
        UNIQUE (content_sha256, embedding_provider, embedding_model, embedding_dimensions)
);

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    page_start INTEGER,
    page_end INTEGER,
    heading VARCHAR(512),
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    embedding VECTOR(1536) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_chunk_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_documents_embedding_fingerprint
    ON documents (embedding_provider, embedding_model, embedding_dimensions);

CREATE INDEX idx_document_chunks_document_id
    ON document_chunks (document_id);

CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops);
