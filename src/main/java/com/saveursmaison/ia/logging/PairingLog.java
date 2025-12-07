package com.saveursmaison.ia.logging;

import com.google.cloud.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Documento que se guardará en Firestore para cada interacción con la IA.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairingLog {

    private String userId;
    private String locale;
    private String source; // "prompt", "selection", etc.

    private String message;
    private List<String> selectedWineIds;
    private List<String> selectedCheeseIds;

    private String answer;
    private List<String> recommendedWineIds;
    private List<String> recommendedCheeseIds;

    private Timestamp createdAt;
}
