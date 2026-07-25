import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class SyncCSS {
    public static void main(String[] args) throws Exception {
        String adminPath = "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/admin-home.html";
        String empPath = "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/employee-management.html";
        
        String adminHtml = new String(Files.readAllBytes(Paths.get(adminPath)), StandardCharsets.UTF_8);
        String empHtml = new String(Files.readAllBytes(Paths.get(empPath)), StandardCharsets.UTF_8);
        
        int styleStart = adminHtml.indexOf("<style>");
        int styleEnd = adminHtml.indexOf("</style>") + 8;
        String styleBlock = adminHtml.substring(styleStart, styleEnd);
        
        int empStyleStart = empHtml.indexOf("<style>");
        int empStyleEnd = empHtml.indexOf("</style>") + 8;
        
        empHtml = empHtml.substring(0, empStyleStart) + styleBlock + empHtml.substring(empStyleEnd);
        
        Files.write(Paths.get(empPath), empHtml.getBytes(StandardCharsets.UTF_8));
        System.out.println("CSS synced to employee-management.html");
    }
}
