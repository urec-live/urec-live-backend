package com.ureclive.urec_live_backend.service;

import com.ureclive.urec_live_backend.dto.SendChatMessageRequest;
import com.ureclive.urec_live_backend.repository.WorkoutSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class WorkoutFriendsService {
    private static final Duration NUDGE_COOLDOWN = Duration.ofHours(24);
    private final JdbcTemplate db;
    private final WorkoutSessionRepository workouts;
    private final ChatMessageService chat;
    private final WorkoutNudgePush push;

    public WorkoutFriendsService(JdbcTemplate db, WorkoutSessionRepository workouts,
                                 ChatMessageService chat, WorkoutNudgePush push) {
        this.db = db;
        this.workouts = workouts;
        this.chat = chat;
        this.push = push;
    }

    public record Friend(String username, String status, Integer days, Integer target, Instant canNudgeAt) {}
    private record Pair(long me, long other) {
        long first() { return Math.min(me, other); }
        long second() { return Math.max(me, other); }
    }
    private record Row(long id, String username, boolean accepted, boolean incoming,
                       boolean sharing, int target, String timeZone, Instant nudgedAt) {}

    public List<Friend> list(String username) {
        long me = userId(username);
        Instant now = Instant.now();
        List<Row> rows = db.query("""
                SELECT u.id, u.username, f.accepted, f.requester_id <> ? AS incoming,
                       s.sharing, s.weekly_target, s.time_zone,
                       CASE WHEN f.first_id = ? THEN f.first_nudged_at ELSE f.second_nudged_at END AS nudged_at
                FROM workout_friendships f
                JOIN users u ON u.id = CASE WHEN f.first_id = ? THEN f.second_id ELSE f.first_id END
                LEFT JOIN workout_friend_settings s ON s.user_id = u.id
                WHERE (f.first_id = ? OR f.second_id = ?) AND u.enabled = true
                ORDER BY u.username
                """, (rs, index) -> {
            Timestamp nudged = rs.getTimestamp("nudged_at");
            return new Row(rs.getLong("id"), rs.getString("username"), rs.getBoolean("accepted"),
                    rs.getBoolean("incoming"), rs.getBoolean("sharing"), rs.getInt("weekly_target"),
                    rs.getString("time_zone"), nudged == null ? null : nudged.toInstant());
        }, me, me, me, me, me);
        return rows.stream().map(row -> {
            if (!row.accepted()) {
                return new Friend(row.username(), row.incoming() ? "INCOMING" : "OUTGOING", null, null, null);
            }
            Instant canNudge = row.nudgedAt() == null ? null : row.nudgedAt().plus(NUDGE_COOLDOWN);
            if (!row.sharing()) return new Friend(row.username(), "FRIEND", null, null, canNudge);
            ZoneId zone = ZoneId.of(row.timeZone());
            LocalDate monday = now.atZone(zone).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Instant start = monday.atStartOfDay(zone).toInstant();
            int days = (int) workouts.findCompletedTimes(row.id(), start, now).stream()
                    .map(time -> time.atZone(zone).toLocalDate()).distinct().count();
            return new Friend(row.username(), "FRIEND", days, row.target(), canNudge);
        }).toList();
    }

    public boolean isSharing(String username) {
        return db.queryForList("SELECT sharing FROM workout_friend_settings WHERE user_id = ?",
                Boolean.class, userId(username)).stream().findFirst().orElse(false);
    }

    public void settings(String username, boolean sharing, int target, String timeZone) {
        db.update("""
                INSERT INTO workout_friend_settings (user_id, sharing, weekly_target, time_zone)
                VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE
                SET sharing = EXCLUDED.sharing, weekly_target = EXCLUDED.weekly_target, time_zone = EXCLUDED.time_zone
                """, userId(username), sharing, target, validZone(timeZone));
    }

    public void updateTarget(String username, int target, String timeZone) {
        db.update("UPDATE workout_friend_settings SET weekly_target = ?, time_zone = ? WHERE user_id = ? AND sharing",
                target, validZone(timeZone), userId(username));
    }

    public void registerDevice(String username, String token) {
        db.update("""
                INSERT INTO workout_nudge_devices (token, user_id, active_until) VALUES (?, ?, ?)
                ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, active_until = EXCLUDED.active_until
                """, token, userId(username), Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
    }

    public void removeDevice(String username, String token) {
        db.update("DELETE FROM workout_nudge_devices WHERE user_id = ? AND token = ?", userId(username), token);
    }

    public void request(String username, String recipient) {
        Pair pair = pair(username, recipient);
        db.update("""
                INSERT INTO workout_friendships (first_id, second_id, requester_id, accepted)
                VALUES (?, ?, ?, false) ON CONFLICT (first_id, second_id) DO NOTHING
                """, pair.first(), pair.second(), pair.me());
    }

    public void accept(String username, String requester) {
        Pair pair = pair(username, requester);
        if (db.update("""
                UPDATE workout_friendships SET accepted = true
                WHERE first_id = ? AND second_id = ? AND requester_id = ?
                """, pair.first(), pair.second(), pair.other()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No incoming friend request");
        }
    }

    public void remove(String username, String other) {
        Pair pair = pair(username, other);
        db.update("DELETE FROM workout_friendships WHERE first_id = ? AND second_id = ?",
                pair.first(), pair.second());
    }

    @Transactional
    public void nudge(String username, String recipient, String content) {
        Pair pair = pair(username, recipient);
        Instant now = Instant.now();
        String column = pair.me() == pair.first() ? "first_nudged_at" : "second_nudged_at";
        int updated = db.update("""
                UPDATE workout_friendships SET %1$s = ?
                WHERE first_id = ? AND second_id = ? AND accepted = true AND (%1$s IS NULL OR %1$s <= ?)
                """.formatted(column), Timestamp.from(now), pair.first(), pair.second(),
                Timestamp.from(now.minus(NUDGE_COOLDOWN)));
        if (updated == 0) {
            boolean accepted = db.queryForList("""
                    SELECT accepted FROM workout_friendships WHERE first_id = ? AND second_id = ?
                    """, Boolean.class, pair.first(), pair.second()).stream().findFirst().orElse(false);
            throw new ResponseStatusException(accepted ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN);
        }

        var message = new SendChatMessageRequest();
        message.setRecipient(recipient);
        message.setContent(content);
        chat.sendDirect(username, message);
        var tokens = db.queryForList("SELECT token FROM workout_nudge_devices WHERE user_id = ? AND active_until > ?",
                String.class, pair.other(), Timestamp.from(now));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                push.send(tokens, username, recipient);
            }
        });
    }

    private Pair pair(String me, String other) {
        if (me.equals(other)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another member");
        return new Pair(userId(me), userId(other));
    }

    private long userId(String username) {
        return db.queryForList("SELECT id FROM users WHERE username = ? AND enabled = true", Long.class, username)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    private String validZone(String timeZone) {
        try {
            return ZoneId.of(timeZone).getId();
        } catch (DateTimeException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time zone");
        }
    }
}
