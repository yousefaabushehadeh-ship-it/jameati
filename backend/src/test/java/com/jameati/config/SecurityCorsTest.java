package com.jameati.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityCorsTest {
  private static final String FRONTEND_ORIGIN = "http://localhost:3000";

  @Autowired private MockMvc mockMvc;

  @Test
  void allowsLoginPreflightFromConfiguredFrontendOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
        .andExpect(
            header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
        .andExpect(
            header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")));
  }

  @Test
  void rejectsLoginPreflightFromUnknownOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden());
  }
}
