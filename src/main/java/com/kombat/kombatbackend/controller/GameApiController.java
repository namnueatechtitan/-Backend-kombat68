package com.kombat.kombatbackend.controller;

import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.dto.*;
import com.kombat.kombatbackend.service.GameService;
import com.kombat.kombatbackend.dto.GameStateDto;
import com.kombat.kombatbackend.dto.MinionDto;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game")
@CrossOrigin
public class GameApiController {

    private final GameService gameService;

    public GameApiController(GameService gameService) {
        this.gameService = gameService;
    }

    // =====================================================
// CONFIG (ใช้ DTO ไม่ expose GameConfig ตรง ๆ)
// =====================================================

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(gameService.getConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<?> setConfig(@RequestBody ConfigRequest request) {

        //  ห้ามเปลี่ยน config หลังเกมเริ่มแล้ว
        if (gameService.getGameState() != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Cannot change config after game started"
            ));
        }

        GameConfig config = new GameConfig(
                request.getSpawnCost(),
                request.getHexPurchaseCost(),
                request.getInitBudget(),
                request.getInitHp(),
                request.getTurnBudget(),
                request.getMaxBudget(),
                request.getInterestPct(),
                request.getMaxTurns(),
                request.getMaxSpawns()
        );

        gameService.setConfig(config);

        return ResponseEntity.ok(Map.of(
                "message", "Config set",
                "phase", gameService.getPhase()
        ));
    }
    // =====================================================
    // MODE
    // =====================================================

    @PostMapping("/mode")
    public ResponseEntity<?> setMode(@RequestBody ModeRequest request) {
        gameService.setMode(request.getMode());
        return ResponseEntity.ok(Map.of(
                "mode", gameService.getMode(),
                "phase", gameService.getPhase()
        ));
    }

    @GetMapping("/mode")
    public ResponseEntity<?> getMode() {
        return ResponseEntity.ok(gameService.getMode());
    }

    // =====================================================
    // CHARACTER
    // =====================================================

    @PostMapping("/character")
    public ResponseEntity<?> setCharacter(
            @RequestBody SelectCharacterRequest request) {

        gameService.setCharacter(
                request.getPlayerId(),
                request.getCharacter()
        );

        return ResponseEntity.ok(Map.of(
                "player", request.getPlayerId(),
                "character", gameService.getCharacter(request.getPlayerId()),
                "phase", gameService.getPhase()
        ));
    }

    @GetMapping("/character/{playerId}")
    public ResponseEntity<?> getCharacter(@PathVariable long playerId) {
        return ResponseEntity.ok(gameService.getCharacter(playerId));
    }

    // =====================================================
    // SETUP FULL
    // =====================================================

    @PostMapping("/setup/full/{playerId}")
    public ResponseEntity<?> setupFull(
            @PathVariable long playerId,
            @RequestBody List<MinionStrategyRequest> minions) {

        try {

            gameService.resetMinions(playerId);

            for (MinionStrategyRequest request : minions) {
                gameService.addMinion(
                        playerId,
                        request.getType(),
                        request.getDefenseFactor(),
                        request.getStrategy()
                );
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Setup completed",
                    "player", playerId,
                    "phase", gameService.getPhase()
            ));

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "error", "Invalid strategy",
                            "message", e.getMessage()
                    ));
        }
    }

    // =====================================================
    // START GAME
    // =====================================================

    @PostMapping("/start")
    public ResponseEntity<?> startGame() {

        if (gameService.getPhase() == GamePhase.PLAYING) {
            return ResponseEntity.ok(Map.of(
                    "message", "Game already started",
                    "phase", gameService.getPhase()
            ));
        }

        gameService.startGame();

        return ResponseEntity.ok(Map.of(
                "message", "Game started",
                "phase", gameService.getPhase()
        ));
    }
    // =====================================================
    // TURN CONTROL
    // =====================================================

    @GetMapping("/current-player")
    public ResponseEntity<?> getCurrentPlayer() {
        return ResponseEntity.ok(gameService.getCurrentPlayer());
    }

    @PostMapping("/end-turn")
    public ResponseEntity<?> endTurn() {
        gameService.endTurn();
        return ResponseEntity.ok(Map.of(
                "message", "Turn ended",
                "phase", gameService.getPhase(),
                "actionLogs", gameService.getActionLogs()
        ));
    }

    // =====================================================
    // GAME STATE
    // =====================================================

    @GetMapping("/state")
    public ResponseEntity<?> getState() {

        GameState state = gameService.getGameState();

        if (state == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Game has not started yet");
        }

        return ResponseEntity.ok(state);
    }

    // =====================================================
    // GAMEPLAY ACTIONS
    // =====================================================

    @PostMapping("/spawn")
    public ResponseEntity<?> spawn(@RequestBody SpawnRequest request) {

        boolean success = gameService.spawn(
                request.getType(),
                request.getRow(),
                request.getCol()
        );

        Map<String, Object> body = Map.of(
                "success", success,
                "phase", gameService.getTurnPhase(),
                "currentPlayer", gameService.getCurrentPlayer(),
                "turn", gameService.getGameState().getTurnNumber()
        );

        return success
                ? ResponseEntity.ok(body)
                : ResponseEntity.badRequest().body(body);
    }

    @PostMapping("/buy-hex")
    public ResponseEntity<?> buyHex(@RequestBody BuyHexRequest request) {

        boolean success = gameService.buyHex(
                request.getRow(),
                request.getCol()
        );

        Map<String, Object> body = Map.of(
                "success", success,
                "phase", gameService.getTurnPhase()
        );

        return success
                ? ResponseEntity.ok(body)
                : ResponseEntity.badRequest().body(body);
    }

    // =====================================================
    // STATUS
    // =====================================================

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {

        GameState state = gameService.getGameState();

        if (state == null) {
            return ResponseEntity.badRequest()
                    .body("Game has not started yet");
        }

        GameStateDto dto = convert(state);

        GameStatusResponse response =
                new GameStatusResponse(
                        gameService.getCurrentPlayer(),
                        gameService.isGameOver(),
                        gameService.getWinner(),
                        dto
                );

        response.setSpawnableHexes(
                gameService.getSpawnableHexes()
        );

        response.setBuyableHexes(
                gameService.getBuyableHexes()
        );

        response.setActionLogs(
                gameService.getActionLogs()
        );

        response.setPlayerEconomy(
                gameService.getPlayerEconomy()
        );

        return ResponseEntity.ok(response);
    }
    private GameStateDto convert(GameState state) {

        List<MinionDto> minions = state.getMinions().stream()
                .map(m -> new MinionDto(
                        m.getOwnerId(),
                        m.getType().name(),
                        m.getPosition().getX(),
                        m.getPosition().getY()
                ))
                .toList();

        long budget = state.getBudgetManager()
                .getBudget(gameService.getCurrentPlayer());

        long spawnsLeft = gameService.getSpawnsLeft();

        return new GameStateDto(
                state.getTurnNumber(),
                state.getPhase().name(),
                minions,
                budget,
                spawnsLeft
        );
    }

    // =====================================================
    // DEBUG PHASE
    // =====================================================

    @GetMapping("/phase")
    public ResponseEntity<?> getPhase() {
        return ResponseEntity.ok(gameService.getTurnPhase());
    }
    @PostMapping("/init-full")
    public ResponseEntity<?> initFullGame(
            @RequestBody GameInitRequest request) {

        gameService.initFullGame(request);

        return ResponseEntity.ok(Map.of(
                "message", "Game initialized",
                "phase", gameService.getPhase()
        ));
    }
    @GetMapping("/setup")
    public ResponseEntity<?> getSetupSummary() {

        Map<String, Object> response = new HashMap<>();

        response.put("mode", gameService.getMode());
        response.put("config", gameService.getConfig());

        Map<String, Object> players = new HashMap<>();

        Map<String, Object> p1 = new HashMap<>();
        p1.put("character", gameService.getCharacter(GameService.P1));
        p1.put("definedMinions", gameService.getSelectedMinions(GameService.P1));

        Map<String, Object> p2 = new HashMap<>();
        p2.put("character", gameService.getCharacter(GameService.P2));
        p2.put("definedMinions", gameService.getSelectedMinions(GameService.P2));

        players.put("player1", p1);
        players.put("player2", p2);

        response.put("players", players);

        return ResponseEntity.ok(response);
    }
    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        gameService.resetGame();
        return ResponseEntity.ok("Reset");
    }

}
