package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import com.ureclive.urec_live_backend.service.EquipmentSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class EquipmentSessionPublishAfterCommitTest {

    @Autowired
    private EquipmentSessionService equipmentSessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void publishIsSkippedWhenTransactionRollsBack() {
        String suffix = String.valueOf(System.currentTimeMillis());
        User user = userRepository.save(
                new User("rollback_user_" + suffix, "rollback_user_" + suffix + "@example.com", "password")
        );
        Equipment equipment = equipmentRepository.save(
                new Equipment("ROLLBACK01_" + suffix, "Rollback Machine", "AVAILABLE", null)
        );

        TransactionTemplate template = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        template.execute(status -> {
            equipmentSessionService.startSession(user, equipment, "cli_test");
            status.setRollbackOnly();
            return null;
        });

        verifyNoInteractions(messagingTemplate);
    }
}
