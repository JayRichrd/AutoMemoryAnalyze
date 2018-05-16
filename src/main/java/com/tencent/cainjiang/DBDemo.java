package com.tencent.cainjiang;

import java.sql.*;

public class DBDemo {
    public static void main(String[] args) {
        Connection connection;
        //驱动程序名
        String driver = "com.mysql.jdbc.Driver";
        //URL指向要访问的数据库名login
        String url = "jdbc:mysql://localhost:3306/demo_db";
        //MySQL配置时的用户名
        String user = "jiangyu";
        //MySQL配置时的密码
        String password = "jiangyu12315";
        //加载驱动程序
        //1.getConnection()方法，连接MySQL数据库！！
        try {
            Class.forName(driver);
            connection = DriverManager.getConnection(url, user, password);
            if (!connection.isClosed())
                System.out.println("Succeeded connecting to the Database!");
            //2.创建statement类对象，用来执行SQL语句！！
            Statement statement = connection.createStatement();
            //要执行的SQL语句
            String sql = "select * from demo_table";    //从建立的login数据库的login——message表单读取数据
            //3.ResultSet类，用来存放获取的结果集！！
            ResultSet rs = statement.executeQuery(sql);
            System.out.println("-----------------");
            System.out.println("执行结果如下所示:");
            System.out.println("-----------------");
            System.out.println(" 姓名" + "\t" + " 密码");
            System.out.println("-----------------");
            int name;
            String login_password;
            while (rs.next()) {
                //获取stuname这列数据
                name = rs.getInt("nums");
                //获取stuid这列数据
                login_password = rs.getString("names");
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
