package com.memory.analysis.utils;

import com.squareup.haha.perflib.Instance;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class StableList {
    private static final int MAX_NUM = 50;
    private List<Instance> list = new LinkedList<>();

    public void add(Instance e) {
        if (list.size() >= MAX_NUM) {
            list.remove(list.size() - 1);
        }
        list.add(e);
        list.sort(new Comparator<Instance>() {
            @Override
            public int compare(Instance i1, Instance i2) {
                if (i1.getTotalRetainedSize() > i2.getTotalRetainedSize()) {
                    return -1;
                } else if (i1.getTotalRetainedSize() < i2.getTotalRetainedSize()) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
    }

    public int size() {
        return list.size();
    }

    public Instance get(int i) {
        return list.get(i);
    }
}
