package com.seeburger.rag.llm;

public interface AnswerGenerator {
    String answer(String question, String context);
}
