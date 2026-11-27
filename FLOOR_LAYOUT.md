# Floor layout API

Apply `migrations/20261112_floor_layout.sql` before deploying the updated clients. It adds nullable `floor_plans.layout` JSONB and `illustrative` (false by default). Older clients can ignore both response fields; existing floors serialize an empty version-1 layout. Roll clients back before removing columns, and retain a backup of layout data.

`GET /api/machines/floor-plans` and existing admin floor endpoints include:

```json
{
  "layout": {
    "version": 1,
    "areas": [
      { "id": "pool", "name": "Indoor pool", "kind": "pool", "shape": "rect", "x": 50, "y": 80, "width": 620, "height": 600 },
      { "id": "turf", "name": "Turf", "kind": "turf", "shape": "polygon", "points": [{"x":900,"y":80},{"x":1200,"y":80},{"x":1100,"y":300}] }
    ]
  },
  "illustrative": false
}
```

`PUT /api/admin/floor-plans/{id}/layout` replaces the complete layout with the JSON value of `layout` above. It returns a full FloorPlanResponse and requires ADMIN or ROLE_ADMIN within the existing UREC admin scope. Bad names, duplicate IDs, unsupported kinds/version, non-finite/out-of-bounds coordinates and degenerate polygons return 400; inaccessible/inactive floors return 404. Geometry uses the containing floor's existing width/height units. Areas are an ordered drawing stack; later containing areas win when locating equipment, such as a void inside a track. Voids are not searchable destinations.

Kinds: strength, cardio, pool, court, track, studio, turf, room, amenity, stairs, elevator, entrance, void. Limits: 500 areas/floor; 3–64 vertices/polygon; 100-character names; IDs match `[A-Za-z0-9_-]{1,80}`. Equipment positions remain on Equipment. Bulk updates are transactional, check floor bounds and same-gym ownership; no second area-assignment field is stored. A floor cannot be resized to clip its layout or positioned equipment.

Machine WebSocket payloads and gym-scoped destinations are unchanged. Layout edits are refreshed on member-screen focus/manual refresh. Facility occupancy, reservations and live layout broadcasts are not implemented.

For the explicitly enabled four-floor illustrative seed and local demo accounts, see `app/EQUIPMENT_MAP_TESTING.md` in the neighboring app repository. It runs only with `map-demo` and a local database; it preserves existing seeded records. Ground uses floorNumber 0; selection is data-driven and does not assume four floors in other gyms.
