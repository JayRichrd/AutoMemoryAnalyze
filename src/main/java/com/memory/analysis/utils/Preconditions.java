package com.memory.analysis.utils;

public final class Preconditions {

  /**
   * Returns instance unless it's null.
   *
   * @throws NullPointerException if instance is null
   */
  public static <T> T checkNotNull(T instance, String name) {
    if (instance == null) {
      throw new NullPointerException(name + " must not be null");
    }
    return instance;
  }

  private Preconditions() {
    throw new AssertionError();
  }
}
