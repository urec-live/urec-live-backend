package com.ureclive.urec_live_backend;
import com.ureclive.urec_live_backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:map-demo-only;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test","map-demo"})
class MapDemoIntegrationTest {
    @Autowired MapDemoInitializer demo;
    @Autowired FloorPlanRepository floors;
    @Autowired EquipmentRepository machines;
    @Test void createsFourFloorsAnd160MachinesAndNeverOverwritesEdits() throws Exception {
        assertEquals(4,floors.count());assertEquals(160,machines.count());
        var floor=floors.findAll().getFirst();floor.setName("Staff edited name");floor.setFloorNumber(99);floors.save(floor);
        var machine=machines.findAll().getFirst();machine.setStatus("Out of Order");machines.save(machine);
        demo.run(new DefaultApplicationArguments());
        assertEquals(4,floors.count());assertEquals(160,machines.count());
        assertEquals("Staff edited name",floors.findById(floor.getId()).orElseThrow().getName());
        assertEquals("Out of Order",machines.findById(machine.getId()).orElseThrow().getStatus());
    }
}
