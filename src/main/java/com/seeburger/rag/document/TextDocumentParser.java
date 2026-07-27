package com.seeburger.rag.document;

import com.seeburger.rag.error.UnprocessableDocumentException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String fileName) {
        return "text/plain".equalsIgnoreCase(contentType) || fileName.toLowerCase().endsWith(".txt");
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return new ParsedDocument(List.of(new ParsedPage(1, decoder.decode(ByteBuffer.wrap(content)).toString())));
        } catch (CharacterCodingException exception) {
            throw new UnprocessableDocumentException("The text file must use valid UTF-8 encoding.", exception);
        }
    }
}
