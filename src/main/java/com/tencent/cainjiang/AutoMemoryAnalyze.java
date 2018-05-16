package com.tencent.cainjiang;

import com.memory.analysis.leak.AnalysisResult;
import com.memory.analysis.leak.HeapAnalyzer;
import com.memory.analysis.utils.StableList;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.HprofParser;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.io.HprofBuffer;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author cainjiang
 * @date 2018/5/15
 */
public class AutoMemoryAnalyze {

    static String hprofFilePath = "src/main/files/dump_LowMemory_18-04-21_02.43.10_standard.hprof";

    public static void main(String[] args) throws IOException {
        File hprofFile = new File(hprofFilePath);
        HprofBuffer hprofBuffer = new MemoryMappedFileBuffer(hprofFile);
        final HprofParser hprofParser = new HprofParser(hprofBuffer);
        final Snapshot snapshot = hprofParser.parse();
        snapshot.computeDominators();

        HeapAnalyzer heapAnalyzer = new HeapAnalyzer();
        StableList list = getAllInstance(snapshot);
        for (int i = 0; i < list.size(); i++) {
            Instance instance = list.get(i);
            System.out.println("------>" + instance);
            if (instance instanceof ClassInstance) {
                AnalysisResult result = heapAnalyzer.findLeakTrace(0, snapshot, instance);
                System.out.println(result.className + " leak " + result.retainedHeapSize / 1024.0 / 1024.0 + "M");
                System.out.println(result.leakTrace.toString());
            }
        }
    }

    private static StableList getAllInstance(Snapshot snapshot) {
        StableList list = new StableList();
        List<Instance> instanceList = snapshot.getReachableInstances();
        for (Instance instance : instanceList) {
            list.add(instance);
        }
        return list;
    }
}
