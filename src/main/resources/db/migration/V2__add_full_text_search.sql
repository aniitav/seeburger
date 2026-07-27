ALTER TABLE document_chunks
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', COALESCE(heading, '')), 'A')
            ||
            setweight(to_tsvector('english', content), 'B')
        ) STORED;

CREATE INDEX idx_document_chunks_search_vector_gin
    ON document_chunks USING GIN (search_vector);
