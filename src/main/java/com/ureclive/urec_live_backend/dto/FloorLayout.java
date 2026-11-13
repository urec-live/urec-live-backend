package com.ureclive.urec_live_backend.dto;

import java.util.List;

/** Coordinates use the containing floor's width/height, never screen pixels. */
public record FloorLayout(Integer version, List<Area> areas) {
    public static FloorLayout empty() { return new FloorLayout(1, List.of()); }
    public record Point(Double x, Double y) {}
    public record Area(String id, String name, String kind, String shape,
                       Double x, Double y, Double width, Double height, List<Point> points) {}
}
