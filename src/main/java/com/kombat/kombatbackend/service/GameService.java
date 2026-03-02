package com.kombat.kombatbackend.service;
import com.kombat.kombatbackend.dto.SpawnableHexDto;
import java.util.List;
import com.kombat.kombatbackend.dto.GameInitRequest;
import com.kombat.kombatbackend.dto.MinionSetup;
import com.kombat.kombatbackend.engine.gamestate.*;
import com.kombat.kombatbackend.engine.parser.Parser;
import com.kombat.kombatbackend.engine.strategy.Strategy;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GameService {

    public static final long P1 = 1L;
    public static final long P2 = 2L;

    private GameConfig config;
    private GameMode mode;

    private final Map<Long, CharacterType> selectedCharacters = new HashMap<>();
    private final Map<Long, List<MinionKindDef>> selectedMinionsByPlayer = new HashMap<>();

    private GameState gameState;
    private MockGameState mockGameState;
    private GameEngine engine;

    private GamePhase phase = GamePhase.NOT_CONFIGURED;

    // ================= CONFIG =================

    public GameConfig getConfig() {
        if (this.config == null) {
            this.config = GameConfig.sampleDefaults();
        }
        return this.config;
    }

    public void setConfig(GameConfig config) {

        if (phase == GamePhase.PLAYING || phase == GamePhase.FINISHED) {
            throw new IllegalStateException("Cannot change config after game started");
        }

        this.config = config;
        this.phase = GamePhase.CONFIGURED;
    }

    // ================= MODE =================

    public void setMode(GameMode mode) {

        if (phase == GamePhase.PLAYING || phase == GamePhase.FINISHED) {
            throw new IllegalStateException("Cannot change mode after game started");
        }

        this.mode = mode;
        this.phase = GamePhase.MODE_SET;
    }

    public GameMode getMode() {
        return mode;
    }

    // ================= CHARACTER =================

    public void setCharacter(long playerId, CharacterType character) {

        if (phase == GamePhase.PLAYING || phase == GamePhase.FINISHED) {
            throw new IllegalStateException("Cannot change character after game started");
        }

        selectedCharacters.put(playerId, character);
    }

    public CharacterType getCharacter(long playerId) {
        return selectedCharacters.get(playerId);
    }

    // ================= MINION SETUP =================

    public void resetMinions(long playerId) {

        if (phase == GamePhase.PLAYING || phase == GamePhase.FINISHED) {
            throw new IllegalStateException("Cannot modify setup after game started");
        }

        selectedMinionsByPlayer.put(playerId, new ArrayList<>());
        phase = GamePhase.SETUP_IN_PROGRESS;
    }

    public void addMinion(long playerId,
                          String typeText,
                          int defenseFactor,
                          String strategyCode) {

        if (phase == GamePhase.PLAYING || phase == GamePhase.FINISHED) {
            throw new IllegalStateException("Cannot modify setup after game started");
        }

        if (strategyCode == null || strategyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Strategy must not be empty");
        }

        if (config == null) {
            throw new IllegalStateException("Config must be set first");
        }

        MinionType type = MinionType.fromUserText(typeText);

        List<MinionKindDef> list =
                selectedMinionsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>());

        if (list.stream().anyMatch(m -> m.getType() == type)) {
            throw new IllegalStateException("Duplicate minion type for player " + playerId);
        }

        // Parse strategy with dummy state
        Board board = new Board();
        List<Minion> minions = new ArrayList<>();
        BudgetManager budgetManager = new BudgetManager(config.initBudget());

        GameState dummyState = new GameState(
                board,
                minions,
                budgetManager,
                TurnPhase.PLAYER_ACTION,
                config
        );

        MockGameState mock = new MockGameState(dummyState);
        Parser parser = new Parser(strategyCode, mock);
        Strategy strategy = parser.parseStrategy();

        MinionKindDef def =
                new MinionKindDef(type, defenseFactor, strategyCode, strategy);

        list.add(def);

        if (!getSelectedMinions(P1).isEmpty() &&
                !getSelectedMinions(P2).isEmpty()) {

            phase = GamePhase.READY_TO_START;
        }
    }

    public List<MinionKindDef> getSelectedMinions(long playerId) {
        return selectedMinionsByPlayer.getOrDefault(playerId, List.of());
    }

    // ================= START GAME =================

    public void startGame() {

        if (phase == GamePhase.PLAYING) {
            throw new IllegalStateException("Game already started");
        }

        if (getSelectedMinions(P1).isEmpty() ||
                getSelectedMinions(P2).isEmpty()) {
            throw new IllegalStateException("Both players must configure minions");
        }

        if (!selectedCharacters.containsKey(P1) ||
                !selectedCharacters.containsKey(P2)) {
            throw new IllegalStateException("Both players must select characters");
        }

        if (config == null) {
            throw new IllegalStateException("Config not set");
        }

        Board board = new Board();
        BudgetManager budget = new BudgetManager(config.initBudget());
        budget.initPlayer(P1, config.initBudget());
        budget.initPlayer(P2, config.initBudget());
        List<Minion> minions = new ArrayList<>();

        // 🔥 เปลี่ยนแค่ตรงนี้
        GameState gs = new GameState(
                board,
                minions,
                budget,
                TurnPhase.FREE_SPAWN, // เดิมคือ PLAY
                config
        );

        MockGameState mg = new MockGameState(gs);
        mg.setTerritoryRule((pid, x, y) -> engineCanEnterTerritory(pid, x, y));

        for (MinionKindDef def : getSelectedMinions(P1)) {
            gs.registerKind(P1, def);
        }
        for (MinionKindDef def : getSelectedMinions(P2)) {
            gs.registerKind(P2, def);
        }

        gs.lockSetup();

        this.gameState = gs;
        this.mockGameState = mg;
        this.engine = new GameEngine(config, gs, mg);

        phase = GamePhase.PLAYING;
    }

    private boolean engineCanEnterTerritory(long pid, int x, int y) {

        if (gameState == null || !gameState.getBoard().isInsideBoard(x, y)) {
            return false;
        }

        if (engine == null) {
            return true;
        }

        return engine.getSpawnableHexes().stream()
                .anyMatch(hex -> hex.getRow() == x && hex.getCol() == y && hex.getOwnerId() == pid);
    }

    // ================= GAMEPLAY =================

    public boolean spawn(String type, int row, int col) {
        requirePhase(GamePhase.PLAYING);
        return engine.spawn(type, row, col);
    }

    public boolean buyHex(int row, int col) {
        requirePhase(GamePhase.PLAYING);
        return engine.buyHex(row, col);
    }

    public void endTurn() {
        requirePhase(GamePhase.PLAYING);
        engine.executeTurn();

        if (engine.isGameOver()) {
            phase = GamePhase.FINISHED;
        }
    }

    public long getCurrentPlayer() {

        if (phase != GamePhase.PLAYING &&
                phase != GamePhase.FINISHED) {

            throw new IllegalStateException("Game has not started yet");
        }

        if (engine == null) {
            throw new IllegalStateException("Engine not initialized");
        }

        return engine.getCurrentPlayer();
    }
    public boolean isGameOver() {
        return engine != null && engine.isGameOver();
    }

    public String getWinner() {
        if (engine == null) return "NOT_STARTED";
        return engine.getWinner();
    }

    public GameState getGameState() {
        return gameState;
    }

    public GamePhase getPhase() {
        return phase;
    }

    private void requirePhase(GamePhase... allowed) {

        for (GamePhase p : allowed) {
            if (this.phase == p) return;
        }

        throw new IllegalStateException(
                "Invalid game phase: " + this.phase
        );
    }

    public void initFullGame(GameInitRequest req) {

        this.phase = GamePhase.NOT_CONFIGURED;
        this.selectedCharacters.clear();
        this.selectedMinionsByPlayer.clear();
        this.engine = null;
        this.gameState = null;
        this.mockGameState = null;

        setConfig(req.getConfig());
        setMode(req.getMode());

        setCharacter(P1, req.getPlayer1().getCharacter());
        setCharacter(P2, req.getPlayer2().getCharacter());

        resetMinions(P1);
        for (MinionSetup m : req.getPlayer1().getMinions()) {
            addMinion(P1, m.getType(), m.getDefenseFactor(), m.getStrategy());
        }

        resetMinions(P2);
        for (MinionSetup m : req.getPlayer2().getMinions()) {
            addMinion(P2, m.getType(), m.getDefenseFactor(), m.getStrategy());
        }

        startGame();
    }

    public void resetGame() {
        this.phase = GamePhase.NOT_CONFIGURED;
        this.config = null;
        this.mode = null;
        this.selectedCharacters.clear();
        this.selectedMinionsByPlayer.clear();
        this.engine = null;
        this.gameState = null;
        this.mockGameState = null;
    }
    public List<SpawnableHexDto> getSpawnableHexes() {

        if (engine == null) {
            return List.of();
        }

        return engine.getSpawnableHexes();
    }
    public TurnPhase getTurnPhase() {
        if (gameState == null) return null;
        return gameState.getPhase();
    }

    public List<SpawnableHexDto> getBuyableHexes() {

        if (engine == null || phase != GamePhase.PLAYING) {
            return List.of();
        }

        return engine.getBuyableHexes(getCurrentPlayer());
    }

    public long getSpawnsLeft() {

        if (engine == null || phase != GamePhase.PLAYING) {
            return 0L;
        }

        return engine.getSpawnsLeft(getCurrentPlayer());
    }

    public List<String> getActionLogs() {

        if (engine == null || (phase != GamePhase.PLAYING && phase != GamePhase.FINISHED)) {
            return List.of();
        }

        return engine.getActionLogs();
    }
}
