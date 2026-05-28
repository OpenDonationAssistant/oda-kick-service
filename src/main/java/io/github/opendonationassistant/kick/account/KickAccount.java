package io.github.opendonationassistant.kick.account;

public class KickAccount {

  private final KickAccountData data;

  public KickAccount(KickAccountData data) {
    this.data = data;
  }

  public KickAccountData data() {
    return data;
  }
}
