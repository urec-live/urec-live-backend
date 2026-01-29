package com.ureclive.urec_live_backend;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;
import com.ureclive.urec_live_backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AccountDeletionTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentSessionRepository equipmentSessionRepository;

    @Autowired
    private EquipmentEventRepository equipmentEventRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private UserWorkoutSplitRepository userWorkoutSplitRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // user setup
    }

    @Test
    @SuppressWarnings("null")
    void deleteUser_ShouldRemoveAllDependencies_AndSucceed() {
        // 1. Create User
        User user = new User("deleteMe", "delete@example.com", passwordEncoder.encode("password"));
        user = userRepository.save(user);

        // 2. Create Equipment (needed for session)
        Equipment equipment = new Equipment();
        equipment.setCode("EQ-DELETE-TEST");
        equipment.setName("Delete Test Machine");
        // equipment.setMuscleGroup("Legs"); // Removed: Field does not exist
        equipment.setStatus("AVAILABLE"); // Fix: Status is String, not Enum
        equipment = equipmentRepository.save(equipment);

        // 3. Create Equipment Session
        EquipmentSession session = new EquipmentSession();
        session.setUser(user);
        session.setEquipment(equipment);
        session.setStatus(EquipmentSessionStatus.ACTIVE);
        session.setStartedAt(Instant.now());
        session = equipmentSessionRepository.save(session);

        // 4. Create Equipment Event
        EquipmentEvent event = new EquipmentEvent();
        event.setUser(user);
        event.setSession(session);
        event.setEquipment(equipment); // Add missing equipment
        event.setEventType(EquipmentEventType.SESSION_STARTED); // Fix setType -> setEventType
        event.setOccurredAt(Instant.now());
        event.setMetadata("{}"); // Fix setPayload -> setMetadata
        equipmentEventRepository.save(event);

        // 5. Create Workout Session
        WorkoutSession workoutSession = new WorkoutSession(
                user, "Test Exercise", "EQ-1", "Chest", Instant.now(), Instant.now().plusSeconds(60));
        workoutSessionRepository.save(workoutSession);

        // 6. Create User Workout Split
        UserWorkoutSplit split = new UserWorkoutSplit(user, SplitMode.AUTO, "{}");
        userWorkoutSplitRepository.save(split);

        // 7. Create Push Token
        PushToken pushToken = new PushToken();
        pushToken.setUser(user);
        pushToken.setToken("test-push-token");
        pushToken.setPlatform("android");
        pushTokenRepository.save(pushToken);

        // --- PERFORM DELETION ---
        System.out.println("Attempting to delete user with ID: " + user.getId());
        userService.deleteUser(user);

        // --- VERIFY ---
        // Verify user is gone
        Optional<User> deletedUser = userRepository.findById(user.getId());
        assertTrue(deletedUser.isEmpty(), "User should be deleted");

        // Verify dependencies are gone
        assertTrue(equipmentSessionRepository
                .findByUserAndEquipmentAndStatus(user, equipment, EquipmentSessionStatus.ACTIVE).isEmpty());
        assertTrue(pushTokenRepository.findByUser(user).isEmpty());
    }
}
