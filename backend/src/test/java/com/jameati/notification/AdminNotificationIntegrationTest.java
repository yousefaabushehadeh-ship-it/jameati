package com.jameati.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.me.UserNotificationRepository;
import com.jameati.user.RoleName;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminNotificationIntegrationTest {
  private static final String ADMIN_EMAIL = "notification-admin@jameati.test";
  private static final String USER_EMAIL = "notification-user@jameati.test";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserRepository users;
  @Autowired UserNotificationRepository notifications;

  private User recipient;

  @BeforeEach
  void prepareUsers() {
    users
        .findByEmailIgnoreCaseAndDeletedFalse(ADMIN_EMAIL)
        .orElseGet(
            () -> {
              var admin = new User(ADMIN_EMAIL, "unused-test-password", "مدير الإشعارات");
              admin.getRoles().add(RoleName.ADMIN);
              return users.save(admin);
            });
    recipient =
        users
            .findByEmailIgnoreCaseAndDeletedFalse(USER_EMAIL)
            .orElseGet(
                () -> users.save(new User(USER_EMAIL, "unused-test-password", "مستخدم الإشعارات")));
  }

  @Test
  @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
  void sendsAnImmediateInAppNotificationAndPersistsItsCounters() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "title", "فرصة جديدة مناسبة لك",
                            "body", "راجع تفاصيل الفرصة الجديدة ومتطلبات التقديم.",
                            "link", "/opportunities",
                            "audience", "USERS",
                            "channels", Set.of("IN_APP")))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("SENT"))
        .andExpect(jsonPath("$.data.recipientCount", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.data.inAppSentCount", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.data.failedCount").value(0));

    assertThat(
            notifications
                .findByUserIdAndDeletedFalse(recipient.getId(), PageRequest.of(0, 20))
                .getContent())
        .anySatisfy(
            notification -> {
              assertThat(notification.getTitle()).isEqualTo("فرصة جديدة مناسبة لك");
              assertThat(notification.getLink()).isEqualTo("/opportunities");
            });
  }

  @Test
  @WithMockUser(username = ADMIN_EMAIL, roles = "ADMIN")
  void schedulesFutureDeliveryWithoutDispatchingItEarly() throws Exception {
    var scheduledAt = Instant.now().plusSeconds(3600);
    mockMvc
        .perform(
            post("/api/v1/admin/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "title", "إشعار مجدول",
                            "body", "سيصل هذا الإشعار في موعده المحدد.",
                            "audience", "STAFF",
                            "channels", Set.of("IN_APP", "EMAIL"),
                            "scheduledAt", scheduledAt))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.data.recipientCount").value(0))
        .andExpect(jsonPath("$.data.scheduledAt").exists());
  }

  @Test
  @WithMockUser(username = "entry@jameati.test", roles = "DATA_ENTRY")
  void dataEntryCannotReadOrSendAdministrativeNotifications() throws Exception {
    mockMvc.perform(get("/api/v1/admin/notifications")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/admin/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"غير مسموح","body":"لن يُرسل","audience":"ALL","channels":["IN_APP"]}
                    """))
        .andExpect(status().isForbidden());
  }
}
