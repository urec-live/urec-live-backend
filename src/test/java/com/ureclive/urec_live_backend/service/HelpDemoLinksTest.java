package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.HelpDemoLink;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HelpDemoLinksTest {

    private static final String VIDEO = "https://cdn.example.com/demos/placeholder.mp4";
    private static final String GIF = "https://cdn.example.com/demos/placeholder.gif";
    private static final String REAL_GIF = "https://media.example.com/bench-press.gif";
    private static final String SEEDED_GIF = "https://via.placeholder.com/200x200?text=Bench+Press";

    private final HelpDemoLinks demos = new HelpDemoLinks(VIDEO, GIF);

    private static Equipment machine(String name, Exercise... exercises) {
        Equipment equipment = new Equipment("BP001", name, "Available", null);
        for (Exercise exercise : exercises) equipment.addExercise(exercise);
        return equipment;
    }

    private static Exercise exercise(String name, String gifUrl) {
        return new Exercise(name, "Chest", gifUrl);
    }

    // ── Which exercises get demos ───────────────────────────────────────────

    @Test
    void theExerciseTheMemberIsDoingWinsOverTheMachinesList() {
        Exercise bench = exercise("Bench Press", REAL_GIF);
        Exercise incline = exercise("Incline Press", null);

        List<HelpDemoLink> links = demos.forRequest(machine("Flat Bench Press 1", bench, incline), incline);

        assertEquals(1, links.size());
        assertEquals("Incline Press", links.get(0).getExerciseName());
    }

    @Test
    void withoutAKnownExerciseEveryLinkedExerciseIsListedAlphabetically() {
        Equipment cable = machine("Cable Station 1",
                exercise("Tricep Pushdown", null), exercise("cable curl", null), exercise("Face Pull", null));

        List<HelpDemoLink> links = demos.forRequest(cable, null);

        assertEquals(List.of("cable curl", "Face Pull", "Tricep Pushdown"),
                links.stream().map(HelpDemoLink::getExerciseName).toList());
    }

    @Test
    void atMostThreeExercisesAreListed() {
        Equipment rack = machine("Rack 1", exercise("A", null), exercise("B", null), exercise("C", null),
                exercise("D", null), exercise("E", null));

        assertEquals(HelpDemoLinks.MAX_LINKS, demos.forRequest(rack, null).size());
    }

    @Test
    void aMachineWithNoExercisesGetsOnePlaceholderDemoForTheMachineItself() {
        List<HelpDemoLink> links = demos.forRequest(machine("Rowing Machine 3"), null);

        assertEquals(1, links.size());
        assertEquals("Rowing Machine", links.get(0).getExerciseName());
        assertPlaceholderVideo(links.get(0));
        assertPlaceholderGif(links.get(0));
    }

    // ── Placeholder media ───────────────────────────────────────────────────

    @Test
    void everyDemoPlaysThePlaceholderVideo() {
        Equipment cable = machine("Cable Station 1", exercise("Face Pull", REAL_GIF), exercise("Cable Curl", null));

        List<HelpDemoLink> links = demos.forRequest(cable, null);

        assertEquals(2, links.size());
        links.forEach(HelpDemoLinksTest::assertPlaceholderVideo);
    }

    @ParameterizedTest(name = "an exercise with GIF \"{0}\" gets the placeholder GIF")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", SEEDED_GIF, "not a url"})
    void exercisesWithoutARealGifGetThePlaceholderGif(String gifUrl) {
        assertPlaceholderGif(demos.forExercise(exercise("Bench Press", gifUrl)));
    }

    @Test
    void aRealGifWinsOverThePlaceholderButTheVideoIsStillAPlaceholder() {
        HelpDemoLink link = demos.forExercise(exercise("Bench Press", "  " + REAL_GIF + " "));

        assertEquals(REAL_GIF, link.getGifUrl());
        assertFalse(link.isGifPlaceholder());
        assertPlaceholderVideo(link);
    }

    @Test
    void blankSettingsSwitchThePlaceholdersOff() {
        HelpDemoLinks noPlaceholders = new HelpDemoLinks("  ", "");

        HelpDemoLink seeded = noPlaceholders.forExercise(exercise("Bench Press", SEEDED_GIF));
        assertNull(seeded.getGifUrl());
        assertFalse(seeded.isGifPlaceholder());
        assertNull(seeded.getVideoUrl());
        assertFalse(seeded.isVideoPlaceholder());

        // A real GIF still shows
        HelpDemoLink real = noPlaceholders.forExercise(exercise("Bench Press", REAL_GIF));
        assertEquals(REAL_GIF, real.getGifUrl());
        assertFalse(real.isGifPlaceholder());
    }

    @Test
    void placeholderSettingsAreTrimmed() {
        HelpDemoLink link = new HelpDemoLinks(" " + VIDEO + " ", "\t" + GIF + "\n")
                .forExercise(exercise("Bench Press", null));

        assertEquals(VIDEO, link.getVideoUrl());
        assertEquals(GIF, link.getGifUrl());
    }

    // ── Settings (the same @Value wiring Spring uses in the app) ────────────

    private final ApplicationContextRunner context = new ApplicationContextRunner().withBean(HelpDemoLinks.class);

    @Test
    void withNoSettingsTheBuiltInPlaceholdersAreUsed() {
        context.run(ctx -> {
            HelpDemoLink link = ctx.getBean(HelpDemoLinks.class).forExercise(exercise("Bench Press", SEEDED_GIF));

            assertEquals(HelpDemoLinks.DEFAULT_PLACEHOLDER_VIDEO_URL, link.getVideoUrl());
            assertEquals(HelpDemoLinks.DEFAULT_PLACEHOLDER_GIF_URL, link.getGifUrl());
            assertTrue(link.isVideoPlaceholder());
            assertTrue(link.isGifPlaceholder());
        });
    }

    @Test
    void theSettingsSwapInOtherMedia() {
        context.withPropertyValues(
                        "app.help-requests.placeholder-video-url=https://media.example.com/how-to.mp4",
                        "app.help-requests.placeholder-gif-url=")
                .run(ctx -> {
                    HelpDemoLink link = ctx.getBean(HelpDemoLinks.class).forExercise(exercise("Bench Press", null));

                    assertEquals("https://media.example.com/how-to.mp4", link.getVideoUrl());
                    assertNull(link.getGifUrl());
                    assertFalse(link.isGifPlaceholder());
                });
    }

    @Test
    void theBuiltInPlaceholdersAreHttpsMediaFiles() {
        assertTrue(HelpDemoLinks.DEFAULT_PLACEHOLDER_VIDEO_URL.startsWith("https://"));
        assertTrue(HelpDemoLinks.DEFAULT_PLACEHOLDER_VIDEO_URL.endsWith(".mp4"));
        assertTrue(HelpDemoLinks.DEFAULT_PLACEHOLDER_GIF_URL.startsWith("https://"));
        assertTrue(HelpDemoLinks.DEFAULT_PLACEHOLDER_GIF_URL.endsWith(".gif"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "\"{0}\" is not a usable GIF")
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            SEEDED_GIF,   // the seeded data
            "http://VIA.PLACEHOLDER.COM/100",
            "https://placeholder.example.com/x.gif",
            "ftp://example.com/x.gif",
            "javascript:alert(1)",
            "not a url",
            "/relative/path.gif"
    })
    void unusableGifUrlsAreDropped(String url) {
        assertNull(HelpDemoLinks.usableGifUrl(url));
    }

    @Test
    void baseMachineNameOnlyStripsATrailingNumber() {
        assertEquals("Flat Bench Press", HelpDemoLinks.baseMachineName("Flat Bench Press 12"));
        assertEquals("Smith Machine", HelpDemoLinks.baseMachineName("Smith Machine"));
        assertEquals("3D Trainer", HelpDemoLinks.baseMachineName("3D Trainer"));
        assertEquals("42", HelpDemoLinks.baseMachineName("42"));
    }

    private static void assertPlaceholderVideo(HelpDemoLink link) {
        assertEquals(VIDEO, link.getVideoUrl());
        assertTrue(link.isVideoPlaceholder());
    }

    private static void assertPlaceholderGif(HelpDemoLink link) {
        assertEquals(GIF, link.getGifUrl());
        assertTrue(link.isGifPlaceholder());
    }
}
