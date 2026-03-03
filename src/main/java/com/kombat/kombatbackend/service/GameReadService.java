package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.dto.PlayerEconomyDto;
import com.kombat.kombatbackend.dto.SpawnableHexDto;
import com.kombat.kombatbackend.engine.gamestate.GameEngine;
import com.kombat.kombatbackend.engine.gamestate.GamePhase;
import com.kombat.kombatbackend.engine.gamestate.GameState;
import com.kombat.kombatbackend.engine.gamestate.TurnPhase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GameReadService {

    List<SpawnableHexDto> getSpawnableHexes(GameEngine engine) {
        if (engine == null) {
            return List.of();
        }
        return engine.getSpawnableHexes();
    }

    TurnPhase getTurnPhase(GameState gameState) {
        if (gameState == null) return null;
        return gameState.getPhase();
    }

    List<SpawnableHexDto> getBuyableHexes(GameEngine engine, GamePhase phase, long currentPlayer) {
        if (engine == null || phase != GamePhase.PLAYING) {
            return List.of();
        }
        return engine.getBuyableHexes(currentPlayer);
    }

    long getSpawnsLeft(GameEngine engine, GamePhase phase, long currentPlayer) {
        if (engine == null || phase != GamePhase.PLAYING) {
            return 0L;
        }
        return engine.getSpawnsLeft(currentPlayer);
    }

    List<String> getActionLogs(GameEngine engine, GamePhase phase) {
        if (engine == null || (phase != GamePhase.PLAYING && phase != GamePhase.FINISHED)) {
            return List.of();
        }
        return engine.getActionLogs();
    }

    Map<Long, PlayerEconomyDto> getPlayerEconomy(
            GameEngine engine,
            GameState gameState,
            GamePhase phase,
            long p1,
            long p2
    ) {
        if (engine == null || gameState == null || (phase != GamePhase.PLAYING && phase != GamePhase.FINISHED)) {
            return Map.of();
        }

        Map<Long, PlayerEconomyDto> result = new LinkedHashMap<>();
        result.put(p1, new PlayerEconomyDto(
                p1,
                gameState.getBudgetManager().getBudget(p1),
                engine.getSpawnsLeft(p1),
                engine.getLastInterest(p1)
        ));
        result.put(p2, new PlayerEconomyDto(
                p2,
                gameState.getBudgetManager().getBudget(p2),
                engine.getSpawnsLeft(p2),
                engine.getLastInterest(p2)
        ));
        return result;
    }
}
