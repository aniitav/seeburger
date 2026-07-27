package com.seeburger.rag.chunking;

import com.seeburger.rag.common.Hashing;
import com.seeburger.rag.config.RagProperties;
import com.seeburger.rag.document.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class HeadingAwareChunker implements ChunkingStrategy {
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.+$");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    private final TokenEstimator tokenEstimator;
    private final RagProperties properties;

    public HeadingAwareChunker(TokenEstimator tokenEstimator, RagProperties properties) {
        this.tokenEstimator = tokenEstimator;
        this.properties = properties;
    }

    @Override
    public List<DocumentChunk> chunk(ParsedDocument document) {
        var units = extractUnits(document);
        if (units.isEmpty()) {
            return List.of();
        }

        var chunks = new ArrayList<DocumentChunk>();
        var current = new ChunkAccumulator();
        for (var unit : units) {
            for (var bounded : splitOversized(unit)) {
                if (!current.isEmpty() && !Objects.equals(current.heading(), bounded.heading())) {
                    chunks.add(toChunk(chunks.size(), current));
                    current = new ChunkAccumulator();
                }
                if (!current.isEmpty()
                        && tokenEstimator.estimate(current.contentWith(bounded.text()))
                        > properties.chunk().maxTokens()) {
                    chunks.add(toChunk(chunks.size(), current));
                    var availableForOverlap = Math.max(
                            0,
                            properties.chunk().maxTokens() - tokenEstimator.estimate(bounded.text()) - 1
                    );
                    current = ChunkAccumulator.withOverlap(
                            tail(
                                    current.content(),
                                    Math.min(properties.chunk().overlapTokens(), availableForOverlap)
                            ),
                            current.pageEnd(),
                            current.heading()
                    );
                    if (tokenEstimator.estimate(current.contentWith(bounded.text()))
                            > properties.chunk().maxTokens()) {
                        current = new ChunkAccumulator();
                    }
                }
                current.add(bounded);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(toChunk(chunks.size(), current));
        }
        return List.copyOf(chunks);
    }

    private List<TextUnit> extractUnits(ParsedDocument document) {
        var units = new ArrayList<TextUnit>();
        String heading = null;
        for (var page : document.pages()) {
            var normalized = page.text()
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replaceAll("[\\t\\x0B\\f]+", " ")
                    .trim();
            var paragraph = new StringBuilder();
            for (var rawLine : normalized.split("\\n")) {
                var line = rawLine.replaceAll("[ ]{2,}", " ").trim();
                if (line.isBlank()) {
                    flushParagraph(units, paragraph, page.pageNumber(), heading);
                } else if (isHeading(line)) {
                    flushParagraph(units, paragraph, page.pageNumber(), heading);
                    heading = line.replaceFirst("^#{1,6}\\s+", "").trim();
                } else {
                    if (!paragraph.isEmpty()) {
                        paragraph.append(' ');
                    }
                    paragraph.append(line);
                }
            }
            flushParagraph(units, paragraph, page.pageNumber(), heading);
        }
        return units;
    }

    private void flushParagraph(
            List<TextUnit> units,
            StringBuilder paragraph,
            int page,
            String heading
    ) {
        if (!paragraph.isEmpty()) {
            units.add(new TextUnit(page, heading, paragraph.toString().trim()));
            paragraph.setLength(0);
        }
    }

    private boolean isHeading(String text) {
        if (text.contains("\n") || text.length() > 120) {
            return false;
        }
        if (MARKDOWN_HEADING.matcher(text).matches()) {
            return true;
        }
        var letters = text.chars().filter(Character::isLetter).count();
        return letters >= 3 && text.equals(text.toUpperCase(Locale.ROOT));
    }

    private List<TextUnit> splitOversized(TextUnit unit) {
        if (tokenEstimator.estimate(unit.text()) <= properties.chunk().maxTokens()) {
            return List.of(unit);
        }
        var result = new ArrayList<TextUnit>();
        var current = new StringBuilder();
        for (var sentence : SENTENCE_BOUNDARY.split(unit.text())) {
            if (tokenEstimator.estimate(sentence) > properties.chunk().maxTokens()) {
                flush(result, current, unit);
                splitByWords(sentence, unit, result);
            } else if (tokenEstimator.estimate(join(current, sentence)) > properties.chunk().maxTokens()) {
                flush(result, current, unit);
                current.append(sentence);
            } else {
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(sentence);
            }
        }
        flush(result, current, unit);
        return result;
    }

    private void splitByWords(String text, TextUnit unit, List<TextUnit> output) {
        var current = new StringBuilder();
        for (var word : text.split("\\s+")) {
            if (!current.isEmpty()
                    && tokenEstimator.estimate(join(current, word)) > properties.chunk().maxTokens()) {
                flush(output, current, unit);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        flush(output, current, unit);
    }

    private void flush(List<TextUnit> output, StringBuilder value, TextUnit source) {
        if (!value.isEmpty()) {
            output.add(new TextUnit(source.page(), source.heading(), value.toString().trim()));
            value.setLength(0);
        }
    }

    private String join(StringBuilder current, String next) {
        return current.isEmpty() ? next : current + " " + next;
    }

    private String tail(String content, int targetTokens) {
        if (targetTokens <= 0 || content.isBlank()) {
            return "";
        }
        var words = content.split("\\s+");
        var tail = new StringBuilder();
        for (int i = words.length - 1; i >= 0; i--) {
            var candidate = tail.isEmpty() ? words[i] : words[i] + " " + tail;
            if (tokenEstimator.estimate(candidate) > targetTokens) {
                break;
            }
            tail.insert(0, words[i] + (tail.isEmpty() ? "" : " "));
        }
        return tail.toString();
    }

    private DocumentChunk toChunk(int index, ChunkAccumulator accumulator) {
        var content = accumulator.content().trim();
        return new DocumentChunk(
                UUID.randomUUID(),
                index,
                accumulator.pageStart(),
                accumulator.pageEnd(),
                accumulator.heading(),
                content,
                Hashing.sha256(content)
        );
    }

    private record TextUnit(int page, String heading, String text) {}

    private static final class ChunkAccumulator {
        private final StringBuilder content = new StringBuilder();
        private Integer pageStart;
        private Integer pageEnd;
        private String heading;

        static ChunkAccumulator withOverlap(String overlap, Integer page, String heading) {
            var value = new ChunkAccumulator();
            if (!overlap.isBlank()) {
                value.content.append(overlap);
                value.pageStart = page;
                value.pageEnd = page;
                value.heading = heading;
            }
            return value;
        }

        void add(TextUnit unit) {
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            content.append(unit.text());
            pageStart = pageStart == null ? unit.page() : Math.min(pageStart, unit.page());
            pageEnd = pageEnd == null ? unit.page() : Math.max(pageEnd, unit.page());
            if (heading == null || !heading.equals(unit.heading())) {
                heading = unit.heading();
            }
        }

        String contentWith(String next) {
            return content.isEmpty() ? next : content + "\n\n" + next;
        }

        boolean isEmpty() {
            return content.isEmpty();
        }

        String content() {
            return content.toString();
        }

        Integer pageStart() {
            return pageStart;
        }

        Integer pageEnd() {
            return pageEnd;
        }

        String heading() {
            return heading;
        }
    }
}
