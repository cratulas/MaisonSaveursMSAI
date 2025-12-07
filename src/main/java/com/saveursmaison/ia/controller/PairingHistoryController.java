// ia-service/src/main/java/com/saveursmaison/ia/controller/PairingHistoryController.java
package com.saveursmaison.ia.controller;

import com.google.cloud.Timestamp;
import com.saveursmaison.ia.dto.PairingHistoryItemResponse;
import com.saveursmaison.ia.logging.PairingLog;
import com.saveursmaison.ia.logging.PairingLogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai/pairings")
@CrossOrigin(origins = "*")
public class PairingHistoryController {

    private final PairingLogRepository pairingLogRepository;

    public PairingHistoryController(PairingLogRepository pairingLogRepository) {
        this.pairingLogRepository = pairingLogRepository;
    }

    @GetMapping("/history")
    public List<PairingHistoryItemResponse> getHistoryByUser(
            @RequestParam("userId") String userId
    ) {
        // últimas 20 recomendaciones
        List<PairingLog> logs = pairingLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId, 20);

        return logs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private PairingHistoryItemResponse toDto(PairingLog log) {
        String createdAtIso = null;
        Timestamp ts = log.getCreatedAt();
        if (ts != null) {
            createdAtIso = ts.toSqlTimestamp().toInstant().toString();
        }

        return new PairingHistoryItemResponse(
                log.getUserId(),
                log.getLocale(),
                log.getSource(),
                log.getMessage(),
                log.getSelectedWineIds(),
                log.getSelectedCheeseIds(),
                log.getAnswer(),
                log.getRecommendedWineIds(),
                log.getRecommendedCheeseIds(),
                createdAtIso
        );
    }
}
