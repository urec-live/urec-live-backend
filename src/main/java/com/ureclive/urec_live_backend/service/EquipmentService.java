package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.EquipmentWaitTimeEstimate;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final AnalyticsService analyticsService;

    public EquipmentService(EquipmentRepository equipmentRepository, AnalyticsService analyticsService) {
        this.equipmentRepository = equipmentRepository;
        this.analyticsService = analyticsService;
    }

    @Transactional(readOnly = true)
    public List<EquipmentWaitTimeEstimate> getRecommendations(Long equipmentId) {
        Equipment currentEquipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found"));

        Collection<String> muscleGroups = currentEquipment.getExercises().stream()
                .map(Exercise::getMuscleGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (muscleGroups.isEmpty()) {
            return Collections.emptyList();
        }

        List<Equipment> alternatives = equipmentRepository.findByMuscleGroupInAndIdNot(muscleGroups, equipmentId);

        // Convert to WaitTimeEstimate to get status and wait times
        List<EquipmentWaitTimeEstimate> estimates = alternatives.stream()
                .map(eq -> analyticsService.getWaitTimeEstimate(eq, Duration.ofDays(7)))
                .collect(Collectors.toList());

        // Sort: Available first, then by shortest wait time
        return estimates.stream()
                .sorted((a, b) -> {
                    boolean aFree = !a.isInUse();
                    boolean bFree = !b.isInUse();
                    if (aFree && !bFree)
                        return -1;
                    if (!aFree && bFree)
                        return 1;

                    // Both busy or both free. If busy, prefer shorter wait.
                    // If free, wait time is usually 0 or null, so simpler compare.
                    long waitA = a.getEstimatedWaitSeconds() != null ? a.getEstimatedWaitSeconds() : 0;
                    long waitB = b.getEstimatedWaitSeconds() != null ? b.getEstimatedWaitSeconds() : 0;
                    return Long.compare(waitA, waitB);
                })
                .limit(5) // Top 5
                .collect(Collectors.toList());
    }
}
