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
    void gameIsNotOverAfterOnlyFirstFreeSpawn() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertTrue(service.spawn("FIGHTER", 0, 0));

        assertFalse(service.isGameOver());
        assertEquals("ONGOING", service.getWinner());
        assertEquals(TurnPhase.FREE_SPAWN, service.getTurnPhase());
        assertEquals(GameService.P2, service.getCurrentPlayer());
    }

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

        assertTrue(service.spawn("FIGHTER", 0, 1));
    }

    @Test
    void playerCannotBuyHexAfterSpawningInSameTurn() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        assertTrue(service.spawn("FIGHTER", 0, 1));

        assertFalse(service.buyHex(1, 2));
    }

    @Test
    void playerCanBuyHexBeforeSpawningInSameTurn() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        assertTrue(service.buyHex(1, 2));
        assertTrue(service.spawn("FIGHTER", 0, 1));
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

    @Test
    void spawnsLeftDecreasesAfterSpawnInPlayerActionPhase() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        assertEquals(2, service.getSpawnsLeft());

        assertTrue(service.spawn("FIGHTER", 0, 1));

        assertEquals(1, service.getSpawnsLeft());
    }

    @Test
    void actionLogsAreAvailableAfterEndTurnExecutesStrategies() {
        GameService service = new GameService();
        service.initFullGame(buildRequest());

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        service.endTurn();

        var actionLogs = service.getActionLogs();
        assertFalse(actionLogs.isEmpty());
        assertTrue(actionLogs.stream().anyMatch(log -> log.contains("DONE")));
    }

    @Test
    void moveStrategyConsumesExactlyOneBudgetInExecution() {
        GameService service = new GameService();
        service.initFullGame(buildRequest("move down; done;", "done;"));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        long budgetBefore = service.getGameState().getBudgetManager().getBudget(GameService.P1);

        service.endTurn();

        long budgetAfter = service.getGameState().getBudgetManager().getBudget(GameService.P1);
        assertEquals(budgetBefore - 1, budgetAfter);
        assertTrue(service.getActionLogs().stream().anyMatch(log -> log.contains("MOVE DOWN")));
    }

    @Test
    void minionCanMoveOutsideOwnedSpawnableTerritory() {
        GameService service = new GameService();
        service.initFullGame(buildRequest("move down; done;", "done;"));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        service.endTurn(); // P1: (0,0) -> (1,0) (still initial territory)
        service.endTurn(); // P2: done
        service.endTurn(); // P1: (1,0) -> (2,0) (outside initial territory)

        var p1Minion = service.getGameState().getMinions().stream()
                .filter(m -> m.getOwnerId() == GameService.P1)
                .findFirst()
                .orElseThrow();

        assertEquals(2, p1Minion.getPosition().getX());
        assertEquals(0, p1Minion.getPosition().getY());
    }

    @Test
    void moveNoOpStillConsumesOneBudgetAndLogsPlayerPrefix() {
        GameService service = new GameService();
        service.initFullGame(buildRequest("move up; done;", "done;"));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        long budgetBefore = service.getGameState().getBudgetManager().getBudget(GameService.P1);
        service.endTurn();
        long budgetAfter = service.getGameState().getBudgetManager().getBudget(GameService.P1);

        assertEquals(budgetBefore - 1, budgetAfter);
        assertTrue(service.getActionLogs().stream().anyMatch(log -> log.contains("TURN 1 EXECUTE P1")));
        assertTrue(service.getActionLogs().stream().anyMatch(log -> log.contains("P1 MOVE UP NO-OP")));
    }

    @Test
    void shootNoTargetConsumesTwoBudgetAndLogsPlayerPrefix() {
        GameService service = new GameService();
        service.initFullGame(buildRequest("shoot up 1; done;", "done;"));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        long budgetBefore = service.getGameState().getBudgetManager().getBudget(GameService.P1);
        service.endTurn();
        long budgetAfter = service.getGameState().getBudgetManager().getBudget(GameService.P1);

        assertEquals(budgetBefore - 2, budgetAfter);
        assertTrue(service.getActionLogs().stream().anyMatch(log -> log.contains("P1 SHOOT UP x=1 from=(0,0) NO_TARGET")));
    }

    @Test
    void opponentInfoUsesDistanceDirectionEncodingSoMoveBranchCanRun() {
        GameService service = new GameService();
        service.initFullGame(buildRequest(
                "op = opponent; if (op - 0) then { if (op / 10 - 1) then { if (op % 10 - 2) then move downright; else done; } else done; } else done; done;",
                "done;"
        ));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        service.endTurn();

        assertTrue(service.getActionLogs().stream().anyMatch(log -> log.contains("P1 MOVE DOWNRIGHT")));
    }


    @Test
    void strategyExecutionUsesRuntimeGameStateNotParserDummyGame() {
        GameService service = new GameService();
        service.initFullGame(buildRequest("move down; done;", "done;"));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        service.endTurn();

        assertTrue(service.getActionLogs().stream().anyMatch(log -> log.contains("P1 MOVE DOWN")));
        assertTrue(service.getActionLogs().stream().noneMatch(log -> log.contains("P0 MOVE")));
    }


    @Test
    void endTurnAfterGameOverIsGracefulAndTurnPhaseIsEnd() {
        GameService service = new GameService();
        service.initFullGame(buildRequestWithMaxTurns("done;", "done;", 1));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        service.endTurn(); // P1 turn
        service.endTurn(); // P2 turn => both reached max turns

        assertTrue(service.isGameOver());
        assertEquals(com.kombat.kombatbackend.engine.gamestate.GamePhase.FINISHED, service.getPhase());
        assertEquals(TurnPhase.END, service.getTurnPhase());

        assertDoesNotThrow(service::endTurn);
        assertEquals(TurnPhase.END, service.getTurnPhase());
    }

    @Test
    void playerEconomyContainsBothPlayersWithInterestAfterTurnProgress() {
        GameService service = new GameService();
        service.initFullGame(buildRequest("done;", "done;"));

        assertTrue(service.spawn("FIGHTER", 0, 0));
        assertTrue(service.spawn("FIGHTER", 7, 7));

        service.endTurn();
        service.endTurn();
        service.endTurn();

        var economy = service.getPlayerEconomy();
        assertTrue(economy.containsKey(GameService.P1));
        assertTrue(economy.containsKey(GameService.P2));

        var p1 = economy.get(GameService.P1);
        var p2 = economy.get(GameService.P2);

        assertNotNull(p1);
        assertNotNull(p2);

        assertEquals(service.getGameState().getBudgetManager().getBudget(GameService.P1), p1.getBudget());
        assertEquals(service.getGameState().getBudgetManager().getBudget(GameService.P2), p2.getBudget());
        assertEquals(service.getSpawnsLeft(), economy.get(service.getCurrentPlayer()).getSpawnsLeft());
        assertTrue(p1.getLastInterest() >= 0);
        assertTrue(p2.getLastInterest() >= 0);
        assertTrue(p1.getLastInterestRate() >= 0);
        assertTrue(p2.getLastInterestRate() >= 0);
    }

    private static GameInitRequest buildRequest() {
        return buildRequest("done;", "done;");
    }

    private static GameInitRequest buildRequest(String p1Strategy, String p2Strategy) {
        GameInitRequest req = new GameInitRequest();
        req.setConfig(new GameConfig(100, 100, 1000, 100, 90, 5000, 0, 10, 2));
        req.setMode(GameMode.DUEL);
        req.setPlayer1(buildPlayer(p1Strategy));
        req.setPlayer2(buildPlayer(p2Strategy));
        return req;
    }


    private static GameInitRequest buildRequestWithMaxTurns(String p1Strategy, String p2Strategy, long maxTurns) {
        GameInitRequest req = new GameInitRequest();
        req.setConfig(new GameConfig(100, 100, 1000, 100, 90, 5000, 0, maxTurns, 2));
        req.setMode(GameMode.DUEL);
        req.setPlayer1(buildPlayer(p1Strategy));
        req.setPlayer2(buildPlayer(p2Strategy));
        return req;
    }

    private static PlayerSetupRequest buildPlayer(String strategyCode) {
        PlayerSetupRequest player = new PlayerSetupRequest();
        player.setCharacter(CharacterType.HUMAN);

        MinionSetup fighter = new MinionSetup();
        fighter.setType("FIGHTER");
        fighter.setDefenseFactor(1);
        fighter.setStrategy(strategyCode);

        player.setMinions(List.of(fighter));
        return player;
    }
}
