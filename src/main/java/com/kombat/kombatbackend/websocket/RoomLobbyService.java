package com.kombat.kombatbackend.websocket;

import com.kombat.kombatbackend.engine.gamestate.CharacterType;
import com.kombat.kombatbackend.engine.gamestate.GameConfig;
import com.kombat.kombatbackend.engine.gamestate.GameMode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomLobbyService {

    public static final long PLAYER_ONE_ID = 1L;
    public static final long PLAYER_TWO_ID = 2L;

    public static final class LeaveOutcome {
        private final RoomState room;
        private final boolean roomClosed;

        public LeaveOutcome(RoomState room, boolean roomClosed) {
            this.room = room;
            this.roomClosed = roomClosed;
        }

        public RoomState getRoom() {
            return room;
        }

        public boolean isRoomClosed() {
            return roomClosed;
        }
    }

    private static final long ROOM_IDLE_EXPIRE_MS = 6L * 60L * 60L * 1000L;
    private final RoomPersistenceStore store;
    private final Map<String, Map<String, String>> roomSessionPlayers = new ConcurrentHashMap<>();

    public RoomLobbyService(RoomPersistenceStore store) {
        this.store = store;
    }

    public synchronized RoomState createRoom(String suggestedRoomId, String playerName, GameMode mode) {
        String host = safeName(playerName, "Host");
        String roomId = normalizeRoomId(suggestedRoomId);

        if (roomId == null || getRoom(roomId) != null) {
            roomId = generateRoomId();
            while (getRoom(roomId) != null) {
                roomId = generateRoomId();
            }
        }

        RoomState room = new RoomState();
        room.setRoomId(roomId);
        room.setHost(host);
        room.setMode(mode == null ? GameMode.DUEL : mode);
        room.setConfig(GameConfig.sampleDefaults());
        room.setPlayers(new ArrayList<>(List.of(host)));
        room.setStarted(false);
        room.setSetupPhase(RoomSetupPhase.LOBBY);
        touch(room);
        applyModeDefaults(room);
        store.saveRoom(room);
        return room;
    }

    public synchronized RoomState joinRoom(String roomId, String playerName) {
        RoomState room = getRoom(roomId);
        if (room == null) {
            return null;
        }

        String name = safeName(playerName, "Player");
        if (!room.getPlayers().contains(name)) {
            room.getPlayers().add(name);
        }
        if (requiresTwoHumanPlayers(room.getMode())
                && humanPlayers(room.getPlayers()).size() >= 2
                && room.getSetupPhase() == RoomSetupPhase.LOBBY) {
            room.setSetupPhase(RoomSetupPhase.MINION_TYPE_COUNT);
        }
        touch(room);
        store.saveRoom(room);
        return room;
    }

    public synchronized RoomState submitMinionTypeCount(String roomId, long playerId, int count) {
        RoomState room = getRequiredEditableRoom(roomId);
        if (count <= 0) {
            throw new IllegalArgumentException("Minion type count must be positive");
        }

        if (room.getSetupPhase() != RoomSetupPhase.MINION_TYPE_COUNT) {
            throw new IllegalStateException("Not allowed to submit minion count in phase " + room.getSetupPhase());
        }

        if (usesHostManagedSetup(room.getMode())) {
            if (playerId != PLAYER_ONE_ID) {
                throw new IllegalStateException("Only the host can choose minion count in this room mode");
            }
            room.setPlayer1MinionTypeCount(count);
            room.setPlayer2MinionTypeCount(count);
            room.setEffectiveMinionTypeCount(count);
            room.setSetupPhase(RoomSetupPhase.CHARACTER_SELECT);
            touch(room);
            store.saveRoom(room);
            return room;
        }

        if (playerId == PLAYER_ONE_ID) {
            room.setPlayer1MinionTypeCount(count);
        } else if (playerId == PLAYER_TWO_ID) {
            room.setPlayer2MinionTypeCount(count);
        } else {
            throw new IllegalStateException("Unknown player");
        }

        if (room.getPlayer1MinionTypeCount() != null && room.getPlayer2MinionTypeCount() != null) {
            int effective = Math.max(room.getPlayer1MinionTypeCount(), room.getPlayer2MinionTypeCount());
            room.setEffectiveMinionTypeCount(effective);
            room.setSetupPhase(RoomSetupPhase.CHARACTER_SELECT);
        }

        touch(room);
        store.saveRoom(room);
        return room;
    }

    public synchronized RoomState selectCharacter(String roomId, long playerId, CharacterType character) {
        RoomState room = getRequiredEditableRoom(roomId);
        if (character == null) {
            throw new IllegalArgumentException("Character is required");
        }

        if (room.getSetupPhase() != RoomSetupPhase.CHARACTER_SELECT) {
            throw new IllegalStateException("Not allowed to select character in phase " + room.getSetupPhase());
        }

        if (usesHostManagedSetup(room.getMode())) {
            if (playerId != PLAYER_ONE_ID) {
                throw new IllegalStateException("Player 1 chooses the character alignment in this room mode");
            }

            room.setPlayer1Character(character);
            room.setPlayer2Character(oppositeCharacter(character));
            room.setSetupPhase(RoomSetupPhase.MINION_SETUP);
            touch(room);
            store.saveRoom(room);
            return room;
        }

        if (playerId == PLAYER_ONE_ID) {
            room.setPlayer1Character(character);
        } else if (playerId == PLAYER_TWO_ID) {
            room.setPlayer2Character(character);
        } else {
            throw new IllegalStateException("Unknown player");
        }

        if (room.getPlayer1Character() != null && room.getPlayer2Character() != null) {
            room.setSetupPhase(RoomSetupPhase.MINION_SETUP);
        }

        touch(room);
        store.saveRoom(room);
        return room;
    }

    public synchronized RoomState updateRoomConfig(String roomId, long playerId, GameConfig config) {
        RoomState room = getRequiredEditableRoom(roomId);
        if (config == null) {
            throw new IllegalArgumentException("Config is required");
        }
        if (playerId != PLAYER_ONE_ID) {
            throw new IllegalStateException("Only host can update room config");
        }

        room.setConfig(config);
        touch(room);
        store.saveRoom(room);
        return room;
    }

    public synchronized RoomState submitMinionSetup(String roomId, long playerId, List<RoomConfiguredMinion> minions) {
        RoomState room = getRequiredEditableRoom(roomId);
        int expected = room.getEffectiveMinionTypeCount() == null ? 0 : room.getEffectiveMinionTypeCount();
        if (expected <= 0) {
            throw new IllegalStateException("Minion type count has not been finalized");
        }
        if (minions == null || minions.size() != expected) {
            throw new IllegalArgumentException("Expected exactly " + expected + " configured minions");
        }

        if (room.getSetupPhase() != RoomSetupPhase.MINION_SETUP) {
            throw new IllegalStateException("Not allowed to submit minion setup in phase " + room.getSetupPhase());
        }

        List<RoomConfiguredMinion> copied = copyMinions(minions);
        if (room.getMode() == GameMode.DUEL) {
            if (playerId != PLAYER_ONE_ID) {
                throw new IllegalStateException("Only the host can configure duel minions");
            }
            room.setSharedConfiguredMinions(copied);
            room.setPlayer1ConfiguredMinions(copyMinionsForCharacter(copied, room.getPlayer1Character(), false));
            room.setPlayer2ConfiguredMinions(copyMinionsForCharacter(copied, room.getPlayer2Character(), true));
            room.setPlayer1SharedSetupConfirmed(true);
            room.setPlayer2SharedSetupConfirmed(true);
            room.setSetupPhase(RoomSetupPhase.PRE_BATTLE);
            touch(room);
            store.saveRoom(room);
            return room;
        }

        if (room.getMode() == GameMode.AUTO) {
            if (playerId != PLAYER_ONE_ID) {
                throw new IllegalStateException("Only the host can configure auto minions");
            }
            room.setPlayer1ConfiguredMinions(copyMinionsForCharacter(copied, room.getPlayer1Character(), false));
            room.setPlayer2ConfiguredMinions(copyMinionsForCharacter(copied, room.getPlayer2Character(), true));
            room.setSetupPhase(RoomSetupPhase.PRE_BATTLE);
            touch(room);
            store.saveRoom(room);
            return room;
        }

        if (playerId == PLAYER_ONE_ID) {
            room.setPlayer1ConfiguredMinions(copied);
        } else if (playerId == PLAYER_TWO_ID) {
            room.setPlayer2ConfiguredMinions(copied);
        } else {
            throw new IllegalStateException("Unknown player");
        }

        if (room.getPlayer1ConfiguredMinions().size() == expected && room.getPlayer2ConfiguredMinions().size() == expected) {
            room.setSetupPhase(RoomSetupPhase.PRE_BATTLE);
        }

        touch(room);
        store.saveRoom(room);
        return room;
    }

    public synchronized void bindSession(String roomId, String sessionId, String playerName) {
        String normalizedRoomId = normalizeRoomId(roomId);
        String normalizedPlayerName = safeName(playerName, "Player");
        if (normalizedRoomId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }

        roomSessionPlayers
                .computeIfAbsent(normalizedRoomId, ignored -> new ConcurrentHashMap<>())
                .put(sessionId, normalizedPlayerName);
    }

    public synchronized void unbindSession(String roomId, String sessionId) {
        String normalizedRoomId = normalizeRoomId(roomId);
        if (normalizedRoomId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }

        Map<String, String> roomBindings = roomSessionPlayers.get(normalizedRoomId);
        if (roomBindings == null) {
            return;
        }

        roomBindings.remove(sessionId);
        if (roomBindings.isEmpty()) {
            roomSessionPlayers.remove(normalizedRoomId);
        }
    }

    public synchronized Long resolvePlayerId(String roomId, String sessionId) {
        RoomState room = getRoom(roomId);
        if (room == null || sessionId == null || sessionId.isBlank()) {
            return null;
        }

        Map<String, String> bindings = roomSessionPlayers.get(normalizeRoomId(roomId));
        if (bindings == null) {
            return null;
        }

        String playerName = bindings.get(sessionId);
        if (playerName == null || playerName.isBlank()) {
            return null;
        }

        List<String> humans = humanPlayers(room.getPlayers());
        if (!humans.isEmpty() && playerName.equals(humans.getFirst())) {
            return PLAYER_ONE_ID;
        }
        if (humans.size() > 1 && playerName.equals(humans.get(1))) {
            return PLAYER_TWO_ID;
        }

        return null;
    }

    public synchronized boolean canStart(RoomState room) {
        if (room == null) {
            return false;
        }
        if (requiresTwoHumanPlayers(room.getMode()) && humanPlayers(room.getPlayers()).size() < 2) {
            return false;
        }
        return room.getSetupPhase() == RoomSetupPhase.PRE_BATTLE;
    }

    public synchronized LeaveOutcome leaveRoom(String roomId, String playerName) {
        RoomState room = getRoom(roomId);
        if (room == null) {
            return new LeaveOutcome(null, true);
        }

        String name = safeName(playerName, "Player");
        room.getPlayers().remove(name);

        List<String> humans = humanPlayers(room.getPlayers());
        if (humans.isEmpty()) {
            roomSessionPlayers.remove(normalizeRoomId(roomId));
            store.deleteAllRoomArtifacts(room.getRoomId());
            return new LeaveOutcome(null, true);
        }

        if (name.equals(room.getHost()) || room.getHost() == null || room.getHost().isBlank()) {
            room.setHost(humans.getFirst());
        }

        touch(room);
        store.saveRoom(room);
        return new LeaveOutcome(room, false);
    }

    public synchronized boolean closeRoom(String roomId, String requestedBy) {
        RoomState room = getRoom(roomId);
        if (room == null) {
            return true;
        }
        String actor = safeName(requestedBy, "");
        if (!actor.equals(room.getHost())) {
            return false;
        }
        store.deleteAllRoomArtifacts(room.getRoomId());
        roomSessionPlayers.remove(normalizeRoomId(roomId));
        return true;
    }

    public synchronized RoomState getRoom(String roomId) {
        String normalized = normalizeRoomId(roomId);
        if (normalized == null) {
            return null;
        }

        RoomState room = store.getRoom(normalized).orElse(null);
        if (room == null) {
            return null;
        }
        if (isExpired(room)) {
            roomSessionPlayers.remove(normalized);
            store.deleteAllRoomArtifacts(room.getRoomId());
            return null;
        }
        return room;
    }

    public synchronized void markStarted(String roomId, boolean started) {
        RoomState room = getRoom(roomId);
        if (room == null) {
            return;
        }
        room.setStarted(started);
        room.setSetupPhase(started ? RoomSetupPhase.PLAYING : room.getSetupPhase());
        touch(room);
        store.saveRoom(room);
    }

    public WsMessages.RoomStateMessage toMessage(RoomState room) {
        WsMessages.RoomStateMessage msg = new WsMessages.RoomStateMessage();
        if (room == null) {
            msg.setClosed(true);
            msg.setError("Room closed");
            return msg;
        }
        msg.setRoomId(room.getRoomId());
        msg.setMode(room.getMode());
        msg.setConfig(room.getConfig());
        msg.setHost(room.getHost());
        msg.setPlayers(new ArrayList<>(room.getPlayers()));
        msg.setStarted(room.isStarted());
        msg.setSetupPhase(room.getSetupPhase());
        msg.setPlayer1MinionTypeCount(room.getPlayer1MinionTypeCount());
        msg.setPlayer2MinionTypeCount(room.getPlayer2MinionTypeCount());
        msg.setEffectiveMinionTypeCount(room.getEffectiveMinionTypeCount());
        msg.setPlayer1Character(room.getPlayer1Character());
        msg.setPlayer2Character(room.getPlayer2Character());
        msg.setSharedConfiguredMinions(copyMinions(room.getSharedConfiguredMinions()));
        msg.setPlayer1SharedSetupConfirmed(room.isPlayer1SharedSetupConfirmed());
        msg.setPlayer2SharedSetupConfirmed(room.isPlayer2SharedSetupConfirmed());
        if (room.getMode() == GameMode.DUEL && !room.getSharedConfiguredMinions().isEmpty()) {
            msg.setPlayer1ConfiguredMinions(copyMinionsForCharacter(
                    room.getSharedConfiguredMinions(),
                    room.getPlayer1Character(),
                    false
            ));
            msg.setPlayer2ConfiguredMinions(copyMinionsForCharacter(
                    room.getSharedConfiguredMinions(),
                    room.getPlayer2Character(),
                    true
            ));
        } else {
            msg.setPlayer1ConfiguredMinions(copyMinions(room.getPlayer1ConfiguredMinions()));
            msg.setPlayer2ConfiguredMinions(copyMinions(room.getPlayer2ConfiguredMinions()));
        }
        msg.setClosed(false);
        return msg;
    }

    private void applyModeDefaults(RoomState room) {
        if (room.getMode() == GameMode.SOLITAIRE) {
            addIfAbsent(room.getPlayers(), "BOT");
        }
    }

    private static boolean requiresTwoHumanPlayers(GameMode mode) {
        return mode == GameMode.DUEL || mode == GameMode.AUTO;
    }

    private static boolean usesHostManagedSetup(GameMode mode) {
        return mode == GameMode.DUEL || mode == GameMode.AUTO;
    }

    private static void addIfAbsent(List<String> players, String value) {
        if (!players.contains(value)) {
            players.add(value);
        }
    }

    private static List<String> humanPlayers(List<String> players) {
        return players.stream()
                .filter(p -> !isBotName(p))
                .toList();
    }

    private static boolean isBotName(String name) {
        return "BOT".equalsIgnoreCase(name)
                || "BOT_A".equalsIgnoreCase(name)
                || "BOT_B".equalsIgnoreCase(name);
    }

    private static String safeName(String input, String fallback) {
        if (input == null || input.trim().isEmpty()) {
            return fallback;
        }
        return input.trim();
    }

    private static String normalizeRoomId(String roomId) {
        if (roomId == null) {
            return null;
        }
        String normalized = roomId.trim().toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String generateRoomId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private static void touch(RoomState room) {
        room.setUpdatedAtEpochMs(System.currentTimeMillis());
    }

    private static List<RoomConfiguredMinion> copyMinions(List<RoomConfiguredMinion> minions) {
        if (minions == null || minions.isEmpty()) {
            return new ArrayList<>();
        }

        List<RoomConfiguredMinion> copy = new ArrayList<>(minions.size());
        for (RoomConfiguredMinion minion : minions) {
            RoomConfiguredMinion item = new RoomConfiguredMinion();
            item.setType(minion.getType());
            item.setName(minion.getName());
            item.setDefenseFactor(minion.getDefenseFactor());
            item.setStrategy(minion.getStrategy());
            copy.add(item);
        }
        return copy;
    }

    private static List<RoomConfiguredMinion> copyMinionsForCharacter(List<RoomConfiguredMinion> minions,
                                                                      CharacterType character,
                                                                      boolean forceDefaultNames) {
        List<RoomConfiguredMinion> copy = copyMinions(minions);
        for (RoomConfiguredMinion minion : copy) {
            if (forceDefaultNames || minion.getName() == null || minion.getName().isBlank()) {
                minion.setName(defaultNameForCharacter(character, minion.getType()));
            }
        }
        return copy;
    }

    private static String defaultNameForCharacter(CharacterType character, String type) {
        boolean demon = character == CharacterType.DEMON;
        return switch (type == null ? "" : type.trim().toUpperCase()) {
            case "FIGHTER" -> demon ? "MUZAN" : "TANJIRO";
            case "ASSASSIN" -> demon ? "KOKUSHIBO" : "YORIICHI";
            case "DPS" -> demon ? "DOMA" : "GIYU";
            case "TANK" -> demon ? "AKAZA" : "KYOJURO";
            case "SUPPORT" -> demon ? "NAKIME" : "INOSUKE";
            default -> demon ? "DEMON" : "HUMAN";
        };
    }

    private static CharacterType oppositeCharacter(CharacterType character) {
        return character == CharacterType.DEMON ? CharacterType.HUMAN : CharacterType.DEMON;
    }

    private RoomState getRequiredEditableRoom(String roomId) {
        RoomState room = getRoom(roomId);
        if (room == null) {
            throw new IllegalStateException("Room not found");
        }
        if (room.isStarted()) {
            throw new IllegalStateException("Room already started");
        }
        return room;
    }

    private static boolean isExpired(RoomState room) {
        long last = room.getUpdatedAtEpochMs();
        return last > 0 && (System.currentTimeMillis() - last) > ROOM_IDLE_EXPIRE_MS;
    }
}
