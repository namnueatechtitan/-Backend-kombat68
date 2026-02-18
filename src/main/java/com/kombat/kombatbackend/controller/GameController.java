package com.kombat.kombatbackend.controller;

import com.kombat.kombatbackend.dto.GameCommandRequest;
import com.kombat.kombatbackend.dto.GameCommandResponse;
import com.kombat.kombatbackend.service.GameService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/command")
    public GameCommandResponse sendCommand(@RequestBody GameCommandRequest request) {
        return gameService.processCommand(request.getCommand());
    }
}
