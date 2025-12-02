package com.saveursmaison.ia.controller;

import com.saveursmaison.ia.dto.PairingChatRequest;
import com.saveursmaison.ia.dto.PairingChatResponse;
import com.saveursmaison.ia.service.PairingAIService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/pairings")
@CrossOrigin(origins = "*") // Restringir luego
public class PairingAIController {

    private final PairingAIService pairingAIService;

    public PairingAIController(PairingAIService pairingAIService) {
        this.pairingAIService = pairingAIService;
    }

    @PostMapping("/chat")
    public ResponseEntity<PairingChatResponse> chat(@RequestBody PairingChatRequest request) {
        PairingChatResponse response = pairingAIService.chat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public String health() {
        return "OK - ia-service";
    }
}
