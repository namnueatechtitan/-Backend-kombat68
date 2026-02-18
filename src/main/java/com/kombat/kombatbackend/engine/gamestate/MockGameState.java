package com.kombat.kombatbackend.engine.gamestate;

import java.util.Objects;

public class MockGameState {

    // 2) INTERFACE
    public enum AttackResult { NO_TARGET, NO_DAMAGE, HIT, KILL }

    // 3) INTERNAL
    private final GameState state;
    private Minion currentMinion; // minion ที่กำลังรัน strategy

    /** Territory rule for move (duel mode). */
    public interface TerritoryRule {
        boolean canEnter(long playerId, int x, int y);
    }

    private TerritoryRule territoryRule = (pid, x, y) -> true;

    // 1) CONSTRUCTOR
    public MockGameState(GameState state) {
        this.state = Objects.requireNonNull(state);
    }

    public void setTerritoryRule(TerritoryRule rule) {
        this.territoryRule = Objects.requireNonNull(rule);
    }

    // 2) INTERFACE
    public long currentPlayerId() {
        Minion m = currentMinion;
        return (m == null) ? 0L : m.getOwnerId();
    }

    public long getBudget(long pid) {
        return state.getBudgetManager().getBudget(pid);
    }

    public void decreaseBudget(long pid, long amount) {
        if (amount < 0) return;
        state.getBudgetManager().spendBudget(pid, amount);
    }

    public void setCurrentMinion(Minion m) {
        this.currentMinion = Objects.requireNonNull(m);
    }

    public Minion getCurrentMinion() {
        return currentMinion;
    }

    public boolean moveCurrentMinion(Direction dir) {
        if (currentMinion == null) return false;

        Hex from = currentMinion.getPosition();
        int x = from.getX();
        int y = from.getY();

        // แยก mapping ออกไป
        int[] next = nextCell(x, y, dir);
        int nx = next[0], ny = next[1];

        Board board = state.getBoard();
        if (!board.isInsideBoard(nx, ny)) return false;

        // duel: must stay inside owner's territory
        long pid = currentPlayerId();
        if (!territoryRule.canEnter(pid, nx, ny)) return false;

        Hex to = board.getHex(nx, ny);
        if (to.isOccupied()) return false;

        // move
        from.removeMinion();
        to.placeMinion(currentMinion);
        currentMinion.setPosition(to);
        return true;
    }

    // 3) INTERNAL (movement mapping)
    private static int[] nextCell(int x, int y, Direction dir) {
        int nx = x, ny = y;
        // NOTE: mock mapping บน grid 8x8 (ยังไม่ใช่ hex-geometry จริง)
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

    // 2) INTERFACE
    public AttackResult shoot(Direction dir, long expenditure) {
        if (currentMinion == null) return AttackResult.NO_TARGET;

        Minion target = findNearestInDirection(dir);
        if (target == null) return AttackResult.NO_TARGET;

        int dmg = computeDamage(expenditure, target);
        if (dmg <= 0) return AttackResult.NO_DAMAGE;

        return applyDamageAndResolve(target, dmg);
    }

    // 3) INTERNAL HELPERS (shoot)
    /// แก้ไขให้ test ผ่าน
    private static int computeDamage(long expenditure, Minion target) {
        int def = target.getDefenseFactor();
        long dmg = Math.max(1L, expenditure - def); // damage = max(1, x - def)
        if (dmg > Integer.MAX_VALUE) dmg = Integer.MAX_VALUE;
        return (int) dmg;
    }

    private AttackResult applyDamageAndResolve(Minion target, int dmg) {
        int hpBefore = target.getHp();
        int hpAfter = Math.max(0, hpBefore - dmg); // clamp HP >= 0
        target.setHp(hpAfter);

        if (hpAfter == 0) {
            // Spec: HP==0 -> remove from board AND from the minion roster.
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


    // 3) INTERNAL (HELPERS)
    private Minion findNearestInDirection(Direction dir) {
        Hex from = currentMinion.getPosition();
        int x = from.getX();
        int y = from.getY();

        Board board = state.getBoard();
        for (int step = 1; step <= 7; step++) {
            int nx = x, ny = y;
            switch (dir) {
                case UP -> nx = x - step;
                case UPRIGHT -> { nx = x - step; ny = y + step; }
                case DOWNRIGHT -> ny = y + step;
                case DOWN -> nx = x + step;
                case DOWNLEFT -> { nx = x + step; ny = y - step; }
                case UPLEFT -> ny = y - step;
            }
            if (!board.isInsideBoard(nx, ny)) break;

            Hex h = board.getHex(nx, ny);
            if (!h.isOccupied()) continue;

            Minion occ = h.getOccupant();
            return occ; // เจอใครก็ยิงได้ (ally/enemy) ตามสเปค self-destruction

            /**
            *Minion occ = h.getOccupant();
            *if (occ.getOwnerId() == currentMinion.getOwnerId()) return null; // เจอเพื่อน -> ยิงไม่ได้
            *return occ; // เจอศัตรู -> ยิงได้
            */

        }
        return null;
    }
}
