package com.ureclive.urec_live_backend.dto;

/**
 * "Watch how it's done" media for one exercise, shown to a member after they call staff. Until
 * real how-to media exist, both URLs point at placeholder media (the GIF only when no admin has
 * set a real one), and the {@code *Placeholder} flags let the app say a real demo is coming soon.
 * A URL is null only when its placeholder has been switched off in the settings.
 */
public class HelpDemoLink {

    private final String exerciseName;
    private final String gifUrl;
    private final boolean gifPlaceholder;
    private final String videoUrl;
    private final boolean videoPlaceholder;

    public HelpDemoLink(String exerciseName, String gifUrl, boolean gifPlaceholder,
                        String videoUrl, boolean videoPlaceholder) {
        this.exerciseName = exerciseName;
        this.gifUrl = gifUrl;
        this.gifPlaceholder = gifPlaceholder;
        this.videoUrl = videoUrl;
        this.videoPlaceholder = videoPlaceholder;
    }

    public String getExerciseName() { return exerciseName; }
    public String getGifUrl() { return gifUrl; }
    public boolean isGifPlaceholder() { return gifPlaceholder; }
    public String getVideoUrl() { return videoUrl; }
    public boolean isVideoPlaceholder() { return videoPlaceholder; }
}
