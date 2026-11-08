package com.jameati.auth;

import static com.jameati.auth.AuthDtos.*;

import com.jameati.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public static final String ACCESS_COOKIE = "jameati_access";
  public static final String REFRESH_COOKIE = "jameati_refresh";

  private final AuthService service;
  private final boolean secureCookies;
  private final long refreshCookieMaxAge;

  public AuthController(
      AuthService service,
      @Value("${app.auth.cookie-secure:false}") boolean secureCookies,
      @Value("${app.auth.refresh-cookie-max-age:2592000}") long refreshCookieMaxAge) {
    this.service = service;
    this.secureCookies = secureCookies;
    this.refreshCookieMaxAge = refreshCookieMaxAge;
  }

  @PostMapping("/register")
  ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
    return authenticated(service.register(request), HttpStatus.CREATED);
  }

  @PostMapping("/login")
  ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    return authenticated(service.login(request), HttpStatus.OK);
  }

  @PostMapping("/refresh")
  ResponseEntity<ApiResponse<AuthResponse>> refresh(
      @RequestBody(required = false) RefreshRequest request,
      @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken) {
    var raw = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
        ? request.refreshToken() : cookieToken;
    return authenticated(service.refresh(raw), HttpStatus.OK);
  }

  @PostMapping("/logout")
  ResponseEntity<ApiResponse<Void>> logout(
      @RequestBody(required = false) RefreshRequest request,
      @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken) {
    var raw = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
        ? request.refreshToken() : cookieToken;
    service.logout(raw);
    return ResponseEntity.ok().headers(clearCookieHeaders()).body(ApiResponse.ok(null));
  }

  @GetMapping("/session")
  ApiResponse<UserSummary> session(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new BadCredentialsException("الجلسة غير صالحة");
    }
    return ApiResponse.ok(service.session(authentication.getName()));
  }

  private ResponseEntity<ApiResponse<AuthResponse>> authenticated(AuthResponse response, HttpStatus status) {
    var headers = new HttpHeaders();
    headers.setCacheControl("no-store");
    headers.setPragma("no-cache");
    headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, response.accessToken(), response.expiresIn()).toString());
    headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, response.refreshToken(), refreshCookieMaxAge).toString());
    var body = status == HttpStatus.CREATED ? ApiResponse.created(response) : ApiResponse.ok(response);
    return ResponseEntity.status(status).headers(headers).body(body);
  }

  private HttpHeaders clearCookieHeaders() {
    var headers = new HttpHeaders();
    headers.setCacheControl("no-store");
    headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", 0).toString());
    headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", 0).toString());
    return headers;
  }

  private ResponseCookie cookie(String name, String value, long maxAgeSeconds) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secureCookies)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofSeconds(maxAgeSeconds))
        .build();
  }
}
