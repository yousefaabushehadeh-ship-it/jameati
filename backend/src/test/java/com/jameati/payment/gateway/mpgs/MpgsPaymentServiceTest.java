package com.jameati.payment.gateway.mpgs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jameati.subscription.*;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MpgsPaymentServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private AnnualMembershipRepository memberships;
  private MpgsClient gateway;
  private MpgsPaymentAttemptStore attempts;
  private MpgsPaymentService service;
  private User user;

  @BeforeEach
  void setUp() {
    memberships = mock(AnnualMembershipRepository.class);
    gateway = mock(MpgsClient.class);
    attempts = mock(MpgsPaymentAttemptStore.class);
    when(gateway.returnUrl(anyLong())).thenReturn("https://example.test/subscription?mpgsCallback=1");
    user = new User("payer@example.com", "hash", "Payer");
    user.setId(7L);
    service = new MpgsPaymentService(memberships, mock(SubscriptionPlanService.class),
        mock(UserRepository.class), gateway, attempts);
  }

  @Test void quarterlyPaymentCannotBeCreated() {
    assertThatThrownBy(() -> service.create(user,
        new MpgsPaymentDtos.CreatePaymentRequest(MembershipPlanCode.QUARTERLY)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(memberships, never()).saveAndFlush(any(AnnualMembership.class));
  }

  @Test void successfulFrictionlessAuthenticationDoesNotPayBeforeServerVerification() throws Exception {
    var item = pending(); locked(item);
    when(gateway.authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(json.readTree("{\"result\":\"SUCCESS\",\"transaction\":{\"authenticationStatus\":\"AUTHENTICATION_SUCCESSFUL\"}}"));
    assertThat(service.authenticate(user, 91L, request()).status()).isEqualTo(PaymentStatus.AUTHENTICATED);
    assertThat(item.getStatus()).isEqualTo(MembershipStatus.PENDING);
    verify(gateway, never()).pay(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test void challengeFlowDoesNotPayEarly() throws Exception {
    var item = pending(); locked(item);
    when(gateway.authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(json.readTree("{\"result\":\"PENDING\",\"transaction\":{\"authenticationStatus\":\"AUTHENTICATION_PENDING\"},\"authentication\":{\"redirect\":{\"html\":\"<form>challenge</form>\"}}}"));
    var result = service.authenticate(user, 91L, request());
    assertThat(result.status()).isEqualTo(PaymentStatus.REQUIRES_AUTHENTICATION);
    assertThat(result.authentication().redirectHtml()).contains("challenge");
    verify(gateway, never()).pay(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test void declinedPaymentDoesNotActivate() throws Exception {
    var item = pending(); locked(item);
    when(attempts.inspect(user, 91L)).thenReturn(new MpgsPaymentAttemptStore.Claim(
        MpgsPaymentAttemptStore.Action.VERIFY_AUTHENTICATION, item));
    when(gateway.retrieveTransaction(item.getGatewayOrderId(), item.getGatewayAuthenticationTransactionId()))
        .thenReturn(successfulAuthentication());
    when(attempts.claimExecution(user, 91L)).thenReturn(new MpgsPaymentAttemptStore.Claim(
        MpgsPaymentAttemptStore.Action.VERIFY_AUTHENTICATION, item));
    when(gateway.pay(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(json.readTree("{\"result\":\"FAILURE\",\"response\":{\"gatewayCode\":\"DECLINED\"}}"));
    item.setPaymentStatus(PaymentStatus.FAILED);
    item.setGatewayResponseCode("DECLINED");
    when(attempts.applyPaymentResult(eq(user), eq(91L), any())).thenReturn(item);
    var result = service.pay(user, 91L);
    assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(result.gatewayResponseCode()).isEqualTo("DECLINED");
    assertThat(item.getStatus()).isEqualTo(MembershipStatus.PENDING);
  }

  @Test void failedAuthenticationNeverPays() throws Exception {
    var item = pending(); locked(item);
    when(gateway.authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(json.readTree("{\"transaction\":{\"authenticationStatus\":\"AUTHENTICATION_FAILED\"}}"));
    assertThat(service.authenticate(user, 91L, request()).status()).isEqualTo(PaymentStatus.FAILED);
    verify(gateway, never()).pay(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test void duplicatePaymentAndCallbackReplayAreIdempotent() {
    var item = pending(); item.setPaymentStatus(PaymentStatus.PAID); item.setStatus(MembershipStatus.PAID); locked(item);
    when(attempts.inspect(user, 91L)).thenReturn(new MpgsPaymentAttemptStore.Claim(
        MpgsPaymentAttemptStore.Action.DONE, item));
    assertThat(service.pay(user, 91L).status()).isEqualTo(PaymentStatus.PAID);
    assertThat(service.pay(user, 91L).status()).isEqualTo(PaymentStatus.PAID);
    verifyNoInteractions(gateway);
  }

  @Test void timeoutRemainsProcessingForSafeRetry() throws Exception {
    var item = pending(); locked(item);
    when(attempts.inspect(user, 91L)).thenReturn(new MpgsPaymentAttemptStore.Claim(
        MpgsPaymentAttemptStore.Action.VERIFY_AUTHENTICATION, item));
    when(gateway.retrieveTransaction(anyString(), anyString()))
        .thenReturn(successfulAuthentication());
    when(attempts.claimExecution(user, 91L)).thenReturn(new MpgsPaymentAttemptStore.Claim(
        MpgsPaymentAttemptStore.Action.VERIFY_AUTHENTICATION, item));
    when(gateway.pay(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new MpgsGatewayException("timeout"));
    item.setPaymentStatus(PaymentStatus.PROCESSING);
    item.setGatewayResponseCode("PAYMENT_REQUIRES_RECONCILIATION");
    when(attempts.keepProcessing(user, 91L, "PAYMENT_REQUIRES_RECONCILIATION")).thenReturn(item);
    var result = service.pay(user, 91L);
    assertThat(result.status()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(result.gatewayResponseCode()).isEqualTo("PAYMENT_REQUIRES_RECONCILIATION");
    assertThat(item.getStatus()).isEqualTo(MembershipStatus.PENDING);
  }

  @Test void initiationAndAuthenticationReplayReuseStoredIdsExactlyOnce() throws Exception {
    var item = pending(); locked(item);
    item.setPaymentStatus(PaymentStatus.PENDING);
    item.setGatewayAuthenticationStage(null);
    when(gateway.initiateAuthentication(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(json.readTree("""
          {"result":"SUCCESS","response":{"gatewayCode":"APPROVED"},
           "transaction":{"authenticationStatus":"AUTHENTICATION_AVAILABLE"},
           "authentication":{"version":"3DS2","redirectHtml":"<script>method</script>"}}
          """));
    when(gateway.authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(successfulAuthentication());
    var first = service.initiate(user, 91L, new MpgsPaymentDtos.SessionRequest("SESSION-123"));
    assertThat(first.status()).isEqualTo(PaymentStatus.REQUIRES_AUTHENTICATION);
    assertThat(first.authentication().redirectHtml()).contains("method");
    assertThat(item.getGatewayAuthenticationVersion()).isEqualTo("3DS2");
    service.initiate(user, 91L, new MpgsPaymentDtos.SessionRequest("SESSION-123"));
    service.authenticate(user, 91L, request());
    assertThat(service.authenticate(user, 91L, request()).status()).isEqualTo(PaymentStatus.AUTHENTICATED);
    service.initiate(user, 91L, new MpgsPaymentDtos.SessionRequest("SESSION-123"));
    verify(gateway, times(1)).initiateAuthentication("JAM-order", "AUTH-tx", "SESSION-123", "35.00", "JOD");
    verify(gateway, times(1)).authenticatePayer(eq("JAM-order"), eq("AUTH-tx"), eq("SESSION-123"), eq("35.00"), eq("JOD"), anyString(), anyMap());
  }

  @Test void unavailableInitiationDoesNotAuthenticateOnReplay() throws Exception {
    var item = pending(); locked(item);
    item.setPaymentStatus(PaymentStatus.PENDING); item.setGatewayAuthenticationStage(null);
    when(gateway.initiateAuthentication(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(json.readTree("""
          {"result":"SUCCESS","transaction":{"authenticationStatus":"AUTHENTICATION_NOT_SUPPORTED"}}
          """));
    assertThat(service.initiate(user, 91L, new MpgsPaymentDtos.SessionRequest("SESSION-123")).status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(service.authenticate(user, 91L, request()).status()).isEqualTo(PaymentStatus.FAILED);
    verify(gateway, never()).authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
  }

  @Test void challengeReplayReturnsOriginalHtmlWithoutSecondGatewayCall() throws Exception {
    var item = pending(); locked(item);
    when(gateway.authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap()))
        .thenReturn(json.readTree("""
          {"result":"PENDING","transaction":{"authenticationStatus":"AUTHENTICATION_PENDING"},
           "authentication":{"redirectHtml":"<form>challenge</form>"}}
          """));
    service.authenticate(user, 91L, request());
    assertThat(service.authenticate(user, 91L, request()).authentication().redirectHtml()).contains("challenge");
    verify(gateway, times(1)).authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
  }

  @Test void wrongSessionNeverCallsGateway() {
    var item = pending(); locked(item);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.initiate(user, 91L,
        new MpgsPaymentDtos.SessionRequest("SESSION-other"))).isInstanceOf(IllegalArgumentException.class);
    var wrong = new MpgsPaymentDtos.AuthenticateRequest("SESSION-other", request().browser());
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.authenticate(user, 91L, wrong))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(gateway);
  }

  @Test void gatewayRejectionFailsInitiationAndPreservesSafeCause() {
    var item = pending(); locked(item);
    item.setPaymentStatus(PaymentStatus.PENDING); item.setGatewayAuthenticationStage(null);
    when(gateway.initiateAuthentication(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new MpgsGatewayException(400, "INVALID_REQUEST"));
    var result = service.initiate(user, 91L, new MpgsPaymentDtos.SessionRequest("SESSION-123"));
    assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(result.gatewayResponseCode()).isEqualTo("GATEWAY_HTTP_400_INVALID_REQUEST");
    service.authenticate(user, 91L, request());
    verify(gateway, never()).authenticatePayer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
  }

  private void locked(AnnualMembership item) {
    when(memberships.findByIdAndUserIdForUpdate(91L, 7L)).thenReturn(Optional.of(item));
  }
  private MpgsPaymentDtos.AuthenticateRequest request() {
    return new MpgsPaymentDtos.AuthenticateRequest("SESSION-123",
        new MpgsPaymentDtos.BrowserDetails("browser", "text/html", "en-US", 1200, 800, 24, -180, false));
  }
  private AnnualMembership pending() {
    var item = new AnnualMembership();
    item.setId(91L); item.setUser(user); item.setPlanCode(MembershipPlanCode.ANNUAL);
    item.setBillingPeriodMonths(12); item.setAmount(new BigDecimal("35.00")); item.setCurrency("JOD");
    item.setStatus(MembershipStatus.PENDING); item.setPaymentStatus(PaymentStatus.REQUIRES_AUTHENTICATION);
    item.setPaymentProvider("MPGS"); item.setGatewayOrderId("JAM-order");
    item.setGatewayAuthenticationTransactionId("AUTH-tx"); item.setGatewayTransactionId("PAY-tx");
    item.setGatewaySessionId("SESSION-123");
    item.setGatewayAuthenticationStage("INITIATED");
    item.setGatewayAuthenticationStatus("AUTHENTICATION_AVAILABLE");
    return item;
  }

  private com.fasterxml.jackson.databind.JsonNode successfulAuthentication() throws Exception {
    return json.readTree("{\"result\":\"SUCCESS\",\"order\":{\"id\":\"JAM-order\",\"amount\":\"35.00\",\"currency\":\"JOD\"},\"transaction\":{\"id\":\"AUTH-tx\",\"authenticationStatus\":\"AUTHENTICATION_SUCCESSFUL\"}}");
  }
}
