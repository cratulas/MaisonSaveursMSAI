// ia-service/src/main/java/com/saveursmaison/ia/dto/PairingHistoryItemResponse.java
package com.saveursmaison.ia.dto;

import java.util.List;

/**
 * DTO que el MS de IA devolverá al BFF para el historial.
 */
public class PairingHistoryItemResponse {

    private String userId;
    private String locale;
    private String source;
    private String message;
    private List<String> selectedWineIds;
    private List<String> selectedCheeseIds;
    private String answer;
    private List<String> recommendedWineIds;
    private List<String> recommendedCheeseIds;
    private String createdAt;

    public PairingHistoryItemResponse() {
    }

    public PairingHistoryItemResponse(
            String userId,
            String locale,
            String source,
            String message,
            List<String> selectedWineIds,
            List<String> selectedCheeseIds,
            String answer,
            List<String> recommendedWineIds,
            List<String> recommendedCheeseIds,
            String createdAt
    ) {
        this.userId = userId;
        this.locale = locale;
        this.source = source;
        this.message = message;
        this.selectedWineIds = selectedWineIds;
        this.selectedCheeseIds = selectedCheeseIds;
        this.answer = answer;
        this.recommendedWineIds = recommendedWineIds;
        this.recommendedCheeseIds = recommendedCheeseIds;
        this.createdAt = createdAt;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getSelectedWineIds() { return selectedWineIds; }
    public void setSelectedWineIds(List<String> selectedWineIds) { this.selectedWineIds = selectedWineIds; }

    public List<String> getSelectedCheeseIds() { return selectedCheeseIds; }
    public void setSelectedCheeseIds(List<String> selectedCheeseIds) { this.selectedCheeseIds = selectedCheeseIds; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<String> getRecommendedWineIds() { return recommendedWineIds; }
    public void setRecommendedWineIds(List<String> recommendedWineIds) { this.recommendedWineIds = recommendedWineIds; }

    public List<String> getRecommendedCheeseIds() { return recommendedCheeseIds; }
    public void setRecommendedCheeseIds(List<String> recommendedCheeseIds) { this.recommendedCheeseIds = recommendedCheeseIds; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
