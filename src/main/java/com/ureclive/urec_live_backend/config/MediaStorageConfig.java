package com.ureclive.urec_live_backend.config;

import com.ureclive.urec_live_backend.service.LocalDiskMediaStorage;
import com.ureclive.urec_live_backend.service.MediaStorage;
import com.ureclive.urec_live_backend.service.S3MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks where chat photos are stored: media.storage=s3 (Cloudflare R2, for production; needs the
 * four MEDIA_S3_* variables) or local (a folder on this machine, the default for development).
 */
@Configuration
public class MediaStorageConfig {

    private static final Logger logger = LoggerFactory.getLogger(MediaStorageConfig.class);

    @Bean
    public MediaStorage mediaStorage(
            @Value("${media.storage:local}") String storage,
            @Value("${media.local-dir:${user.home}/.urec-live/chat-media}") String localDir,
            @Value("${media.s3.endpoint:}") String endpoint,
            @Value("${media.s3.bucket:}") String bucket,
            @Value("${media.s3.access-key:}") String accessKey,
            @Value("${media.s3.secret-key:}") String secretKey,
            @Value("${jwt.secret}") String secret) {
        return create(storage, localDir, endpoint, bucket, accessKey, secretKey, secret);
    }

    public static MediaStorage create(String storage, String localDir, String endpoint, String bucket,
                               String accessKey, String secretKey, String secret) {
        String type = storage == null ? "" : storage.trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "s3" -> {
                Map<String, String> required = new LinkedHashMap<>();
                required.put("MEDIA_S3_ENDPOINT", endpoint);
                required.put("MEDIA_S3_BUCKET", bucket);
                required.put("MEDIA_S3_ACCESS_KEY", accessKey);
                required.put("MEDIA_S3_SECRET_KEY", secretKey);
                List<String> missing = new ArrayList<>();
                required.forEach((name, value) -> {
                    if (value == null || value.isBlank()) missing.add(name);
                });
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("media.storage=s3 needs " + String.join(", ", missing));
                }
                logger.info("[Media] Chat photos are stored in bucket {} at {}", bucket.trim(), endpoint.trim());
                return new S3MediaStorage(endpoint.trim(), bucket.trim(), accessKey.trim(), secretKey.trim());
            }
            case "local" -> {
                LocalDiskMediaStorage local = new LocalDiskMediaStorage(Path.of(localDir), secret, Clock.systemUTC());
                logger.warn("[Media] Chat photos are stored on this machine in {} (media.storage=local). "
                        + "Set MEDIA_STORAGE=s3 and the MEDIA_S3_* variables in production.", local.root());
                return local;
            }
            default -> throw new IllegalStateException("media.storage must be local or s3, not '" + storage + "'");
        }
    }
}
