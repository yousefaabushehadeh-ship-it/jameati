package com.jameati.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import com.jameati.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthorizationIntegrationTest {
  @Autowired MockMvc mockMvc;
  @Autowired AuditLogRepository auditLogs;

  @Test
  @WithMockUser(username = "entry@example.com", roles = "DATA_ENTRY")
  void dataEntryCanReadCatalogButCannotDeleteOrReadSettings() throws Exception {
    mockMvc.perform(get("/api/v1/admin/catalog/COURSE").param("size", "1"))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/admin/catalog/COURSE/1"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/admin/settings/homepage"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "admin@example.com", roles = "ADMIN")
  void ordinaryAdminCannotManageUsers() throws Exception {
    mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "root@example.com", roles = "SUPER_ADMIN")
  void superAdminCanListUsers() throws Exception {
    mockMvc.perform(get("/api/v1/admin/users").param("size", "1"))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "admin@example.com", roles = "ADMIN")
  void successfulAdministrationMutationIsPersistedInTheAuditLog() throws Exception {
    mockMvc.perform(put("/api/v1/admin/settings/homepage")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"hero\":{\"title\":\"جامعتي\"}}"))
        .andExpect(status().isOk());

    assertThat(auditLogs.findAll()).anySatisfy(log -> {
      assertThat(log.getActorEmail()).isEqualTo("admin@example.com");
      assertThat(log.getAction()).isEqualTo("PUT");
      assertThat(log.getResourceType()).isEqualTo("settings");
      assertThat(log.isSuccess()).isTrue();
    });
  }

  @Test
  @WithMockUser(username = "admin@example.com", roles = "ADMIN")
  void seededAdministrativeSettingCanBeReplacedAndPublishedForPublicTools() throws Exception {
    mockMvc.perform(get("/api/v1/admin/settings/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isMap())
        .andExpect(jsonPath("$.data.records").isArray())
        .andExpect(jsonPath("$.data.records[0].id").exists());

    mockMvc.perform(put("/api/v1/admin/settings/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"records\":[{\"id\":\"engineering\",\"name\":\"الهندسة\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records[0].id").value("engineering"));

    mockMvc.perform(get("/api/v1/public/settings").param("namespace", "categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records[0].id").value("engineering"));

    mockMvc.perform(get("/api/v1/public/settings").param("namespace", "notifications"))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(username = "admin@example.com", roles = "ADMIN")
  void analyticsIngestAndAggregationUseTheDatabase() throws Exception {
    mockMvc.perform(post("/api/v1/public/analytics/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventType\":\"PAGE_VIEW\",\"path\":\"/courses?q=java\",\"sessionId\":\"d81555c0-78a0-4dc8-86a1-7a6530c8e573\",\"metadata\":{\"width\":1280}}"))
        .andExpect(status().isAccepted());

    mockMvc.perform(get("/api/v1/admin/analytics/summary").param("days", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalEvents", greaterThanOrEqualTo(1)));
  }
}
