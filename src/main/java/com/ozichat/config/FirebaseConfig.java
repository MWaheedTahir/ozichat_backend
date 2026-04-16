package com.ozichat.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Initialises the Firebase Admin SDK at startup.
 *
 * To enable:
 *   1. Download your Firebase project's service-account JSON from
 *      Firebase Console → Project Settings → Service Accounts → Generate new private key
 *   2. Place the file on the classpath (src/main/resources/firebase-service-account.json)
 *      OR set FIREBASE_CREDENTIALS_PATH to an absolute path outside the JAR
 *   3. Set FIREBASE_ENABLED=true in your environment / application.yml
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials-path:firebase-service-account.json}")
    private String credentialsPath;

    @Value("${firebase.project-id:ozichat}")
    private String projectId;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @PostConstruct
    public void init() {
        if (!firebaseEnabled) {
            log.info("Firebase is disabled (firebase.enabled=false). Push notifications will be logged only.");
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            try (InputStream serviceAccount = resolveCredentials()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setProjectId(projectId)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialised — projectId={}", projectId);
            } catch (IOException e) {
                log.error("Failed to initialise Firebase: {}. Push notifications disabled.", e.getMessage());
            }
        }
    }

    private InputStream resolveCredentials() throws IOException {
        // First try classpath (works inside JAR)
        Resource classpathResource = new ClassPathResource(credentialsPath);
        if (classpathResource.exists()) {
            return classpathResource.getInputStream();
        }

        // Fall back to absolute file-system path (useful in Docker/K8s with mounted secrets)
        Resource fsResource = new FileSystemResource(credentialsPath);
        if (fsResource.exists()) {
            return fsResource.getInputStream();
        }

        throw new IOException("Firebase credentials file not found at: " + credentialsPath);
    }
}
