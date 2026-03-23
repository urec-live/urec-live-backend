package com.ureclive.urec_live_backend.dto;

import com.ureclive.urec_live_backend.entity.WorkoutSession;
import com.ureclive.urec_live_backend.entity.WorkoutSet;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class SessionResponse {

    private Long id;
    private String exerciseName;
    private String machineName;
    private String machineCode;
    private String muscleGroup;
    private Instant startedAt;
    private Instant endedAt;
    private Integer durationSeconds;
    private String notes;
    private List<SetDetailDto> setDetails;

    public SessionResponse() {}

    public static class SetDetailDto {
        private Integer setNumber;
        private Integer reps;
        private Double weightLbs;

        public SetDetailDto() {}

        public SetDetailDto(Integer setNumber, Integer reps, Double weightLbs) {
            this.setNumber = setNumber;
            this.reps = reps;
            this.weightLbs = weightLbs;
        }

        public Integer getSetNumber() { return setNumber; }
        public void setSetNumber(Integer setNumber) { this.setNumber = setNumber; }

        public Integer getReps() { return reps; }
        public void setReps(Integer reps) { this.reps = reps; }

        public Double getWeightLbs() { return weightLbs; }
        public void setWeightLbs(Double weightLbs) { this.weightLbs = weightLbs; }
    }

    public static SessionResponse from(WorkoutSession session) {
        SessionResponse dto = new SessionResponse();
        dto.id = session.getId();
        dto.exerciseName = session.getExercise() != null ? session.getExercise().getName() : null;
        dto.machineName = session.getMachine() != null ? session.getMachine().getName() : null;
        dto.machineCode = session.getMachine() != null ? session.getMachine().getCode() : null;
        dto.muscleGroup = session.getMuscleGroup();
        dto.startedAt = session.getStartedAt();
        dto.endedAt = session.getEndedAt();
        dto.durationSeconds = session.getDurationSeconds();
        dto.notes = session.getNotes();

        if (session.getSets() != null && !session.getSets().isEmpty()) {
            dto.setDetails = session.getSets().stream()
                    .map(s -> new SetDetailDto(s.getSetNumber(), s.getReps(), s.getWeightLbs()))
                    .collect(Collectors.toList());
        }

        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }

    public String getMachineName() { return machineName; }
    public void setMachineName(String machineName) { this.machineName = machineName; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public String getMuscleGroup() { return muscleGroup; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<SetDetailDto> getSetDetails() { return setDetails; }
    public void setSetDetails(List<SetDetailDto> setDetails) { this.setDetails = setDetails; }
}
