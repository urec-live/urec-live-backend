package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ChatChannel;
import com.ureclive.urec_live_backend.entity.ChatMessageReport;
import com.ureclive.urec_live_backend.entity.CommunityMessage;
import com.ureclive.urec_live_backend.entity.ReportReason;
import com.ureclive.urec_live_backend.repository.ChatMessageReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Two staff acting on the same message at once: the one whose reports changed underneath gets a 409. */
@ExtendWith(MockitoExtension.class)
class AdminChatReportServiceTest {

    @Mock private ChatMessageReportRepository reportRepo;
    @Mock private ChatMessageService chat;
    @Mock private ActivityLogService activityLogService;

    @InjectMocks private AdminChatReportService service;

    private CommunityMessage message;

    @BeforeEach
    void setUp() {
        message = new CommunityMessage();
        message.setSenderUsername("alice");
        when(reportRepo.findByChannelAndMessageIdOrderByReportedAtDesc(ChatChannel.COMMUNITY, 7L))
                .thenReturn(List.of(new ChatMessageReport(ChatChannel.COMMUNITY, 7L, "bob", ReportReason.SPAM, null)));
        // The other staff member's change committed first
        when(reportRepo.saveAllAndFlush(anyList()))
                .thenThrow(new ObjectOptimisticLockingFailureException(ChatMessageReport.class, 1L));
    }

    @Test
    void removingWhileSomeoneElseDismissesIs409AndLeavesTheMessageAlone() {
        when(chat.findMessage(ChatChannel.COMMUNITY, 7L)).thenReturn(Optional.of(message));

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.remove(ChatChannel.COMMUNITY, 7L, "staff_two"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertFalse(message.isRemovedByStaff());
        verify(chat, never()).saveMessage(any());
        verify(chat, never()).broadcast(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void dismissingWhileSomeoneElseRemovesIs409() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.dismiss(ChatChannel.COMMUNITY, 7L, "staff_two"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoInteractions(activityLogService);
    }
}
