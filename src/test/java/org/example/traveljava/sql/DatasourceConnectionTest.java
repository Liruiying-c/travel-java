package org.example.traveljava.sql;

import java.sql.Connection;
import java.sql.DriverManager;

public class DatasourceConnectionTest {
    public static void main(String[] args) {
        // 和你application.yml里面的数据库配置保持一致
        String url = "jdbc:mysql://localhost:3306/travel_db?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&useSSL=false";
        String username = "root";
        String password = "MySQL";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            System.out.println("✅ JDBC数据库连接成功");
            System.out.println("连接URL:" + conn.getMetaData().getURL());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ 数据库连接失败");
        }
    }
}