package com.kombat.kombatbackend.terminal;
import com.kombat.kombatbackend.engine.strategy.Strategy;
import com.kombat.kombatbackend.engine.evaluator.Evaluator;
import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.engine.parser.SyntaxException;
import com.kombat.kombatbackend.engine.strategy.EvalContext;
import com.kombat.kombatbackend.engine.strategy.ExecContext;
import com.kombat.kombatbackend.engine.strategy.InfoProvider;
import com.kombat.kombatbackend.engine.strategy.SpecialVars;

import java.util.*;

/// Terminal duel controller (2-player).
/// Rules implemented from the course spec:
/// - separate budgets + separate territories (top-left vs bottom-right)
/// - at most 1 BUY and 1 SPAWN per player's turn
/// - end turn with: step (runs strategies for the current player's minions)
public final class DuelGameController {

    public static final long P1 = 1L;
    public static final long P2 = 2L;

    private final GameConfig config;
    private final GameState gs;
    private final MockGameState mg;
    private final Evaluator evaluator = new Evaluator();

    // territories
    private final boolean[][] territoryP1 = new boolean[8][8];
    private final boolean[][] territoryP2 = new boolean[8][8];

    // spawn counts
    private long spawnsUsedP1 = 0;
    private long spawnsUsedP2 = 0;

    // turn state
    private long currentPlayer = P1;
    private long turnsPlayedP1 = 0;
    private long turnsPlayedP2 = 0;
    private boolean turnInitialized = false;
    private boolean boughtThisTurn = false;
    private boolean spawnedThisTurn = false;

    // evaluator contexts
    private final Map<String, Long> localVars = new HashMap<String, Long>();
    private final Map<String, Long> globalVars = new HashMap<String, Long>();
    private final List<String> actionLog = new ArrayList<String>();
    private final ExecContext exec;

    // randomness
    private final Random rnd = new Random(1);

    private DuelGameController(GameConfig config, SetupResult setup) {
        this.config = Objects.requireNonNull(config, "config");
        this.gs = Objects.requireNonNull(setup.getGameState(), "gameState");
        this.mg = Objects.requireNonNull(setup.getMock(), "mock");

        initDefaultTerritories();

        // enforce territory restriction for MOVE
        mg.setTerritoryRule((pid, x, y) -> isInTerritory(pid, x, y));

        // SpecialVars
        SpecialVars special = new SpecialVars() {
            @Override public long row() {
                Minion m = mg.getCurrentMinion();
                return (m == null) ? 0 : m.getPosition().getX();
            }
            @Override public long col() {
                Minion m = mg.getCurrentMinion();
                return (m == null) ? 0 : m.getPosition().getY();
            }
            @Override public long budget() {
                long pid = mg.currentPlayerId();
                return gs.getBudgetManager().getBudget(pid);
            }

            @Override public long interestRate() {
                long pid = mg.currentPlayerId();
                long m = gs.getBudgetManager().getBudget(pid);
                if (m < 1L || config.interestPct() <= 0) return 0;

                long t = Math.max(1L, turnsPlayed(pid) + 1L);
                double r = (double) config.interestPct() * Math.log10((double) m) * Math.log((double) t);
                if (Double.isNaN(r) || Double.isInfinite(r) || r <= 0) return 0;
                return Math.max(0L, (long) r); // truncate to integer
            }

            @Override public long maxBudget() { return config.maxBudget(); }

            @Override public long spawnsLeft() {
                long pid = mg.currentPlayerId();
                return Math.max(0L, config.maxSpawns() - spawnsUsed(pid));
            }

            @Override public long random0to999() { return rnd.nextInt(1000); }
        };

        // InfoProvider (ally/opponent/nearby)
        InfoProvider info = new InfoProvider() {
            @Override public long opponent() { return closestInAnyDirection(false); }
            @Override public long ally() { return closestInAnyDirection(true); }
            @Override public long nearby(Direction dir) { return nearbyEncoding(dir); }

            private long closestInAnyDirection(boolean wantAlly) {
                Minion me = mg.getCurrentMinion();
                if (me == null) return 0;

                long myPid = me.getOwnerId();
                long best = 0;
                int bestDist = Integer.MAX_VALUE;
                int bestDirNum = Integer.MAX_VALUE;

                for (Direction d : Direction.values()) {
                    Minion target = firstMinionInDirection(me, d);
                    if (target == null) continue;

                    boolean isAlly = target.getOwnerId() == myPid;
                    if (wantAlly != isAlly) continue;

                    int dist = firstMinionDistanceInDirection(me, d);
                    if (dist <= 0) continue;
                    int dirNum = d.dirNum();

                    if (dist < bestDist || (dist == bestDist && dirNum < bestDirNum)) {
                        bestDist = dist;
                        bestDirNum = dirNum;
                        best = (long) dist * 10L + (long) dirNum;
                    }
                }
                return best;
            }

            private long nearbyEncoding(Direction dir) {
                Minion me = mg.getCurrentMinion();
                if (me == null) return 0;

                Minion target = firstMinionInDirection(me, dir);
                if (target == null) return 0;

                int dist = firstMinionDistanceInDirection(me, dir);
                if (dist <= 0) return 0;

                int x = digits(target.getHp());
                int y = digits(target.getDefenseFactor());
                int z = Math.max(1, dist);

                long enc = 100L * x + 10L * y + z;
                if (target.getOwnerId() == me.getOwnerId()) enc = -enc;
                return enc;
            }

            private int digits(int v) {
                int n = Math.abs(v);
                if (n == 0) return 1;
                int d = 0;
                while (n > 0) { d++; n /= 10; }
                return d;
            }

            private Minion firstMinionInDirection(Minion me, Direction dir) {
                Board b = gs.getBoard();
                Hex from = me.getPosition();
                int x = from.getX(), y = from.getY();

                for (int step = 1; step <= 7; step++) {
                    int nx = x, ny = y;
                    switch (dir) {
                        case UP: nx = x - step; break;
                        case DOWN: nx = x + step; break;
                        case UPLEFT: ny = y - step; break;
                        case UPRIGHT: ny = y + step; break;
                        case DOWNLEFT: nx = x + step; ny = y - step; break;
                        case DOWNRIGHT: nx = x + step; ny = y + step; break;
                    }
                    if (!b.isInsideBoard(nx, ny)) break;
                    Hex h = b.getHex(nx, ny);
                    if (h.isOccupied()) return h.getOccupant();
                }
                return null;
            }

            private int firstMinionDistanceInDirection(Minion me, Direction dir) {
                Board b = gs.getBoard();
                Hex from = me.getPosition();
                int x = from.getX(), y = from.getY();

                for (int step = 1; step <= 7; step++) {
                    int nx = x, ny = y;
                    switch (dir) {
                        case UP: nx = x - step; break;
                        case DOWN: nx = x + step; break;
                        case UPLEFT: ny = y - step; break;
                        case UPRIGHT: ny = y + step; break;
                        case DOWNLEFT: nx = x + step; ny = y - step; break;
                        case DOWNRIGHT: nx = x + step; ny = y + step; break;
                    }
                    if (!b.isInsideBoard(nx, ny)) break;
                    Hex h = b.getHex(nx, ny);
                    if (h.isOccupied()) return step;
                }
                return -1;
            }
        };

        EvalContext eval = new EvalContext(localVars, globalVars, special, info);
        this.exec = new ExecContext(eval, localVars, globalVars, actionLog);
    }

    public static DuelGameController create(GameConfig config,
                                            List<KindInput> p1Kinds,
                                            List<KindInput> p2Kinds) throws SyntaxException {
        SetupResult setup = AutoModeSetup.createDuelGame(config, p1Kinds, p2Kinds);
        return new DuelGameController(config, setup);
    }

    public void runTerminal() { runTerminal(new Scanner(System.in)); }

    public void runTerminal(Scanner sc) {
        System.out.println("=== KOMBAT (terminal DUEL) ===");
        System.out.println("config: spawn_cost=" + config.spawnCost()
                + ", hex_purchase_cost=" + config.hexPurchaseCost()
                + ", init_budget=" + config.initBudget()
                + ", init_hp=" + config.initHp()
                + ", turn_budget=" + config.turnBudget()
                + ", max_budget=" + config.maxBudget()
                + ", interest_pct=" + config.interestPct()
                + ", max_turns=" + config.maxTurns()
                + ", max_spawns=" + config.maxSpawns());

        printKinds();

        freeSpawnSetup(sc, P1);
        freeSpawnSetup(sc, P2);

        // 2.1 เพิ่มตัวแปร quit
        boolean quit = false;

        while (!isGameOver()) {

            if (!turnInitialized) beginPlayerTurn(currentPlayer);

            System.out.println();
            System.out.println("TURN " + (totalTurnsPlayed() + 1) + " (P" + currentPlayer + ")");
            printBoard();
            printBudgetsAndSpawns();
            System.out.println("Commands: kinds | buy x y | spawn TYPE_OR_KIND x y | step | log | board | quit  (x,y = 1..8; 0..7 legacy)");
            System.out.print("> ");

            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            // 2.2 แก้คำสั่ง quit ให้ตั้งค่า quit ก่อน break
            if (line.equalsIgnoreCase("quit")) { quit = true; break; }

            if (line.equalsIgnoreCase("board")) { printBoard(); continue; }
            if (line.equalsIgnoreCase("log")) { printLog(); continue; }
            if (line.equalsIgnoreCase("kinds")) { printKinds(); continue; }

            if (line.equalsIgnoreCase("step")) {
                runStrategiesOnce(currentPlayer);
                endPlayerTurn();
                continue;
            }

            String[] tok = line.split("\\s+");
            try {
                if (tok[0].equalsIgnoreCase("buy") && tok.length == 3) {
                    if (boughtThisTurn) {
                        System.out.println("BUY NO-OP (already bought this turn)");
                        continue;
                    }
                    int x = parseCoord(tok[1]);
                    int y = parseCoord(tok[2]);
                    boolean ok = buyHex(currentPlayer, x, y);
                    if (ok) boughtThisTurn = true;
                    System.out.println(ok ? "BUY OK" : "BUY NO-OP");
                } else if (tok[0].equalsIgnoreCase("spawn") && tok.length == 4) {
                    if (spawnedThisTurn) {
                        System.out.println("SPAWN NO-OP (already spawned this turn)");
                        continue;
                    }
                    MinionType type = resolveType(currentPlayer, tok[1]);
                    int x = parseCoord(tok[2]);
                    int y = parseCoord(tok[3]);
                    boolean ok = spawn(currentPlayer, type, x, y);
                    if (ok) spawnedThisTurn = true;
                    System.out.println(ok ? "SPAWN OK" : "SPAWN NO-OP");
                } else {
                    System.out.println("Unknown command.");
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }

        // 2.3 หลังหลุดลูป ให้พิมพ์สรุปผู้ชนะ (ถ้าไม่ได้ quit)
        if (!quit) {
            printGameOverSummary();
        }

        System.out.println("=== END ===");
    }

    // ---------------- Setup free spawn ----------------

    private void freeSpawnSetup(Scanner sc, long pid) {
        System.out.println("[Setup] Player " + pid + ": Free spawn 1 minion (no cost).");
        printBoard();
        System.out.println("Territory P1 uses '*', territory P2 uses '+'.");

        while (true) {
            try {
                System.out.print("P" + pid + " choose type/kind: ");
                MinionType type = resolveType(pid, sc.nextLine());

                System.out.print("P" + pid + " choose x y (1..8): ");
                String[] xy = sc.nextLine().trim().split("\\s+");
                int x = parseCoord(xy[0]);
                int y = parseCoord(xy[1]);

                // free spawn: temporarily add spawn cost then subtract inside spawn()
                gs.getBudgetManager().addBudget(pid, config.spawnCost());
                boolean ok = spawn(pid, type, x, y);
                if (ok) {
                    System.out.println("P" + pid + " free spawn OK.");
                    return;
                }
                if (config.spawnCost() > 0) gs.getBudgetManager().spendBudget(pid, config.spawnCost());
                System.out.println("Free spawn failed. Try again.");
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    // ---------------- Turn logic ----------------

    private void beginPlayerTurn(long pid) {
        turnInitialized = true;
        boughtThisTurn = false;
        spawnedThisTurn = false;

        // 1) add turn_budget
        long add = config.turnBudget();
        if (add > 0) gs.getBudgetManager().addBudget(pid, add);

        // 2) apply interest: Int = max(0, b*log10(m)*ln(t)), truncated
        long before = gs.getBudgetManager().getBudget(pid);
        if (before >= 1 && config.interestPct() > 0) {
            double b = config.interestPct();
            double m = before;
            long t = Math.max(1L, turnsPlayed(pid) + 1L);
            double raw = b * Math.log10(m) * Math.log(t);
            long interestRate = (raw > 0 && !Double.isNaN(raw) && !Double.isInfinite(raw)) ? (long) raw : 0L;
            if (interestRate > 0) {
                long addInterest = (long) (before * (double) interestRate / 100.0);
                if (addInterest > 0) gs.getBudgetManager().addBudget(pid, addInterest);
            }
        }

        // 3) cap max_budget
        long after = gs.getBudgetManager().getBudget(pid);
        long cap = config.maxBudget();
        if (after > cap) gs.getBudgetManager().spendBudget(pid, after - cap);
    }

    private void endPlayerTurn() {
        // increment that player's turn counter
        if (currentPlayer == P1) turnsPlayedP1++;
        else turnsPlayedP2++;

        gs.advanceTurn();

        // swap player
        currentPlayer = (currentPlayer == P1) ? P2 : P1;
        turnInitialized = false;
    }

    private void runStrategiesOnce(long pid) {
        actionLog.clear();
        Map<Minion, Strategy> bindings = gs.buildStrategyBindings(pid);
        List<Minion> owned = filterByOwner(gs.getMinions(), pid);
        evaluator.runMinionsOldestToNewest(owned, bindings, mg, exec);
        printLog();
    }

    // ---------------- Buy / Spawn / Resolve ----------------

    private boolean buyHex(long pid, int x, int y) {
        if (!gs.getBoard().isInsideBoard(x, y)) return false;
        if (isInAnyTerritory(x, y)) return false;
        if (!isAdjacentToTerritory(pid, x, y)) return false;

        long cost = config.hexPurchaseCost();
        if (gs.getBudgetManager().getBudget(pid) < cost) return false;

        gs.getBudgetManager().spendBudget(pid, cost);
        territory(pid)[x][y] = true;
        return true;
    }

    private boolean spawn(long pid, MinionType type, int x, int y) {
        if (!gs.getBoard().isInsideBoard(x, y)) return false;
        if (!territory(pid)[x][y]) return false;
        if (spawnsUsed(pid) >= config.maxSpawns()) return false;

        Hex h = gs.getBoard().getHex(x, y);
        if (h.isOccupied()) return false;

        try {
            gs.spawnMinion(pid, type, (int) config.initHp(), x, y);
        } catch (Exception e) {
            return false;
        }

        incSpawnsUsed(pid);
        return true;
    }

    private MinionType resolveType(long pid, String userText) {
        MinionKindDef byName = gs.getKind(pid, userText);
        if (byName != null) return byName.getType();

        MinionType t = MinionType.fromUserText(userText);
        if (gs.getKind(pid, t) == null) {
            throw new IllegalArgumentException("Type not selected in setup: " + t);
        }
        return t;
    }

    /**
     * UI accepts 1..8 (recommended) and also 0..7 (legacy). Internally we keep 0..7.
     */
    private int parseCoord(String s) {
        int v = Integer.parseInt(s.trim());
        if (v >= 1 && v <= 8) return v - 1;
        if (v >= 0 && v <= 7) return v;
        throw new IllegalArgumentException("Coordinate must be 1..8 (or 0..7 legacy). Got: " + v);
    }

    // ---------------- Territory helpers ----------------

    private void initDefaultTerritories() {
        // P1: top-left 5
        territoryP1[0][0] = true;
        territoryP1[0][1] = true;
        territoryP1[0][2] = true;
        territoryP1[1][0] = true;
        territoryP1[1][1] = true;

        // P2: bottom-right 5
        territoryP2[7][7] = true;
        territoryP2[7][6] = true;
        territoryP2[7][5] = true;
        territoryP2[6][7] = true;
        territoryP2[6][6] = true;
    }

    private boolean[][] territory(long pid) {
        return (pid == P2) ? territoryP2 : territoryP1;
    }

    private boolean isInTerritory(long pid, int x, int y) {
        return territory(pid)[x][y];
    }

    private boolean isInAnyTerritory(int x, int y) {
        return territoryP1[x][y] || territoryP2[x][y];
    }

    private boolean isAdjacentToTerritory(long pid, int x, int y) {
        // hex-neighbors (ตาม mapping ที่ใช้ร่วมกับ Direction)
        // UP        = (-1, 0)
        // UPRIGHT   = (-1, +1)
        // DOWNRIGHT = ( 0, +1)
        // DOWN      = (+1, 0)
        // DOWNLEFT  = (+1, -1)
        // UPLEFT    = ( 0, -1)
        int[][] deltas = new int[][]{
                {-1, 0},
                {-1, 1},
                { 0, 1},
                { 1, 0},
                { 1,-1},
                { 0,-1}
        };

        boolean[][] terr = territory(pid);
        for (int[] d : deltas) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (gs.getBoard().isInsideBoard(nx, ny) && terr[nx][ny]) return true;
        }
        return false;
    }

    // ---------------- Display ----------------

    private void printKinds() {
        System.out.println("[Kinds selected] (P1)");
        for (MinionKindDef def : gs.getKinds(P1).values()) {
            if (def == null) continue;
            System.out.println(" - " + def.getType().name()
                    + "  name=\"" + def.getKindName() + "\""
                    + "  def=" + def.getDefenseFactor());
        }
        System.out.println("[Kinds selected] (P2)");
        for (MinionKindDef def : gs.getKinds(P2).values()) {
            if (def == null) continue;
            System.out.println(" - " + def.getType().name()
                    + "  name=\"" + def.getKindName() + "\""
                    + "  def=" + def.getDefenseFactor());
        }
    }

    private static List<Minion> filterByOwner(List<Minion> all, long pid) {
        List<Minion> out = new ArrayList<>();
        for (Minion m : all) {
            if (m != null && m.getOwnerId() == pid) out.add(m);
        }
        return out;
    }

    private void printBudgetsAndSpawns() {
        long b1 = gs.getBudgetManager().getBudget(P1);
        long b2 = gs.getBudgetManager().getBudget(P2);
        System.out.println("Budget(P1)=" + b1 + " | SpawnsLeft(P1)=" + (config.maxSpawns() - spawnsUsedP1));
        System.out.println("Budget(P2)=" + b2 + " | SpawnsLeft(P2)=" + (config.maxSpawns() - spawnsUsedP2));
    }

    private void printBoard() {
        System.out.println("Board (x=row 1..8, y=col 1..8)  '*'=P1 territory  '+'=P2 territory  '.'=empty");
        System.out.print("    ");
        for (int y = 0; y < 8; y++) System.out.print((y + 1) + "  ");
        System.out.println();
        for (int x = 0; x < 8; x++) {
            System.out.printf("%2d  ", (x + 1));
            for (int y = 0; y < 8; y++) {
                Hex h = gs.getBoard().getHex(x, y);
                String cell;
                if (h.isOccupied()) cell = token(h.getOccupant());
                else if (territoryP1[x][y]) cell = "*";
                else if (territoryP2[x][y]) cell = "+";
                else cell = ".";
                System.out.print(pad2(cell) + " ");
            }
            System.out.println();
        }

        System.out.println("Minions (oldest -> newest):");
        List<Minion> ms = gs.getMinions();
        for (int i = 0; i < ms.size(); i++) {
            Minion m = ms.get(i);
            Hex p = m.getPosition();
            System.out.println("  [" + i + "] P" + m.getOwnerId()
                    + " name=\"" + (m.getKindName() == null ? "" : m.getKindName()) + "\""
                    + " type=" + (m.getType() == null ? "?" : m.getType().name())
                    + " hp=" + m.getHp()
                    + " def=" + m.getDefenseFactor()
                    + " pos=(" + (p.getX() + 1) + "," + (p.getY() + 1) + ")");
        }
    }

    private String token(Minion m) {
        String nm = m.getKindName();
        if (nm == null || nm.trim().isEmpty()) nm = (m.getType() != null) ? m.getType().name() : "M";
        nm = nm.trim().toUpperCase();
        char c = nm.charAt(0);
        char pid = (char) ('0' + Math.toIntExact(Math.max(0, Math.min(9, m.getOwnerId()))));
        return "" + c + pid;
    }

    private String pad2(String s) {
        if (s == null) return "  ";
        if (s.length() == 1) return s + " ";
        if (s.length() == 2) return s;
        return s.substring(0, 2);
    }

    private void printLog() {
        if (actionLog.isEmpty()) {
            System.out.println("(no actions)");
            return;
        }
        for (String s : actionLog) System.out.println(s);
    }

    // ---------------- End condition helpers ----------------

    // --- GAME OVER / WINNER RULES (spec) ---

    private boolean isGameOver() {
        // spec: ฝ่ายใดฝ่ายหนึ่ง “ไม่มี minion เหลือใน territory” -> จบเกม
        if (countLivingInTerritory(P1) == 0) return true;
        if (countLivingInTerritory(P2) == 0) return true;

        // spec: ครบจำนวนเทิร์นที่กำหนด (ทั้ง 2 ฝั่งเล่นครบ)
        return turnsPlayedP1 >= config.maxTurns() && turnsPlayedP2 >= config.maxTurns();
    }

    private int countLivingInTerritory(long pid) {
        int count = 0;
        for (Minion m : gs.getMinions()) {
            if (m.getOwnerId() != pid) continue;
            if (m.getHp() <= 0) continue;

            int x = m.getPosition().getX();
            int y = m.getPosition().getY();
            if (!isInTerritory(pid, x, y)) continue;

            // กันกรณีตำแหน่งไม่ sync กับกระดาน
            Hex h = gs.getBoard().getHex(x, y);
            if (h.getOccupant() != m) continue;

            count++;
        }
        return count;
    }

    private long sumHpLivingInTerritory(long pid) {
        long sum = 0;
        for (Minion m : gs.getMinions()) {
            if (m.getOwnerId() != pid) continue;
            if (m.getHp() <= 0) continue;

            int x = m.getPosition().getX();
            int y = m.getPosition().getY();
            if (!isInTerritory(pid, x, y)) continue;

            Hex h = gs.getBoard().getHex(x, y);
            if (h.getOccupant() != m) continue;

            sum += m.getHp();
        }
        return sum;
    }

    private enum Outcome { P1_WIN, P2_WIN, TIE }

    private Outcome decideOutcome() {
        int c1 = countLivingInTerritory(P1);
        int c2 = countLivingInTerritory(P2);

        // 1) ไม่มี minion เหลือใน territory -> อีกฝ่ายชนะ
        if (c1 == 0 && c2 == 0) return Outcome.TIE;
        if (c1 == 0) return Outcome.P2_WIN;
        if (c2 == 0) return Outcome.P1_WIN;

        // 2) ครบ max_turns -> ตัดสินตามลำดับ: minion count -> sum HP -> budget -> tie
        if (turnsPlayedP1 >= config.maxTurns() && turnsPlayedP2 >= config.maxTurns()) {
            if (c1 != c2) return (c1 > c2) ? Outcome.P1_WIN : Outcome.P2_WIN;

            long hp1 = sumHpLivingInTerritory(P1);
            long hp2 = sumHpLivingInTerritory(P2);
            if (hp1 != hp2) return (hp1 > hp2) ? Outcome.P1_WIN : Outcome.P2_WIN;

            long b1 = gs.getBudgetManager().getBudget(P1);
            long b2 = gs.getBudgetManager().getBudget(P2);
            if (b1 != b2) return (b1 > b2) ? Outcome.P1_WIN : Outcome.P2_WIN;

            return Outcome.TIE;
        }

        return Outcome.TIE;
    }

    private void printGameOverSummary() {
        System.out.println("=== GAME OVER ===");

        int c1 = countLivingInTerritory(P1);
        int c2 = countLivingInTerritory(P2);
        long hp1 = sumHpLivingInTerritory(P1);
        long hp2 = sumHpLivingInTerritory(P2);
        long b1 = gs.getBudgetManager().getBudget(P1);
        long b2 = gs.getBudgetManager().getBudget(P2);

        System.out.printf("P1: minions_in_territory=%d, sum_hp=%d, budget=%d%n", c1, hp1, b1);
        System.out.printf("P2: minions_in_territory=%d, sum_hp=%d, budget=%d%n", c2, hp2, b2);

        Outcome out = decideOutcome();
        switch (out) {
            case P1_WIN -> System.out.println("Winner: P1");
            case P2_WIN -> System.out.println("Winner: P2");
            case TIE -> System.out.println("Result: TIE");
        }
    }

    private long turnsPlayed(long pid) {
        return (pid == P2) ? turnsPlayedP2 : turnsPlayedP1;
    }

    private long totalTurnsPlayed() {
        return turnsPlayedP1 + turnsPlayedP2;
    }

    private long spawnsUsed(long pid) {
        return (pid == P2) ? spawnsUsedP2 : spawnsUsedP1;
    }

    private void incSpawnsUsed(long pid) {
        if (pid == P2) spawnsUsedP2++;
        else spawnsUsedP1++;
    }
}
