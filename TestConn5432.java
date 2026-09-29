import java.sql.*;
import java.util.Properties;

public class TestConn5432 {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://aws-0-us-west-2.pooler.supabase.com:5432/postgres?sslmode=require&prepareThreshold=0";
        Properties props = new Properties();
        props.setProperty("user", "postgres.zprznayvpeijjoiknird");
        props.setProperty("password", "uade123uade");
        props.setProperty("loginTimeout", "10");
        try (Connection c = DriverManager.getConnection(url, props)) {
            System.out.println("CONEXION_EXITOSA_5432!");
        } catch (Exception e) {
            System.out.println("EXCEPTION_MSG: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("CAUSE: " + e.getCause().getMessage());
            }
        }
    }
}
