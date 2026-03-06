package com.kombat.kombatbackend.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kombat.kombatbackend.dto.GameStatusResponse;
import com.kombat.kombatbackend.engine.gamestate.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomPersistenceStore {
    private static final Logger log = LoggerFactory.getLogger(RoomPersistenceStore.class);
    private static final String ROOM_PREFIX = "kombat:room:";
    private static final String HISTORY_PREFIX = "kombat:history:";
    private static final String SEED_PREFIX = "kombat:seed:";
    private static final String SNAPSHOT_PREFIX = "kombat:snapshot:";

    public static class SessionEvent {
        private String eventType;
        private GameMode mode;
        private WsMessages.PlayerActionRequest playerAction;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public GameMode getMode() {
            return mode;
        }

        public void setMode(GameMode mode) {
            this.mode = mode;
        }

        public WsMessages.PlayerActionRequest getPlayerAction() {
            return playerAction;
        }

        public void setPlayerAction(WsMessages.PlayerActionRequest playerAction) {
            this.playerAction = playerAction;
        }
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Map<String, String> roomCache = new ConcurrentHashMap<>();
    private final Map<String, String> seedCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> historyCache = new ConcurrentHashMap<>();
    private final Map<String, String> snapshotCache = new ConcurrentHashMap<>();
    private volatile boolean useInMemoryFallback;

    public RoomPersistenceStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper().findAndRegisterModules();
    }

    public Optional<RoomState> getRoom(String roomId) {
        String raw = getValue(roomKey(roomId), roomCache);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return readValue(raw, RoomState.class);
    }

    public void saveRoom(RoomState room) {
        setValue(roomKey(room.getRoomId()), writeValue(room), roomCache);
    }

    public void deleteRoom(String roomId) {
        deleteValue(roomKey(roomId), roomCache);
    }

    public Optional<Long> getSeed(String roomId) {
        String raw = getValue(seedKey(roomId), seedCache);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public void saveSeed(String roomId, long seed) {
        setValue(seedKey(roomId), Long.toString(seed), seedCache);
    }

    public void deleteSeed(String roomId) {
        deleteValue(seedKey(roomId), seedCache);
    }

    public void appendEvent(String roomId, SessionEvent event) {
        String key = historyKey(roomId);
        String payload = writeValue(event);
        if (useInMemoryFallback) {
            historyCache.computeIfAbsent(key, ignored -> new ArrayList<>()).add(payload);
            return;
        }
        try {
            redis.opsForList().rightPush(key, payload);
        } catch (DataAccessException ex) {
            switchToInMemoryFallback(ex);
            historyCache.computeIfAbsent(key, ignored -> new ArrayList<>()).add(payload);
        }
    }

    public List<SessionEvent> getHistory(String roomId) {
        List<String> raw;
        if (useInMemoryFallback) {
            raw = historyCache.get(historyKey(roomId));
        } else {
            String key = historyKey(roomId);
            try {
                Long size = redis.opsForList().size(key);
                if (size == null || size <= 0) {
                    return List.of();
                }
                raw = redis.opsForList().range(key, 0, size - 1);
            } catch (DataAccessException ex) {
                switchToInMemoryFallback(ex);
                raw = historyCache.get(key);
            }
        }

        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<SessionEvent> out = new ArrayList<>(raw.size());
        for (String value : raw) {
            readValue(value, SessionEvent.class).ifPresent(out::add);
        }
        return out;
    }

    public void clearHistory(String roomId) {
        deleteValue(historyKey(roomId), historyCache);
    }

    public void saveSnapshot(String roomId, GameStatusResponse snapshot) {
        setValue(snapshotKey(roomId), writeValue(snapshot), snapshotCache);
    }

    public Optional<GameStatusResponse> getSnapshot(String roomId) {
        String raw = getValue(snapshotKey(roomId), snapshotCache);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return readValue(raw, GameStatusResponse.class);
    }

    public void deleteSnapshot(String roomId) {
        deleteValue(snapshotKey(roomId), snapshotCache);
    }

    public void deleteAllRoomArtifacts(String roomId) {
        String roomKey = roomKey(roomId);
        String historyKey = historyKey(roomId);
        String seedKey = seedKey(roomId);
        String snapshotKey = snapshotKey(roomId);

        if (useInMemoryFallback) {
            roomCache.remove(roomKey);
            historyCache.remove(historyKey);
            seedCache.remove(seedKey);
            snapshotCache.remove(snapshotKey);
            return;
        }

        try {
            redis.delete(List.of(roomKey, historyKey, seedKey, snapshotKey));
        } catch (DataAccessException ex) {
            switchToInMemoryFallback(ex);
            roomCache.remove(roomKey);
            historyCache.remove(historyKey);
            seedCache.remove(seedKey);
            snapshotCache.remove(snapshotKey);
        }
    }

    private String roomKey(String roomId) {
        return ROOM_PREFIX + normalizeRoomId(roomId);
    }

    private String historyKey(String roomId) {
        return HISTORY_PREFIX + normalizeRoomId(roomId);
    }

    private String seedKey(String roomId) {
        return SEED_PREFIX + normalizeRoomId(roomId);
    }

    private String snapshotKey(String roomId) {
        return SNAPSHOT_PREFIX + normalizeRoomId(roomId);
    }

    private static String normalizeRoomId(String roomId) {
        if (roomId == null) {
            return "";
        }
        return roomId.trim().toUpperCase();
    }

    private String writeValue(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Serialize failed", ex);
        }
    }

    private <T> Optional<T> readValue(String raw, Class<T> type) {
        try {
            return Optional.of(mapper.readValue(raw, type));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private <T> String getValue(String key, Map<String, T> fallbackMap) {
        if (useInMemoryFallback) {
            return toStoredString(fallbackMap.get(key));
        }

        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            switchToInMemoryFallback(ex);
            return toStoredString(fallbackMap.get(key));
        }
    }

    private void setValue(String key, String value, Map<String, String> fallbackMap) {
        if (useInMemoryFallback) {
            fallbackMap.put(key, value);
            return;
        }

        try {
            redis.opsForValue().set(key, value);
        } catch (DataAccessException ex) {
            switchToInMemoryFallback(ex);
            fallbackMap.put(key, value);
        }
    }

    private void deleteValue(String key, Map<?, ?> fallbackMap) {
        if (useInMemoryFallback) {
            removeFromFallback(key, fallbackMap);
            return;
        }

        try {
            redis.delete(key);
        } catch (DataAccessException ex) {
            switchToInMemoryFallback(ex);
            removeFromFallback(key, fallbackMap);
        }
    }

    private void removeFromFallback(String key, Map<?, ?> fallbackMap) {
        if (fallbackMap == historyCache) {
            historyCache.remove(key);
            return;
        }
        if (fallbackMap == roomCache) {
            roomCache.remove(key);
            return;
        }
        if (fallbackMap == seedCache) {
            seedCache.remove(key);
            return;
        }
        if (fallbackMap == snapshotCache) {
            snapshotCache.remove(key);
        }
    }

    private String toStoredString(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }

    private void switchToInMemoryFallback(DataAccessException ex) {
        if (useInMemoryFallback) {
            return;
        }
        useInMemoryFallback = true;
        log.warn("Redis unavailable, using in-memory room store instead. Rooms will reset on backend restart.", ex);
    }
}
