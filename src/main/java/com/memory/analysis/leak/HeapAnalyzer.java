/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.memory.analysis.leak;

import com.memory.analysis.exclusion.ExcludedRefs;
import com.squareup.haha.perflib.*;
import com.squareup.haha.perflib.io.HprofBuffer;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;
import com.squareup.haha.trove.THashMap;
import com.squareup.haha.trove.TObjectProcedure;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class HeapAnalyzer {

  private static final ExcludedRefs NO_EXCLUDED_REFS = ExcludedRefs.builder().build();

  private static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";

  private final ExcludedRefs excludedRefs;

  public HeapAnalyzer(ExcludedRefs excludedRefs) {
    this.excludedRefs = excludedRefs;
  }

  public HeapAnalyzer() {
    this.excludedRefs = NO_EXCLUDED_REFS;
  }

  public List<TrackedReference> findTrackedReferences(File heapDumpFile, String leakObject) {
    if (!heapDumpFile.exists()) {
      throw new IllegalArgumentException("File does not exist: " + heapDumpFile);
    }
    try {
      HprofBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
      HprofParser parser = new HprofParser(buffer);
      Snapshot snapshot = parser.parse();
      deduplicateGcRoots(snapshot);

      ClassObj refClass = snapshot.findClass(leakObject);
      List<TrackedReference> references = new ArrayList<>();
      for (Instance weakRef : refClass.getInstancesList()) {
        List<ClassInstance.FieldValue> values = HahaHelper.classInstanceValues(weakRef);
        String key = HahaHelper.asString(HahaHelper.fieldValue(values, "key"));
        String name =
            HahaHelper.hasField(values, "name") ? HahaHelper.asString(HahaHelper.fieldValue(values, "name")) : "(No name field)";
        Instance instance = HahaHelper.fieldValue(values, "referent");
        if (instance != null) {
          String className = getClassName(instance);
          List<LeakReference> fields = describeFields(instance);
          references.add(new TrackedReference(key, name, className, fields));
        }
      }
      return references;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Searches the heap dump for a LeakInspector$InspectUUID/KeyedWeakReference instance with the corresponding key,
   * and then computes the shortest strong reference path from that instance to the GC roots.
   */
  public AnalysisResult checkForLeak(File heapDumpFile, String referenceKey) { // referenceKey是生成的uuid
    long analysisStartNanoTime = System.nanoTime();

    if (!heapDumpFile.exists()) {
      Exception exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
      return AnalysisResult.failure(exception, since(analysisStartNanoTime));
    }

    try {
      HprofBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
      HprofParser parser = new HprofParser(buffer);
      // Snapshot类似Jsoup的Document，方便的解析hprof
      Snapshot snapshot = parser.parse();
      deduplicateGcRoots(snapshot);

      Instance leakingRef = findLeakingReference(referenceKey, snapshot);

      // False alarm, weak reference was cleared in between key check and heap dump.
      // 即使经过gc对象还存在，并不能说明发生了泄露，在开始检测到dump堆这段时间还有可能被回收
      // 因此现在再判断下，如果在hprof中没找到那就是这段时间内被回收了，没有发生泄漏
      if (leakingRef == null) {
        return AnalysisResult.noLeak(since(analysisStartNanoTime));
      }

      return findLeakTrace(analysisStartNanoTime, snapshot, leakingRef);
    } catch (Throwable e) {
      return AnalysisResult.failure(e, since(analysisStartNanoTime));
    }
  }

  /**
   * Pruning duplicates reduces memory pressure from hprof bloat added in Marshmallow.
   */
  void deduplicateGcRoots(Snapshot snapshot) {
    // THashMap has a smaller memory footprint than HashMap.
    final THashMap<String, RootObj> uniqueRootMap = new THashMap<>();

    // 获取GCRoots，GCRoots存在于SnapShot里面的default heap
    final Collection<RootObj> gcRoots = snapshot.getGCRoots();
    // 遍历GCRoot，针对每个GCRoot生成特有的key值，类似于native static@0x82763689
    // 将GCRoot添加到map中，达到去重的目的
    // RootObj的name和id一样就是同一个对象
    for (RootObj root : gcRoots) {
      // 遍历每个GCRoot，生成一个key
      // 类似： native static@0x82763689
      String key = generateRootKey(root);
      if (!uniqueRootMap.containsKey(key)) {
        uniqueRootMap.put(key, root);
      }
    }

    // Repopulate snapshot with unique GC roots.
    gcRoots.clear(); // 清空从snapShot中拿到的gcRoots，并重新赋值，此后这个gcRoot中的root对象是唯一的
    uniqueRootMap.forEach(new TObjectProcedure<String>() {
      @Override public boolean execute(String key) {
        return gcRoots.add(uniqueRootMap.get(key));
      }
    });
  }

  private String generateRootKey(RootObj root) {
    /**
     * {@link RootType}
      */
    return String.format("%s@0x%08x", root.getRootType().getName(), root.getId());
  }

  // 返回的是泄露的那个对象实例

  /**
   * 使用LeakCanary，产生泄露的对象是com.squareup.leakcanary.KeyedWeakReference对象\n
   * 使用APM，产生泄露的对象是com.tencent.magnifiersdk.memory.LeakInspector$InspectUUID对象
   * key的含义：每个对象对应的一个唯一的uuid
   */
  private Instance findLeakingReference(String key, Snapshot snapshot) {
    // 找到KeyedWeakReference类型的对象
    // 类似的 也可以找到android.graphic.Bitmap等其他对象
    ClassObj refClass = snapshot.findClass("com.tencent.magnifiersdk.memory.LeakInspector$InspectUUID");
    List<String> keysFound = new ArrayList<>();
    // 一个类可能有很多实例，如何找到在监控的的那个可能泄露的对象？
    // 在这个类的所有实例中找key相同的那个对象
    for (Instance instance : refClass.getInstancesList()) {
      List<ClassInstance.FieldValue> values = HahaHelper.classInstanceValues(instance);
      String keyCandidate = HahaHelper.asString(HahaHelper.fieldValue(values, "key"));
      if (keyCandidate.equals(key)) {
        return HahaHelper.fieldValue(values, "referent");
      }
      keysFound.add(keyCandidate);
    }
    throw new IllegalStateException(
        "Could not find weak reference with key " + key + " in " + keysFound);
  }

  /**
   * 找到某个实例的引用链
   * @param analysisStartNanoTime
   * @param snapshot
   * @param leakingRef
   * @return
   */
  public AnalysisResult findLeakTrace(long analysisStartNanoTime, Snapshot snapshot,
                                      Instance leakingRef) {

    ShortestPathFinder pathFinder = new ShortestPathFinder(excludedRefs);
    ShortestPathFinder.Result result = pathFinder.findPath(snapshot, leakingRef);

    // False alarm, no strong reference path to GC Roots.
    if (result.leakingNode == null) {
      return AnalysisResult.noLeak(since(analysisStartNanoTime));
    }

    LeakTrace leakTrace = buildLeakTrace(result.leakingNode);

    String className = leakingRef.getClassObj().getClassName();

    // Side effect: computes retained size.
    snapshot.computeDominators();

    Instance leakingInstance = result.leakingNode.instance;

    long retainedSize = leakingInstance.getTotalRetainedSize();

    // TODO: check O sources and see what happened to android.graphics.Bitmap.mBuffer
//    if (SDK_INT <= N_MR1) {
//      retainedSize += computeIgnoredBitmapRetainedSize(snapshot, leakingInstance);
//    }

    return AnalysisResult.leakDetected(result.excludingKnownLeaks, className, leakTrace, retainedSize,
        since(analysisStartNanoTime));
  }

  /**
   * Bitmaps and bitmap byte arrays are sometimes held by native gc roots, so they aren't included
   * in the retained size because their root dominator is a native gc root.
   * To fix this, we check if the leaking instance is a dominator for each bitmap instance and then
   * add the bitmap size.
   *
   * From experience, we've found that bitmap created in code (Bitmap.createBitmap()) are correctly
   * accounted for, however bitmaps set in layouts are not.
   */
  private long computeIgnoredBitmapRetainedSize(Snapshot snapshot, Instance leakingInstance) {
    long bitmapRetainedSize = 0;
    ClassObj bitmapClass = snapshot.findClass("android.graphics.Bitmap");

    for (Instance bitmapInstance : bitmapClass.getInstancesList()) {
      if (isIgnoredDominator(leakingInstance, bitmapInstance)) {
        ArrayInstance mBufferInstance = HahaHelper.fieldValue(HahaHelper.classInstanceValues(bitmapInstance), "mBuffer");
        // Native bitmaps have mBuffer set to null. We sadly can't account for them.
        if (mBufferInstance == null) {
          continue;
        }
        long bufferSize = mBufferInstance.getTotalRetainedSize();
        long bitmapSize = bitmapInstance.getTotalRetainedSize();
        // Sometimes the size of the buffer isn't accounted for in the bitmap retained size. Since
        // the buffer is large, it's easy to detect by checking for bitmap size < buffer size.
        if (bitmapSize < bufferSize) {
          bitmapSize += bufferSize;
        }
        bitmapRetainedSize += bitmapSize;
      }
    }
    return bitmapRetainedSize;
  }

  private boolean isIgnoredDominator(Instance dominator, Instance instance) {
    boolean foundNativeRoot = false;
    while (true) {
      Instance immediateDominator = instance.getImmediateDominator();
      if (immediateDominator instanceof RootObj
          && ((RootObj) immediateDominator).getRootType() == RootType.UNKNOWN) {
        // Ignore native roots
        instance = instance.getNextInstanceToGcRoot();
        foundNativeRoot = true;
      } else {
        instance = immediateDominator;
      }
      if (instance == null) {
        return false;
      }
      if (instance == dominator) {
        return foundNativeRoot;
      }
    }
  }

  private LeakTrace buildLeakTrace(LeakNode leakingNode) {
    List<LeakTraceElement> elements = new ArrayList<>();
    // We iterate from the leak to the GC root
    LeakNode node = new LeakNode(null, null, leakingNode, null);
    while (node != null) {
      LeakTraceElement element = buildLeakElement(node);
      if (element != null) {
        elements.add(0, element);
      }
      node = node.parent;
    }
    return new LeakTrace(elements);
  }

  private LeakTraceElement buildLeakElement(LeakNode node) {
    if (node.parent == null) {
      // Ignore any root node.
      return null;
    }
    Instance holder = node.parent.instance;

    if (holder instanceof RootObj) {
      return null;
    }
    LeakTraceElement.Holder holderType;
    String className;
    String extra = null;
    List<LeakReference> leakReferences = describeFields(holder);

    className = getClassName(holder);

    List<String> classHierarchy = new ArrayList<>();
    classHierarchy.add(className);
    String rootClassName = Object.class.getName();
    if (holder instanceof ClassInstance) {
      ClassObj classObj = holder.getClassObj();
      while (!(classObj = classObj.getSuperClassObj()).getClassName().equals(rootClassName)) {
        classHierarchy.add(classObj.getClassName());
      }
    }

    if (holder instanceof ClassObj) {
      holderType = LeakTraceElement.Holder.CLASS;
    } else if (holder instanceof ArrayInstance) {
      holderType = LeakTraceElement.Holder.ARRAY;
    } else {
      ClassObj classObj = holder.getClassObj();
      if (HahaHelper.extendsThread(classObj)) {
        holderType = LeakTraceElement.Holder.THREAD;
        String threadName = HahaHelper.threadName(holder);
        extra = "(named '" + threadName + "')";
      } else if (className.matches(ANONYMOUS_CLASS_NAME_PATTERN)) {
        String parentClassName = classObj.getSuperClassObj().getClassName();
        if (rootClassName.equals(parentClassName)) {
          holderType = LeakTraceElement.Holder.OBJECT;
          try {
            // This is an anonymous class implementing an interface. The API does not give access
            // to the interfaces implemented by the class. We check if it's in the class path and
            // use that instead.
            Class<?> actualClass = Class.forName(classObj.getClassName());
            Class<?>[] interfaces = actualClass.getInterfaces();
            if (interfaces.length > 0) {
              Class<?> implementedInterface = interfaces[0];
              extra = "(anonymous implementation of " + implementedInterface.getName() + ")";
            } else {
              extra = "(anonymous subclass of java.lang.Object)";
            }
          } catch (ClassNotFoundException ignored) {
          }
        } else {
          holderType = LeakTraceElement.Holder.OBJECT;
          // Makes it easier to figure out which anonymous class we're looking at.
          extra = "(anonymous subclass of " + parentClassName + ")";
        }
      } else {
        holderType = LeakTraceElement.Holder.OBJECT;
      }
    }
    return new LeakTraceElement(node.leakReference, holderType, classHierarchy, extra,
        node.exclusion, leakReferences);
  }

  private List<LeakReference> describeFields(Instance instance) {
    List<LeakReference> leakReferences = new ArrayList<>();

    if (instance instanceof ClassObj) {
      ClassObj classObj = (ClassObj) instance;
      for (Map.Entry<Field, Object> entry : classObj.getStaticFieldValues().entrySet()) {
        String name = entry.getKey().getName();
        String value = entry.getValue() == null ? "null" : entry.getValue().toString();
        leakReferences.add(new LeakReference(LeakTraceElement.Type.STATIC_FIELD, name, value));
      }
    } else if (instance instanceof ArrayInstance) {
      ArrayInstance arrayInstance = (ArrayInstance) instance;
      if (arrayInstance.getArrayType() == Type.OBJECT) {
        Object[] values = arrayInstance.getValues();
        for (int i = 0; i < values.length; i++) {
          String name = Integer.toString(i);
          String value = values[i] == null ? "null" : values[i].toString();
          leakReferences.add(new LeakReference(LeakTraceElement.Type.ARRAY_ENTRY, name, value));
        }
      }
    } else {
      ClassObj classObj = instance.getClassObj();
      for (Map.Entry<Field, Object> entry : classObj.getStaticFieldValues().entrySet()) {
        String name = entry.getKey().getName();
        String value = entry.getValue() == null ? "null" : entry.getValue().toString();
        leakReferences.add(new LeakReference(LeakTraceElement.Type.STATIC_FIELD, name, value));
      }
      ClassInstance classInstance = (ClassInstance) instance;
      for (ClassInstance.FieldValue field : classInstance.getValues()) {
        String name = field.getField().getName();
        String value = field.getValue() == null ? "null" : field.getValue().toString();
        leakReferences.add(new LeakReference(LeakTraceElement.Type.INSTANCE_FIELD, name, value));
      }
    }
    return leakReferences;
  }

  private String getClassName(Instance instance) {
    String className;
    if (instance instanceof ClassObj) {
      ClassObj classObj = (ClassObj) instance;
      className = classObj.getClassName();
    } else if (instance instanceof ArrayInstance) {
      ArrayInstance arrayInstance = (ArrayInstance) instance;
      className = arrayInstance.getClassObj().getClassName();
    } else {
      ClassObj classObj = instance.getClassObj();
      className = classObj.getClassName();
    }
    return className;
  }

  private long since(long analysisStartNanoTime) {
    return NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
  }
}
