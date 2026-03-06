package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.dto.GameInitRequest;
import com.kombat.kombatbackend.dto.MinionSetup;
import com.kombat.kombatbackend.dto.PlayerEconomyDto;
import com.kombat.kombatbackend.dto.SpawnableHexDto;
import com.kombat.kombatbackend.engine.gamestate.CharacterType;
import com.kombat.kombatbackend.engine.gamestate.GameConfig;
import com.kombat.kombatbackend.engine.gamestate.GameEngine;
import com.kombat.kombatbackend.engine.gamestate.GameMode;
import com.kombat.kombatbackend.engine.gamestate.GamePhase;
import com.kombat.kombatbackend.engine.gamestate.GameState;
import com.kombat.kombatbackend.engine.gamestate.MinionKindDef;
import com.kombat.kombatbackend.engine.gamestate.MinionType;
import com.kombat.kombatbackend.engine.gamestate.TurnPhase;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Service
public class GameService {

    public static final long P1 = 1L;
    public static final long P2 = 2L;

    private GameConfig config;
    private GameMode mode;

    private final Map<Long, CharacterType> selectedCharacters = new HashMap<>();
    private final Map<Long, List<MinionKindDef>> selectedMinionsByPlayer = new HashMap<>();

    private GameState gameState;
    private GameEngine engine;
    private final GameSetupService setupService = new GameSetupService();
    private final GameTurnService turnService = new GameTurnService();
    private final GameReadService readService = new GameReadService();
    private final GameBotService botService = new GameBotService();
    private final GameLobbyDefaultsService lobbyDefaultsService = new GameLobbyDefaultsService();
    private final Random random;

    private GamePhase phase = GamePhase.NOT_CONFIGURED;

    public GameService() {
        this(new Random());
    }

    public GameService(Random random) {
        this.random = Objects.requireNonNull(random);
    }

    public GameConfig getConfig() {
        if (this.config == null) {
            this.config = GameConfig.sampleDefaults();
        }
        return this.config;
    }

    public void setConfig(GameConfig config) {
        setupService.ensureEditable(phase, "Cannot change config after game started");
        this.config = config;
        this.phase = GamePhase.CONFIGURED;
    }

    public void setMode(GameMode mode) {
        setupService.ensureEditable(phase, "Cannot change mode after game started");
        this.mode = mode;
        this.phase = GamePhase.MODE_SET;
    }

    public GameMode getMode() {
        return mode;
    }

    public void setCharacter(long playerId, CharacterType character) {
        setupService.ensureEditable(phase, "Cannot change character after game started");
        selectedCharacters.put(playerId, character);
    }

    public CharacterType getCharacter(long playerId) {
        return selectedCharacters.get(playerId);
    }

    public void resetMinions(long playerId) {
        setupService.ensureEditable(phase, "Cannot modify setup after game started");
        selectedMinionsByPlayer.put(playerId, new ArrayList<>());
        phase = GamePhase.SETUP_IN_PROGRESS;
    }

    public void addMinion(long playerId,
                          String typeText,
                          int defenseFactor,
                          String strategyCode) {
        addMinion(playerId, typeText, null, defenseFactor, strategyCode);
    }

    public void addMinion(long playerId,
                          String typeText,
                          String kindName,
                          int defenseFactor,
                          String strategyCode) {
        setupService.ensureEditable(phase, "Cannot modify setup after game started");

        MinionType type = MinionType.fromUserText(typeText);
        List<MinionKindDef> list =
                selectedMinionsByPlayer.computeIfAbsent(playerId, ignored -> new ArrayList<>());

        if (list.stream().anyMatch(m -> m.getType() == type)) {
            throw new IllegalStateException("Duplicate minion type for player " + playerId);
        }

        MinionKindDef def = setupService.buildMinionKindDef(
                config,
                typeText,
                kindName,
                defenseFactor,
                strategyCode
        );

        list.add(def);

        if (!getSelectedMinions(P1).isEmpty() && !getSelectedMinions(P2).isEmpty()) {
            phase = GamePhase.READY_TO_START;
        }
    }

    public List<MinionKindDef> getSelectedMinions(long playerId) {
        return selectedMinionsByPlayer.getOrDefault(playerId, List.of());
    }

    public void startGame() {
        if (phase == GamePhase.PLAYING) {
            throw new IllegalStateException("Game already started");
        }

        if (getSelectedMinions(P1).isEmpty() || getSelectedMinions(P2).isEmpty()) {
            throw new IllegalStateException("Both players must configure minions");
        }

        if (!selectedCharacters.containsKey(P1) || !selectedCharacters.containsKey(P2)) {
            throw new IllegalStateException("Both players must select characters");
        }

        GameSetupService.GameStartBundle bundle =
                setupService.createGameStartBundle(config, selectedMinionsByPlayer, P1, P2);
        this.gameState = bundle.gameState();
        this.engine = bundle.engine();

        phase = GamePhase.PLAYING;
    }

    public boolean spawn(String type, int row, int col) {
        requirePhase(GamePhase.PLAYING);
        boolean success = turnService.spawn(engine, type, row, col);
        if (success) {
            runSolitaireBotIfNeeded();
        }
        if (turnService.markFinishedIfGameOver(engine, gameState)) {
            phase = GamePhase.FINISHED;
        }
        return success;
    }

    public boolean buyHex(int row, int col) {
        requirePhase(GamePhase.PLAYING);
        return turnService.buyHex(engine, row, col);
    }

    public void endTurn() {
        if (phase == GamePhase.FINISHED) {
            if (gameState != null) {
                gameState.setPhase(TurnPhase.END);
            }
            return;
        }

        requirePhase(GamePhase.PLAYING);
        turnService.executeTurn(engine);
        runSolitaireBotIfNeeded();

        if (turnService.markFinishedIfGameOver(engine, gameState)) {
            phase = GamePhase.FINISHED;
        }
    }

    public long getCurrentPlayer() {
        if (phase != GamePhase.PLAYING && phase != GamePhase.FINISHED) {
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
        if (engine == null) {
            return "NOT_STARTED";
        }
        return engine.getWinner();
    }

    public GameState getGameState() {
        return gameState;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void initFullGame(GameInitRequest req) {
        this.phase = GamePhase.NOT_CONFIGURED;
        this.selectedCharacters.clear();
        this.selectedMinionsByPlayer.clear();
        this.engine = null;
        this.gameState = null;

        setConfig(req.getConfig());
        setMode(req.getMode());

        setCharacter(P1, req.getPlayer1().getCharacter());
        setCharacter(P2, req.getPlayer2().getCharacter());

        resetMinions(P1);
        for (MinionSetup minion : req.getPlayer1().getMinions()) {
            addMinion(P1, minion.getType(), minion.getDefenseFactor(), minion.getStrategy());
        }

        resetMinions(P2);
        for (MinionSetup minion : req.getPlayer2().getMinions()) {
            addMinion(P2, minion.getType(), minion.getDefenseFactor(), minion.getStrategy());
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
    }

    public void initLobbyGame(GameMode requestedMode) {
        resetGame();

        GameMode resolvedMode = setupService.resolveMode(requestedMode);
        setConfig(GameConfig.sampleDefaults());
        setMode(resolvedMode);

        setCharacter(P1, CharacterType.HUMAN);
        setCharacter(P2, CharacterType.DEMON);

        addDefaultMinionsForPlayer(P1);
        addDefaultMinionsForPlayer(P2);

        startGame();
    }

    private void addDefaultMinionsForPlayer(long playerId) {
        resetMinions(playerId);
        String strategy = "done;";
        for (String type : lobbyDefaultsService.defaultTypes()) {
            String name = lobbyDefaultsService.defaultName(playerId, type, P1);
            addMinion(playerId, type, name, 1, strategy);
        }
    }

    public List<SpawnableHexDto> getSpawnableHexes() {
        return readService.getSpawnableHexes(engine);
    }

    public TurnPhase getTurnPhase() {
        return readService.getTurnPhase(gameState);
    }

    public List<SpawnableHexDto> getBuyableHexes() {
        if (engine == null || phase != GamePhase.PLAYING) {
            return List.of();
        }
        return readService.getBuyableHexes(engine, phase, getCurrentPlayer());
    }

    public long getSpawnsLeft() {
        if (engine == null || phase != GamePhase.PLAYING) {
            return 0L;
        }
        return readService.getSpawnsLeft(engine, phase, getCurrentPlayer());
    }

    public List<String> getActionLogs() {
        return readService.getActionLogs(engine, phase);
    }

    public Map<Long, PlayerEconomyDto> getPlayerEconomy() {
        return readService.getPlayerEconomy(engine, gameState, phase, P1, P2);
    }

    public List<String> getAvailableTypes() {
        if (gameState == null || engine == null || phase != GamePhase.PLAYING) {
            return List.of();
        }

        long current = engine.getCurrentPlayer();
        return gameState.getKinds(current)
                .keySet()
                .stream()
                .map(Enum::name)
                .toList();
    }

    public void progressAutoModeIfNeeded() {
        GameBotService.Result result =
                botService.progressAutoModeIfNeeded(mode, phase, engine, gameState, random);
        if (result == GameBotService.Result.FINISHED) {
            phase = GamePhase.FINISHED;
        }
    }

    private void runSolitaireBotIfNeeded() {
        GameBotService.Result result =
                botService.runSolitaireBotIfNeeded(mode, phase, engine, gameState, random, P2);
        if (result == GameBotService.Result.FINISHED) {
            phase = GamePhase.FINISHED;
        }
    }

    private void requirePhase(GamePhase... allowed) {
        for (GamePhase p : allowed) {
            if (this.phase == p) {
                return;
            }
        }

        throw new IllegalStateException("Invalid game phase: " + this.phase);
    }
}
