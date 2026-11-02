package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineDTOTest {

    private static Equipment machineWith(String... namesAndGroups) {
        Equipment equipment = new Equipment("CC001", "Cable Station", "Available", null);
        for (int i = 0; i < namesAndGroups.length; i += 2) {
            equipment.addExercise(new Exercise(namesAndGroups[i], namesAndGroups[i + 1], null));
        }
        return equipment;
    }

    @Test
    void muscleGroupsAreTheDistinctSortedGroupsOfEveryExercise() {
        Equipment cable = machineWith(
                "Tricep Pushdown", "Triceps",
                "Cable Crossover", "Chest",
                "Cable Fly", "Chest");

        MachineDTO dto = new MachineDTO(cable, "Cable Crossover");

        assertEquals(List.of("Chest", "Triceps"), dto.getMuscleGroups());
        assertEquals(List.of("Cable Crossover", "Cable Fly", "Tricep Pushdown"), dto.getExerciseNames());
        // The primary exercise stays the one the caller picked
        assertEquals("Cable Crossover", dto.getExercise());
    }

    @Test
    void blankAndMissingGroupsAreLeftOut() {
        Equipment equipment = machineWith(
                "Mystery Move", null,
                "Blank Move", "  ",
                "Back Row", "Back");

        MachineDTO dto = new MachineDTO(equipment, "Back Row");

        assertEquals(List.of("Back"), dto.getMuscleGroups());
        assertEquals(List.of("Back Row", "Blank Move", "Mystery Move"), dto.getExerciseNames());
    }

    @Test
    void aMachineWithNoExercisesHasEmptyListsNotNull() {
        Equipment bare = new Equipment("XX001", "Bare Machine", "Available", null);

        assertEquals(List.of(), new MachineDTO(bare, "Unknown").getMuscleGroups());
        assertEquals(List.of(), new MachineDTO(bare, "Unknown").getExerciseNames());
        assertEquals(List.of(), new MachineDTO().getMuscleGroups());
        assertEquals(List.of(), new MachineDTO().getExerciseNames());
    }
}
