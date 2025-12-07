package com.saveursmaison.ia.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saveursmaison.ia.client.CatalogClient;
import com.saveursmaison.ia.client.ChatCompletionRequest;
import com.saveursmaison.ia.client.ChatCompletionResponse;
import com.saveursmaison.ia.config.OpenAIProperties;
import com.saveursmaison.ia.dto.AiPairingResult;
import com.saveursmaison.ia.dto.CheeseForAiDto;
import com.saveursmaison.ia.dto.PairingChatRequest;
import com.saveursmaison.ia.dto.PairingChatResponse;
import com.saveursmaison.ia.dto.WineForAiDto;
import com.saveursmaison.ia.logging.PairingLog;
import com.saveursmaison.ia.logging.PairingLogRepository;
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
    private final PairingLogRepository pairingLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Prompt de sistema para guiar al modelo.
     */
    private static final String SYSTEM_PROMPT = """
        You are the AI sommelier of the Saveurs Maison app.

        Your job is to recommend wine and/or cheese using ONLY products from our catalog.

        First, you MUST infer what the user is asking:

        1) CHEESE ONLY
        - If the user clearly asks ONLY for cheeses
          (e.g. "recommend me a cheese", "solo un queso",
           "what cheese goes with Pinot Noir", etc.),
          then you must recommend ONLY cheeses from the catalog.
        - "recommendedCheeseIds" must contain one or more IDs.
        - "recommendedWineIds" MUST be an empty array [].
        - In the textual "answer" you can mention the wine
          the user already gave (e.g. "your Pinot Noir"),
          but you MUST NOT introduce any new wine product from the catalog
          by ID or by name beyond what is necessary.

        2) WINE ONLY
        - If the user clearly asks ONLY for wines
          (e.g. "recommend me a red wine", "solo un vino",
           "what wine goes with this cheese", etc.),
          then you must recommend ONLY wines from the catalog.
        - "recommendedWineIds" must contain one or more IDs.
        - "recommendedCheeseIds" MUST be an empty array [].
        - In the textual "answer" you can mention the cheese
          the user already gave, but you MUST NOT introduce any new cheese
          product from the catalog beyond what is necessary.

        3) PAIRING (BOTH)
        - If the user asks for a pairing or is ambiguous
          (e.g. "recommend a pairing", "vino y queso", etc.),
          then you can recommend BOTH a wine and a cheese.
        - In that case, both "recommendedWineIds" and "recommendedCheeseIds"
          can contain IDs.

        General rules:
        - Answer in a friendly but concise tone.
        - ONLY use wines and cheeses that appear in the catalog list provided.
        - In the "answer" text you MUST NOT show product IDs.
          Use only the product names (e.g. "Pinot Noir Reserve 2022",
          "Brie de Meaux AOP").
        - However, in "recommendedWineIds" and "recommendedCheeseIds"
          you MUST include the correct catalog product IDs that match
          the products you mention in the answer.
        - If the user asks for something we don't have, suggest
          the closest style using our catalog.

        OUTPUT FORMAT (VERY IMPORTANT):
        - You MUST respond ONLY with a single JSON object.
        - Do NOT include any markdown, explanation, or extra text.
        - The JSON MUST have exactly these fields:
          {
            "answer": "final answer text in the user's language",
            "recommendedWineIds": ["id1", "id2"],
            "recommendedCheeseIds": ["id3", "id4"]
          }
        - If you do not want to recommend any wine or any cheese,
          use an empty array [] for that field.
        - Do NOT add any other fields.
        """;

    public PairingAIService(
            @Qualifier("openAIWebClient") WebClient openAIWebClient,
            OpenAIProperties openAIProperties,
            CatalogClient catalogClient,
            PairingLogRepository pairingLogRepository
    ) {
        this.openAIClient = openAIWebClient;
        this.openAIProperties = openAIProperties;
        this.catalogClient = catalogClient;
        this.pairingLogRepository = pairingLogRepository;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

        // 3. Llamar a OpenAI y obtener un resultado estructurado
        AiPairingResult aiResult = getPairingRecommendation(userPrompt, locale);

        // 4. Fallback si algo salió mal
        String finalAnswer;
        List<String> wineIds;
        List<String> cheeseIds;

        if (aiResult == null || aiResult.getAnswer() == null) {
            finalAnswer = locale.startsWith("es")
                    ? "Lo siento, no pude generar una recomendación en este momento."
                    : "Sorry, I could not generate a recommendation at this time.";

            wineIds = Collections.emptyList();
            cheeseIds = Collections.emptyList();
        } else {
            finalAnswer = aiResult.getAnswer();
            wineIds = Optional.ofNullable(aiResult.getRecommendedWineIds())
                    .orElse(Collections.emptyList());
            cheeseIds = Optional.ofNullable(aiResult.getRecommendedCheeseIds())
                    .orElse(Collections.emptyList());
        }

        PairingChatResponse response = new PairingChatResponse(
                finalAnswer,
                wineIds,
                cheeseIds
        );

        // 5. Guardar log en Firestore
        try {
            PairingLog log = PairingLog.builder()
                    // si el usuario no está logeado vendrá null,
                    // si está logeado vendrá el oid/sub desde el BFF
                    .userId(request.getUserId())
                    .locale(locale)
                    .source("prompt")
                    .message(request.getMessage())
                    .selectedWineIds(request.getSelectedWineIds())
                    .selectedCheeseIds(request.getSelectedCheeseIds())
                    .answer(finalAnswer)
                    .recommendedWineIds(wineIds)
                    .recommendedCheeseIds(cheeseIds)
                    .build();

            pairingLogRepository.save(log);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
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

        sb.append("User language (use this language in 'answer'): ").append(locale).append("\n");
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

        sb.append("TASK:\n");
        sb.append("Please recommend the best wine and/or cheese pairing using ONLY the products above.\n");
        sb.append("You MUST respond ONLY with a single JSON object with this structure:\n");
        sb.append("{\n");
        sb.append("  \"answer\": \"final answer text in the user's language\",\n");
        sb.append("  \"recommendedWineIds\": [\"id1\", \"id2\"],\n");
        sb.append("  \"recommendedCheeseIds\": [\"id3\", \"id4\"]\n");
        sb.append("}\n");
        sb.append("If you don't want to recommend any product, use an empty array [].\n");
        sb.append("Do NOT include any extra text outside the JSON.\n");

        return sb.toString();
    }

    /**
     * Llama a la API de OpenAI usando WebClient, obtiene el texto
     * y lo parsea a AiPairingResult (JSON).
     */
    private AiPairingResult getPairingRecommendation(String userPrompt, String locale) {

        var messages = List.of(
                new ChatCompletionRequest.Message("system", SYSTEM_PROMPT),
                new ChatCompletionRequest.Message("user", userPrompt)
        );

        var request = new ChatCompletionRequest(
                openAIProperties.getModel(),
                messages,
                600,    // max_tokens 
                0.3     // temperature baja para respuestas más consistentes
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
            return null;
        }

        String rawContent = Optional.ofNullable(response.choices().get(0).message().content())
                .map(String::trim)
                .orElse(null);

        if (rawContent == null || rawContent.isBlank()) {
            return null;
        }

        try {
            // Intentamos parsear directamente el JSON que devuelve el modelo
            return objectMapper.readValue(rawContent, AiPairingResult.class);
        } catch (Exception ex) {
            ex.printStackTrace();
            AiPairingResult fallback = new AiPairingResult();
            fallback.setAnswer(rawContent);
            fallback.setRecommendedWineIds(Collections.emptyList());
            fallback.setRecommendedCheeseIds(Collections.emptyList());
            return fallback;
        }
    }
}
