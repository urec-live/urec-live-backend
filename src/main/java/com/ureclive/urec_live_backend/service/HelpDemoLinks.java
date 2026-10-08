package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.HelpDemoLink;
import com.ureclive.urec_live_backend.entity.Equipment;
import com.ureclive.urec_live_backend.entity.Exercise;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the "watch how it's done" demos a member sees after calling staff.
 *
 * <p>No exercise has a real how-to video yet, and the seeded exercise GIFs are dead
 * via.placeholder.com text images, so every demo plays placeholder media. The placeholder URLs are
 * settings, so real media can be swapped in without a code change. A real GIF set by an admin
 * still wins over the placeholder GIF.
 */
@Component
public class HelpDemoLinks {

    static final int MAX_LINKS = 3;
    /** A 5-second CC0 sample clip from MDN. */
    public static final String DEFAULT_PLACEHOLDER_VIDEO_URL = "https://mdn.github.io/shared-assets/videos/flower.mp4";
    /** An animated "Coming soon" loading bar. */
    public static final String DEFAULT_PLACEHOLDER_GIF_URL = "https://media.giphy.com/media/KzeZ3OXHoSDVZH9cmy/giphy.gif";

    private final String placeholderVideoUrl;
    private final String placeholderGifUrl;

    /** A blank setting switches that placeholder off, leaving the URL null. */
    public HelpDemoLinks(
            @Value("${app.help-requests.placeholder-video-url:" + DEFAULT_PLACEHOLDER_VIDEO_URL + "}") String placeholderVideoUrl,
            @Value("${app.help-requests.placeholder-gif-url:" + DEFAULT_PLACEHOLDER_GIF_URL + "}") String placeholderGifUrl) {
        this.placeholderVideoUrl = blankToNull(placeholderVideoUrl);
        this.placeholderGifUrl = blankToNull(placeholderGifUrl);
    }

    /**
     * The exercise the member is attempting if known; otherwise the machine's exercises (sorted,
     * at most {@value #MAX_LINKS}); otherwise one demo for the machine itself.
     */
    public List<HelpDemoLink> forRequest(Equipment equipment, Exercise exercise) {
        if (exercise != null) {
            return List.of(forExercise(exercise));
        }
        Set<Exercise> linked = equipment.getExercises();
        if (linked != null && !linked.isEmpty()) {
            return linked.stream()
                    .sorted(Comparator.comparing(Exercise::getName, String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_LINKS)
                    .map(this::forExercise)
                    .toList();
        }
        return List.of(demo(baseMachineName(equipment.getName()), null));
    }

    HelpDemoLink forExercise(Exercise exercise) {
        return demo(exercise.getName(), usableGifUrl(exercise.getGifUrl()));
    }

    /** The real GIF if there is one, else the placeholder GIF. Videos are always placeholders for now. */
    private HelpDemoLink demo(String name, String realGifUrl) {
        String gifUrl = realGifUrl != null ? realGifUrl : placeholderGifUrl;
        return new HelpDemoLink(name,
                gifUrl, realGifUrl == null && gifUrl != null,
                placeholderVideoUrl, placeholderVideoUrl != null);
    }

    /** The URL if it is a real http(s) link, or null for blank, malformed or placeholder-image URLs. */
    static String usableGifUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return null;
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return null;
            if (host.toLowerCase(Locale.ROOT).contains("placeholder")) return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
        return trimmed;
    }

    /** "Flat Bench Press 2" becomes "Flat Bench Press", so the demo isn't titled after machine number 2. */
    static String baseMachineName(String name) {
        String base = name.trim().replaceAll("\\s+\\d+$", "");
        return base.isEmpty() ? name.trim() : base;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
