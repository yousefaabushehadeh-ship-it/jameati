package com.jameati.payment.gateway.mpgs;

import static com.jameati.payment.gateway.mpgs.MpgsPaymentDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.jameati.common.NotFoundException;
import com.jameati.subscription.*;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(prefix = "app.payment.mpgs", name = "enabled", havingValue = "true")
public class MpgsPaymentService {
  private static final Logger log = LoggerFactory.getLogger(MpgsPaymentService.class);
  private static final String PROVIDER = "MPGS";
  private final AnnualMembershipRepository memberships;
  private final SubscriptionPlanService plans;
  private final UserRepository users;
  private final MpgsClient gateway;
  private final MpgsPaymentAttemptStore attempts;

  public MpgsPaymentService(AnnualMembershipRepository memberships, SubscriptionPlanService plans,
      UserRepository users, MpgsClient gateway,
      MpgsPaymentAttemptStore attempts) {
    this.memberships = memberships;
    this.plans = plans;
    this.users = users;
    this.gateway = gateway;
    this.attempts = attempts;
  }

  @Transactional
  public PaymentResponse create(User user, CreatePaymentRequest request) {
    if (request == null || request.planCode() == null) throw new IllegalArgumentException("خطة الاشتراك مطلوبة");
    request.planCode().requirePurchasable();
    if (!plans.current().enabled()) throw new IllegalArgumentException("الاشتراكات غير متاحة حالياً");
    var lockedUser = lockUser(user);
    var now = Instant.now();
    var active = activeForPlan(lockedUser, request.planCode(), now);
    if (active != null) throw new IllegalArgumentException("لديك اشتراك فعّال في هذه الباقة");
    var existing = memberships
        .findFirstByUserIdAndPlanCodeAndStatusAndPaymentProviderAndDeletedFalseOrderByCreatedAtDesc(
            lockedUser.getId(), request.planCode(), MembershipStatus.PENDING, PROVIDER);
    if (existing.isPresent() && existing.get().getPaymentStatus() != PaymentStatus.FAILED
        && existing.get().getGatewaySessionId() != null) return response(existing.get(), storedAuthentication(existing.get()),
            new SessionResponse(existing.get().getGatewaySessionId(), gateway.hostedSessionScriptUrl()));

    var membership = new AnnualMembership();
    membership.setUser(lockedUser);
    membership.setPlanCode(request.planCode());
    membership.setBillingPeriodMonths(request.planCode().months());
    membership.setAmount(request.planCode().price());
    membership.setCurrency("JOD");
    membership.setStatus(MembershipStatus.PENDING);
    membership.setPaymentStatus(PaymentStatus.PENDING);
    membership.setPaymentProvider(PROVIDER);
    membership.setGatewayOrderId(id("JAM"));
    membership.setGatewayAuthenticationTransactionId(id("AUTH"));
    membership.setGatewayTransactionId(id("PAY"));
    membership = memberships.saveAndFlush(membership);
    try {
      var session = gateway.createSession();
      if (!"SUCCESS".equals(text(session, "result")) || text(session, "session", "id") == null) {
        return fail(membership, session, "SESSION_CREATION_FAILED");
      }
      membership.setGatewaySessionId(text(session, "session", "id"));
      membership.setGatewayResult(text(session, "result"));
      memberships.save(membership);
      return response(membership, null,
          new SessionResponse(membership.getGatewaySessionId(), gateway.hostedSessionScriptUrl()));
    } catch (MpgsGatewayException exception) {
      membership.setPaymentStatus(PaymentStatus.FAILED);
      membership.setGatewayResult("ERROR");
      membership.setGatewayResponseCode(exception.code());
      memberships.save(membership);
      return response(membership, null, null);
    }
  }

  @Transactional
  public PaymentResponse initiate(User user, long paymentId, SessionRequest request) {
    var membership = requireLocked(user, paymentId);
    requireSession(membership, request.sessionId());
    if (membership.getGatewayAuthenticationStage() != null
        || membership.getPaymentStatus() != PaymentStatus.PENDING) {
      return response(membership, storedAuthentication(membership), null);
    }
    membership.setGatewayAuthenticationStage("INITIATED");
    JsonNode result;
    try {
      result = gateway.initiateAuthentication(membership.getGatewayOrderId(),
          membership.getGatewayAuthenticationTransactionId(), request.sessionId(),
          amount(membership), membership.getCurrency());
    } catch (MpgsGatewayException exception) {
      return gatewayFailure(membership, PaymentStatus.FAILED, exception);
    }
    var authenticationStatus = authenticationStatus(result);
    updateAuthentication(membership, result);
    updateGatewayResult(membership, result);
    if (!"AUTHENTICATION_AVAILABLE".equals(authenticationStatus)
        || !acceptableResult(result)
        || (membership.getGatewayAuthenticationVersion() != null
            && !"3DS2".equals(membership.getGatewayAuthenticationVersion()))) {
      membership.setPaymentStatus(PaymentStatus.FAILED);
      return response(membership, authentication(result), null);
    }
    membership.setPaymentStatus(PaymentStatus.REQUIRES_AUTHENTICATION);
    return response(membership, authentication(result), null);
  }

  @Transactional
  public PaymentResponse authenticate(User user, long paymentId, AuthenticateRequest request) {
    var membership = requireLocked(user, paymentId);
    requireSession(membership, request.sessionId());
    if ("AUTHENTICATE_REQUESTED".equals(membership.getGatewayAuthenticationStage())
        || membership.getPaymentStatus() == PaymentStatus.FAILED
        || membership.getPaymentStatus() == PaymentStatus.PAID
        || membership.getPaymentStatus() == PaymentStatus.PROCESSING) {
      return response(membership, storedAuthentication(membership), null);
    }
    if (membership.getPaymentStatus() != PaymentStatus.REQUIRES_AUTHENTICATION
        || !"INITIATED".equals(membership.getGatewayAuthenticationStage())
        || !"AUTHENTICATION_AVAILABLE".equals(membership.getGatewayAuthenticationStatus())) {
      throw new MpgsPaymentException("INVALID_PAYMENT_STATE", "يجب بدء مصادقة البطاقة أولاً");
    }
    membership.setGatewayAuthenticationStage("AUTHENTICATE_REQUESTED");
    var browser = request.browser();
    var device = Map.<String, Object>of(
        "browser", browser.userAgent(),
        "browserDetails", Map.of(
            "acceptHeaders", browser.acceptHeader(), "colorDepth", browser.colorDepth(),
            "javaEnabled", browser.javaEnabled(), "language", browser.language(),
            "screenHeight", browser.screenHeight(), "screenWidth", browser.screenWidth(),
            "timeZone", browser.timeZone()));
    JsonNode result;
    try {
      result = gateway.authenticatePayer(membership.getGatewayOrderId(),
          membership.getGatewayAuthenticationTransactionId(), request.sessionId(), amount(membership),
          membership.getCurrency(), gateway.returnUrl(paymentId) + "&plan=" + membership.getPlanCode().name(), device);
    } catch (MpgsGatewayException exception) {
      return gatewayFailure(membership, PaymentStatus.FAILED, exception);
    }
    updateGatewayResult(membership, result);
    updateAuthentication(membership, result);
    var status = authenticationStatus(result);
    if (!acceptableResult(result)) {
      membership.setPaymentStatus(PaymentStatus.FAILED);
      return response(membership, authentication(result), null);
    }
    if ("AUTHENTICATION_SUCCESSFUL".equals(status)) {
      membership.setPaymentStatus(PaymentStatus.AUTHENTICATED);
      return response(membership, authentication(result), null);
    }
    if ("AUTHENTICATION_PENDING".equals(status) && redirectHtml(result) != null) {
      membership.setPaymentStatus(PaymentStatus.REQUIRES_AUTHENTICATION);
      return response(membership, authentication(result), null);
    }
    membership.setPaymentStatus(PaymentStatus.FAILED);
    return response(membership, authentication(result), null);
  }

  public PaymentResponse pay(User user, long paymentId) {
    var claim = attempts.inspect(user, paymentId);
    debugAuthentication(claim.payment());
    if (claim.action() == MpgsPaymentAttemptStore.Action.DONE) return response(claim.payment(), null, null);
    if (claim.action() == MpgsPaymentAttemptStore.Action.RECONCILE_PAYMENT) {
      return reconcilePayment(user, claim.payment());
    }
    JsonNode authentication;
    try {
      authentication = gateway.retrieveTransaction(claim.payment().getGatewayOrderId(),
          claim.payment().getGatewayAuthenticationTransactionId());
    } catch (MpgsGatewayException exception) {
      return response(claim.payment(), null, null);
    }
    if (!MpgsPaymentAttemptStore.validAuthentication(claim.payment(), authentication)) {
      var status = authenticationStatus(authentication);
      var payment = "AUTHENTICATION_PENDING".equals(status)
          ? attempts.authenticationPending(user, paymentId, authentication)
          : attempts.authenticationFailed(user, paymentId, authentication);
      return response(payment, authentication(authentication), null);
    }
    var execution = attempts.claimExecution(user, paymentId);
    if (execution.action() == MpgsPaymentAttemptStore.Action.DONE) return response(execution.payment(), null, null);
    if (execution.action() == MpgsPaymentAttemptStore.Action.RECONCILE_PAYMENT) {
      return reconcilePayment(user, execution.payment());
    }
    try {
      var result = gateway.pay(execution.payment().getGatewayOrderId(),
          execution.payment().getGatewayTransactionId(),
          execution.payment().getGatewayAuthenticationTransactionId(),
          execution.payment().getGatewaySessionId(), amount(execution.payment()),
          execution.payment().getCurrency());
      var applied = attempts.applyPaymentResult(user, paymentId, result);
      debugAuthentication(applied);
      return response(applied, null, null);
    } catch (MpgsGatewayException exception) {
      debugAuthentication(execution.payment());
      return response(attempts.keepProcessing(user, paymentId, "PAYMENT_REQUIRES_RECONCILIATION"), null, null);
    }
  }

  private PaymentResponse reconcilePayment(User user, AnnualMembership payment) {
    try {
      var result = gateway.retrieveTransaction(payment.getGatewayOrderId(), payment.getGatewayTransactionId());
      var applied = attempts.applyPaymentResult(user, payment.getId(), result);
      debugAuthentication(applied);
      return response(applied, null, null);
    } catch (MpgsGatewayException exception) {
      debugAuthentication(payment);
      return response(attempts.keepProcessing(user, payment.getId(), "PAYMENT_REQUIRES_RECONCILIATION"), null, null);
    }
  }

  @Transactional(readOnly = true)
  public PaymentResponse status(User user, long paymentId) {
    var membership = memberships.findByIdAndUserIdAndDeletedFalse(paymentId, user.getId())
        .orElseThrow(() -> new NotFoundException("عملية الدفع غير موجودة"));
    requireMpgs(membership);
    return response(membership, null, null);
  }

  private AnnualMembership requireLocked(User user, long paymentId) {
    if (user == null || user.getId() == null) throw new IllegalArgumentException("المستخدم غير صالح");
    var membership = memberships.findByIdAndUserIdForUpdate(paymentId, user.getId())
        .orElseThrow(() -> new NotFoundException("عملية الدفع غير موجودة"));
    requireMpgs(membership);
    return membership;
  }
  private static void requireMpgs(AnnualMembership membership) {
    if (!PROVIDER.equals(membership.getPaymentProvider())) throw new IllegalArgumentException("عملية الدفع ليست عبر MPGS");
  }
  private static void requireSession(AnnualMembership membership, String sessionId) {
    requireMpgs(membership);
    if (sessionId == null || !sessionId.equals(membership.getGatewaySessionId()))
      throw new IllegalArgumentException("جلسة الدفع غير صالحة");
  }
  private User lockUser(User user) {
    if (user == null || user.getId() == null) throw new IllegalArgumentException("المستخدم غير صالح");
    return users.findEnabledByIdForUpdate(user.getId()).orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
  }
  private AnnualMembership activeForPlan(User user, MembershipPlanCode code, Instant now) {
    if (code.contentAccess()) return memberships
        .findFirstByUserIdAndStatusAndStartsAtLessThanEqualAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            user.getId(), MembershipStatus.PAID, now, now).orElse(null);
    return memberships.findByUserIdAndPlanCodeAndStatusAndDeletedFalseOrderByCreatedAtDesc(
        user.getId(), code, MembershipStatus.PAID).stream().filter(item -> item.getStartsAt() != null
            && !item.getStartsAt().isAfter(now) && !"ACHIEVED".equals(item.getConsultationProgress())).findFirst().orElse(null);
  }
  private PaymentResponse fail(AnnualMembership membership, JsonNode result, String fallbackCode) {
    updateGatewayResult(membership, result);
    membership.setPaymentStatus(PaymentStatus.FAILED);
    if (membership.getGatewayResponseCode() == null) membership.setGatewayResponseCode(fallbackCode);
    memberships.save(membership);
    return response(membership, null, null);
  }
  private PaymentResponse gatewayFailure(AnnualMembership membership, PaymentStatus status, MpgsGatewayException exception) {
    membership.setPaymentStatus(status);
    membership.setGatewayResult("ERROR");
    membership.setGatewayResponseCode(exception.code());
    return response(membership, null, null);
  }
  private static void updateGatewayResult(AnnualMembership membership, JsonNode result) {
    membership.setGatewayResult(text(result, "result"));
    var code = text(result, "response", "gatewayCode");
    if (code == null) code = text(result, "error", "cause");
    membership.setGatewayResponseCode(code);
  }
  static PaymentResponse response(AnnualMembership membership, AuthenticationResponse authentication, SessionResponse session) {
    debugAuthentication(membership);
    return new PaymentResponse(membership.getId(), membership.getGatewayOrderId(),
        membership.getGatewayAuthenticationTransactionId(), membership.getPaymentStatus(),
        membership.getAmount(), membership.getCurrency(), session, authentication,
        membership.getGatewayResult(), membership.getGatewayResponseCode());
  }
  private static AuthenticationResponse authentication(JsonNode result) {
    return new AuthenticationResponse(authenticationStatus(result), redirectHtml(result));
  }
  private static String authenticationStatus(JsonNode result) {
    var status = text(result, "transaction", "authenticationStatus");
    return status == null ? text(result, "order", "authenticationStatus") : status;
  }
  private static String redirectHtml(JsonNode result) {
    var html = text(result, "authentication", "redirectHtml");
    return html == null ? text(result, "authentication", "redirect", "html") : html;
  }
  private static void updateAuthentication(AnnualMembership payment, JsonNode result) {
    payment.setGatewayAuthenticationStatus(authenticationStatus(result));
    var version = text(result, "authentication", "version");
    if (version != null) payment.setGatewayAuthenticationVersion(version);
    payment.setGatewayAuthenticationHtml(redirectHtml(result));
  }
  private static AuthenticationResponse storedAuthentication(AnnualMembership payment) {
    return new AuthenticationResponse(payment.getGatewayAuthenticationStatus(), payment.getGatewayAuthenticationHtml());
  }
  private static boolean acceptableResult(JsonNode result) {
    var outcome = text(result, "result");
    var code = text(result, "response", "gatewayCode");
    return (outcome == null || "SUCCESS".equals(outcome) || "PENDING".equals(outcome))
        && (code == null || "APPROVED".equals(code) || "SUBMITTED".equals(code)
            || "AUTHENTICATION_IN_PROGRESS".equals(code))
        && !"DO_NOT_PROCEED".equals(text(result, "response", "gatewayRecommendation"));
  }
  // Temporary diagnostic: never log the raw gateway payload or full session identifier.
  private static void debugAuthentication(AnnualMembership payment) {
    var session = payment.getGatewaySessionId();
    log.debug("MPGS paymentId={} orderId={} authenticationTransactionId={} authenticationStatus={} state={} sessionSuffix={}",
        payment.getId(), payment.getGatewayOrderId(), payment.getGatewayAuthenticationTransactionId(),
        payment.getGatewayAuthenticationStatus(), payment.getPaymentStatus(),
        session == null ? null : session.substring(Math.max(0, session.length() - 4)));
  }
  private static String amount(AnnualMembership membership) { return membership.getAmount().toPlainString(); }
  private static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24); }
  private static String text(JsonNode root, String... path) {
    if (root == null) return null;
    var node = root;
    for (var segment : path) node = node.path(segment);
    return node.isMissingNode() || node.isNull() ? null : node.asText();
  }
}
