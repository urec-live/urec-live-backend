package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.EquipmentSession;
import com.ureclive.urec_live_backend.entity.GymLocation;
import com.ureclive.urec_live_backend.entity.User;
import com.ureclive.urec_live_backend.repository.EquipmentRepository;
import com.ureclive.urec_live_backend.repository.EquipmentSessionRepository;
import com.ureclive.urec_live_backend.repository.GymLocationRepository;
import com.ureclive.urec_live_backend.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataMigrationService {

    private final GymLocationRepository gymLocationRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final EquipmentSessionRepository sessionRepository;
    private final com.ureclive.urec_live_backend.repository.RoleRepository roleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public DataMigrationService(GymLocationRepository gymLocationRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            EquipmentSessionRepository sessionRepository,
            com.ureclive.urec_live_backend.repository.RoleRepository roleRepository,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.gymLocationRepository = gymLocationRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateData() {
        // 0. Bootstrap Roles and Admin User
        ensureRolesAndAdmin();

        if (gymLocationRepository.count() == 0) {
            System.out.println("Starting initial data migration for Multi-Site Support...");

            // 1. Create Default Gym
            GymLocation mainCampus = new GymLocation(
                    "UREC Main Campus",
                    "MAIN",
                    "America/New_York");
            mainCampus = gymLocationRepository.save(mainCampus);
            System.out.println("Created default gym: " + mainCampus.getName());

            // 2. Assign to all Equipment
            List<Equipment> equipmentList = equipmentRepository.findAll();
            for (Equipment e : equipmentList) {
                if (e.getLocation() == null) {
                    e.setLocation(mainCampus);
                }
            }
            equipmentRepository.saveAll(equipmentList);
            System.out.println("Migrated " + equipmentList.size() + " equipment items.");

            // 3. Assign to all Users
            List<User> users = userRepository.findAll();
            for (User u : users) {
                if (u.getHomeGym() == null) {
                    u.setHomeGym(mainCampus);
                }
            }
            userRepository.saveAll(users);
            System.out.println("Migrated " + users.size() + " users.");

            // 4. Assign to all Sessions
            List<EquipmentSession> sessions = sessionRepository.findAll();
            for (EquipmentSession s : sessions) {
                if (s.getLocation() == null) {
                    s.setLocation(mainCampus);
                }
            }
            sessionRepository.saveAll(sessions);
            System.out.println("Migrated " + sessions.size() + " sessions.");

            System.out.println("Migration complete.");
        }
    }

    private void ensureRolesAndAdmin() {
        // Ensure Roles Exist
        com.ureclive.urec_live_backend.entity.Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new com.ureclive.urec_live_backend.entity.Role("ROLE_ADMIN")));
        roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new com.ureclive.urec_live_backend.entity.Role("ROLE_USER")));

        // Ensure Admin User Exists
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User("admin", "admin@urec.live", passwordEncoder.encode("admin123"));
            admin.addRole(adminRole);
            admin.setEnabled(true);

            // Assign to default gym if exists (best effort for now, simplified)
            gymLocationRepository.findByCode("MAIN").ifPresent(admin::setHomeGym);

            userRepository.save(admin);
            System.out.println("Bootstrapped 'admin' user with ROLE_ADMIN.");
        }
    }
}
