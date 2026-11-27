package com.ureclive.urec_live_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ureclive.urec_live_backend.dto.FloorLayout;
import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;
import com.ureclive.urec_live_backend.service.FloorLayoutValidator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in local demo ONLY. Never modifies existing maps or machine records. */
@Component
@Profile("map-demo")
@Order(200)
public class MapDemoInitializer implements ApplicationRunner {
    private final Environment env;
    private final ObjectMapper mapper;
    private final FloorPlanRepository floors;
    private final EquipmentRepository machines;
    private final ExerciseRepository exercises;
    private final GymRepository gyms;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final FloorLayoutValidator validator;
    public MapDemoInitializer(Environment env, ObjectMapper mapper, FloorPlanRepository floors,
            EquipmentRepository machines, ExerciseRepository exercises, GymRepository gyms,
            UserRepository users, RoleRepository roles, PasswordEncoder encoder, FloorLayoutValidator validator) {
        this.env=env; this.mapper=mapper; this.floors=floors; this.machines=machines;
        this.exercises=exercises; this.gyms=gyms; this.users=users; this.roles=roles;
        this.encoder=encoder; this.validator=validator;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        String url = env.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:h2:mem:") && !url.startsWith("jdbc:h2:file:") && !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1)(:.*|/.*)"))
            throw new IllegalStateException("map-demo requires an isolated local database");
        var gym = gyms.findByName("UREC").orElseThrow();
        var existing = floors.findAll().stream().filter(f -> gym.getId().equals(f.getGymId())).toList();
        if (existing.stream().anyMatch(f -> !f.isIllustrative()))
            throw new IllegalStateException("map-demo refuses to modify a gym with existing real floor plans");
        var definitions = mapper.readTree(new ClassPathResource("demo/university-gym.json").getInputStream());
        for (var def : definitions) {
            // Repeated starts preserve staff edits, removed machines and status changes.
            if (!existing.isEmpty()) break;
            var floor = new FloorPlan();
            floor.setGymId(gym.getId()); floor.setName(def.get("name").asText());
            floor.setFloorNumber(def.get("floorNumber").asInt());
            floor.setWidth(def.get("width").asInt()); floor.setHeight(def.get("height").asInt());
            floor.setIllustrative(true);
            floor.setLayout(mapper.treeToValue(def.get("layout"), FloorLayout.class));
            validator.validate(floor.getLayout(), floor.getWidth(), floor.getHeight());
            floors.save(floor);
            for (var m : def.get("machines")) {
                if (machines.findByCode(m.get("code").asText()).isPresent()) continue;
                var machine = new Equipment(m.get("code").asText(), m.get("name").asText(), m.get("status").asText(), null);
                machine.setGymId(gym.getId()); machine.setFloorPlan(floor);
                machine.setFloorX(m.get("x").asDouble()); machine.setFloorY(m.get("y").asDouble());
                machine.setFloorLabel(m.get("name").asText());
                var exercise = exercises.findByNameIgnoreCase(m.get("exercise").asText())
                        .orElseGet(() -> exercises.save(new Exercise(m.get("exercise").asText(),
                                def.get("floorNumber").asInt() == 3 ? "Cardio" :
                                m.get("name").asText().contains("Rack") || m.get("name").asText().contains("Leg Press") ? "Legs" :
                                m.get("name").asText().contains("Row") || m.get("name").asText().contains("Pulldown") ? "Back" : "Chest", null)));
                machine.getExercises().add(exercise);
                machines.save(machine);
            }
        }
        for (String name : new String[]{"mapmember", "mapadmin"}) {
            if (users.findByUsername(name).isPresent()) continue;
            var user = new User(name, name + "@example.com", encoder.encode("MapDemo2026!"));
            user.setConnectedGymId(gym.getId());
            user.addRole(roles.findByName("ROLE_USER").orElseGet(() -> roles.save(new Role("ROLE_USER"))));
            if (name.equals("mapadmin"))
                user.addRole(roles.findByName("ROLE_ADMIN").orElseGet(() -> roles.save(new Role("ROLE_ADMIN"))));
            users.save(user);
        }
    }
}
