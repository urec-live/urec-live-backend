package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.entity.*;
import com.ureclive.urec_live_backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Map;

class FloorLayoutIntegrationTest extends ApiIntegrationTestSupport {
    private final java.util.List<Long> createdFloors = new java.util.ArrayList<>();
    @org.junit.jupiter.api.AfterEach void cleanUpFloors() {
        createdFloors.forEach(floors::deleteById);
    }
    @Autowired FloorPlanRepository floors;
    @Autowired GymRepository gyms;
    @Autowired UserRepository users;
    FloorPlan floor(String name) {
        var f=new FloorPlan();f.setName(name);f.setGymId(gyms.findByName("UREC").orElseThrow().getId());f=floors.save(f);createdFloors.add(f.getId());return f;
    }
    Map<String,Object> rectangle(String id,int x) {return fields("id",id,"name","Pool","kind","pool","shape","rect","x",x,"y",40,"width",200,"height",150);}
    @Test void persistsTypedLayoutAndPreservesLegacyFields() throws Exception {
        var admin=tokenFor("layout-admin","ROLE_ADMIN");var f=floor("Layout test");
        mvc.perform(json(put("/api/admin/floor-plans/"+f.getId()+"/layout"),fields("version",1,"areas",List.of(rectangle("pool",20)))).header("Authorization",bearer(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.layout.areas[0].name").value("Pool"))
            .andExpect(jsonPath("$.illustrative").value(false)).andExpect(jsonPath("$.width").value(800)).andExpect(jsonPath("$.equipment").isArray());
        assertEquals("Pool",floors.findById(f.getId()).orElseThrow().getLayout().areas().getFirst().name());
        getAs(admin,"/api/machines/floor-plans").andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+f.getId()+")].layout.areas[0].id").value("pool"));
    }
    @Test void rejectsInvalidLayoutsAndMemberWrites() throws Exception {
        var admin=tokenFor("layout-validation-admin","ADMIN");var member=tokenFor("layout-member","ROLE_USER");var f=floor("Validation floor");
        for(var body:List.of(fields("version",2,"areas",List.of()),fields("version",1,"areas",List.of(rectangle("same",20),rectangle("same",300))),fields("version",1,"areas",List.of(rectangle("outside",750))),fields("version",1,"areas",List.of(fields("id","bad","name","Bad","kind","room","shape","polygon","points",List.of(fields("x",1,"y",1),fields("x",2,"y",2),fields("x",3,"y",3))))))) {
            mvc.perform(json(put("/api/admin/floor-plans/"+f.getId()+"/layout"),body).header("Authorization",bearer(admin))).andExpect(status().isBadRequest());
        }
        mvc.perform(json(put("/api/admin/floor-plans/"+f.getId()+"/layout"),fields("version",1,"areas",List.of())).header("Authorization",bearer(member))).andExpect(status().isForbidden());
    }
    @Test void placementIsAtomicBoundedAndGymScoped() throws Exception {
        var admin=tokenFor("layout-scope-admin","ROLE_ADMIN");var f=floor("Placement floor");
        var first=machine("MAP-FIRST","Map first",false);var second=machine("MAP-SECOND","Map second",false);
        mvc.perform(json(put("/api/admin/floor-plans/"+f.getId()+"/equipment"),List.of(fields("equipmentId",first.getId(),"floorX",30,"floorY",40),fields("equipmentId",second.getId(),"floorX",9999,"floorY",40))).header("Authorization",bearer(admin))).andExpect(status().isBadRequest());
        assertNull(equipmentRepository.findById(first.getId()).orElseThrow().getFloorPlan());
        var other=gyms.findByName("Other map gym").orElseGet(()->{var g=new Gym();g.setName("Other map gym");return gyms.save(g);});
        f.setGymId(other.getId());floors.save(f);
        mvc.perform(json(put("/api/admin/equipment/"+first.getId()+"/position"),fields("floorPlanId",f.getId(),"floorX",30,"floorY",40)).header("Authorization",bearer(admin))).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/admin/floor-plans/"+f.getId()+"/layout"),fields("version",1,"areas",List.of())).header("Authorization",bearer(admin))).andExpect(status().isNotFound());
        getAs(admin,"/api/machines/floor-plans").andExpect(jsonPath("$[?(@.id == "+f.getId()+")]").isEmpty());
    }
}
