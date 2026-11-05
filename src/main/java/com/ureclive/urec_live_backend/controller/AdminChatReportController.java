package com.ureclive.urec_live_backend.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ureclive.urec_live_backend.dto.ChatReportGroupResponse;
import com.ureclive.urec_live_backend.dto.ChatReportSummaryResponse;
import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.service.AdminChatReportService;

@RestController
@RequestMapping("/api/admin/chat-reports")
@CrossOrigin(origins = "*")
// Role names are stored as-is (the admin UI assigns "ADMIN"), so accept both forms like AdminUserController
@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ROLE_ADMIN')")
public class AdminChatReportController {

    private static final Logger logger = LoggerFactory.getLogger(AdminChatReportController.class);

    private final AdminChatReportService reportService;

    @Autowired
    public AdminChatReportController(AdminChatReportService reportService) {
        this.reportService = reportService;
    }

    /** GET /api/admin/chat-reports?includeResolved=false — reports grouped by message */
    @GetMapping
    public List<ChatReportGroupResponse> getGrouped(@RequestParam(defaultValue = "false") boolean includeResolved) {
        logger.info("[GET /api/admin/chat-reports] includeResolved={}", includeResolved);
        return reportService.getGrouped(includeResolved);
    }

    /** GET /api/admin/chat-reports/summary — counts for the sidebar badge (polled; not logged) */
    @GetMapping("/summary")
    public ChatReportSummaryResponse getSummary() {
        return reportService.getSummary();
    }

    /** PUT /api/admin/chat-reports/{community|dm}/{messageId}/dismiss — close the reports, keep the message */
    @PutMapping("/{channel}/{messageId}/dismiss")
    public ChatReportGroupResponse dismiss(@PathVariable String channel, @PathVariable Long messageId, Authentication auth) {
        logger.info("[PUT /api/admin/chat-reports/{}/{}/dismiss]", channel, messageId);
        return reportService.dismiss(ChatChannel.fromPath(channel), messageId, auth.getName());
    }

    /** PUT /api/admin/chat-reports/{community|dm}/{messageId}/remove — hide the message from members */
    @PutMapping("/{channel}/{messageId}/remove")
    public ChatReportGroupResponse remove(@PathVariable String channel, @PathVariable Long messageId, Authentication auth) {
        logger.info("[PUT /api/admin/chat-reports/{}/{}/remove]", channel, messageId);
        return reportService.remove(ChatChannel.fromPath(channel), messageId, auth.getName());
    }
}
