package com.example.habittracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HabitApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void habitLifecycle() throws Exception {
        String response = mvc.perform(post("/api/habits").contentType("application/json")
                .content("{\"name\":\"Read books\",\"description\":\"Daily reading\",\"frequency\":\"DAILY\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Read books"))
                .andReturn().getResponse().getContentAsString();
        JsonNode habit = mapper.readTree(response);
        String path = "/api/habits/" + habit.get("id").asLong();
        mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(post(path + "/complete")).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(1));
        mvc.perform(get(path + "/stats")).andExpect(status().isOk());
        mvc.perform(delete(path + "/complete")).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(0));
        mvc.perform(put(path).contentType("application/json").content("{\"name\":\"Read more\",\"frequency\":\"WEEKLY\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Read more"));
        mvc.perform(patch(path + "/active").contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(delete(path)).andExpect(status().isNoContent());
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }
    @Test void rejectsBlankHabitName() throws Exception {
        mvc.perform(post("/api/habits").contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void servesDashboard() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }
    @Test void exposesReadiness() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void missingHabitReturns404() throws Exception {
        mvc.perform(get("/api/habits/999999")).andExpect(status().isNotFound());
    }
    @Test void rejectsUntrustedCrossOriginPreflight() throws Exception {
        mvc.perform(options("/api/habits")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
