package com.kombat.kombatbackend.engine.gamestate;

import java.util.Objects;

public class MockGameState {

    public enum AttackResult { NO_TARGET, NO_DAMAGE, HIT, KILL }

    private final GameState state;
    private Minion currentMinion;

    public interface TerritoryRule {
        boolean canEnter(long playerId, int x, int y);
    }

    private TerritoryRule territoryRule = (pid, x, y) -> true;

    public MockGameState(GameState state) {
        this.state = Objects.requireNonNull(state);
    }

    public void setTerritoryRule(TerritoryRule rule) {
        this.territoryRule = Objects.requireNonNull(rule);
    }

    public void setCurrentMinion(Minion m) {
        this.currentMinion = Objects.requireNonNull(m);
    }

    public Minion getCurrentMinion() {
        return currentMinion;
    }

    public long currentPlayerId() {
        return currentMinion == null ? 0L : currentMinion.getOwnerId();
    }

    public long getBudget(long pid) {
        return state.getBudgetManager().getBudget(pid);
    }

    public void decreaseBudget(long pid, long amount) {
        if (amount > 0) {
            state.getBudgetManager().spendBudget(pid, amount);
        }
    }

    // ================= MOVE =================

    public boolean moveCurrentMinion(Direction dir) {

        if (currentMinion == null) return false;

        long pid = currentPlayerId();

        // move cost = 1
        if (getBudget(pid) < 1) return false;

        decreaseBudget(pid, 1);

        Hex from = currentMinion.getPosition();
        int[] next = nextCell(from.getX(), from.getY(), dir);

        int nx = next[0], ny = next[1];
        Board board = state.getBoard();

        if (!board.isInsideBoard(nx, ny)) return false;
        if (!territoryRule.canEnter(pid, nx, ny)) return false;

        Hex to = board.getHex(nx, ny);
        if (to.isOccupied()) return false;

        from.removeMinion();
        to.placeMinion(currentMinion);
        currentMinion.setPosition(to);

        return true;
    }

    // ================= SHOOT =================

    public AttackResult shoot(Direction dir, long expenditure) {

        if (currentMinion == null) return AttackResult.NO_TARGET;

        long pid = currentPlayerId();
        long totalCost = expenditure + 1;

        // insufficient budget → no-op
        if (getBudget(pid) < totalCost) {
            return AttackResult.NO_TARGET;
        }

        decreaseBudget(pid, totalCost);

        Minion target = findNearestInDirection(dir);
        if (target == null) return AttackResult.NO_TARGET;

        int dmg = computeDamage(expenditure, target);
        if (dmg <= 0) return AttackResult.NO_DAMAGE;

        return applyDamageAndResolve(target, dmg);
    }

    // ================= DAMAGE =================

    private static int computeDamage(long expenditure, Minion target) {
        int def = target.getDefenseFactor();
        long dmg = Math.max(1L, expenditure - def);
        if (dmg > Integer.MAX_VALUE) dmg = Integer.MAX_VALUE;
        return (int) dmg;
    }

    private AttackResult applyDamageAndResolve(Minion target, int dmg) {

        int hpAfter = Math.max(0, target.getHp() - dmg);
        target.setHp(hpAfter);

        if (hpAfter == 0) {
            removeFromBoardIfPresent(target);
            state.removeMinion(target);
            return AttackResult.KILL;
        }

        return AttackResult.HIT;
    }

    private static void removeFromBoardIfPresent(Minion target) {
        Hex pos = target.getPosition();
        if (pos != null && pos.getOccupant() == target) {
            pos.removeMinion();
        }
    }

    // ================= DIRECTION =================

    private static int[] nextCell(int x, int y, Direction dir) {
        int nx = x, ny = y;

        switch (dir) {
            case UP -> nx -= 1;
            case UPRIGHT -> { nx -= 1; ny += 1; }
            case DOWNRIGHT -> ny += 1;
            case DOWN -> nx += 1;
            case DOWNLEFT -> { nx += 1; ny -= 1; }
            case UPLEFT -> ny -= 1;
        }
        return new int[]{nx, ny};
    }

    private Minion findNearestInDirection(Direction dir) {

        Hex from = currentMinion.getPosition();
        int x = from.getX();
        int y = from.getY();
        Board board = state.getBoard();

        for (int step = 1; step <= 7; step++) {

            int[] next = nextCell(x, y, dir);
            x = next[0];
            y = next[1];

            if (!board.isInsideBoard(x, y)) break;

            Hex h = board.getHex(x, y);
            if (h.isOccupied()) {
                return h.getOccupant();
            }
        }

        return null;
    }
}