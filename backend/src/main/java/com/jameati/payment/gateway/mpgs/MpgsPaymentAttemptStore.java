package com.jameati.payment.gateway.mpgs;

import com.fasterxml.jackson.databind.JsonNode;
import com.jameati.common.NotFoundException;
import com.jameati.me.UserNotification;
import com.jameati.me.UserNotificationRepository;
import com.jameati.subscription.AnnualMembership;
import com.jameati.subscription.AnnualMembershipRepository;
import com.jameati.subscription.MembershipPlanCode;
import com.jameati.subscription.MembershipStatus;
import com.jameati.subscription.PaymentStatus;
import com.jameati.user.User;
import com.jameati.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Short database transactions surrounding the remote MPGS PAY call. */
@Component
class MpgsPaymentAttemptStore {
  enum Action { VERIFY_AUTHENTICATION, RECONCILE_PAYMENT, DONE }
  record Claim(Action action, AnnualMembership payment) {}

  private static final String PROVIDER = "MPGS";
  private static final Set<String> APPROVED_CODES =
      Set.of("APPROVED", "APPROVED_AUTO", "APPROVED_PENDING_SETTLEMENT");
  private final AnnualMembershipRepository memberships;
  private final UserRepository users;
  private final UserNotificationRepository notifications;

  MpgsPaymentAttemptStore(AnnualMembershipRepository memberships, UserRepository users,
      UserNotificationRepository notifications) {
    this.memberships = memberships;
    this.users = users;
    this.notifications = notifications;
  }

  @Transactional
  Claim inspect(User user, long paymentId) {
    var payment = lockOwned(user, paymentId);
    if (payment.getPaymentStatus() == PaymentStatus.PAID && payment.getStatus() == MembershipStatus.PAID) {
      return new Claim(Action.DONE, payment);
    }
    if (payment.getPaymentStatus() == PaymentStatus.PROCESSING) {
      return new Claim(Action.RECONCILE_PAYMENT, payment);
    }
    if (payment.getPaymentStatus() == PaymentStatus.FAILED
        || payment.getPaymentStatus() == PaymentStatus.CANCELLED
        || payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
      throw new MpgsPaymentException("INVALID_PAYMENT_STATE", "لا يمكن متابعة عملية الدفع في حالتها الحالية");
    }
    if (payment.getPaymentStatus() == PaymentStatus.PENDING
        || "INITIATED".equals(payment.getGatewayAuthenticationStage())) {
      return new Claim(Action.DONE, payment);
    }
    return new Claim(Action.VERIFY_AUTHENTICATION, payment);
  }

  @Transactional
  Claim claimExecution(User user, long paymentId) {
    var payment = lockOwned(user, paymentId);
    if (payment.getPaymentStatus() == PaymentStatus.PAID && payment.getStatus() == MembershipStatus.PAID) {
      return new Claim(Action.DONE, payment);
    }
    if (payment.getPaymentStatus() == PaymentStatus.PROCESSING) {
      return new Claim(Action.RECONCILE_PAYMENT, payment);
    }
    if (payment.getPaymentStatus() != PaymentStatus.AUTHENTICATED
        && payment.getPaymentStatus() != PaymentStatus.REQUIRES_AUTHENTICATION) {
      throw new MpgsPaymentException("INVALID_PAYMENT_STATE", "لا يمكن تنفيذ الدفع قبل مصادقة البطاقة");
    }
    payment.setGatewayAuthenticationStatus("AUTHENTICATION_SUCCESSFUL");
    payment.setPaymentStatus(PaymentStatus.PROCESSING);
    payment.setGatewayResult("PENDING");
    payment.setGatewayResponseCode("PAYMENT_PROCESSING");
    memberships.saveAndFlush(payment);
    return new Claim(Action.VERIFY_AUTHENTICATION, payment);
  }

  @Transactional
  AnnualMembership authenticationPending(User user, long paymentId, JsonNode result) {
    var payment = lockOwned(user, paymentId);
    if (payment.getPaymentStatus() != PaymentStatus.REQUIRES_AUTHENTICATION) return payment;
    payment.setGatewayAuthenticationStatus(authenticationStatus(result));
    updateGateway(payment, result);
    payment.setPaymentStatus(PaymentStatus.REQUIRES_AUTHENTICATION);
    return payment;
  }

  @Transactional
  AnnualMembership authenticationFailed(User user, long paymentId, JsonNode result) {
    var payment = lockOwned(user, paymentId);
    if (payment.getPaymentStatus() != PaymentStatus.REQUIRES_AUTHENTICATION) return payment;
    payment.setGatewayAuthenticationStatus(authenticationStatus(result));
    updateGateway(payment, result);
    payment.setPaymentStatus(PaymentStatus.FAILED);
    return payment;
  }

  @Transactional
  AnnualMembership keepProcessing(User user, long paymentId, String code) {
    var payment = lockOwned(user, paymentId);
    if (payment.getPaymentStatus() != PaymentStatus.PAID) {
      payment.setPaymentStatus(PaymentStatus.PROCESSING);
      payment.setGatewayResult("PENDING");
      payment.setGatewayResponseCode(code);
    }
    return payment;
  }

  @Transactional
  AnnualMembership applyPaymentResult(User user, long paymentId, JsonNode result) {
    var payment = lockOwned(user, paymentId);
    if (payment.getPaymentStatus() == PaymentStatus.PAID && payment.getStatus() == MembershipStatus.PAID) {
      return payment;
    }
    updateGateway(payment, result);
    if (!approved(payment, result)) {
      if (looksApproved(result)) {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setGatewayResponseCode("PAYMENT_VERIFICATION_FAILED");
      } else {
        payment.setPaymentStatus(definitiveFailure(result) ? PaymentStatus.FAILED : PaymentStatus.PROCESSING);
      }
      return payment;
    }
    if (memberships.existsPaidMpgsTransactionForAnotherMembership(
        payment.getGatewayOrderId(), payment.getGatewayTransactionId(), payment.getId())) {
      throw new MpgsPaymentException("PAYMENT_TRANSACTION_ALREADY_CONSUMED",
          "عملية الدفع مرتبطة بعضوية أخرى");
    }
    var now = Instant.now();
    payment.setPaymentStatus(PaymentStatus.PAID);
    payment.setStatus(MembershipStatus.PAID);
    payment.setPaymentReference(payment.getGatewayOrderId());
    payment.setPaidAt(now);
    payment.setStartsAt(now);
    payment.setExpiresAt(payment.getPlanCode() == MembershipPlanCode.CONSULTATION ? null
        : now.atZone(ZoneOffset.UTC).plusMonths(payment.getBillingPeriodMonths()).toInstant());
    payment.setActivationSource(PROVIDER);
    memberships.saveAndFlush(payment);
    var notification = new UserNotification();
    notification.setUser(payment.getUser());
    notification.setType("MEMBERSHIP_PAID");
    notification.setTitle("تم تأكيد الدفع وتفعيل باقتك");
    notification.setBody("تمت معالجة دفعتك بأمان وتفعيل باقتك.");
    notification.setLink("/dashboard/membership");
    notifications.save(notification);
    return payment;
  }

  static boolean validAuthentication(AnnualMembership payment, JsonNode result) {
    return "SUCCESS".equals(text(result, "result"))
        && "AUTHENTICATION_SUCCESSFUL".equals(authenticationStatus(result))
        && payment.getGatewayOrderId().equals(text(result, "order", "id"))
        && payment.getGatewayAuthenticationTransactionId().equals(text(result, "transaction", "id"))
        && amountAndCurrencyMatch(payment, result);
  }

  private AnnualMembership lockOwned(User user, long paymentId) {
    if (user == null || user.getId() == null) throw new IllegalArgumentException("المستخدم غير صالح");
    users.findEnabledByIdForUpdate(user.getId())
        .orElseThrow(() -> new NotFoundException("المستخدم غير موجود"));
    var payment = memberships.findByIdAndUserIdForUpdate(paymentId, user.getId())
        .orElseThrow(() -> new NotFoundException("عملية الدفع غير موجودة"));
    if (!PROVIDER.equals(payment.getPaymentProvider())) {
      throw new IllegalArgumentException("عملية الدفع ليست عبر MPGS");
    }
    return payment;
  }

  private static boolean approved(AnnualMembership payment, JsonNode result) {
    var type = text(result, "transaction", "type");
    return "SUCCESS".equals(text(result, "result"))
        && APPROVED_CODES.contains(text(result, "response", "gatewayCode"))
        && payment.getGatewayOrderId().equals(text(result, "order", "id"))
        && payment.getGatewayTransactionId().equals(text(result, "transaction", "id"))
        && ("PAYMENT".equals(type) || "PAY".equals(type))
        && amountAndCurrencyMatch(payment, result);
  }

  private static boolean looksApproved(JsonNode result) {
    return "SUCCESS".equals(text(result, "result"))
        && APPROVED_CODES.contains(text(result, "response", "gatewayCode"));
  }

  private static boolean amountAndCurrencyMatch(AnnualMembership payment, JsonNode result) {
    try {
      var returned = text(result, "order", "amount");
      return returned != null && new BigDecimal(returned).compareTo(payment.getAmount()) == 0
          && payment.getCurrency().equals(text(result, "order", "currency"));
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private static boolean definitiveFailure(JsonNode result) {
    var code = text(result, "response", "gatewayCode");
    return code != null && !"SUBMITTED".equals(code) && !"UNKNOWN".equals(code)
        && !"TIMED_OUT".equals(code) && !"SYSTEM_ERROR".equals(code);
  }

  private static void updateGateway(AnnualMembership payment, JsonNode result) {
    payment.setGatewayResult(text(result, "result"));
    var code = text(result, "response", "gatewayCode");
    if (code == null) code = text(result, "error", "cause");
    payment.setGatewayResponseCode(code);
  }

  private static String authenticationStatus(JsonNode result) {
    var status = text(result, "transaction", "authenticationStatus");
    return status == null ? text(result, "order", "authenticationStatus") : status;
  }

  private static String text(JsonNode root, String... path) {
    if (root == null) return null;
    var node = root;
    for (var segment : path) node = node.path(segment);
    return node.isMissingNode() || node.isNull() ? null : node.asText();
  }
}
