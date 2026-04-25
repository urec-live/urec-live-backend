package com.ureclive.urec_live_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.dto.MachineDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CvAvailabilityService {
    private static final Logger logger = LoggerFactory.getLogger(CvAvailabilityService.class);
    private static final String PRIMARY_SOURCE = "PRIMARY";
    private static final String SECONDARY_SOURCE = "CV_SECONDARY";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.cv.secondary.enabled:false}")
    private boolean enabled;

    @Value("${app.cv.secondary.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${app.cv.secondary.confidence-threshold:0.65}")
    private double confidenceThreshold;

    @Value("${app.cv.secondary.cache-ttl-ms:3000}")
    private long cacheTtlMs;

    private volatile long lastFetchEpochMs = 0L;
    private volatile Map<String, CvStatus> cachedStatuses = Collections.emptyMap();

    public CvAvailabilityService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public MachineDTO applySecondaryStatus(MachineDTO dto) {
        if (!enabled) {
            dto.setAvailabilitySource(PRIMARY_SOURCE);
            return dto;
        }

        CvStatus cvStatus = findMatchingCvStatus(dto);
        if (cvStatus == null) {
            dto.setAvailabilitySource(PRIMARY_SOURCE);
            return dto;
        }

        dto.setSecondaryStatus(toPrimaryStatus(cvStatus.status()));
        dto.setSecondaryConfidence(cvStatus.confidence());
        dto.setSecondaryTimestamp(cvStatus.timestamp());

        if (shouldUseSecondary(dto.getStatus(), cvStatus)) {
            dto.setStatus("In Use");
            dto.setAvailabilitySource(SECONDARY_SOURCE);
        } else {
            dto.setAvailabilitySource(PRIMARY_SOURCE);
        }

        return dto;
    }

    private boolean shouldUseSecondary(String primaryStatus, CvStatus cvStatus) {
        if (!"AVAILABLE".equals(normalizeStatus(primaryStatus))) {
            return false;
        }

        if (!"IN_USE".equalsIgnoreCase(cvStatus.status())) {
            return false;
        }

        if (cvStatus.confidence() == null) {
            return true;
        }

        return cvStatus.confidence() >= confidenceThreshold;
    }

    private CvStatus findMatchingCvStatus(MachineDTO dto) {
        Map<String, CvStatus> statuses = getStatuses();
        if (statuses.isEmpty()) {
            return null;
        }

        List<String> keys = new ArrayList<>();
        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            keys.add(normalizeKey(dto.getCode()));
        }
        if (dto.getId() != null) {
            keys.add(normalizeKey(String.valueOf(dto.getId())));
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            keys.add(normalizeKey(dto.getName()));
        }

        for (String key : keys) {
            CvStatus match = statuses.get(key);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    private synchronized Map<String, CvStatus> getStatuses() {
        long now = System.currentTimeMillis();
        if ((now - lastFetchEpochMs) < cacheTtlMs) {
            return cachedStatuses;
        }

        lastFetchEpochMs = now;
        cachedStatuses = fetchStatusesFromCvApi();
        return cachedStatuses;
    }

    private Map<String, CvStatus> fetchStatusesFromCvApi() {
        String url = baseUrl + "/equipment/status";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("[CV] Unexpected status {} while fetching {}", response.statusCode(), url);
                return Collections.emptyMap();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                logger.warn("[CV] Response from {} was not an array", url);
                return Collections.emptyMap();
            }

            Map<String, CvStatus> out = new HashMap<>();
            for (JsonNode node : root) {
                String equipmentId = node.path("equipment_id").asText(null);
                if (equipmentId == null || equipmentId.isBlank()) {
                    continue;
                }

                String status = node.path("status").asText("AVAILABLE");
                Double confidence = node.hasNonNull("confidence") ? node.path("confidence").asDouble() : null;
                String timestamp = node.path("timestamp").asText(null);
                String equipmentType = node.path("equipment_type").asText(null);

                CvStatus cvStatus = new CvStatus(equipmentId, equipmentType, status, confidence, timestamp);
                out.put(normalizeKey(equipmentId), cvStatus);

                if (equipmentType != null && !equipmentType.isBlank()) {
                    out.putIfAbsent(normalizeKey(equipmentType), cvStatus);
                }
            }

            return out;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("[CV] Failed to fetch secondary statuses from {}: {}", url, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private static String toPrimaryStatus(String cvStatus) {
        return "IN_USE".equalsIgnoreCase(cvStatus) ? "In Use" : "Available";
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase().replace(' ', '_');
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private record CvStatus(String equipmentId, String equipmentType, String status, Double confidence, String timestamp) {
    }
}