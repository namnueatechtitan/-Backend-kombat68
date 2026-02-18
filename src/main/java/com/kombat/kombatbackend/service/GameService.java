package com.kombat.kombatbackend.service;

import com.kombat.kombatbackend.dto.GameCommandResponse;
import org.springframework.stereotype.Service;

@Service
public class GameService {

    public GameCommandResponse processCommand(String command) {
        return new GameCommandResponse("You sent command: " + command);
    }

}
