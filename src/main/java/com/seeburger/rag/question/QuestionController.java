package com.seeburger.rag.question;

import com.seeburger.rag.question.dto.AnswerResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class QuestionController {
    private final RagService ragService;

    public QuestionController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/ask")
    public AnswerResponse ask(
            @RequestParam("q") String question,
            @RequestParam(value = "documentId", required = false) UUID documentId
    ) {
        return ragService.ask(question, documentId);
    }
}
