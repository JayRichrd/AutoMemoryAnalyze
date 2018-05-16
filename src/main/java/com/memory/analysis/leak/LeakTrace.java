package com.memory.analysis.leak;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

/**
 * A chain of references that constitute the shortest strong reference path from a leaking instance
 * to the GC roots. Fixing the leak usually means breaking one of the references in that chain.
 */
public final class LeakTrace implements Serializable {

  public final List<LeakTraceElement> elements;

  LeakTrace(List<LeakTraceElement> elements) {
    this.elements = unmodifiableList(new ArrayList<>(elements));
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < elements.size(); i++) {
      LeakTraceElement element = elements.get(i);
      sb.append("* ");
      if (i == 0) {
        sb.append("GC ROOT ");
      } else if (i == elements.size() - 1) {
        sb.append("leaks ");
      } else {
        sb.append("references ");
      }
      sb.append(element).append("\n");
    }
    return sb.toString();
  }

  public String toDetailedString() {
    String string = "";
    for (LeakTraceElement element : elements) {
      string += element.toDetailedString();
    }
    return string;
  }
}
