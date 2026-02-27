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
    // CONFIG
    // =====================================================

    @GetMapping("/config")
    public GameConfig getConfig() {
        return gameService.getConfig();
    }

    @PostMapping("/config")
    public GameConfig setConfig(@RequestBody GameConfig config) {
        gameService.setConfig(config);
        return gameService.getConfig();
    }

    // =====================================================
    // MODE
    // =====================================================

    @PostMapping("/mode")
    public GameMode setMode(@RequestBody ModeRequest request) {
        gameService.setMode(request.getMode());
        return gameService.getMode();
    }

    @GetMapping("/mode")
    public GameMode getMode() {
        return gameService.getMode();
    }

    // =====================================================
    // CHARACTER (per player)
    // =====================================================

    @PostMapping("/character")
    public CharacterType setCharacter(
            @RequestBody SelectCharacterRequest request) {

        gameService.setCharacter(
                request.getPlayerId(),
                request.getCharacter()
        );

        return gameService.getCharacter(request.getPlayerId());
    }

    @GetMapping("/character/{playerId}")
    public CharacterType getCharacter(@PathVariable long playerId) {
        return gameService.getCharacter(playerId);
    }

    // =====================================================
    // SETUP FULL (per player)
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

        return ResponseEntity.ok("Setup completed for player " + playerId);
    }

    // =====================================================
    // SETUP SUMMARY (both players)
    // =====================================================

    @GetMapping("/setup")
    public Map<String, Object> getSetupSummary() {

        Map<String, Object> data = new HashMap<>();

        data.put("mode", gameService.getMode());
        data.put("config", gameService.getConfig());

        Map<String, Object> players = new HashMap<>();

        for (long pid : List.of(GameService.P1, GameService.P2)) {

            Map<String, Object> pData = new HashMap<>();

            pData.put("character", gameService.getCharacter(pid));

            List<MinionKindDef> defs =
                    gameService.getSelectedMinions(pid);

            List<MinionType> types =
                    defs.stream()
                            .map(MinionKindDef::getType)
                            .collect(Collectors.toList());

            pData.put("selectedMinionTypes", types);
            pData.put("definedMinions", defs);

            players.put("player" + pid, pData);
        }

        data.put("players", players);

        return data;
    }

    // =====================================================
    // START GAME
    // =====================================================

    @PostMapping("/start")
    public ResponseEntity<?> startGame() {
        gameService.startGame();
        return ResponseEntity.ok("Game started");
    }

    // =====================================================
    // TURN CONTROL
    // =====================================================

    @GetMapping("/current-player")
    public long getCurrentPlayer() {
        return gameService.getCurrentPlayer();
    }

    @PostMapping("/end-turn")
    public ResponseEntity<?> endTurn() {
        gameService.endTurn();
        return ResponseEntity.ok("Turn ended");
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
}