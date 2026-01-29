package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DataExportService {

    @Autowired
    private EquipmentSessionRepository sessionRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("UTC"));

    public String exportSessionsToCsv() {
        List<EquipmentSession> sessions = sessionRepository.findAll();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Header
        pw.println("ID,Reviewer/User,Machine Code,Machine Name,Start Time (UTC),End Time (UTC),Duration (min),Status");

        for (EquipmentSession s : sessions) {
            long durationMin = 0;
            if (s.getEndedAt() != null && s.getStartedAt() != null) {
                durationMin = java.time.Duration.between(s.getStartedAt(), s.getEndedAt()).toMinutes();
            }

            pw.printf("%d,%s,%s,\"%s\",%s,%s,%d,%s%n",
                    s.getId(),
                    s.getUser() != null ? s.getUser().getUsername() : "Guest/Anon",
                    s.getEquipment() != null ? s.getEquipment().getCode() : "N/A",
                    s.getEquipment() != null ? s.getEquipment().getName() : "Unknown",
                    s.getStartedAt() != null ? DATE_FORMAT.format(s.getStartedAt()) : "",
                    s.getEndedAt() != null ? DATE_FORMAT.format(s.getEndedAt()) : "",
                    durationMin,
                    s.getStatus());
        }

        return sw.toString();
    }
}
