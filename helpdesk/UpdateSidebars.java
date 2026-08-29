import java.nio.file.*;
import java.util.regex.*;
import java.io.*;

public class UpdateSidebars {
    public static void main(String[] args) throws Exception {
        String[] files = {
            "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/admin-home.html",
            "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/dashboard.html",
            "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/ticket-management.html",
            "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/employee-management.html"
        };
        
        String menuTemplate = "<!-- Menu Items -->\n" +
"        <div class=\"sidebar-menu\">\n" +
"            <a href=\"/\" title=\"Trang chủ\" th:classappend=\"${#httpServletRequest.requestURI == '/' or #httpServletRequest.requestURI == '/admin-home'} ? 'active' : ''\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-house\"></i></div>\n" +
"                <span>Trang chủ</span>\n" +
"            </a>\n" +
"            <a href=\"/dashboard\" title=\"Dashboard\" th:classappend=\"${#httpServletRequest.requestURI == '/dashboard' or #httpServletRequest.requestURI == '/user-home'} ? 'active' : ''\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-chart-pie\"></i></div>\n" +
"                <span>Dashboard</span>\n" +
"            </a>\n" +
"            <a href=\"/ticket-management\" title=\"Quản lý Ticket\" th:classappend=\"${#httpServletRequest.requestURI == '/ticket-management'} ? 'active' : ''\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-ticket\"></i></div>\n" +
"                <span>Quản lý Ticket</span>\n" +
"            </a>\n" +
"            <a href=\"#\" sec:authorize=\"hasRole('ADMIN')\" title=\"Mạng\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-network-wired\"></i></div>\n" +
"                <span>Mạng</span>\n" +
"            </a>\n" +
"            <a href=\"#\" sec:authorize=\"hasRole('ADMIN')\" title=\"Công Cụ Dụng Cụ\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-boxes-stacked\"></i></div>\n" +
"                <span>Công Cụ Dụng Cụ</span>\n" +
"            </a>\n" +
"            <a href=\"/schedule\" title=\"Lịch Trình\" th:classappend=\"${#httpServletRequest.requestURI == '/schedule'} ? 'active' : ''\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-calendar-check\"></i></div>\n" +
"                <span>Lịch Trình</span>\n" +
"            </a>\n" +
"            <a href=\"/employees\" sec:authorize=\"hasRole('ADMIN')\" title=\"Quản Lý Nhân Sự\" th:classappend=\"${#httpServletRequest.requestURI == '/employees'} ? 'active' : ''\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-user-tie\"></i></div>\n" +
"                <span>Quản Lý Nhân Sự</span>\n" +
"            </a>\n" +
"            <div sec:authorize=\"hasRole('ADMIN')\" style=\"margin: 10px 25px; border-top: 1px solid #e5e7eb;\"></div>\n" +
"            <a href=\"#\" sec:authorize=\"hasRole('ADMIN')\" title=\"Quản Lý User\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-users-gear\"></i></div>\n" +
"                <span>Quản Lý User</span>\n" +
"            </a>\n" +
"            <div sec:authorize=\"hasRole('USER')\" style=\"margin: 10px 25px; border-top: 1px solid #e5e7eb;\"></div>\n" +
"            <a href=\"/profile\" sec:authorize=\"hasRole('USER')\" title=\"Profile\" th:classappend=\"${#httpServletRequest.requestURI == '/profile'} ? 'active' : ''\">\n" +
"                <div class=\"icon-wrapper\"><i class=\"fa-solid fa-user\"></i></div>\n" +
"                <span>Profile</span>\n" +
"            </a>\n" +
"        </div>";

        for (String f : files) {
            Path path = Paths.get(f);
            if (!Files.exists(path)) continue;
            String content = new String(Files.readAllBytes(path), "UTF-8");
            
            // Replace sidebar menu
            content = content.replaceAll("(?s)<!-- Menu Items -->\\s*<div class=\"sidebar-menu\">.*?</div>\\s*<!-- Bottom Account -->", menuTemplate + "\n\n        <!-- Bottom Account -->");
            
            // Fix namespace
            if (!content.contains("xmlns:sec=")) {
                content = content.replace("<html lang=\"vi\" xmlns:th=\"http://www.thymeleaf.org\">", "<html lang=\"vi\" xmlns:th=\"http://www.thymeleaf.org\" xmlns:sec=\"http://www.thymeleaf.org/extras/spring-security\">");
                content = content.replace("<html xmlns:th=\"http://www.thymeleaf.org\">", "<html xmlns:th=\"http://www.thymeleaf.org\" xmlns:sec=\"http://www.thymeleaf.org/extras/spring-security\">");
            }
            
            Files.write(path, content.getBytes("UTF-8"));
            System.out.println("Updated " + f);
        }
    }
}
