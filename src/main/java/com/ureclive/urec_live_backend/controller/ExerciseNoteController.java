package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.service.ExerciseNoteService;
import com.ureclive.urec_live_backend.service.ExerciseNoteService.NoteDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exercises/{exerciseId}/notes")
public class ExerciseNoteController {
    private final ExerciseNoteService notes;
    public ExerciseNoteController(ExerciseNoteService notes) { this.notes = notes; }
    public record NoteInput(@NotNull @Size(max = 2000) String note) {}

    @GetMapping
    public NoteDto get(@PathVariable Long exerciseId) { return notes.get(exerciseId); }

    @PutMapping
    public NoteDto save(@PathVariable Long exerciseId, @Valid @RequestBody NoteInput input) {
        return notes.save(exerciseId, input.note());
    }
}
