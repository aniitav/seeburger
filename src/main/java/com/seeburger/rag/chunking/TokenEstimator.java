package com.seeburger.rag.chunking;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
