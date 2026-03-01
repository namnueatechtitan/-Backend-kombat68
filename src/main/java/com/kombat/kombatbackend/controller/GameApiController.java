package com.kombat.kombatbackend.controller;

import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.dto.*;
import com.kombat.kombatbackend.service.GameService;

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
    }

    // =====================================================
    // START GAME
    // =====================================================

    @PostMapping("/start")
    public ResponseEntity<?> startGame() {
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
                "phase", gameService.getPhase()
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

        return ResponseEntity.ok(Map.of(
                "success", success,
                "phase", gameService.getPhase()
        ));
    }

    @PostMapping("/buy-hex")
    public ResponseEntity<?> buyHex(@RequestBody BuyHexRequest request) {

        boolean success = gameService.buyHex(
                request.getRow(),
                request.getCol()
        );

        return ResponseEntity.ok(Map.of(
                "success", success,
                "phase", gameService.getPhase()
        ));
    }

    // =====================================================
    // STATUS
    // =====================================================

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {

        GameState state = gameService.getGameState();

        if (state == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Game has not started yet");
        }

        GameStatusResponse response =
                new GameStatusResponse(
                        gameService.getCurrentPlayer(),
                        gameService.isGameOver(),
                        gameService.getWinner(),
                        state
                );

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // DEBUG PHASE
    // =====================================================

    @GetMapping("/phase")
    public ResponseEntity<?> getPhase() {
        return ResponseEntity.ok(gameService.getPhase());
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