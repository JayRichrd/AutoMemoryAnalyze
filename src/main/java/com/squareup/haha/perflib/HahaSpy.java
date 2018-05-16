package com.squareup.haha.perflib;

public final class HahaSpy {

  public static Instance allocatingThread(Instance instance) {
    Snapshot snapshot = instance.mHeap.mSnapshot;
    int threadSerialNumber;
    if (instance instanceof RootObj) {
      threadSerialNumber = ((RootObj) instance).mThread;
    } else {
      threadSerialNumber = instance.mStack.mThreadSerialNumber;
    }
    ThreadObj thread = snapshot.getThread(threadSerialNumber);
    return snapshot.findInstance(thread.mId);
  }

  private HahaSpy() {
    throw new AssertionError();
  }
}
