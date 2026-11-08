package com.jameati.subscription;

import java.math.BigDecimal;

public enum MembershipPlanCode {
  ANNUAL("25.00", 12, true),
  QUARTERLY("20.00", 3, true),
  CONSULTATION("150.00", 0, false);

  private final BigDecimal price;
  private final int months;
  private final boolean contentAccess;

  MembershipPlanCode(String price, int months, boolean contentAccess) {
    this.price = new BigDecimal(price);
    this.months = months;
    this.contentAccess = contentAccess;
  }

  public boolean purchasable() { return this != QUARTERLY; }
  public void requirePurchasable() {
    if (!purchasable()) throw new IllegalArgumentException("هذه الباقة غير متاحة حالياً");
  }

  public BigDecimal price() { return price; }
  public int months() { return months; }
  public boolean contentAccess() { return contentAccess; }
  public boolean consultationAccess() { return this == CONSULTATION; }
  public static MembershipPlanCode orAnnual(MembershipPlanCode code) {
    return code == null ? ANNUAL : code;
  }
}
