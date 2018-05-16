package com.memory.analysis.leak;

import com.memory.analysis.exclusion.Exclusion;
import com.squareup.haha.perflib.Instance;

final class LeakNode {
  /** May be null. */
  final Exclusion exclusion;
  final Instance instance;
  final LeakNode parent;
  final LeakReference leakReference;

  LeakNode(Exclusion exclusion, Instance instance, LeakNode parent, LeakReference leakReference) {
    this.exclusion = exclusion;
    this.instance = instance;
    this.parent = parent;
    this.leakReference = leakReference;
  }
}
