package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.entity.ExerciseNote;
import com.ureclive.urec_live_backend.repository.ExerciseNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExerciseNoteService {
    private final ExerciseNoteRepository notes;
    private final ExerciseCatalogService catalog;
    private final GymAccessService access;

    public ExerciseNoteService(ExerciseNoteRepository notes, ExerciseCatalogService catalog, GymAccessService access) {
        this.notes = notes;
        this.catalog = catalog;
        this.access = access;
    }

    public record NoteDto(String note) {}

    @Transactional(readOnly = true)
    public NoteDto get(Long exerciseId) {
        catalog.get(exerciseId); // Same visibility boundary as exercise details.
        return new NoteDto(notes.findByUserIdAndExerciseId(access.user().getId(), exerciseId)
                .map(ExerciseNote::getNote).orElse(""));
    }

    @Transactional
    public NoteDto save(Long exerciseId, String text) {
        catalog.get(exerciseId);
        Long userId = access.user().getId();
        var note = notes.findByUserIdAndExerciseId(userId, exerciseId).orElseGet(() -> {
            var value = new ExerciseNote();
            value.setUserId(userId);
            value.setExerciseId(exerciseId);
            return value;
        });
        note.setNote(text.trim());
        return new NoteDto(notes.save(note).getNote());
    }
}
