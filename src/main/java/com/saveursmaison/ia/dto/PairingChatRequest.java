package com.saveursmaison.ia.dto;

import java.util.List;

public class PairingChatRequest {

    private String message;
    private String locale; // "en", "fr", "es"
    private List<String> selectedWineIds;
    private List<String> selectedCheeseIds;
    private String userId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public List<String> getSelectedWineIds() {
        return selectedWineIds;
    }

    public void setSelectedWineIds(List<String> selectedWineIds) {
        this.selectedWineIds = selectedWineIds;
    }

    public List<String> getSelectedCheeseIds() {
        return selectedCheeseIds;
    }

    public void setSelectedCheeseIds(List<String> selectedCheeseIds) {
        this.selectedCheeseIds = selectedCheeseIds;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
