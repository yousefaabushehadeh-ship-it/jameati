package com.jameati.crm;

public enum B2bStage {
  PROSPECT,
  CONTACTED,
  MEETING,
  DEMO,
  PROPOSAL,
  NEGOTIATION,
  WON,
  LOST,
  ON_HOLD;

  public boolean terminal() {
    return this == WON || this == LOST;
  }
}
