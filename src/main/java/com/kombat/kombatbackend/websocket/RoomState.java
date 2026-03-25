package com.kombat.kombatbackend.websocket;

import com.kombat.kombatbackend.engine.gamestate.CharacterType;
import com.kombat.kombatbackend.engine.gamestate.GameConfig;
import com.kombat.kombatbackend.engine.gamestate.GameMode;

import java.util.ArrayList;
import java.util.List;

public class RoomState {

    private String roomId;
    private String host;
    private GameMode mode;
    private GameConfig config;
    private List<String> players = new ArrayList<>();
    private boolean started;
    private RoomSetupPhase setupPhase = RoomSetupPhase.LOBBY;
    private Integer player1MinionTypeCount;
    private Integer player2MinionTypeCount;
    private Integer effectiveMinionTypeCount;
    private CharacterType player1Character;
    private CharacterType player2Character;
    private List<RoomConfiguredMinion> sharedConfiguredMinions = new ArrayList<>();
    private boolean player1SharedSetupConfirmed;
    private boolean player2SharedSetupConfirmed;
    private List<RoomConfiguredMinion> player1ConfiguredMinions = new ArrayList<>();
    private List<RoomConfiguredMinion> player2ConfiguredMinions = new ArrayList<>();
    private long updatedAtEpochMs;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public GameConfig getConfig() {
        return config;
    }

    public void setConfig(GameConfig config) {
        this.config = config;
    }

    public List<String> getPlayers() {
        return players;
    }

    public void setPlayers(List<String> players) {
        this.players = players;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public RoomSetupPhase getSetupPhase() {
        return setupPhase;
    }

    public void setSetupPhase(RoomSetupPhase setupPhase) {
        this.setupPhase = setupPhase;
    }

    public Integer getPlayer1MinionTypeCount() {
        return player1MinionTypeCount;
    }

    public void setPlayer1MinionTypeCount(Integer player1MinionTypeCount) {
        this.player1MinionTypeCount = player1MinionTypeCount;
    }

    public Integer getPlayer2MinionTypeCount() {
        return player2MinionTypeCount;
    }

    public void setPlayer2MinionTypeCount(Integer player2MinionTypeCount) {
        this.player2MinionTypeCount = player2MinionTypeCount;
    }

    public Integer getEffectiveMinionTypeCount() {
        return effectiveMinionTypeCount;
    }

    public void setEffectiveMinionTypeCount(Integer effectiveMinionTypeCount) {
        this.effectiveMinionTypeCount = effectiveMinionTypeCount;
    }

    public CharacterType getPlayer1Character() {
        return player1Character;
    }

    public void setPlayer1Character(CharacterType player1Character) {
        this.player1Character = player1Character;
    }

    public CharacterType getPlayer2Character() {
        return player2Character;
    }

    public void setPlayer2Character(CharacterType player2Character) {
        this.player2Character = player2Character;
    }

    public List<RoomConfiguredMinion> getSharedConfiguredMinions() {
        return sharedConfiguredMinions;
    }

    public void setSharedConfiguredMinions(List<RoomConfiguredMinion> sharedConfiguredMinions) {
        this.sharedConfiguredMinions = sharedConfiguredMinions;
    }

    public boolean isPlayer1SharedSetupConfirmed() {
        return player1SharedSetupConfirmed;
    }

    public void setPlayer1SharedSetupConfirmed(boolean player1SharedSetupConfirmed) {
        this.player1SharedSetupConfirmed = player1SharedSetupConfirmed;
    }

    public boolean isPlayer2SharedSetupConfirmed() {
        return player2SharedSetupConfirmed;
    }

    public void setPlayer2SharedSetupConfirmed(boolean player2SharedSetupConfirmed) {
        this.player2SharedSetupConfirmed = player2SharedSetupConfirmed;
    }

    public List<RoomConfiguredMinion> getPlayer1ConfiguredMinions() {
        return player1ConfiguredMinions;
    }

    public void setPlayer1ConfiguredMinions(List<RoomConfiguredMinion> player1ConfiguredMinions) {
        this.player1ConfiguredMinions = player1ConfiguredMinions;
    }

    public List<RoomConfiguredMinion> getPlayer2ConfiguredMinions() {
        return player2ConfiguredMinions;
    }

    public void setPlayer2ConfiguredMinions(List<RoomConfiguredMinion> player2ConfiguredMinions) {
        this.player2ConfiguredMinions = player2ConfiguredMinions;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }
}
