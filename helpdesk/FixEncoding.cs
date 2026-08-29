using System;
using System.IO;
using System.Text;

class Program {
    static void Main(string[] args) {
        string[] files = {
            @"d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\admin-home.html",
            @"d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\dashboard.html",
            @"d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\ticket-management.html",
            @"d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\employee-management.html"
        };
        
        Encoding win1252 = Encoding.GetEncoding(1252);
        Encoding utf8 = new UTF8Encoding(false); // No BOM
        
        foreach(string f in files) {
            try {
                // Read the file bytes
                byte[] fileBytes = File.ReadAllBytes(f);
                // The file currently has UTF-8 bytes that represent the wrong characters.
                // We decode them as UTF-8 string to get the characters.
                string corruptedText = Encoding.UTF8.GetString(fileBytes);
                
                // Those characters were actually supposed to be interpreted as 1252 bytes.
                // Convert the characters back to 1252 bytes.
                byte[] originalBytes = win1252.GetBytes(corruptedText);
                
                // Now interpret those original bytes as UTF-8 text.
                string recoveredText = Encoding.UTF8.GetString(originalBytes);
                
                // Write the recovered text back as UTF-8 (without BOM)
                File.WriteAllText(f + ".recovered", recoveredText, utf8);
                Console.WriteLine("Recovered " + f);
            } catch (Exception e) {
                Console.WriteLine("Error processing " + f + ": " + e.Message);
            }
        }
    }
}
