package com.jameati.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiAvailabilityIntegrationTest {
  @Autowired MockMvc mvc;
  @Test void anonymousBrowserCanProbeDeploymentAndValidateInput() throws Exception {
    mvc.perform(get("/api/v1/public/ai/status"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
        .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    mvc.perform(post("/api/v1/public/opportunities/12548/ai/chat")
        .contentType("application/json").content("{\"message\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
  @Test void protectedRoutesStillRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/admin/settings/homepage")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me/ai-recommendations")).andExpect(status().isUnauthorized());
  }
}
