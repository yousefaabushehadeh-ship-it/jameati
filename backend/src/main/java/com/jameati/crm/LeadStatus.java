package com.jameati.crm;

public enum LeadStatus {
  NEW,
  CONTACTED,
  INTERESTED,
  QUALIFIED,
  CONSULTATION_BOOKED,
  PAID,
  ACTIVE,
  RENEWAL,
  LOST,
  NOT_INTERESTED;

  public boolean terminal() {
    return this == LOST || this == NOT_INTERESTED;
  }
}
