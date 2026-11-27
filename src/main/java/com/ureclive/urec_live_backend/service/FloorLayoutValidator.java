package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.FloorLayout;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashSet;
import java.util.Set;

@Component
public class FloorLayoutValidator {
    public static final Set<String> KINDS = Set.of("strength", "cardio", "pool", "court", "track", "studio", "turf", "room", "amenity", "stairs", "elevator", "entrance", "void");
    public void validate(FloorLayout layout, int width, int height) {
        check(width > 0 && height > 0, "Floor dimensions must be positive");
        check(layout != null && Integer.valueOf(1).equals(layout.version()) && layout.areas() != null,
                "Expected layout version 1 and areas");
        check(layout.areas().size() <= 500, "At most 500 areas per floor");
        var ids = new HashSet<String>();
        for (var area : layout.areas()) {
            check(area != null, "Area is required");
            check(area.id() != null && area.id().matches("[A-Za-z0-9_-]{1,80}") && ids.add(area.id()), "Area IDs must be unique and valid");
            check(area.name() != null && !area.name().isBlank() && area.name().length() <= 100, "Area name is required (100 characters maximum)");
            check(area.kind() != null && KINDS.contains(area.kind()), "Unknown area kind");
            if ("rect".equals(area.shape())) {
                point(area.x(), area.y(), width, height);
                check(area.width() != null && Double.isFinite(area.width()) && area.width() > 0 && area.height() != null && Double.isFinite(area.height()) && area.height() > 0, "Area dimensions must be positive");
                point(area.x() + area.width(), area.y() + area.height(), width, height);
            } else {
                check("polygon".equals(area.shape()) && area.points() != null && area.points().size() >= 3 && area.points().size() <= 64, "Polygon needs 3 to 64 points");
                double sum = 0;
                for (int i = 0; i < area.points().size(); i++) {
                    var p = area.points().get(i);
                    var q = area.points().get((i + 1) % area.points().size());
                    check(p != null && q != null, "Polygon points are required");
                    point(p.x(), p.y(), width, height);
                    point(q.x(), q.y(), width, height);
                    sum += p.x() * q.y() - q.x() * p.y();
                }
                check(Math.abs(sum) > 1, "Polygon must enclose an area");
            }
        }
    }
    public void point(Double x, Double y, int width, int height) {
        check(x != null && y != null && Double.isFinite(x) && Double.isFinite(y) && x >= 0 && y >= 0 && x <= width && y <= height, "Coordinates must lie inside the floor");
    }
    public void check(boolean valid, String message) {
        if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
