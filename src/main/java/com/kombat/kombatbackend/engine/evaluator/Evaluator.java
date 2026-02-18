package com.kombat.kombatbackend.engine.evaluator;

import com.kombat.kombatbackend.engine.gamestate.Minion;
import com.kombat.kombatbackend.engine.gamestate.MockGameState;
import com.kombat.kombatbackend.engine.strategy.ExecContext;
import com.kombat.kombatbackend.engine.strategy.Strategy;

import java.util.*;

public class Evaluator {

    // locals ต้องอยู่ระดับ field เพื่อจำข้ามเทิร์น (ข้ามการเรียก step)
    private final IdentityHashMap<Minion, Map<String, Long>> localsByMinion =
            new IdentityHashMap<Minion, Map<String, Long>>();

    public void runOneMinion(Minion m,
                             Strategy st,
                             MockGameState game,
                             ExecContext ctx) {
        if (m == null || st == null) return;
        if (m.getHp() <= 0 || m.getPosition() == null) {
            localsByMinion.remove(m); // ตามสเปค: minion ตายแล้ว locals หายไป
            return;
        }

        // โหลด locals ของ minion นี้
        Map<String, Long> mine = localsByMinion.get(m);
        if (mine == null) {
            mine = new HashMap<String, Long>();
            localsByMinion.put(m, mine);
        }
        ctx.localVars.clear();
        ctx.localVars.putAll(mine);

        game.setCurrentMinion(m);

        try {
            st.exec(ctx);
        } catch (StopEvaluation e) {
            ctx.log("STOP(" + minionTag(m) + "): " + e.getMessage());
        }

        // เซฟ locals กลับ
        mine.clear();
        mine.putAll(ctx.localVars);
    }

    public void runMinionsOldestToNewest(List<Minion> orderedMinions,
                                         Map<Minion, Strategy> strategies,
                                         MockGameState game,
                                         ExecContext ctx) {
        // IMPORTANT:
        // During strategy execution, SHOOT can kill a target and remove it from GameState's
        // underlying minion list. If we iterate the live list here, it can throw
        // ConcurrentModificationException. Iterate over a snapshot instead.
        List<Minion> snapshot = new ArrayList<>(orderedMinions);

        for (Minion m : snapshot) {
            if (m == null) continue;

            if (m.getHp() <= 0 || m.getPosition() == null) {
                localsByMinion.remove(m);
                continue;
            }

            Strategy st = strategies.get(m);
            if (st == null) {
                ctx.log("NO STRATEGY(" + minionTag(m) + "): skip");
                continue;
            }

            // โหลด locals ของ minion นี้
            Map<String, Long> mine = localsByMinion.get(m);
            if (mine == null) {
                mine = new HashMap<String, Long>();
                localsByMinion.put(m, mine);
            }
            ctx.localVars.clear();
            ctx.localVars.putAll(mine);

            game.setCurrentMinion(m);

            try {
                st.exec(ctx);
            } catch (StopEvaluation e) {
                ctx.log("STOP(" + minionTag(m) + "): " + e.getMessage());
            }

            // เซฟ locals กลับ
            mine.clear();
            mine.putAll(ctx.localVars);
        }

        // Post-pass cleanup: if a minion died after it already executed earlier in the snapshot,
        // it will not be revisited again in this pass. Ensure its locals are still purged.
        localsByMinion.keySet().removeIf(mm -> mm == null || mm.getHp() <= 0);
    }

    // Prefer "kindName" (human-chosen), fallback to type, else position.
    private String minionTag(Minion m) {
        if (m.getKindName() != null && !m.getKindName().isBlank()) return m.getKindName();
        if (m.getType() != null) return m.getType().name();
        if (m.getPosition() == null) return "unknown";
        return "x=" + m.getPosition().getX() + ",y=" + m.getPosition().getY();
    }
}