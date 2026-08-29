import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AddQuantumToggle {
    public static void main(String[] args) throws IOException {
        Path sidebarPath = Paths.get("d:/Midomax/MIDOMAX PROJECT/helpdesk/helpdesk/src/main/resources/templates/fragments/sidebar.html");
        String content = new String(Files.readAllBytes(sidebarPath), "UTF-8");
        
        String quantumCss = "\n        .quantum-toggle {\n" +
            "            --uib-size: 28px;\n" +
            "            --uib-color: #1e3a8a;\n" +
            "            --uib-speed: 1.75s;\n" +
            "            position: relative;\n" +
            "            height: var(--uib-size);\n" +
            "            width: var(--uib-size);\n" +
            "            animation: rotate calc(var(--uib-speed) * 4) linear infinite;\n" +
            "            cursor: pointer;\n" +
            "            margin-right: 12px;\n" +
            "            flex-shrink: 0;\n" +
            "            display: inline-block;\n" +
            "        }\n" +
            "\n" +
            "        @keyframes rotate { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }\n" +
            "\n" +
            "        .quantum-toggle .particle { position: absolute; top: 0%; left: 0; display: flex; align-items: center; justify-content: center; height: 100%; width: 100%; }\n" +
            "        .quantum-toggle .particle:nth-child(1) { --uib-delay: 0; transform: rotate(8deg); }\n" +
            "        .quantum-toggle .particle:nth-child(2) { --uib-delay: -0.4; transform: rotate(36deg); }\n" +
            "        .quantum-toggle .particle:nth-child(3) { --uib-delay: -0.9; transform: rotate(72deg); }\n" +
            "        .quantum-toggle .particle:nth-child(4) { --uib-delay: -0.5; transform: rotate(90deg); }\n" +
            "        .quantum-toggle .particle:nth-child(5) { --uib-delay: -0.3; transform: rotate(144deg); }\n" +
            "        .quantum-toggle .particle:nth-child(6) { --uib-delay: -0.2; transform: rotate(180deg); }\n" +
            "        .quantum-toggle .particle:nth-child(7) { --uib-delay: -0.6; transform: rotate(216deg); }\n" +
            "        .quantum-toggle .particle:nth-child(8) { --uib-delay: -0.7; transform: rotate(252deg); }\n" +
            "        .quantum-toggle .particle:nth-child(9) { --uib-delay: -0.1; transform: rotate(300deg); }\n" +
            "        .quantum-toggle .particle:nth-child(10) { --uib-delay: -0.8; transform: rotate(324deg); }\n" +
            "        .quantum-toggle .particle:nth-child(11) { --uib-delay: -1.2; transform: rotate(335deg); }\n" +
            "        .quantum-toggle .particle:nth-child(12) { --uib-delay: -0.5; transform: rotate(290deg); }\n" +
            "        .quantum-toggle .particle:nth-child(13) { --uib-delay: -0.2; transform: rotate(240deg); }\n" +
            "\n" +
            "        .quantum-toggle .particle::before {\n" +
            "            content: ''; position: absolute; height: 17.5%; width: 17.5%; border-radius: 50%;\n" +
            "            background-color: var(--uib-color); flex-shrink: 0; transition: background-color 0.3s ease;\n" +
            "            --uib-d: calc(var(--uib-delay) * var(--uib-speed));\n" +
            "            animation: orbit var(--uib-speed) linear var(--uib-d) infinite;\n" +
            "        }\n" +
            "\n" +
            "        @keyframes orbit {\n" +
            "            0% { transform: translate(calc(var(--uib-size) * 0.5)) scale(0.73684); opacity: 0.65; }\n" +
            "            5% { transform: translate(calc(var(--uib-size) * 0.4)) scale(0.684208); opacity: 0.58; }\n" +
            "            10% { transform: translate(calc(var(--uib-size) * 0.3)) scale(0.631576); opacity: 0.51; }\n" +
            "            15% { transform: translate(calc(var(--uib-size) * 0.2)) scale(0.578944); opacity: 0.44; }\n" +
            "            20% { transform: translate(calc(var(--uib-size) * 0.1)) scale(0.526312); opacity: 0.37; }\n" +
            "            25% { transform: translate(0%) scale(0.47368); opacity: 0.3; }\n" +
            "            30% { transform: translate(calc(var(--uib-size) * -0.1)) scale(0.526312); opacity: 0.37; }\n" +
            "            35% { transform: translate(calc(var(--uib-size) * -0.2)) scale(0.578944); opacity: 0.44; }\n" +
            "            40% { transform: translate(calc(var(--uib-size) * -0.3)) scale(0.631576); opacity: 0.51; }\n" +
            "            45% { transform: translate(calc(var(--uib-size) * -0.4)) scale(0.684208); opacity: 0.58; }\n" +
            "            50% { transform: translate(calc(var(--uib-size) * -0.5)) scale(0.73684); opacity: 0.65; }\n" +
            "            55% { transform: translate(calc(var(--uib-size) * -0.4)) scale(0.789472); opacity: 0.72; }\n" +
            "            60% { transform: translate(calc(var(--uib-size) * -0.3)) scale(0.842104); opacity: 0.79; }\n" +
            "            65% { transform: translate(calc(var(--uib-size) * -0.2)) scale(0.894736); opacity: 0.86; }\n" +
            "            70% { transform: translate(calc(var(--uib-size) * -0.1)) scale(0.947368); opacity: 0.93; }\n" +
            "            75% { transform: translate(0%) scale(1); opacity: 1; }\n" +
            "            80% { transform: translate(calc(var(--uib-size) * 0.1)) scale(0.947368); opacity: 0.93; }\n" +
            "            85% { transform: translate(calc(var(--uib-size) * 0.2)) scale(0.894736); opacity: 0.86; }\n" +
            "            90% { transform: translate(calc(var(--uib-size) * 0.3)) scale(0.842104); opacity: 0.79; }\n" +
            "            95% { transform: translate(calc(var(--uib-size) * 0.4)) scale(0.789472); opacity: 0.72; }\n" +
            "            100% { transform: translate(calc(var(--uib-size) * 0.5)) scale(0.73684); opacity: 0.65; }\n" +
            "        }\n";

        if (!content.contains(".quantum-toggle {")) {
            content = content.replaceFirst("<style>", "<style>" + quantumCss);
            Files.write(sidebarPath, content.getBytes("UTF-8"));
            System.out.println("Added quantum toggle CSS to sidebar.");
        } else {
            System.out.println("Quantum toggle CSS already exists.");
        }
    }
}
