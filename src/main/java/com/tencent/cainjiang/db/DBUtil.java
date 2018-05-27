package com.tencent.cainjiang.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author cainjiang
 * @date 2018/5/27
 */
public class DBUtil {
    //URL指向要访问的数据库名
    public static final String URL = "jdbc:mysql://localhost:3306/auto_memory_analyze_result_db?useUnicode=true&characterEncoding=utf-8";
    //MySQL配置时的用户名
    public static final String USER = "root";
    //MySQL配置时的密码
    public static final String PASSWORD = "jiang12315";
    //驱动程序名
    public static final String DRIVER = "com.mysql.jdbc.Driver";
    private static Connection connection = null;

    static {
        try {
            //1.加载驱动程序
            Class.forName(DRIVER);
            //2. 获得数据库连接
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() {
        return connection;
    }
}
