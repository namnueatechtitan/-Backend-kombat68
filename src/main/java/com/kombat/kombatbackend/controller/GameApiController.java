package com.kombat.kombatbackend.controller;

import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.dto.*;
import com.kombat.kombatbackend.service.GameService;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
@CrossOrigin
public class GameApiController {

    private final GameService gameService;

    public GameApiController(GameService gameService) {
        this.gameService = gameService;
    }

    // =========================
    // CONFIG
    // =========================

    @GetMapping("/config")
    public GameConfig getConfig() {
        return gameService.getConfig();
    }

    @PostMapping("/config")
    public GameConfig setConfig(@RequestBody GameConfig config) {
        gameService.setConfig(config);
        return gameService.getConfig();
    }

    // =========================
    // MODE
    // =========================

    @PostMapping("/mode")
    public GameMode setMode(@RequestBody ModeRequest request) {
        gameService.setMode(request.getMode());
        return gameService.getMode();
    }

    @GetMapping("/mode")
    public GameMode getMode() {
        return gameService.getMode();
    }

    // =========================
    // CHARACTER
    // =========================

    @PostMapping("/character")
    public CharacterType setCharacter(
            @RequestBody SelectCharacterRequest request) {

        gameService.setCharacter(request.getCharacter());
        return gameService.getSelectedCharacter();
    }

    @GetMapping("/character")
    public CharacterType getCharacter() {
        return gameService.getSelectedCharacter();
    }

    // =========================
    // MINION TYPE COUNT
    // =========================

    @PostMapping("/minion-type-count")
    public int setMinionTypeCount(
            @RequestBody MinionTypeCountRequest request) {

        gameService.setMinionTypeCount(request.getCount());
        return gameService.getMinionTypeCount();
    }

    @GetMapping("/minion-type-count")
    public int getMinionTypeCount() {
        return gameService.getMinionTypeCount();
    }

    // =========================
    // ADD MINION (เพิ่มใหม่)
    // =========================

    @PostMapping("/minion")
    public ResponseEntity<?> addMinion(
            @RequestBody MinionTypeRequest request) {

        gameService.addMinion(request.getType());
        return ResponseEntity.ok(gameService.getSelectedMinions());
    }

    // =========================
    // SETUP SUMMARY
    // =========================

    @GetMapping("/setup")
    public Map<String, Object> getSetupSummary() {

        Map<String, Object> data = new HashMap<>();
        data.put("mode", gameService.getMode());
        data.put("config", gameService.getConfig());
        data.put("character", gameService.getSelectedCharacter());
        data.put("minionTypeCount", gameService.getMinionTypeCount());
        data.put("selectedMinions", gameService.getSelectedMinions());

        return data;
    }

    // =========================
    // START GAME
    // =========================

    @PostMapping("/start")
    public ResponseEntity<?> startGame() {
        gameService.startGame();
        return ResponseEntity.ok("Game started");
    }

    // =========================
    // GAME STATE
    // =========================

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