package com.kombat.kombatbackend.terminal;
import com.kombat.kombatbackend.engine.strategy.Strategy;
import com.kombat.kombatbackend.engine.evaluator.Evaluator;
import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.engine.parser.SyntaxException;
import com.kombat.kombatbackend.engine.strategy.EvalContext;
import com.kombat.kombatbackend.engine.strategy.ExecContext;
import com.kombat.kombatbackend.engine.strategy.InfoProvider;
import com.kombat.kombatbackend.engine.strategy.SpecialVars;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Terminal GameController (Phase-2 friendly)
 * - setup: humans choose defenseFactor + strategy for each selected MinionType (3..5 kinds)
 * - free spawn 1 minion (setup stage)
 * - each turn:
 *    1) add turn_budget + apply interest + cap max_budget
 *    2) optional buy spawnable hex (adjacent)
 *    3) optional spawn a minion (cost spawn_cost, max_spawns)
 *    4) run strategies oldest->newest
 */
public final class GameController {

    private final GameConfig config;

    private final GameState gs;
    private final MockGameState mg;

    private final Evaluator evaluator = new Evaluator();

    // spawn system (single-player / demo)
    private final boolean[][] spawnable = new boolean[8][8];
    private long spawnsUsed = 0;

    // evaluator contexts
    private final Map<String, Long> localVars = new HashMap<String, Long>();
    private final Map<String, Long> globalVars = new HashMap<String, Long>();
    private final List<String> actionLog = new ArrayList<String>();
    private final ExecContext exec;

    // randomness
    private final Random rnd = new Random(1);

    private GameController(GameConfig config, SetupResult setup) {
        this.config = Objects.requireNonNull(config, "config");
        this.gs = Objects.requireNonNull(setup.gameState, "gameState");
        this.mg = Objects.requireNonNull(setup.mock, "mock");

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
            @Override public long budget() { return mg.getBudget(mg.currentPlayerId()); }

            @Override public long interestRate() {
                double b = config.interestPct();
                double m = Math.max(0, mg.getBudget(mg.currentPlayerId()));
                long t = Math.max(1L, gs.getTurnNumber() + 1L);
                if (m < 1.0) return 0;
                double r = b * Math.log10(m) * Math.log(t);
                if (Double.isNaN(r) || Double.isInfinite(r) || r < 0) return 0;
                return Math.max(0L, (long) r);
            }

            @Override public long maxBudget() { return config.maxBudget(); }

            @Override public long spawnsLeft() {
                long left = config.maxSpawns() - spawnsUsed;
                return Math.max(0, left);
            }

            @Override public long random0to999() { return rnd.nextInt(1000); }
        };

        // InfoProvider (no owners in phase-2): treat ALL other minions as opponent, no allies
        InfoProvider info = new InfoProvider() {
            @Override public long opponent() { return closestInAnyDirectionAsOpponent(); }
            @Override public long ally() { return 0; }
            @Override public long nearby(Direction dir) { return nearbyEncoding(dir); }

            private long closestInAnyDirectionAsOpponent() {
                Minion me = mg.getCurrentMinion();
                if (me == null) return 0;

                long best = 0;
                int bestDist = Integer.MAX_VALUE;
                int bestDirNum = Integer.MAX_VALUE;

                for (Direction d : Direction.values()) {
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
                int x = digits(target.getHp());
                int y = digits(target.getDefenseFactor());
                int z = Math.max(1, dist);

                return 100L * x + 10L * y + z;
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

        initDefaultSpawnableAreaTopLeft5();
    }

    public static GameController create(GameConfig config, List<KindInput> kindInputs) throws SyntaxException {
        SetupResult setup = AutoModeSetup.createGame(config, kindInputs);
        return new GameController(config, setup);
    }

    public void runTerminal() { runTerminal(new Scanner(System.in)); }

    public void runTerminal(Scanner sc) {

        System.out.println("=== KOMBAT (terminal) ===");
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

        freeSpawnSetup(sc);

        boolean turnStarted = false;

        while (!isGameOver()) {
            if (!turnStarted) {
                System.out.println();
                System.out.println("TURN " + (gs.getTurnNumber() + 1));
                beginTurnBudgetUpdate();
                turnStarted = true;
            }

            printBoard();
            System.out.println("Budget=" + gs.getBudgetManager().getBudget()
                    + " | SpawnsLeft=" + (config.maxSpawns() - spawnsUsed));
            System.out.println("Commands: kinds | buy x y | spawn TYPE_OR_KIND x y | step | log | board | quit");
            System.out.print("> ");

            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("quit")) break;
            if (line.equalsIgnoreCase("board")) { printBoard(); continue; }
            if (line.equalsIgnoreCase("log")) { printLog(); continue; }
            if (line.equalsIgnoreCase("kinds")) { printKinds(); continue; }

            if (line.equalsIgnoreCase("step")) {
                runStrategiesOnce();
                gs.advanceTurn();
                turnStarted = false;
                continue;
            }

            String[] tok = line.split("\\s+");
            try {
                if (tok[0].equalsIgnoreCase("buy") && tok.length == 3) {
                    int x = Integer.parseInt(tok[1]);
                    int y = Integer.parseInt(tok[2]);
                    boolean ok = buySpawnableHex(x, y);
                    System.out.println(ok ? "BUY OK" : "BUY NO-OP");
                } else if (tok[0].equalsIgnoreCase("spawn") && tok.length == 4) {
                    MinionType type = resolveType(tok[1]);
                    int x = Integer.parseInt(tok[2]);
                    int y = Integer.parseInt(tok[3]);
                    boolean ok = spawn(type, x, y);
                    System.out.println(ok ? "SPAWN OK" : "SPAWN NO-OP");
                } else {
                    System.out.println("Unknown command.");
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }

        System.out.println("=== END ===");
    }

    private void printKinds() {
        System.out.println("[Kinds selected]");
        for (MinionKindDef def : gs.getKinds().values()) {
            if (def == null) continue;
            System.out.println(" - " + def.getType().name()
                    + "  name=\"" + def.getKindName() + "\""
                    + "  def=" + def.getDefenseFactor());
        }
    }

    private MinionType resolveType(String userText) {
        MinionKindDef byName = gs.getKind(userText);
        if (byName != null) return byName.getType();

        MinionType t = MinionType.fromUserText(userText);
        if (gs.getKind(t) == null) {
            throw new IllegalArgumentException("Type not selected in setup: " + t);
        }
        return t;
    }

    // ---------------- Turn logic ----------------

    private void beginTurnBudgetUpdate() {
        long add = config.turnBudget();
        if (add > 0) gs.getBudgetManager().addBudget(add);

        long before = gs.getBudgetManager().getBudget();
        if (before >= 1 && config.interestPct() > 0) {
            // spec: Int = max(0, b*log10(m)*ln(t)) truncated to integer
            double b = config.interestPct();
            double m = before;
            long t = Math.max(1L, gs.getTurnNumber() + 1L);
            double raw = b * Math.log10(m) * Math.log(t);
            if (!Double.isNaN(raw) && !Double.isInfinite(raw) && raw > 0) {
                long I = (long) raw;
                long addInterest = (I <= 0) ? 0 : (long) (m * I / 100.0);
                if (addInterest > 0) gs.getBudgetManager().addBudget(addInterest);
            }
        }

        long after = gs.getBudgetManager().getBudget();
        long cap = config.maxBudget();
        if (after > cap) gs.getBudgetManager().spendBudget(after - cap);
    }

    private void runStrategiesOnce() {
        actionLog.clear();

        // Phase-2 ไม่มี player id → ใช้ทุก minion บนกระดาน
        Map<Minion, Strategy> bindings = gs.buildStrategyBindings();

        evaluator.runMinionsOldestToNewest(
                gs.getMinions(),
                bindings,
                mg,
                exec
        );

        printLog();
    }


    // ---------------- Setup free spawn ----------------

    private void freeSpawnSetup(Scanner sc) {
        System.out.println("[Setup] Free spawn 1 minion (no cost).");
        printBoard();
        System.out.println("Spawnable cells are marked with '*' in the board view.");

        while (true) {
            try {
                System.out.print("Choose type/kind (e.g. Fighter or your kind name): ");
                MinionType type = resolveType(sc.nextLine());

                System.out.print("Choose x y (0..7): ");
                String[] xy = sc.nextLine().trim().split("\\s+");
                int x = Integer.parseInt(xy[0]);
                int y = Integer.parseInt(xy[1]);

                gs.getBudgetManager().addBudget(config.spawnCost());
                boolean ok = spawn(type, x, y);
                if (ok) {
                    System.out.println("Free spawn OK.");
                    return;
                }
                if (config.spawnCost() > 0) gs.getBudgetManager().spendBudget(config.spawnCost());
                System.out.println("Free spawn failed. Try again.");
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    // ---------------- Buy / Spawn ----------------

    private boolean buySpawnableHex(int x, int y) {
        if (!gs.getBoard().isInsideBoard(x, y)) return false;
        if (spawnable[x][y]) return false;
        if (!isAdjacentToSpawnable(x, y)) return false;

        long cost = config.hexPurchaseCost();
        if (gs.getBudgetManager().getBudget() < cost) return false;

        gs.getBudgetManager().spendBudget(cost);
        spawnable[x][y] = true;
        return true;
    }

    private boolean spawn(MinionType type, int x, int y) {
        if (!gs.getBoard().isInsideBoard(x, y)) return false;
        if (!spawnable[x][y]) return false;

        Hex h = gs.getBoard().getHex(x, y);
        if (h.isOccupied()) return false;

        if (spawnsUsed >= config.maxSpawns()) return false;

        try {
            gs.spawnMinion(type, x, y);
        } catch (Exception e) {
            return false;
        }

        spawnsUsed++;
        return true;
    }

    private boolean isAdjacentToSpawnable(int x, int y) {
        int[][] deltas = new int[][]{
                {-1,0},
                {-1,1},
                {0, 1},
                {1, 0},
                {1,-1},
                {0,-1}
        };
        for (int[] d : deltas) {
            int nx = x + d[0], ny = y + d[1];
            if (gs.getBoard().isInsideBoard(nx, ny) && spawnable[nx][ny]) return true;
        }
        return false;
    }

    private void initDefaultSpawnableAreaTopLeft5() {
        spawnable[0][0] = true;
        spawnable[0][1] = true;
        spawnable[0][2] = true;
        spawnable[1][0] = true;
        spawnable[1][1] = true;
    }

    // ---------------- Display / End ----------------

    private void printBoard() {
        System.out.println("Board (x=row 0..7, y=col 0..7)  '*'=spawnable  '.'=empty");
        System.out.print("    ");
        for (int y = 0; y < 8; y++) System.out.print(y + "  ");
        System.out.println();
        for (int x = 0; x < 8; x++) {
            System.out.printf("%2d  ", x);
            for (int y = 0; y < 8; y++) {
                Hex h = gs.getBoard().getHex(x, y);
                String cell;
                if (h.isOccupied()) cell = token(h.getOccupant());
                else cell = spawnable[x][y] ? "*" : ".";
                System.out.print(pad2(cell) + " ");
            }
            System.out.println();
        }

        System.out.println("Minions (oldest -> newest):");
        List<Minion> ms = gs.getMinions();
        for (int i = 0; i < ms.size(); i++) {
            Minion m = ms.get(i);
            Hex p = m.getPosition();
            String nm = (m.getKindName() != null) ? m.getKindName()
                    : (m.getType() != null ? m.getType().name() : "M");
            System.out.println("  [" + i + "] name=\"" + nm + "\""
                    + " type=" + (m.getType() == null ? "?" : m.getType().name())
                    + " hp=" + m.getHp()
                    + " def=" + m.getDefenseFactor()
                    + " pos=(" + p.getX() + "," + p.getY() + ")");
        }
    }

    private String token(Minion m) {
        String nm = m.getKindName();
        if (nm == null || nm.trim().isEmpty()) {
            nm = (m.getType() != null) ? m.getType().name() : "M";
        }
        nm = nm.trim().toUpperCase();
        return (nm.length() <= 2) ? nm : nm.substring(0, 2);
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

    private boolean isGameOver() {
        if (gs.getTurnNumber() >= config.maxTurns()) return true;
        return countLivingMinionsOnBoard() == 0;
    }

    private int countLivingMinionsOnBoard() {
        int c = 0;
        for (Minion m : gs.getMinions()) {
            if (m == null) continue;
            if (m.getHp() <= 0) continue;
            Hex p = m.getPosition();
            if (p != null && p.getOccupant() == m) c++;
        }
        return c;
    }

    // ---------------- main() example ----------------

    public static void main(String[] args) throws IOException, SyntaxException {
        Path configPath = (args.length >= 1) ? Path.of(args[0]) : null;
        GameConfig cfg = (configPath == null) ? GameConfig.sampleDefaults() : GameConfig.load(configPath);

        List<KindInput> kinds = Arrays.asList(
                new KindInput(MinionType.FIGHTER, "F1", 2, "{ done; }"),
                new KindInput(MinionType.DPS, "D1", 2, "{ done; }"),
                new KindInput(MinionType.TANK, "T1", 2, "{ done; }")
        );

        GameController gc = GameController.create(cfg, kinds);
        gc.runTerminal();
    }
}