package com.kombat.kombatbackend.engine.gamestate;

import com.kombat.kombatbackend.engine.evaluator.Evaluator;
import com.kombat.kombatbackend.engine.strategy.*;
import com.kombat.kombatbackend.dto.SpawnableHexDto;
import java.util.*;

public class GameEngine {

    public static final long P1 = 1L;
    public static final long P2 = 2L;

    private final GameConfig config;
    private final GameState gameState;
    private final MockGameState mockGameState;
    private final Evaluator evaluator = new Evaluator();

    private boolean boughtThisTurn = false;
    private boolean spawnedThisTurn = false;
    private long currentPlayer = P1;

    private long turnsPlayedP1 = 0;
    private long turnsPlayedP2 = 0;

    private long spawnsUsedP1 = 0;
    private long spawnsUsedP2 = 0;

    private boolean freeSpawnDoneP1 = false;
    private boolean freeSpawnDoneP2 = false;

    private final boolean[][] territoryP1 = new boolean[8][8];
    private final boolean[][] territoryP2 = new boolean[8][8];

    private final Map<String, Long> localVars = new HashMap<>();
    private final Map<String, Long> globalVars = new HashMap<>();
    private final List<String> actionLog = new ArrayList<>();

    public GameEngine(GameConfig config,
                      GameState gameState,
                      MockGameState mockGameState) {

        this.config = config;
        this.gameState = gameState;
        this.mockGameState = mockGameState;

        initDefaultTerritories();
    }
    // =====================================================
    // TURN LIFECYCLE
    // =====================================================

    public void executeTurn() {
        if (gameState.getPhase() != TurnPhase.PLAYER_ACTION)
            return;

        gameState.setPhase(TurnPhase.EXECUTION);
        runStrategies(currentPlayer);

        if (currentPlayer == P1) turnsPlayedP1++;
        else turnsPlayedP2++;

        gameState.advanceTurn();

        switchPlayer();
        startPlayerActionPhase();
    }

    private void startPlayerActionPhase() {
        gameState.setPhase(TurnPhase.PLAYER_ACTION);
        beginTurn();
    }
    private void switchPlayer() {
        currentPlayer = (currentPlayer == P1) ? P2 : P1;

        boughtThisTurn = false;
        spawnedThisTurn = false;
    }

    private void beginTurn() {

        long pid = currentPlayer;

        boughtThisTurn = false;
        spawnedThisTurn = false;

        long add = config.turnBudget();
        if (add > 0)
            gameState.getBudgetManager().addBudget(pid, add);

        applyInterest(pid);
        enforceMaxBudget(pid);
    }

    // =====================================================
    // SPAWN / BUY
    // =====================================================

    public boolean buyHex(int x, int y) {

        if (gameState.getPhase() != TurnPhase.PLAYER_ACTION)
            return false;

        if (boughtThisTurn)
            return false;

        if (!gameState.getBoard().isInsideBoard(x, y))
            return false;

        if (isInAnyTerritory(x, y))
            return false;

        if (!isAdjacentToTerritory(currentPlayer, x, y))
            return false;

        long cost = config.hexPurchaseCost();

        if (gameState.getBudgetManager()
                .getBudget(currentPlayer) < cost)
            return false;

        gameState.getBudgetManager()
                .spendBudget(currentPlayer, cost);

        territory(currentPlayer)[x][y] = true;

        boughtThisTurn = true;

        return true;
    }
    public boolean spawn(String typeName, int x, int y) {
        if (!gameState.getBoard().isInsideBoard(x, y))
            return false;

        if (!territory(currentPlayer)[x][y])
            return false;

        if (gameState.getPhase() == TurnPhase.FREE_SPAWN) {
            return handleFreeSpawn(typeName, x, y);
        }

        if (gameState.getPhase() != TurnPhase.PLAYER_ACTION)
            return false;

        return handleNormalSpawn(typeName, x, y);
    }

    private boolean handleFreeSpawn(String typeName, int x, int y) {
        if (isFreeSpawnDoneForCurrentPlayer()) {
            return false;
        }

        if (!trySpawnMinion(currentPlayer, typeName, x, y)) {
            return false;
        }

        markFreeSpawnDone(currentPlayer);

        if (freeSpawnDoneP1 && freeSpawnDoneP2) {
            startFirstActionTurn();
        } else {
            switchPlayer();
        }

        return true;
    }

    private boolean handleNormalSpawn(String typeName, int x, int y) {

        if (spawnedThisTurn)
            return false;

        if (spawnsUsed(currentPlayer) >= config.maxSpawns())
            return false;

        if (gameState.getBudgetManager()
                .getBudget(currentPlayer) < config.spawnCost())
            return false;

        gameState.getBudgetManager()
                .spendBudget(currentPlayer, config.spawnCost());

        if (!trySpawnMinion(currentPlayer, typeName, x, y)) {
            gameState.getBudgetManager()
                    .addBudget(currentPlayer, config.spawnCost());
            return false;
        }

        incrementSpawns(currentPlayer);
        spawnedThisTurn = true;

        return true;
    }

    public long getCurrentPlayer() {
        return currentPlayer;
    }

    private void startFirstActionTurn() {
        currentPlayer = P1;
        gameState.advanceTurn();
        startPlayerActionPhase();
    }

    private boolean trySpawnMinion(long playerId, String typeName, int x, int y) {
        try {
            gameState.spawnMinion(
                    playerId,
                    typeName,
                    (int) config.initHp(),
                    x,
                    y);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFreeSpawnDoneForCurrentPlayer() {
        return currentPlayer == P1 ? freeSpawnDoneP1 : freeSpawnDoneP2;
    }

    private void markFreeSpawnDone(long playerId) {
        if (playerId == P1) {
            freeSpawnDoneP1 = true;
        } else {
            freeSpawnDoneP2 = true;
        }
    }
    // =====================================================
    // STRATEGY EXECUTION
    // =====================================================

    private void runStrategies(long pid) {

        actionLog.clear();

        SpecialVars special = new SpecialVars() {
            @Override
            public long row() {
                Minion m = mockGameState.getCurrentMinion();
                return (m == null) ? 0 : m.getPosition().getX();
            }

            @Override
            public long col() {
                Minion m = mockGameState.getCurrentMinion();
                return (m == null) ? 0 : m.getPosition().getY();
            }

            @Override
            public long budget() {
                return gameState.getBudgetManager().getBudget(pid);
            }

            @Override public long interestRate() { return 0; }
            @Override public long maxBudget() { return config.maxBudget(); }
            @Override public long spawnsLeft() { return 0; }
            @Override public long random0to999() { return new Random().nextInt(1000); }
        };

        InfoProvider info = new InfoProvider() {

            @Override
            public long opponent() {
                Minion m = mockGameState.getCurrentMinion();
                if (m == null) return 0;
                return (m.getOwnerId() == P1) ? P2 : P1;
            }

            @Override
            public long ally() {
                Minion m = mockGameState.getCurrentMinion();
                if (m == null) return 0;
                return m.getOwnerId();
            }

            @Override
            public long nearby(Direction dir) {

                Minion m = mockGameState.getCurrentMinion();
                if (m == null) return 0;

                int nx = m.getPosition().getX();
                int ny = m.getPosition().getY();

                switch (dir) {
                    case UP -> nx -= 1;
                    case UPRIGHT -> { nx -= 1; ny += 1; }
                    case DOWNRIGHT -> ny += 1;
                    case DOWN -> nx += 1;
                    case DOWNLEFT -> { nx += 1; ny -= 1; }
                    case UPLEFT -> ny -= 1;
                }

                if (!gameState.getBoard().isInsideBoard(nx, ny))
                    return 0;

                Hex h = gameState.getBoard().getHex(nx, ny);
                if (!h.isOccupied())
                    return 0;

                return h.getOccupant().getOwnerId();
            }
        };

        EvalContext eval = new EvalContext(localVars, globalVars, special, info);
        ExecContext exec = new ExecContext(eval, localVars, globalVars, actionLog);

        Map<Minion, Strategy> bindings =
                gameState.buildStrategyBindings(pid);

        List<Minion> owned = new ArrayList<>();
        for (Minion m : gameState.getMinions()) {
            if (m.getOwnerId() == pid)
                owned.add(m);
        }

        evaluator.runMinionsOldestToNewest(
                owned,
                bindings,
                mockGameState,
                exec
        );
    }

    // =====================================================
    // WIN CONDITION
    // =====================================================

    public boolean isGameOver() {

        if (gameState.getMinions().isEmpty())
            return false;

        boolean p1Alive = countLiving(P1) > 0;
        boolean p2Alive = countLiving(P2) > 0;

        if (!p1Alive && !p2Alive) return true;
        if (!p1Alive) return true;
        if (!p2Alive) return true;

        return turnsPlayedP1 >= config.maxTurns()
                && turnsPlayedP2 >= config.maxTurns();
    }

    public String getWinner() {

        int c1 = countLiving(P1);
        int c2 = countLiving(P2);

        if (c1 == 0 && c2 == 0) return "TIE";
        if (c1 == 0) return "P2";
        if (c2 == 0) return "P1";

        if (turnsPlayedP1 >= config.maxTurns()
                && turnsPlayedP2 >= config.maxTurns()) {

            if (c1 != c2) return (c1 > c2) ? "P1" : "P2";

            long hp1 = sumHp(P1);
            long hp2 = sumHp(P2);
            if (hp1 != hp2) return (hp1 > hp2) ? "P1" : "P2";

            long b1 = gameState.getBudgetManager().getBudget(P1);
            long b2 = gameState.getBudgetManager().getBudget(P2);
            if (b1 != b2) return (b1 > b2) ? "P1" : "P2";

            return "TIE";
        }

        return "ONGOING";
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void applyInterest(long pid) {

        long budget = gameState.getBudgetManager().getBudget(pid);
        if (budget < 1 || config.interestPct() <= 0) return;

        long turns = (pid == P1) ? turnsPlayedP1 : turnsPlayedP2;
        long t = Math.max(1, turns + 1);

        double raw = config.interestPct()
                * Math.log10(budget)
                * Math.log(t);

        if (raw <= 0 || Double.isNaN(raw) || Double.isInfinite(raw))
            return;

        long interestRate = (long) raw;
        long interest = (long) (budget * interestRate / 100.0);

        if (interest > 0)
            gameState.getBudgetManager().addBudget(pid, interest);
    }

    private void enforceMaxBudget(long pid) {

        long max = config.maxBudget();
        long current = gameState.getBudgetManager().getBudget(pid);

        if (current > max)
            gameState.getBudgetManager()
                    .spendBudget(pid, current - max);
    }

    private void initDefaultTerritories() {

        territoryP1[0][0] = true;
        territoryP1[0][1] = true;
        territoryP1[0][2] = true;
        territoryP1[1][0] = true;
        territoryP1[1][1] = true;

        territoryP2[7][7] = true;
        territoryP2[7][6] = true;
        territoryP2[7][5] = true;
        territoryP2[6][7] = true;
        territoryP2[6][6] = true;
    }

    private boolean[][] territory(long pid) {
        return (pid == P2) ? territoryP2 : territoryP1;
    }

    private boolean isInAnyTerritory(int x, int y) {
        return territoryP1[x][y] || territoryP2[x][y];
    }
    private boolean isAdjacentToTerritory(long pid, int row, int col) {

        boolean[][] terr = territory(pid);

        boolean isOddRow = (row % 2 == 1);

        int[][] directions;

        if (isOddRow) {
            directions = new int[][]{
                    {0, -1}, {0, 1},
                    {-1, 0}, {-1, 1},
                    {1, 0}, {1, 1}
            };
        } else {
            directions = new int[][]{
                    {0, -1}, {0, 1},
                    {-1, -1}, {-1, 0},
                    {1, -1}, {1, 0}
            };
        }

        for (int[] d : directions) {
            int nr = row + d[0];
            int nc = col + d[1];

            if (gameState.getBoard().isInsideBoard(nr, nc)
                    && terr[nr][nc]) {
                return true;
            }
        }

        return false;
    }



    private int countLiving(long pid) {
        int count = 0;
        for (Minion m : gameState.getMinions()) {
            if (m.getOwnerId() == pid && m.getHp() > 0)
                count++;
        }
        return count;
    }

    private long sumHp(long pid) {
        long sum = 0;
        for (Minion m : gameState.getMinions()) {
            if (m.getOwnerId() == pid && m.getHp() > 0)
                sum += m.getHp();
        }
        return sum;
    }

    private long spawnsUsed(long pid) {
        return (pid == P2) ? spawnsUsedP2 : spawnsUsedP1;
    }

    private void incrementSpawns(long pid) {
        if (pid == P2) spawnsUsedP2++;
        else spawnsUsedP1++;
    }
    public List<SpawnableHexDto> getSpawnableHexes() {

        List<SpawnableHexDto> result = new ArrayList<>();

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {

                if (territoryP1[r][c]) {
                    result.add(new SpawnableHexDto(r, c, P1));
                }

                if (territoryP2[r][c]) {
                    result.add(new SpawnableHexDto(r, c, P2));
                }
            }
        }

        return result;
    }

    public List<SpawnableHexDto> getBuyableHexes(long playerId) {

        List<SpawnableHexDto> result = new ArrayList<>();

        if (gameState.getPhase() != TurnPhase.PLAYER_ACTION) {
            return result;
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {

                if (isInAnyTerritory(r, c)) {
                    continue;
                }

                if (isAdjacentToTerritory(playerId, r, c)) {
                    result.add(new SpawnableHexDto(r, c, playerId));
                }
            }
        }

        return result;
    }

}
