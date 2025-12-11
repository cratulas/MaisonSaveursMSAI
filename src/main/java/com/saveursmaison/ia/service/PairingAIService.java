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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PairingAIService {

    private final WebClient openAIClient;
    private final OpenAIProperties openAIProperties;
    private final CatalogClient catalogClient;
    private final PairingLogRepository pairingLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Modos de recomendación para controlar el comportamiento del modelo.
     */
    private enum PairingMode {
        WINE_ONLY,
        CHEESE_ONLY,
        PAIRING
    }

    /**
     * Prompt de sistema para guiar al modelo.
     */
    private static final String SYSTEM_PROMPT = """
        You are the AI sommelier of the Saveurs Maison app.

        Your job is to recommend wine and/or cheese using ONLY products from our catalog.

        VERY IMPORTANT:
        - The user message includes a line "MODE: WINE_ONLY", "MODE: CHEESE_ONLY" or "MODE: PAIRING".
        - You MUST respect this MODE. Do NOT try to infer a different mode.
        - The user message also includes:
          - "MAX_WINE_COUNT: N"
          - "MAX_CHEESE_COUNT: M"
          You MUST NEVER return more than N wines or more than M cheeses.

        BEHAVIOUR BY MODE:

        1) MODE = CHEESE_ONLY
        - Recommend ONLY cheeses from the catalog.
        - "recommendedCheeseIds" must contain one or more IDs (up to MAX_CHEESE_COUNT).
        - "recommendedWineIds" MUST be an empty array [].
        - In the textual "answer" you can mention the wine the user already gave
          (e.g. "your Pinot Noir"), but you MUST NOT introduce any new wine product
          from the catalog by ID or by name beyond what is strictly necessary.

        2) MODE = WINE_ONLY
        - Recommend ONLY wines from the catalog.
        - "recommendedWineIds" must contain one or more IDs (up to MAX_WINE_COUNT).
        - "recommendedCheeseIds" MUST be an empty array [].
        - In the textual "answer" you can mention the cheese the user already gave,
          but you MUST NOT introduce any new cheese product from the catalog beyond
          what is strictly necessary.

        3) MODE = PAIRING
        - Recommend BOTH wine(s) and cheese(s).
        - In this case, both "recommendedWineIds" and "recommendedCheeseIds" can contain IDs,
          but never more than MAX_WINE_COUNT / MAX_CHEESE_COUNT respectively.

        Diversity rules:
        - If several wines fit the request, DO NOT always recommend the same product.
        - Alternate between different products of the same style, region or grape when possible.
        - Avoid repeating the exact same wine or cheese if there are other suitable options.

        General rules:
        - Answer in a friendly but concise tone.
        - ONLY use wines and cheeses that appear in the catalog list provided.
        - In the "answer" text you MUST NOT show product IDs.
          Use only the product names (e.g. "Pinot Noir Reserve 2022", "Brie de Meaux AOP").
        - In "recommendedWineIds" and "recommendedCheeseIds" you MUST include the correct
          catalog product IDs that match the products you mention in the answer.
        - If the user asks for something we don't have, suggest the closest style using our catalog.

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

        // 2. Detectar modo y cantidades máximas
        PairingMode mode = detectMode(request);
        int maxWineCount = computeMaxWineCount(request, mode);
        int maxCheeseCount = computeMaxCheeseCount(request, mode);

        // 3. Construir prompt de usuario con el contexto + catálogo
        String userPrompt = buildUserPrompt(request, locale, wines, cheeses, mode, maxWineCount, maxCheeseCount);

        // 4. Llamar a OpenAI y obtener un resultado estructurado
        AiPairingResult aiResult = getPairingRecommendation(userPrompt, locale);

        // 5. Fallback si algo salió mal
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

            // Blindaje extra: nunca devolver más de lo permitido
            if (maxWineCount >= 0 && wineIds.size() > maxWineCount) {
                wineIds = wineIds.subList(0, maxWineCount);
            }
            if (maxCheeseCount >= 0 && cheeseIds.size() > maxCheeseCount) {
                cheeseIds = cheeseIds.subList(0, maxCheeseCount);
            }

            // Si el modo es WINE_ONLY, aseguramos que no haya quesos, y viceversa
            if (mode == PairingMode.WINE_ONLY) {
                cheeseIds = Collections.emptyList();
            } else if (mode == PairingMode.CHEESE_ONLY) {
                wineIds = Collections.emptyList();
            }
        }

        PairingChatResponse response = new PairingChatResponse(
                finalAnswer,
                wineIds,
                cheeseIds
        );

        // 6. Guardar log en Firestore
        try {
            PairingLog log = PairingLog.builder()
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
     * Detecta el modo de recomendación según el mensaje y/o selección previa.
     *
     * - Si seleccionó solo vino → quiere quesos para ese vino → CHEESE_ONLY.
     * - Si seleccionó solo queso → quiere vinos para ese queso → WINE_ONLY.
     * - Si solo hay texto:
     *      - Menciona solo vino → WINE_ONLY.
     *      - Menciona solo queso → CHEESE_ONLY.
     *      - Caso contrario → PAIRING.
     */
    private PairingMode detectMode(PairingChatRequest request) {
        String msg = Optional.ofNullable(request.getMessage())
                .orElse("")
                .toLowerCase();

        boolean hasSelectedWine = request.getSelectedWineIds() != null
                && !request.getSelectedWineIds().isEmpty();
        boolean hasSelectedCheese = request.getSelectedCheeseIds() != null
                && !request.getSelectedCheeseIds().isEmpty();

        // Flujos guiados por la UI
        if (hasSelectedWine && !hasSelectedCheese) {
            // El usuario eligió un vino y ahora quiere quesos para ese vino
            return PairingMode.CHEESE_ONLY;
        }
        if (hasSelectedCheese && !hasSelectedWine) {
            // El usuario eligió un queso y ahora quiere vinos para ese queso
            return PairingMode.WINE_ONLY;
        }

        // Heurística basada en el mensaje libre
        boolean mentionsWine = msg.contains("wine") || msg.contains("vino");
        boolean mentionsCheese = msg.contains("cheese") || msg.contains("queso");

        boolean saysOnlyCheese = msg.contains("only cheese") || msg.contains("just cheese") || msg.contains("solo queso");
        boolean saysOnlyWine = msg.contains("only wine") || msg.contains("just wine") || msg.contains("solo vino");

        if (saysOnlyCheese) {
            return PairingMode.CHEESE_ONLY;
        }
        if (saysOnlyWine) {
            return PairingMode.WINE_ONLY;
        }

        if (mentionsWine && !mentionsCheese) {
            return PairingMode.WINE_ONLY;
        }
        if (mentionsCheese && !mentionsWine) {
            return PairingMode.CHEESE_ONLY;
        }

        // Si habla de ambas cosas o es muy ambiguo → pairing completo
        return PairingMode.PAIRING;
    }

    /**
     * Calcula el máximo de vinos que puede devolver el modelo.
     */
    private int computeMaxWineCount(PairingChatRequest request, PairingMode mode) {
        String msg = Optional.ofNullable(request.getMessage())
                .orElse("")
                .toLowerCase();

        // 1) Detectar patrones numéricos genéricos"
        Matcher numberWineMatcher = Pattern
                .compile("(\\d+)\\s+\\w*(?:\\s+\\w+)*\\s*wines?")
                .matcher(msg);

        if (numberWineMatcher.find()) {
            try {
                int n = Integer.parseInt(numberWineMatcher.group(1));
                if (n <= 0) {
                    n = 1;
                } else if (n > 5) { // límite de seguridad
                    n = 5;
                }
                return n;
            } catch (NumberFormatException ignored) {
            }
        }

        // 2) Palabras clave en inglés/español (por si no viene número explícito).
        if (msg.contains("two wines") || msg.contains("two red wines") ||
            msg.contains("2 wines") || msg.contains("2 red wines") ||
            msg.contains("dos vinos")) {
            return 2;
        }
        if (msg.contains("three wines") || msg.contains("three red wines") ||
            msg.contains("3 wines") || msg.contains("3 red wines") ||
            msg.contains("tres vinos")) {
            return 3;
        }

        // 3) Por defecto según el modo
        if (mode == PairingMode.WINE_ONLY || mode == PairingMode.PAIRING) {
            return 1; // un vino por defecto
        }

        // Modo solo queso → no debería devolver vinos
        return 0;
    }

    /**
     * Calcula el máximo de quesos que puede devolver el modelo.
     */
    private int computeMaxCheeseCount(PairingChatRequest request, PairingMode mode) {
        String msg = Optional.ofNullable(request.getMessage())
                .orElse("")
                .toLowerCase();

        if (msg.contains("two cheeses") || msg.contains("2 cheeses") || msg.contains("dos quesos")) {
            return 2;
        }
        if (msg.contains("three cheeses") || msg.contains("3 cheeses") || msg.contains("tres quesos")) {
            return 3;
        }

        if (mode == PairingMode.CHEESE_ONLY || mode == PairingMode.PAIRING) {
            return 1; // un queso por defecto
        }

        return 0;
    }

    /**
     * Arma un prompt de usuario usando:
     * - idioma
     * - mensaje del usuario
     * - IDs seleccionados (si vienen)
     * - lista de vinos y quesos del catálogo
     * - modo y cantidades máximas
     */
    private String buildUserPrompt(PairingChatRequest request,
                                   String locale,
                                   List<WineForAiDto> wines,
                                   List<CheeseForAiDto> cheeses,
                                   PairingMode mode,
                                   int maxWineCount,
                                   int maxCheeseCount) {

        // Aleatorizar el orden para ayudar a la diversidad
        Collections.shuffle(wines);
        Collections.shuffle(cheeses);

        StringBuilder sb = new StringBuilder();

        sb.append("User language (use this language in 'answer'): ").append(locale).append("\n");
        sb.append("MODE: ").append(mode.name()).append("\n");
        sb.append("MAX_WINE_COUNT: ").append(maxWineCount).append("\n");
        sb.append("MAX_CHEESE_COUNT: ").append(maxCheeseCount).append("\n");
        sb.append("User message: ").append(request.getMessage()).append("\n\n");

        if (request.getSelectedWineIds() != null && !request.getSelectedWineIds().isEmpty()) {
            sb.append("Selected wine IDs: ").append(request.getSelectedWineIds()).append("\n");
        }
        if (request.getSelectedCheeseIds() != null && !request.getSelectedCheeseIds().isEmpty()) {
            sb.append("Selected cheese IDs: ").append(request.getSelectedCheeseIds()).append("\n");
        }

        sb.append("\nHere is the list of AVAILABLE WINES in the catalog (ID, name, type, price):\n");
        String winesText = wines.stream()
                .limit(50)
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
        sb.append("You MUST respect the MODE above (WINE_ONLY, CHEESE_ONLY or PAIRING) ");
        sb.append("when deciding whether to recommend wines, cheeses, or both.\n");
        sb.append("You MUST also respect MAX_WINE_COUNT and MAX_CHEESE_COUNT:\n");
        sb.append("- Never return more than MAX_WINE_COUNT items in \"recommendedWineIds\".\n");
        sb.append("- Never return more than MAX_CHEESE_COUNT items in \"recommendedCheeseIds\".\n");
        sb.append("Please recommend using ONLY the products above.\n");
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
                0.8     // temperatura más alta para mayor diversidad
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
