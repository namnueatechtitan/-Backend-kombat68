package com.kombat.kombatbackend.websocket;

import com.kombat.kombatbackend.dto.GameStateDto;
import com.kombat.kombatbackend.dto.GameStatusResponse;
import com.kombat.kombatbackend.dto.MinionDto;
import com.kombat.kombatbackend.engine.gamestate.CharacterType;
import com.kombat.kombatbackend.engine.gamestate.GameMode;
import com.kombat.kombatbackend.engine.gamestate.GamePhase;
import com.kombat.kombatbackend.engine.gamestate.GameState;
import com.kombat.kombatbackend.service.GameService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomGameSessionService {

    private static final String EVENT_START = "START_GAME";
    private static final String EVENT_PLAYER_ACTION = "PLAYER_ACTION";
    private static final String EVENT_AUTO_STEP = "AUTO_STEP";

    private static final class RoomGameSession {
        private final Object lock = new Object();
        private final long seed;
        private final GameService game;

        private RoomGameSession(long seed, GameService game) {
            this.seed = seed;
            this.game = game;
        }
    }

    private final Map<String, RoomGameSession> sessions = new ConcurrentHashMap<>();
    private final RoomPersistenceStore store;
    private final RoomLobbyService lobbyService;

    public RoomGameSessionService(RoomPersistenceStore store, RoomLobbyService lobbyService) {
        this.store = store;
        this.lobbyService = lobbyService;
    }

    public void startRoomGame(RoomState room) {
        String key = normalizeRoomId(room.getRoomId());
        long seed = new Random().nextLong();
        RoomGameSession session = new RoomGameSession(seed, new GameService(new Random(seed)));
        synchronized (session.lock) {
            initializeGameFromRoomSetup(session.game, room);
            sessions.put(key, session);
            store.saveSeed(key, seed);
            store.clearHistory(key);
            store.appendEvent(key, startEvent(room.getMode()));
        }
    }

    public void applyAction(String roomId, WsMessages.PlayerActionRequest request) {
        applyAction(roomId, null, request);
    }

    public void applyAction(String roomId, String sessionId, WsMessages.PlayerActionRequest request) {
        if (request == null) {
            return;
        }
        RoomGameSession session = getOrRestoreSession(roomId);
        if (session == null) {
            return;
        }

        synchronized (session.lock) {
            if (!isActionAllowed(roomId, sessionId, session.game, request)) {
                return;
            }
            applyActionInternal(session.game, request);
            store.appendEvent(normalizeRoomId(roomId), playerActionEvent(copyAction(request)));
        }
    }

    public void progressAutoStep(String roomId) {
        RoomGameSession session = getOrRestoreSession(roomId);
        if (session == null) {
            return;
        }
        synchronized (session.lock) {
            session.game.progressAutoModeIfNeeded();
            store.appendEvent(normalizeRoomId(roomId), autoStepEvent());
        }
    }

    public boolean isRoomPlaying(String roomId) {
        RoomGameSession session = getOrRestoreSession(roomId);
        if (session == null) {
            return false;
        }
        synchronized (session.lock) {
            return session.game.getPhase() == GamePhase.PLAYING;
        }
    }

    public boolean isGameOver(String roomId) {
        RoomGameSession session = getOrRestoreSession(roomId);
        if (session == null) {
            return false;
        }
        synchronized (session.lock) {
            return session.game.isGameOver();
        }
    }

    public GameStatusResponse buildStatusPayload(String roomId) {
        String key = normalizeRoomId(roomId);
        RoomGameSession session = getOrRestoreSession(roomId);
        if (session == null) {
            return store.getSnapshot(key).orElseGet(GameStatusResponse::new);
        }

        synchronized (session.lock) {
            GameStatusResponse response = buildLiveStatus(session.game);
            store.saveSnapshot(key, response);
            return response;
        }
    }

    public void removeRoomSession(String roomId) {
        String key = normalizeRoomId(roomId);
        sessions.remove(key);
        store.deleteAllRoomArtifacts(key);
    }

    private RoomGameSession getOrRestoreSession(String roomId) {
        String key = normalizeRoomId(roomId);
        RoomGameSession existing = sessions.get(key);
        if (existing != null) {
            return existing;
        }

        RoomState room = lobbyService.getRoom(key);
        if (room == null || !room.isStarted()) {
            return null;
        }

        long seed = store.getSeed(key).orElse(new Random().nextLong());
        RoomGameSession restored = new RoomGameSession(seed, new GameService(new Random(seed)));
        synchronized (restored.lock) {
            replayHistory(restored.game, store.getHistory(key), room);
            sessions.put(key, restored);
        }
        return restored;
    }

    private static void applyActionInternal(GameService game, WsMessages.PlayerActionRequest request) {
        String actionType = request.getActionType() == null ? "" : request.getActionType().trim().toUpperCase();
        switch (actionType) {
            case "SPAWN" -> {
                if (request.getType() != null && request.getRow() != null && request.getCol() != null) {
                    game.spawn(request.getType(), request.getRow(), request.getCol());
                }
            }
            case "BUY_HEX" -> {
                if (request.getRow() != null && request.getCol() != null) {
                    game.buyHex(request.getRow(), request.getCol());
                }
            }
            case "END_TURN" -> game.endTurn();
            default -> {
            }
        }
    }

    private boolean isActionAllowed(String roomId,
                                    String sessionId,
                                    GameService game,
                                    WsMessages.PlayerActionRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            return true;
        }

        String actionType = request.getActionType() == null ? "" : request.getActionType().trim().toUpperCase();
        if (actionType.isBlank()) {
            return false;
        }

        Long actorPlayerId = lobbyService.resolvePlayerId(roomId, sessionId);
        if (actorPlayerId == null) {
            return false;
        }

        return game.getCurrentPlayer() == actorPlayerId;
    }

    private static void replayHistory(GameService game, List<RoomPersistenceStore.SessionEvent> history, RoomState room) {
        if (history.isEmpty() || !EVENT_START.equalsIgnoreCase(history.getFirst().getEventType())) {
            initializeGameFromRoomSetup(game, room);
        }

        for (RoomPersistenceStore.SessionEvent event : history) {
            String type = event.getEventType() == null ? "" : event.getEventType().trim().toUpperCase();
            switch (type) {
                case EVENT_START -> initializeGameFromRoomSetup(game, room);
                case EVENT_PLAYER_ACTION -> {
                    WsMessages.PlayerActionRequest req = event.getPlayerAction();
                    if (req != null) {
                        applyActionInternal(game, req);
                    }
                }
                case EVENT_AUTO_STEP -> game.progressAutoModeIfNeeded();
                default -> {
                }
            }
        }
    }

    private static void initializeGameFromRoomSetup(GameService game, RoomState room) {
        game.resetGame();
        game.setConfig(room.getConfig() == null
                ? com.kombat.kombatbackend.engine.gamestate.GameConfig.sampleDefaults()
                : room.getConfig());
        game.setMode(room.getMode());
        game.setCharacter(GameService.P1, room.getPlayer1Character());
        game.setCharacter(GameService.P2, room.getPlayer2Character());
        game.resetMinions(GameService.P1);
        game.resetMinions(GameService.P2);
        if (room.getMode() == GameMode.DUEL && room.getSharedConfiguredMinions() != null && !room.getSharedConfiguredMinions().isEmpty()) {
            for (RoomConfiguredMinion minion : room.getSharedConfiguredMinions()) {
                game.addMinion(
                        GameService.P1,
                        minion.getType(),
                        resolvePlayerOneName(room.getPlayer1Character(), minion),
                        minion.getDefenseFactor(),
                        minion.getStrategy()
                );
                game.addMinion(
                        GameService.P2,
                        minion.getType(),
                        defaultNameForCharacter(room.getPlayer2Character(), minion.getType()),
                        minion.getDefenseFactor(),
                        minion.getStrategy()
                );
            }
        } else {
            for (RoomConfiguredMinion minion : room.getPlayer1ConfiguredMinions()) {
                game.addMinion(GameService.P1, minion.getType(), minion.getName(), minion.getDefenseFactor(), minion.getStrategy());
            }
            for (RoomConfiguredMinion minion : room.getPlayer2ConfiguredMinions()) {
                game.addMinion(GameService.P2, minion.getType(), minion.getName(), minion.getDefenseFactor(), minion.getStrategy());
            }
        }
        game.startGame();
    }

    private static String resolvePlayerOneName(CharacterType character, RoomConfiguredMinion minion) {
        if (minion.getName() != null && !minion.getName().isBlank()) {
            return minion.getName();
        }
        return defaultNameForCharacter(character, minion.getType());
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

    private static GameStatusResponse buildLiveStatus(GameService game) {
        GameState state = game.getGameState();
        if (state == null) {
            return new GameStatusResponse();
        }

        GameStateDto gameStateDto = new GameStateDto(
                state.getTurnNumber(),
                state.getPhase().name(),
                state.getMinions().stream()
                        .map(m -> new MinionDto(
                                m.getOwnerId(),
                                m.getType().name(),
                                m.getKindName(),
                                m.getHp(),
                                m.getPosition().getX(),
                                m.getPosition().getY()
                        )).toList(),
                state.getBudgetManager().getBudget(game.getCurrentPlayer()),
                game.getSpawnsLeft()
        );

        GameStatusResponse response = new GameStatusResponse(
                game.getCurrentPlayer(),
                game.isGameOver(),
                game.getWinner(),
                gameStateDto
        );
        response.setSpawnableHexes(game.getSpawnableHexes());
        response.setBuyableHexes(game.getBuyableHexes());
        response.setActionLogs(game.getActionLogs());
        response.setPlayerEconomy(game.getPlayerEconomy());
        response.setAvailableTypes(game.getAvailableTypes());
        return response;
    }

    private static RoomPersistenceStore.SessionEvent startEvent(GameMode mode) {
        RoomPersistenceStore.SessionEvent event = new RoomPersistenceStore.SessionEvent();
        event.setEventType(EVENT_START);
        event.setMode(mode);
        return event;
    }

    private static RoomPersistenceStore.SessionEvent playerActionEvent(WsMessages.PlayerActionRequest action) {
        RoomPersistenceStore.SessionEvent event = new RoomPersistenceStore.SessionEvent();
        event.setEventType(EVENT_PLAYER_ACTION);
        event.setPlayerAction(action);
        return event;
    }

    private static RoomPersistenceStore.SessionEvent autoStepEvent() {
        RoomPersistenceStore.SessionEvent event = new RoomPersistenceStore.SessionEvent();
        event.setEventType(EVENT_AUTO_STEP);
        return event;
    }

    private static WsMessages.PlayerActionRequest copyAction(WsMessages.PlayerActionRequest src) {
        WsMessages.PlayerActionRequest copy = new WsMessages.PlayerActionRequest();
        copy.setRoomId(src.getRoomId());
        copy.setActionType(src.getActionType());
        copy.setType(src.getType());
        copy.setRow(src.getRow());
        copy.setCol(src.getCol());
        return copy;
    }

    private static String normalizeRoomId(String roomId) {
        if (roomId == null) {
            return "";
        }
        return roomId.trim().toUpperCase();
    }
}
