package com.memory.analysis.leak;

import java.io.Serializable;

/**
 * A single field in a {@link LeakTraceElement}.
 */
public final class LeakReference implements Serializable {

  public final LeakTraceElement.Type type;
  public final String name;
  public final String value;

  public LeakReference(LeakTraceElement.Type type, String name, String value) {
    this.type = type;
    this.name = name;
    this.value = value;
  }

  public String getDisplayName() {
    switch (type) {
      case ARRAY_ENTRY:
        return "[" + name + "]";
      case STATIC_FIELD:
      case INSTANCE_FIELD:
        return name;
      case LOCAL:
        return "<Java Local>";
      default:
        throw new IllegalStateException(
            "Unexpected type " + type + " name = " + name + " value = " + value);
    }
  }

  @Override public String toString() {
    switch (type) {
      case ARRAY_ENTRY:
      case INSTANCE_FIELD:
        return getDisplayName() + " = " + value;
      case STATIC_FIELD:
        return "static " + getDisplayName() + " = " + value;
      case LOCAL:
        return getDisplayName();
      default:
        throw new IllegalStateException(
            "Unexpected type " + type + " name = " + name + " value = " + value);
    }
  }
}
