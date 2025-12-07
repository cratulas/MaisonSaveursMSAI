// ia-service/src/main/java/com/saveursmaison/ia/logging/PairingLogRepository.java
package com.saveursmaison.ia.logging;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


@Repository
@RequiredArgsConstructor
public class PairingLogRepository {

    private static final String COLLECTION_NAME = "ai_pairing_sessions";

    private final Firestore firestore;

    /**
     * Guarda un log en Firestore.
     */
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

    /**
     * Devuelve los últimos N logs de un usuario, ordenados por fecha desc.
     */
    public List<PairingLog> findByUserIdOrderByCreatedAtDesc(String userId, int limit) {
        try {
            return firestore.collection(COLLECTION_NAME)
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .get()
                    .getDocuments()
                    .stream()
                    .map(doc -> doc.toObject(PairingLog.class))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
