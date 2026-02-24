package com.kombat.kombatbackend.terminal;

import com.kombat.kombatbackend.engine.gamestate.GameConfig;
import com.kombat.kombatbackend.engine.gamestate.KindInput;
import com.kombat.kombatbackend.engine.gamestate.MinionType;
import com.kombat.kombatbackend.engine.parser.SyntaxException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class Main {

    public static void main(String[] args) throws IOException, SyntaxException {
        Path configPath = (args.length >= 1) ? Path.of(args[0]) : null;
        GameConfig cfg = (configPath == null) ? GameConfig.sampleDefaults() : GameConfig.load(configPath);

        Scanner sc = new Scanner(System.in);

        System.out.println("=== KOMBAT SETUP ===");
        System.out.print("Mode (solo/duel) [default=duel]: ");
        String mode = sc.nextLine().trim();
        boolean duel = !mode.equalsIgnoreCase("solo");

        System.out.println("Choose 1-5 types from: Fighter Assasin DPS Tank Support");
        System.out.println("Example: Fighter DPS");
        List<MinionType> chosen = chooseTypes(sc);

        if (duel) {
            List<KindInput> p1 = new ArrayList<>();
            List<KindInput> p2 = new ArrayList<>();

            for (MinionType t : chosen) {
                System.out.println();
                System.out.println("Configure type: " + t.name());

                SharedKindCore core = readSharedKindCore(sc, t);

                String p1Name = readName(sc, "P1 display name (board/UI)", pretty(t));
                String p2Name = readName(sc, "P2 display name (board/UI)", pretty(t));

                p1.add(new KindInput(t, p1Name, core.defenseFactor, core.strategyCode));
                p2.add(new KindInput(t, p2Name, core.defenseFactor, core.strategyCode));
            }

            System.out.println();
            System.out.println("=== START DUEL ===");
            DuelGameController gc = DuelGameController.create(cfg, p1, p2);
            gc.runTerminal(sc);

        } else {
            List<KindInput> kinds = new ArrayList<>();
            for (MinionType t : chosen) {
                System.out.println();
                System.out.println("Configure type: " + t.name());
                kinds.add(readKind(sc, "Solo", t, pretty(t)));
            }

            System.out.println();
            System.out.println("=== START GAME ===");
            GameController gc = GameController.create(cfg, kinds);
            gc.runTerminal(sc);
        }
    }

    private static KindInput readKind(Scanner sc, String label, MinionType t, String defaultName) {
        System.out.print(label + " display name (board/UI) [default=" + defaultName + "]: ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) name = defaultName;

        int def = readInt(sc, label + " defense factor (>=0): ", 0, Integer.MAX_VALUE);

        System.out.println("Paste strategy code for " + label + " kind \"" + name + "\"");
        System.out.println("- finish with a single line: END");
        System.out.println("- shortcut: type 'done' to use { done; }");
        System.out.println("- comment style: // ...  (NOT #)");
        String code = readMultiline(sc);

        if (code.trim().equalsIgnoreCase("done")) {
            code = "{ done; }";
        }
        return new KindInput(t, name, def, code);
    }

    private static final class SharedKindCore {
        final int defenseFactor;
        final String strategyCode;
        SharedKindCore(int defenseFactor, String strategyCode) {
            this.defenseFactor = defenseFactor;
            this.strategyCode = strategyCode;
        }
    }

    private static SharedKindCore readSharedKindCore(Scanner sc, MinionType t) {
        int def = readInt(sc, "Shared defense factor (>=0): ", 0, Integer.MAX_VALUE);

        System.out.println("Paste strategy code (shared) for type " + t.name());
        System.out.println("- finish with a single line: END");
        System.out.println("- shortcut: type 'done' to use { done; }");
        System.out.println("- comment style: // ...  (NOT #)");

        String code = readMultiline(sc);
        if (code.trim().equalsIgnoreCase("done")) {
            code = "{ done; }";
        }
        return new SharedKindCore(def, code);
    }

    private static String readName(Scanner sc, String label, String defaultName) {
        System.out.print(label + " [default=" + defaultName + "]: ");
        String name = sc.nextLine().trim();
        return name.isEmpty() ? defaultName : name;
    }

    // ✅ แก้ตรงนี้: เลือกได้ 1-5 ชนิด
    private static List<MinionType> chooseTypes(Scanner sc) {
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            List<MinionType> out = new ArrayList<>();

            for (String p : parts) {
                MinionType t = parseType(p);
                if (t == null) {
                    System.out.println("Unknown type: " + p);
                    out.clear();
                    break;
                }
                if (!out.contains(t)) out.add(t);
            }

            if (out.size() < 1) {
                System.out.println("Please choose at least 1 distinct type.");
                continue;
            }

            if (out.size() > 5) {
                System.out.println("Maximum 5 types allowed.");
                continue;
            }

            return out;
        }
    }

    private static MinionType parseType(String s) {
        String x = s.trim().toLowerCase();
        if (x.equals("fighter")) return MinionType.FIGHTER;
        if (x.equals("assasin") || x.equals("assassin")) return MinionType.ASSASSIN;
        if (x.equals("dps")) return MinionType.DPS;
        if (x.equals("tank")) return MinionType.TANK;
        if (x.equals("support")) return MinionType.SUPPORT;
        return null;
    }

    private static String pretty(MinionType t) {
        switch (t) {
            case FIGHTER: return "Fighter";
            case ASSASSIN: return "Assassin";
            case DPS: return "Dps";
            case TANK: return "Tank";
            case SUPPORT: return "Support";
            default: return t.name();
        }
    }

    private static int readInt(Scanner sc, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) {
                    System.out.println("Must be in [" + min + "," + max + "]");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Invalid integer.");
            }
        }
    }

    private static String readMultiline(Scanner sc) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = sc.nextLine();
            if (line.trim().equals("END")) break;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}