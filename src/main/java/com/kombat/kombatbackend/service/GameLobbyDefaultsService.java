package com.kombat.kombatbackend.service;

import java.util.List;

final class GameLobbyDefaultsService {

    private static final List<String> DEFAULT_TYPES = List.of(
            "FIGHTER",
            "ASSASSIN",
            "DPS",
            "TANK",
            "SUPPORT"
    );

    List<String> defaultTypes() {
        return DEFAULT_TYPES;
    }

    String defaultName(long playerId, String type, long p1) {
        if (playerId == p1) {
            return switch (type) {
                case "FIGHTER" -> "TANJIRO";
                case "ASSASSIN" -> "YORIICHI";
                case "DPS" -> "GIYU";
                case "TANK" -> "KYOJURO";
                default -> "INOSUKE";
            };
        }

        return switch (type) {
            case "FIGHTER" -> "MUZAN";
            case "ASSASSIN" -> "KOKUSHIBO";
            case "DPS" -> "DOMA";
            case "TANK" -> "AKAZA";
            default -> "NAKIME";
        };
    }
}

