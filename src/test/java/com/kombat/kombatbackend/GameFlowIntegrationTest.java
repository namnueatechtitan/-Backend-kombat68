package com.kombat.kombatbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GameFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetGame() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockMvc.perform(post("/api/game/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void fullFlow_freeSpawn_to_playerAction_buySpawnAndExecute() throws Exception {

        mockMvc.perform(post("/api/game/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "spawnCost": 100,
                                  "hexPurchaseCost": 1000,
                                  "initBudget": 10000,
                                  "initHp": 100,
                                  "turnBudget": 90,
                                  "maxBudget": 23456,
                                  "interestPct": 5,
                                  "maxTurns": 20,
                                  "maxSpawns": 5
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "mode": "DUEL" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/character")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "playerId": 1, "character": "HUMAN" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/character")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "playerId": 2, "character": "DEMON" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/setup/full/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "type": "FIGHTER",
                                    "defenseFactor": 1,
                                    "strategy": "move down; done;"
                                  }
                                ]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/setup/full/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "type": "FIGHTER",
                                    "defenseFactor": 1,
                                    "strategy": "move up; done;"
                                  }
                                ]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/start"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/game/phase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("FREE_SPAWN"));

        mockMvc.perform(post("/api/game/spawn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "FIGHTER", "row": 0, "col": 0 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/game/spawn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "FIGHTER", "row": 7, "col": 7 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/game/phase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("PLAYER_ACTION"));

        mockMvc.perform(post("/api/game/buy-hex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "row": 2, "col": 1 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/game/buy-hex")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "row": 2, "col": 2 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/game/spawn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "FIGHTER", "row": 0, "col": 1 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/game/spawn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "FIGHTER", "row": 0, "col": 2 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/game/end-turn"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/game/phase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("PLAYER_ACTION"));

        // Verify strategies executed: at least one P1 minion moved from row 0 to row 1.
        mockMvc.perform(get("/api/game/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameState.minions[?(@.ownerId == 1 && @.x == 1)].length()").value(2));
    }
}
