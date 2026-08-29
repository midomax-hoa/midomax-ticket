import java.nio.file.*;
import java.nio.charset.*;
import java.io.*;

public class FixEncoding {
    public static void main(String[] args) throws Exception {
        String f = "d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/dashboard.html";
        byte[] bytes = Files.readAllBytes(Paths.get(f));
        String text = new String(bytes, StandardCharsets.UTF_8);
        
        System.out.println("Trying Windows-1252:");
        byte[] b1 = text.getBytes("Windows-1252");
        String s1 = new String(b1, StandardCharsets.UTF_8);
        System.out.println(s1.substring(19200, 19500));
        
        System.out.println("\nTrying Windows-1258:");
        byte[] b2 = text.getBytes("Windows-1258");
        String s2 = new String(b2, StandardCharsets.UTF_8);
        System.out.println(s2.substring(19200, 19500));
    }
}
