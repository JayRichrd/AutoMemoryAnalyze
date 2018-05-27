package com.tencent.cainjiang;

import java.sql.*;

public class DBDemo {
    //URL指向要访问的数据库名
    public static final String URL = "jdbc:mysql://localhost:3306/auto_memory_analyze_result_db?useUnicode=true&characterEncoding=utf-8";
    //MySQL配置时的用户名
    public static final String USER = "root";
    //MySQL配置时的密码
    public static final String PASSWORD = "jiang12315";
    //驱动程序名
    public static final String DRIVER = "com.mysql.jdbc.Driver";
    private static Connection connection = null;

    public static void main(String[] args) {
        try {
            //1.加载驱动程序
            Class.forName(DRIVER);
            //2. 获得数据库连接
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            if (connection.isClosed()) {
                return;
            }
            System.out.println("Succeeded connecting to the Database!");
            /**
             * 3.操作数据库，实现增删改查。创建statement类对象，用来执行SQL语句！！
             */
            Statement statement = connection.createStatement();
            /**
             * 要执行的SQL语句
             * analyze_result_table是数据库中的表
             */
            String sql = "select * from analyze_result_table WHERE name = '" + "姜瑜" + "'";
            //ResultSet类，用来存放获取的结果集！！
            ResultSet rs = statement.executeQuery(sql);
            System.out.println("-----------------");
            System.out.println("执行结果如下所示:");
            System.out.println("-----------------");
            System.out.println(" 姓名" + "\t" + " 数量");
            System.out.println("-----------------");
            String name;
            int login_password;
            while (rs.next()) {
                //获取stuname这列数据
                name = rs.getString("name");
                //获取stuid这列数据
                login_password = rs.getInt("num");
                //输出结果
                System.out.println(name + "\t" + login_password);
            }
            rs.close();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
