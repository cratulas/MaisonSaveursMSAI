package com.saveursmaison.ia.client;

import java.util.List;

/**
 * Request body para POST https://api.openai.com/v1/chat/completions
 */
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        Integer max_tokens,
        Double temperature
) {
    public record Message(String role, String content) {
    }
}
