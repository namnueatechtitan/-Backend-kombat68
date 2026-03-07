package com.kombat.kombatbackend.websocket;

import com.kombat.kombatbackend.engine.gamestate.GameMode;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
public class GameWebSocketController {

    private final RoomLobbyService lobbyService;
    private final RoomGameSessionService roomGameSessionService;
    private final SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService autoScheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> autoJobs = new ConcurrentHashMap<>();

    public GameWebSocketController(RoomLobbyService lobbyService,
                                   RoomGameSessionService roomGameSessionService,
                                   SimpMessagingTemplate messagingTemplate) {
        this.lobbyService = lobbyService;
        this.roomGameSessionService = roomGameSessionService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/create-room")
    public void createRoom(WsMessages.CreateRoomRequest request,
                           @Header("simpSessionId") String sessionId) {
        RoomState room = lobbyService.createRoom(
                request.getRoomId(),
                request.getPlayerName(),
                request.getMode()
        );
        lobbyService.bindSession(room.getRoomId(), sessionId, request.getPlayerName());
        broadcastRoom(room);
    }

    @MessageMapping("/join-room")
    public void joinRoom(WsMessages.JoinRoomRequest request,
                         @Header("simpSessionId") String sessionId) {
        RoomState room = lobbyService.joinRoom(request.getRoomId(), request.getPlayerName());
        if (room == null) {
            WsMessages.RoomStateMessage err = new WsMessages.RoomStateMessage();
            err.setRoomId(request.getRoomId());
            err.setError("Room not found");
            messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), err);
            return;
        }
        lobbyService.bindSession(room.getRoomId(), sessionId, request.getPlayerName());
        broadcastRoom(room);
    }

    @MessageMapping("/leave-room")
    public void leaveRoom(WsMessages.LeaveRoomRequest request) {
        RoomLobbyService.LeaveOutcome outcome = lobbyService.leaveRoom(request.getRoomId(), request.getPlayerName());
        String roomId = request.getRoomId();
        if (outcome.isRoomClosed()) {
            cleanupRoom(roomId);
            WsMessages.RoomStateMessage closed = new WsMessages.RoomStateMessage();
            closed.setRoomId(roomId);
            closed.setClosed(true);
            closed.setError("Room closed");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, closed);
            return;
        }
        broadcastRoom(outcome.getRoom());
    }

    @MessageMapping("/close-room")
    public void closeRoom(WsMessages.CloseRoomRequest request) {
        boolean closed = lobbyService.closeRoom(request.getRoomId(), request.getPlayerName());
        if (!closed) {
            WsMessages.RoomStateMessage denied = new WsMessages.RoomStateMessage();
            denied.setRoomId(request.getRoomId());
            denied.setError("Only host can close room");
            messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), denied);
            return;
        }

        cleanupRoom(request.getRoomId());
        WsMessages.RoomStateMessage msg = new WsMessages.RoomStateMessage();
        msg.setRoomId(request.getRoomId());
        msg.setClosed(true);
        msg.setError("Room closed by host");
        messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), msg);
    }

    @MessageMapping("/start-game")
    public void startGame(WsMessages.StartGameRequest request) {
        RoomState room = lobbyService.getRoom(request.getRoomId());
        if (room == null) {
            WsMessages.RoomStateMessage err = new WsMessages.RoomStateMessage();
            err.setRoomId(request.getRoomId());
            err.setError("Room not found");
            messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), err);
            return;
        }

        if (!lobbyService.canStart(room)) {
            WsMessages.RoomStateMessage msg = lobbyService.toMessage(room);
            msg.setError("Room requires 2 human players and complete setup before starting");
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), msg);
            return;
        }

        try {
            roomGameSessionService.startRoomGame(room);
            lobbyService.markStarted(room.getRoomId(), true);
            broadcastRoom(lobbyService.getRoom(room.getRoomId()));
            broadcastGame(room.getRoomId());
            autoScheduler.schedule(() -> broadcastGame(room.getRoomId()), 200, TimeUnit.MILLISECONDS);
            autoScheduler.schedule(() -> broadcastGame(room.getRoomId()), 800, TimeUnit.MILLISECONDS);
            startAutoLoopIfNeeded(room);
        } catch (RuntimeException ex) {
            WsMessages.RoomStateMessage msg = lobbyService.toMessage(room);
            msg.setError("Start game failed: " + ex.getMessage());
            messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), msg);
        }
    }

    @MessageMapping("/player-action")
    public void playerAction(WsMessages.PlayerActionRequest request,
                             @Header("simpSessionId") String sessionId) {
        RoomState room = lobbyService.getRoom(request.getRoomId());
        if (room == null || !room.isStarted()) {
            return;
        }

        roomGameSessionService.applyAction(room.getRoomId(), sessionId, request);
        broadcastGame(room.getRoomId());
    }

    @MessageMapping("/submit-minion-type-count")
    public void submitMinionTypeCount(WsMessages.SubmitMinionTypeCountRequest request,
                                      @Header("simpSessionId") String sessionId) {
        applyRoomSetupChange(request.getRoomId(), sessionId, playerId ->
                lobbyService.submitMinionTypeCount(request.getRoomId(), playerId, request.getCount()));
    }

    @MessageMapping("/select-room-character")
    public void selectRoomCharacter(WsMessages.SelectRoomCharacterRequest request,
                                    @Header("simpSessionId") String sessionId) {
        applyRoomSetupChange(request.getRoomId(), sessionId, playerId ->
                lobbyService.selectCharacter(request.getRoomId(), playerId, request.getCharacter()));
    }

    @MessageMapping("/submit-room-minion-setup")
    public void submitRoomMinionSetup(WsMessages.SubmitRoomMinionSetupRequest request,
                                      @Header("simpSessionId") String sessionId) {
        applyRoomSetupChange(request.getRoomId(), sessionId, playerId ->
                lobbyService.submitMinionSetup(request.getRoomId(), playerId, request.getMinions()));
    }

    private void broadcastRoom(RoomState room) {
        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), lobbyService.toMessage(room));
    }

    private void broadcastGame(String roomId) {
        var payload = roomGameSessionService.buildStatusPayload(roomId);
        messagingTemplate.convertAndSend("/topic/game/" + roomId, payload);
    }

    private void startAutoLoopIfNeeded(RoomState room) {
        if (room.getMode() != GameMode.AUTO) {
            return;
        }

        ScheduledFuture<?> existing = autoJobs.remove(room.getRoomId());
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> job = autoScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!roomGameSessionService.isRoomPlaying(room.getRoomId())) {
                    ScheduledFuture<?> toCancel = autoJobs.remove(room.getRoomId());
                    if (toCancel != null) toCancel.cancel(false);
                    return;
                }
                roomGameSessionService.progressAutoStep(room.getRoomId());
                broadcastGame(room.getRoomId());
                if (roomGameSessionService.isGameOver(room.getRoomId())) {
                    ScheduledFuture<?> toCancel = autoJobs.remove(room.getRoomId());
                    if (toCancel != null) toCancel.cancel(false);
                }
            } catch (Exception ignored) {
            }
        }, 350, 650, TimeUnit.MILLISECONDS);

        autoJobs.put(room.getRoomId(), job);
    }

    private void cleanupRoom(String roomId) {
        ScheduledFuture<?> existing = autoJobs.remove(roomId);
        if (existing != null) {
            existing.cancel(false);
        }
        roomGameSessionService.removeRoomSession(roomId);
    }

    private void applyRoomSetupChange(String roomId,
                                      String sessionId,
                                      RoomSetupMutation mutation) {
        RoomState room = lobbyService.getRoom(roomId);
        if (room == null) {
            WsMessages.RoomStateMessage err = new WsMessages.RoomStateMessage();
            err.setRoomId(roomId);
            err.setError("Room not found");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, err);
            return;
        }

        Long playerId = lobbyService.resolvePlayerId(roomId, sessionId);
        if (playerId == null) {
            WsMessages.RoomStateMessage err = lobbyService.toMessage(room);
            err.setError("You are not allowed to modify this room");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, err);
            return;
        }

        try {
            RoomState updated = mutation.apply(playerId);
            broadcastRoom(updated);
        } catch (RuntimeException ex) {
            WsMessages.RoomStateMessage err = lobbyService.toMessage(room);
            err.setError(ex.getMessage());
            messagingTemplate.convertAndSend("/topic/room/" + roomId, err);
        }
    }

    @FunctionalInterface
    private interface RoomSetupMutation {
        RoomState apply(long playerId);
    }
}
