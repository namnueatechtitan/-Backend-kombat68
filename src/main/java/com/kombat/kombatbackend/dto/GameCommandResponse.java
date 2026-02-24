package com.kombat.kombatbackend.dto;

public class GameCommandResponse {

    private String message;

    public GameCommandResponse() {
    }

    public GameCommandResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
