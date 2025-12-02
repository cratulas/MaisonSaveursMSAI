package com.saveursmaison.ia.dto;

import java.util.List;

public class PairingChatResponse {

    private String answer;
    private List<String> recommendedWineIds;
    private List<String> recommendedCheeseIds;

    public PairingChatResponse() {
    }

    public PairingChatResponse(String answer,
                               List<String> recommendedWineIds,
                               List<String> recommendedCheeseIds) {
        this.answer = answer;
        this.recommendedWineIds = recommendedWineIds;
        this.recommendedCheeseIds = recommendedCheeseIds;
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
