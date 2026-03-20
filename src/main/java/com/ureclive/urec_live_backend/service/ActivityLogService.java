package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ActivityLog;
import com.ureclive.urec_live_backend.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Autowired
    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Async
    public void log(String eventType, String username, String description, String equipmentName) {
        activityLogRepository.save(new ActivityLog(eventType, username, description, equipmentName));
    }
}
