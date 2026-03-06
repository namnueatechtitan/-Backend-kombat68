package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.dto.SpawnableHexDto;
import com.kombat.kombatbackend.engine.gamestate.GameEngine;
import com.kombat.kombatbackend.engine.gamestate.GameMode;
import com.kombat.kombatbackend.engine.gamestate.GamePhase;
import com.kombat.kombatbackend.engine.gamestate.GameState;
import com.kombat.kombatbackend.engine.gamestate.Hex;
import com.kombat.kombatbackend.engine.gamestate.Minion;
import com.kombat.kombatbackend.engine.gamestate.TurnPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

final class GameBotService {

    enum Result {
        NOOP,
        PLAYING,
        FINISHED
    }

    Result progressAutoModeIfNeeded(
            GameMode mode,
            GamePhase phase,
            GameEngine engine,
            GameState gameState,
            Random random
    ) {
        if (mode != GameMode.AUTO || engine == null || gameState == null || phase != GamePhase.PLAYING) {
            return Result.NOOP;
        }

        if (engine.isGameOver()) {
            gameState.setPhase(TurnPhase.END);
            return Result.FINISHED;
        }

        TurnPhase turnPhase = gameState.getPhase();
        long current = engine.getCurrentPlayer();

        if (turnPhase == TurnPhase.FREE_SPAWN) {
            tryBotFreeSpawn(engine, gameState, random, current);
        } else if (turnPhase == TurnPhase.PLAYER_ACTION) {
            tryBotBuyHex(engine, random, current);
            tryBotSpawn(engine, gameState, random, current);
            engine.executeTurn();
        }

        if (engine.isGameOver()) {
            gameState.setPhase(TurnPhase.END);
            return Result.FINISHED;
        }

        return Result.PLAYING;
    }

    Result runSolitaireBotIfNeeded(
            GameMode mode,
            GamePhase phase,
            GameEngine engine,
            GameState gameState,
            Random random,
            long p2
    ) {
        if (mode != GameMode.SOLITAIRE || engine == null || gameState == null || phase != GamePhase.PLAYING) {
            return Result.NOOP;
        }

        while (!engine.isGameOver() && engine.getCurrentPlayer() == p2) {
            TurnPhase turnPhase = gameState.getPhase();

            if (turnPhase == TurnPhase.FREE_SPAWN) {
                if (!tryBotFreeSpawn(engine, gameState, random, p2)) {
                    break;
                }
                continue;
            }

            if (turnPhase == TurnPhase.PLAYER_ACTION) {
                tryBotBuyHex(engine, random, p2);
                tryBotSpawn(engine, gameState, random, p2);
                engine.executeTurn();
                continue;
            }

            break;
        }

        if (engine.isGameOver()) {
            gameState.setPhase(TurnPhase.END);
            return Result.FINISHED;
        }

        return Result.PLAYING;
    }

    private boolean tryBotFreeSpawn(GameEngine engine, GameState gameState, Random random, long playerId) {
        List<String> availableTypes = new ArrayList<>(getTypesForPlayer(gameState, playerId));
        if (availableTypes.isEmpty()) {
            return false;
        }

        List<SpawnableHexDto> candidates = new ArrayList<>(getEmptyTerritoryHexes(engine, gameState, playerId));
        if (candidates.isEmpty()) {
            return false;
        }

        Collections.shuffle(availableTypes, random);
        Collections.shuffle(candidates, random);

        for (SpawnableHexDto hex : candidates) {
            for (String type : availableTypes) {
                if (engine.spawn(type, hex.getRow(), hex.getCol())) {
                    return true;
                }
            }
        }

        return false;
    }

    private void tryBotBuyHex(GameEngine engine, Random random, long playerId) {
        List<SpawnableHexDto> buyable = engine.getBuyableHexes(playerId);
        if (buyable.isEmpty()) {
            return;
        }

        if (random.nextInt(100) >= 60) {
            return;
        }

        Collections.shuffle(buyable, random);
        for (SpawnableHexDto hex : buyable) {
            if (engine.buyHex(hex.getRow(), hex.getCol())) {
                return;
            }
        }
    }

    private void tryBotSpawn(GameEngine engine, GameState gameState, Random random, long playerId) {
        if (random.nextInt(100) >= 75) {
            return;
        }

        List<String> availableTypes = new ArrayList<>(getTypesForPlayer(gameState, playerId));
        if (availableTypes.isEmpty()) {
            return;
        }

        List<SpawnableHexDto> candidates = new ArrayList<>(getEmptyTerritoryHexes(engine, gameState, playerId));
        if (candidates.isEmpty()) {
            return;
        }

        Collections.shuffle(availableTypes, random);
        Collections.shuffle(candidates, random);

        for (SpawnableHexDto hex : candidates) {
            for (String type : availableTypes) {
                if (engine.spawn(type, hex.getRow(), hex.getCol())) {
                    return;
                }
            }
        }
    }

    private List<String> getTypesForPlayer(GameState gameState, long playerId) {
        return gameState.getKinds(playerId)
                .keySet()
                .stream()
                .map(Enum::name)
                .toList();
    }

    private List<SpawnableHexDto> getEmptyTerritoryHexes(GameEngine engine, GameState gameState, long playerId) {
        Set<String> occupied = new HashSet<>();
        for (Minion m : gameState.getMinions()) {
            Hex pos = m.getPosition();
            if (pos != null) {
                occupied.add(pos.getX() + "," + pos.getY());
            }
        }

        return engine.getSpawnableHexes().stream()
                .filter(h -> h.getOwnerId() == playerId)
                .filter(h -> !occupied.contains(h.getRow() + "," + h.getCol()))
                .toList();
    }
}
