package com.jameati.common;

import java.util.Map;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(com.jameati.payment.gateway.mpgs.MpgsPaymentException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  ApiResponse<Map<String,String>> paymentConflict(com.jameati.payment.gateway.mpgs.MpgsPaymentException ex) {
    return new ApiResponse<>(false, Map.of("code", ex.code()), ex.getMessage(), java.time.Instant.now());
  }
  @ExceptionHandler(com.jameati.payment.gateway.mpgs.MpgsGatewayException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  ApiResponse<Void> paymentGatewayUnavailable() { return ApiResponse.error("بوابة الدفع غير متاحة مؤقتاً؛ حاول مرة أخرى"); }
  @ExceptionHandler(com.jameati.subscription.ConsultationConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  ApiResponse<Void> consultationConflict(RuntimeException ex) { return ApiResponse.error(ex.getMessage()); }
  @ExceptionHandler(NotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
  ApiResponse<Void> notFound(NotFoundException ex) { return ApiResponse.error(ex.getMessage()); }
  @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiResponse<Map<String,String>> invalid(MethodArgumentNotValidException ex) {
    var errors = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(e -> e.getField(), e -> e.getDefaultMessage() == null ? "قيمة غير صالحة" : e.getDefaultMessage(), (a,b) -> a));
    return new ApiResponse<>(false, errors, "تحقق من الحقول", java.time.Instant.now());
  }
  @ExceptionHandler({IllegalArgumentException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiResponse<Void> badRequest(RuntimeException ex) { return ApiResponse.error(ex.getMessage()); }
  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class, HttpMediaTypeNotSupportedException.class,
      HandlerMethodValidationException.class, ConstraintViolationException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiResponse<Void> malformedRequest() { return ApiResponse.error("الطلب غير صالح"); }
  @ExceptionHandler(MaxUploadSizeExceededException.class) @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
  ApiResponse<Void> uploadTooLarge() { return ApiResponse.error("حجم الملف أكبر من المسموح"); }
  @ExceptionHandler(DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
  ApiResponse<Void> conflict() { return ApiResponse.error("تعارضت البيانات مع سجل موجود"); }
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class) @ResponseStatus(HttpStatus.CONFLICT)
  ApiResponse<Void> concurrentUpdate() { return ApiResponse.error("عُدّل السجل بالتزامن؛ أعد تحميله وحاول مجددًا"); }
  @ExceptionHandler(BadCredentialsException.class) @ResponseStatus(HttpStatus.UNAUTHORIZED)
  ApiResponse<Void> unauthorized() { return ApiResponse.error("بيانات الدخول غير صحيحة"); }
  @ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
  ApiResponse<Void> forbidden() { return ApiResponse.error("ليس لديك صلاحية لهذا الإجراء"); }
}
