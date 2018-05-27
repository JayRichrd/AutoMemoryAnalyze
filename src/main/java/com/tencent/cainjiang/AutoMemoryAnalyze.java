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
import com.tencent.cainjiang.db.DBUtil;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.List;

/**
 * @author cainjiang
 * @date 2018/5/15
 */
public class AutoMemoryAnalyze {

    static String hprofFilePath = "src/main/files/test2.hprof";
    //获取连接
    static Connection conn = DBUtil.getConnection();
    static Statement stmt = null;

    static StringBuilder addStrSql = new StringBuilder("INSERT INTO ").append("result_table ").append("(object_name, num, sum_leak) ").append("VALUES (?,?,?)");
    static StringBuilder updateStrSql = new StringBuilder("UPDATE ").append("result_table ").append("SET ").append("num = ?, sum_leak = ?");

    public static void main(String[] args) throws IOException, SQLException {
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
                int num = queryExist(result.className);
                if (num > 0) {
                    update(result.className, num, result.retainedHeapSize / 1024.0 / 1024.0);
                } else {
                    add(result.className, 1, result.retainedHeapSize / 1024.0 / 1024.0);
                }
                System.out.println(result.className + " leak " + result.retainedHeapSize / 1024.0 / 1024.0 + "M");
                System.out.println(result.leakTrace.toString());
            }
        }
        stmt.close();
        conn.close();
    }

    private static StableList getAllInstance(Snapshot snapshot) {
        StableList list = new StableList();
        List<Instance> instanceList = snapshot.getReachableInstances();
        for (Instance instance : instanceList) {
            list.add(instance);
        }
        return list;
    }

    private static int queryExist(String objectStr) throws SQLException {
        int num = 0;
        if (stmt == null) {
            stmt = conn.createStatement();
        }
        StringBuilder queryStrSql = new StringBuilder("SELECT num FROM result_table WHERE object_name = '").append(objectStr).append("'");
        ResultSet rs = stmt.executeQuery(queryStrSql.toString());
        if (rs.next()) {
            num = rs.getInt("num");
        }
        rs.close();
        return num;
    }

    private static double queryLeak(String objectStr) throws SQLException {
        double sumLeak = 0.0;
        if (stmt == null) {
            stmt = conn.createStatement();
        }
        StringBuilder queryStrSql = new StringBuilder("SELECT sum_leak FROM result_table WHERE object_name = '").append(objectStr).append("'");
        ResultSet rs = stmt.executeQuery(queryStrSql.toString());
        if (rs.next()) {
            sumLeak = rs.getDouble("sum_leak");
        }
        rs.close();
        return sumLeak;
    }


    private static void update(String objectStr, int preNum, double currentLeak) throws SQLException {
        //预编译SQL，减少sql执行
        PreparedStatement ptmt = conn.prepareStatement(updateStrSql.toString());
        ptmt.setInt(1, preNum + 1);
        ptmt.setDouble(2, queryLeak(objectStr) + currentLeak);
        //执行
        ptmt.execute();
    }

    private static void add(String objectStr, int num, double currentLeak) throws SQLException {
        //预编译SQL，减少sql执行
        PreparedStatement ptmt = conn.prepareStatement(addStrSql.toString());
        ptmt.setString(1, objectStr);
        ptmt.setInt(2, num);
        ptmt.setDouble(3, currentLeak);
        //执行
        ptmt.execute();
        ptmt.close();
    }

}
