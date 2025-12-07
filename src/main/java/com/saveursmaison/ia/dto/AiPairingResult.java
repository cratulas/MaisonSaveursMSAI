package com.saveursmaison.ia.dto;

import java.util.List;

/**
 * Representa la respuesta estructurada que esperamos de la IA.
 */
public class AiPairingResult {

    private String answer;
    private List<String> recommendedWineIds;
    private List<String> recommendedCheeseIds;

    public AiPairingResult() {
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getRecommendedWineIds() {
        return recommendedWineIds;
    }

    public void setRecommendedWineIds(List<String> recommendedWineIds) {
        this.recommendedWineIds = recommendedWineIds;
    }

    public List<String> getRecommendedCheeseIds() {
        return recommendedCheeseIds;
    }

    public void setRecommendedCheeseIds(List<String> recommendedCheeseIds) {
        this.recommendedCheeseIds = recommendedCheeseIds;
    }
}
