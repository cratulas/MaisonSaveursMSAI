package com.saveursmaison.ia.logging;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ExecutionException;

/**
 * Repositorio simple para guardar logs de IA en Firestore.
 */
@Repository
@RequiredArgsConstructor
public class PairingLogRepository {

    private static final String COLLECTION_NAME = "ai_pairing_sessions";

    private final Firestore firestore;

    public void save(PairingLog log) {
        // Seteamos createdAt aquí para asegurarnos que siempre vaya
        log.setCreatedAt(Timestamp.now());

        try {
            firestore.collection(COLLECTION_NAME)
                    .add(log)
                    .get(); 
        } catch (InterruptedException | ExecutionException e) {
            // Si falla el log, NO debemos romper la respuesta al usuario.
            e.printStackTrace();
        }
    }
}
