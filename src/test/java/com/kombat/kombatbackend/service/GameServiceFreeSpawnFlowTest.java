package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.dto.GameInitRequest;
import com.kombat.kombatbackend.dto.MinionSetup;
import com.kombat.kombatbackend.dto.PlayerSetupRequest;
import com.kombat.kombatbackend.engine.gamestate.CharacterType;
import com.kombat.kombatbackend.engine.gamestate.GameConfig;
import com.kombat.kombatbackend.engine.gamestate.GameMode;
import com.kombat.kombatbackend.engine.gamestate.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameServiceFreeSpawnFlowTest {

    @Test
    void afterBothFreeSpawnsGameStartsAtP1Turn1AndNoMoreFreeSpawn() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertEquals(TurnPhase.FREE_SPAWN, service.getTurnPhase());
        assertEquals(GameService.P1, service.getCurrentPlayer());

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertEquals(TurnPhase.FREE_SPAWN, service.getTurnPhase());
        assertEquals(GameService.P2, service.getCurrentPlayer());

        assertTrue(service.spawn("FIGHTER", 7, 7));

        assertEquals(TurnPhase.PLAYER_ACTION, service.getTurnPhase());
        assertEquals(GameService.P1, service.getCurrentPlayer());
        assertEquals(1, service.getGameState().getTurnNumber());

        long initialBudget = service.getConfig().initBudget();
        long expectedP1BudgetAfterTurnStart = initialBudget + service.getConfig().turnBudget();
        assertEquals(
                expectedP1BudgetAfterTurnStart,
                service.getGameState().getBudgetManager().getBudget(GameService.P1)
        );

        assertFalse(service.spawn("FIGHTER", 0, 1));
    }

    @Test
    void playerCanBuyHexAfterSpawningInSameTurn() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        assertTrue(service.spawn("FIGHTER", 0, 1));

        assertTrue(service.buyHex(1, 2));
    }

    @Test
    void buyableHexesAreExposedForCurrentPlayerInPlayerActionPhase() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        var buyable = service.getBuyableHexes();

        assertFalse(buyable.isEmpty());
        assertTrue(
                buyable.stream().anyMatch(h -> h.getRow() == 1 && h.getCol() == 2 && h.getOwnerId() == GameService.P1)
        );
    }

    private static GameInitRequest buildRequest() {
        GameInitRequest req = new GameInitRequest();
        req.setConfig(new GameConfig(100, 1000, 1000, 100, 90, 5000, 0, 10, 2));
        req.setMode(GameMode.DUEL);
        req.setPlayer1(buildPlayer());
        req.setPlayer2(buildPlayer());
        return req;
    }

    private static PlayerSetupRequest buildPlayer() {
        PlayerSetupRequest player = new PlayerSetupRequest();
        player.setCharacter(CharacterType.HUMAN);

        MinionSetup fighter = new MinionSetup();
        fighter.setType("FIGHTER");
        fighter.setDefenseFactor(1);
        fighter.setStrategy("done;");

        player.setMinions(List.of(fighter));
        return player;
    }
}
