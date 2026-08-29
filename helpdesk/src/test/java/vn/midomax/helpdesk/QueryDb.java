import java.sql.*;

public class QueryDb {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/helpdesk?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String user = "root";
        String password = "Root@123";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, inventory_code, category_id, status FROM assets")) {

            while (rs.next()) {
                System.out.println("ID: " + rs.getLong("id") +
                                   ", Code: " + rs.getString("inventory_code") +
                                   ", Cat: " + rs.getLong("category_id") +
                                   ", Status: " + rs.getString("status"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
