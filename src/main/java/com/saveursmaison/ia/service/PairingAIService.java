package com.saveursmaison.ia.service;

import com.saveursmaison.ia.client.CatalogClient;
import com.saveursmaison.ia.client.ChatCompletionRequest;
import com.saveursmaison.ia.client.ChatCompletionResponse;
import com.saveursmaison.ia.config.OpenAIProperties;
import com.saveursmaison.ia.dto.CheeseForAiDto;
import com.saveursmaison.ia.dto.PairingChatRequest;
import com.saveursmaison.ia.dto.PairingChatResponse;
import com.saveursmaison.ia.dto.WineForAiDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PairingAIService {

    private final WebClient openAIClient;
    private final OpenAIProperties openAIProperties;
    private final CatalogClient catalogClient;

    /**
     * Prompt de sistema para guiar al modelo.
     * Aquí obligamos a:
     *  - usar SOLO productos del catálogo
     *  - mostrar siempre Nombre (ID: xxx)
     */
    private static final String SYSTEM_PROMPT = """
            You are the AI sommelier of the Saveurs Maison app.

            Your job is to recommend WINE and CHEESE PAIRINGS
            using ONLY products from our catalog.

            Rules:
            - Answer in a friendly tone, but concise.
            - ALWAYS, when you mention a wine or cheese from the catalog,
              write it like: ProductName (ID: <product-id>).
            - Only use wines and cheeses that appear in the catalog list provided.
            - If the user asks for something we don't have, suggest the closest
              style using our catalog.
            """;

    public PairingAIService(
            @Qualifier("openAIWebClient") WebClient openAIWebClient,
            OpenAIProperties openAIProperties,
            CatalogClient catalogClient
    ) {
        this.openAIClient = openAIWebClient;
        this.openAIProperties = openAIProperties;
        this.catalogClient = catalogClient;
    }

    /**
     * Método que usa el controlador. Recibe el DTO y devuelve el DTO.
     */
    public PairingChatResponse chat(PairingChatRequest request) {

        String locale = request.getLocale() != null ? request.getLocale() : "en";

        // 1. Traer vinos y quesos del catálogo
        List<WineForAiDto> wines = catalogClient.getWinesForAi();
        List<CheeseForAiDto> cheeses = catalogClient.getCheesesForAi();

        // 2. Construir prompt de usuario con el contexto + catálogo
        String userPrompt = buildUserPrompt(request, locale, wines, cheeses);

        // 3. Llamar a OpenAI
        String answerText = getPairingRecommendation(userPrompt);

        // Más adelante llenaremos estos arrays con IDs concretos desde JSON.
        return new PairingChatResponse(
                answerText,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    /**
     * Arma un prompt de usuario usando:
     * - idioma
     * - mensaje del usuario
     * - IDs seleccionados (si vienen)
     * - lista de vinos y quesos del catálogo
     */
    private String buildUserPrompt(PairingChatRequest request,
                                   String locale,
                                   List<WineForAiDto> wines,
                                   List<CheeseForAiDto> cheeses) {

        StringBuilder sb = new StringBuilder();

        sb.append("User language: ").append(locale).append("\n");
        sb.append("User message: ").append(request.getMessage()).append("\n\n");

        if (request.getSelectedWineIds() != null && !request.getSelectedWineIds().isEmpty()) {
            sb.append("Selected wine IDs: ").append(request.getSelectedWineIds()).append("\n");
        }
        if (request.getSelectedCheeseIds() != null && !request.getSelectedCheeseIds().isEmpty()) {
            sb.append("Selected cheese IDs: ").append(request.getSelectedCheeseIds()).append("\n");
        }

        sb.append("\nHere is the list of AVAILABLE WINES in the catalog (ID, name, type, price):\n");
        String winesText = wines.stream()
                .limit(50) // por si hay muchos
                .map(w -> String.format(
                        "- id=%s | name=%s | type=%s | price=%.2f",
                        w.getId(),
                        // si name viene null, usamos id como fallback
                        w.getName() != null ? w.getName() : w.getId(),
                        w.getType(),
                        w.getPrice() != null ? w.getPrice() : 0.0
                ))
                .collect(Collectors.joining("\n"));
        sb.append(winesText).append("\n\n");

        sb.append("Here is the list of AVAILABLE CHEESES in the catalog (ID, name, price):\n");
        String cheesesText = cheeses.stream()
                .limit(50)
                .map(c -> String.format(
                        "- id=%s | name=%s | price=%.2f",
                        c.getId(),
                        c.getName() != null ? c.getName() : c.getId(),
                        c.getPrice() != null ? c.getPrice() : 0.0
                ))
                .collect(Collectors.joining("\n"));
        sb.append(cheesesText).append("\n\n");

        sb.append("Please recommend the best pairing using ONLY the products above.\n");
        sb.append("When you refer to a product, ALWAYS include its ID in the format: Name (ID: <id>).\n");

        return sb.toString();
    }

    /**
     * Llama a la API de OpenAI usando WebClient y devuelve el texto generado.
     */
    private String getPairingRecommendation(String userPrompt) {

        var messages = List.of(
                new ChatCompletionRequest.Message("system", SYSTEM_PROMPT),
                new ChatCompletionRequest.Message("user", userPrompt)
        );

        var request = new ChatCompletionRequest(
                openAIProperties.getModel(),
                messages,
                400,    // max_tokens
                0.3     // temperature (baja para respuestas más consistentes)
        );

        ChatCompletionResponse response = openAIClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .onErrorResume(ex -> {
                    ex.printStackTrace();
                    return Mono.justOrEmpty(null);
                })
                .block();

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return "Lo siento, no pude generar una recomendación en este momento.";
        }

        return Optional.ofNullable(response.choices().get(0).message().content())
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .orElse("Lo siento, no pude generar una recomendación en este momento.");
    }
}
