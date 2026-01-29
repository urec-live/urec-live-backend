package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.service.DataExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DataExportService dataExportService;

    @Autowired
    public AdminController(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }

    @GetMapping("/export/sessions")
    public ResponseEntity<String> exportSessions() {
        String csvData = dataExportService.exportSessionsToCsv();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"equipment_sessions.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }
}
