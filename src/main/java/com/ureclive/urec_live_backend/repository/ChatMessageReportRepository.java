package com.ureclive.urec_live_backend.repository;

import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.ChatMessageReport;
import com.ureclive.urec_live_backend.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageReportRepository extends JpaRepository<ChatMessageReport, Long> {

    boolean existsByChannelAndMessageIdAndReporterUsername(ChatChannel channel, Long messageId, String reporterUsername);

    List<ChatMessageReport> findByStatusOrderByReportedAtDesc(ReportStatus status);

    List<ChatMessageReport> findAllByOrderByReportedAtDesc();

    List<ChatMessageReport> findByChannelAndMessageIdOrderByReportedAtDesc(ChatChannel channel, Long messageId);

    long countByStatus(ReportStatus status);
}
