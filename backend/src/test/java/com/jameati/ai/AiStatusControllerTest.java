package com.jameati.ai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AiStatusControllerTest {
  @Autowired MockMvc mvc;
  @Autowired OpenAiConfig config;

  @Test void anonymousStatusReportsConfigurationWithoutExposingSecrets() throws Exception {
    var previous = config.getApiKey();
    try {
      config.setApiKey("test-provider-secret");
      mvc.perform(get("/api/v1/public/ai/status"))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.version").value(1))
          .andExpect(jsonPath("$.data.enabled").value(true))
          .andExpect(jsonPath("$.data.length()").value(2));
      config.setApiKey(" ");
      mvc.perform(get("/api/v1/public/ai/status"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.enabled").value(false));
    } finally {
      config.setApiKey(previous);
    }
  }

  @Test void statusDoesNotMakePrivateRoutesPublic() throws Exception {
    mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
  }
}
