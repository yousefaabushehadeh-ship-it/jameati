package com.jameati.config;

import com.jameati.audit.*;
import com.jameati.auth.AuthController;
import com.jameati.auth.JwtService;
import com.jameati.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.user.UserRepository;
import com.jameati.onboarding.OnboardingRequiredFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors-origins}") String origins) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(
        Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList());
    configuration.setAllowedMethods(
        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, CorrelationIdFilter.HEADER));
    configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http, JwtFilter jwt, RateLimitFilter rateLimitFilter,
      OnboardingRequiredFilter onboardingRequiredFilter,
      AuditLogService auditLogService, ObjectMapper objectMapper,
      CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'")))
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, exception) ->
                writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED, "يلزم تسجيل الدخول"))
            .accessDeniedHandler((request, response, exception) ->
                writeSecurityError(response, objectMapper, HttpStatus.FORBIDDEN, "ليس لديك صلاحية لهذا الإجراء")))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/public/**",
                        "/api/v1/integrations/**",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/prometheus", "/actuator/info",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                    .requestMatchers("/api/v1/admin/users/**", "/api/v1/admin/roles/**")
                    .hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                    .requestMatchers("/api/v1/admin/crm/**")
                    .hasAnyRole("SALES", "OPERATIONS", "SECRETARY", "ADMIN", "SUPER_ADMIN")
                    .requestMatchers(
                        "/api/v1/admin/opportunities/**",
                        "/api/v1/admin/catalog/**",
                        "/api/v1/admin/intake/**",
                        "/api/v1/admin/media/**")
                    .hasAnyRole("DATA_ENTRY", "EDITOR", "ADMIN", "SUPER_ADMIN")
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterAfter(rateLimitFilter, org.springframework.web.filter.CorsFilter.class)
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(onboardingRequiredFilter, JwtFilter.class)
        .addFilterAfter(new AdminAuditFilter(auditLogService), JwtFilter.class)
        .build();
  }

  @Bean
  FilterRegistrationBean<JwtFilter> disableJwtServletRegistration(JwtFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> disableRateLimitServletRegistration(RateLimitFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  FilterRegistrationBean<OnboardingRequiredFilter> disableOnboardingServletRegistration(
      OnboardingRequiredFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  private static void writeSecurityError(
      HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(message));
  }
}

@Component
class JwtFilter extends org.springframework.web.filter.OncePerRequestFilter {
  private final JwtService jwt; private final UserRepository users; JwtFilter(JwtService jwt,UserRepository users){this.jwt=jwt;this.users=users;}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{var token=accessToken(req);if(token!=null){try{var user=users.findByEmailIgnoreCaseAndDeletedFalse(jwt.subject(token)).filter(com.jameati.user.User::isEnabled).orElseThrow();var authorities=user.getRoles().stream().map(r->new SimpleGrantedAuthority("ROLE_"+r.name())).toList();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(),null,authorities));}catch(Exception ignored){SecurityContextHolder.clearContext();}}chain.doFilter(req,res);}
  private static String accessToken(HttpServletRequest request){var header=request.getHeader(HttpHeaders.AUTHORIZATION);if(header!=null&&header.startsWith("Bearer ")&&header.length()>7)return header.substring(7);var cookies=request.getCookies();if(cookies!=null)for(var cookie:cookies)if(AuthController.ACCESS_COOKIE.equals(cookie.getName())&&!cookie.getValue().isBlank())return cookie.getValue();return null;}
}
