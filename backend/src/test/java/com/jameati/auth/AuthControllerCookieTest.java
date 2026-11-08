package com.jameati.auth;

import static com.jameati.auth.AuthDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class AuthControllerCookieTest {
  private final AuthService service = mock(AuthService.class);
  private final AuthController controller = new AuthController(service, false, 2_592_000);

  @Test
  void loginKeepsBodyCompatibilityAndIssuesHttpOnlyCookies() {
    var auth = response();
    var request = new LoginRequest("admin@example.com", "secret123", false);
    when(service.login(request)).thenReturn(auth);

    var result = controller.login(request);

    assertThat(result.getBody().data()).isEqualTo(auth);
    assertThat(result.getHeaders().get(HttpHeaders.SET_COOKIE))
        .anySatisfy(value -> assertThat(value).contains("jameati_access=access.jwt", "HttpOnly", "SameSite=Lax", "Path=/"))
        .anySatisfy(value -> assertThat(value).contains("jameati_refresh=refresh.raw", "HttpOnly", "SameSite=Lax", "Path=/"));
  }

  @Test
  void refreshAndLogoutAcceptCookieWhenBodyIsMissing() {
    when(service.refresh("refresh.raw")).thenReturn(response());
    assertThat(controller.refresh(null, "refresh.raw").getStatusCode().is2xxSuccessful()).isTrue();
    verify(service).refresh("refresh.raw");

    var logout = controller.logout(null, "refresh.raw");
    verify(service).logout("refresh.raw");
    assertThat(logout.getHeaders().get(HttpHeaders.SET_COOKIE))
        .allSatisfy(value -> assertThat(value).contains("Max-Age=0", "HttpOnly", "Path=/"));
  }

  private static AuthResponse response() {
    return new AuthResponse("access.jwt", "refresh.raw", 900,
        new UserSummary(1L, "Admin", "admin@example.com", Set.of("ADMIN")));
  }
}
