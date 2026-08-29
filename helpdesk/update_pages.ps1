$files = @("schedule.html", "user-home.html", "ticket-management.html", "profile.html", "employee-management.html", "admin-home.html")
$dir = "d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates"

$css = @"
        /* QUANTUM TOGGLE BUTTON */
        .quantum-toggle {
            --uib-size: 28px;
            --uib-color: #1e3a8a;
            --uib-speed: 1.75s;
            position: relative;
            height: var(--uib-size);
            width: var(--uib-size);
            animation: rotate calc(var(--uib-speed) * 4) linear infinite;
            cursor: pointer;
            margin-right: 12px;
            flex-shrink: 0;
            display: inline-block;
        }

        @keyframes rotate { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

        .quantum-toggle .particle { position: absolute; top: 0%; left: 0; display: flex; align-items: center; justify-content: center; height: 100%; width: 100%; }
        .quantum-toggle .particle:nth-child(1) { --uib-delay: 0; transform: rotate(8deg); }
        .quantum-toggle .particle:nth-child(2) { --uib-delay: -0.4; transform: rotate(36deg); }
        .quantum-toggle .particle:nth-child(3) { --uib-delay: -0.9; transform: rotate(72deg); }
        .quantum-toggle .particle:nth-child(4) { --uib-delay: -0.5; transform: rotate(90deg); }
        .quantum-toggle .particle:nth-child(5) { --uib-delay: -0.3; transform: rotate(144deg); }
        .quantum-toggle .particle:nth-child(6) { --uib-delay: -0.2; transform: rotate(180deg); }
        .quantum-toggle .particle:nth-child(7) { --uib-delay: -0.6; transform: rotate(216deg); }
        .quantum-toggle .particle:nth-child(8) { --uib-delay: -0.7; transform: rotate(252deg); }
        .quantum-toggle .particle:nth-child(9) { --uib-delay: -0.1; transform: rotate(300deg); }
        .quantum-toggle .particle:nth-child(10) { --uib-delay: -0.8; transform: rotate(324deg); }
        .quantum-toggle .particle:nth-child(11) { --uib-delay: -1.2; transform: rotate(335deg); }
        .quantum-toggle .particle:nth-child(12) { --uib-delay: -0.5; transform: rotate(290deg); }
        .quantum-toggle .particle:nth-child(13) { --uib-delay: -0.2; transform: rotate(240deg); }

        .quantum-toggle .particle::before {
            content: ''; position: absolute; height: 17.5%; width: 17.5%; border-radius: 50%;
            background-color: var(--uib-color); flex-shrink: 0; transition: background-color 0.3s ease;
            --uib-d: calc(var(--uib-delay) * var(--uib-speed));
            animation: orbit var(--uib-speed) linear var(--uib-d) infinite;
        }

        @keyframes orbit {
            0% { transform: translate(calc(var(--uib-size) * 0.5)) scale(0.73684); opacity: 0.65; }
            5% { transform: translate(calc(var(--uib-size) * 0.4)) scale(0.684208); opacity: 0.58; }
            10% { transform: translate(calc(var(--uib-size) * 0.3)) scale(0.631576); opacity: 0.51; }
            15% { transform: translate(calc(var(--uib-size) * 0.2)) scale(0.578944); opacity: 0.44; }
            20% { transform: translate(calc(var(--uib-size) * 0.1)) scale(0.526312); opacity: 0.37; }
            25% { transform: translate(0%) scale(0.47368); opacity: 0.3; }
            30% { transform: translate(calc(var(--uib-size) * -0.1)) scale(0.526312); opacity: 0.37; }
            35% { transform: translate(calc(var(--uib-size) * -0.2)) scale(0.578944); opacity: 0.44; }
            40% { transform: translate(calc(var(--uib-size) * -0.3)) scale(0.631576); opacity: 0.51; }
            45% { transform: translate(calc(var(--uib-size) * -0.4)) scale(0.684208); opacity: 0.58; }
            50% { transform: translate(calc(var(--uib-size) * -0.5)) scale(0.73684); opacity: 0.65; }
            55% { transform: translate(calc(var(--uib-size) * -0.4)) scale(0.789472); opacity: 0.72; }
            60% { transform: translate(calc(var(--uib-size) * -0.3)) scale(0.842104); opacity: 0.79; }
            65% { transform: translate(calc(var(--uib-size) * -0.2)) scale(0.894736); opacity: 0.86; }
            70% { transform: translate(calc(var(--uib-size) * -0.1)) scale(0.947368); opacity: 0.93; }
            75% { transform: translate(0%) scale(1); opacity: 1; }
            80% { transform: translate(calc(var(--uib-size) * 0.1)) scale(0.947368); opacity: 0.93; }
            85% { transform: translate(calc(var(--uib-size) * 0.2)) scale(0.894736); opacity: 0.86; }
            90% { transform: translate(calc(var(--uib-size) * 0.3)) scale(0.842104); opacity: 0.79; }
            95% { transform: translate(calc(var(--uib-size) * 0.4)) scale(0.789472); opacity: 0.72; }
            100% { transform: translate(calc(var(--uib-size) * 0.5)) scale(0.73684); opacity: 0.65; }
        }
    </style>
"@

$btn = @"
            <div class="quantum-toggle" id="toggle-btn">
                <div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div>
            </div>
"@

$mbtn = @"
                <div class="quantum-toggle" id="mobile-toggle-btn" style="display: none;">
                    <div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div><div class="particle"></div>
                </div>
"@

foreach ($f in $files) {
    $p = Join-Path $dir $f
    if (Test-Path $p) {
        $c = Get-Content -Raw $p -Encoding UTF8
        if ($c -notmatch "QUANTUM TOGGLE BUTTON") {
            $c = $c.Replace("    </style>", $css)
        }
        $c = [regex]::Replace($c, '<i[^>]*id="toggle-btn"[^>]*></i>', $btn)
        $c = [regex]::Replace($c, '<i[^>]*id="mobile-toggle-btn"[^>]*></i>', $mbtn)
        $c = [regex]::Replace($c, '#mobile-toggle-btn\s*\{\s*display:\s*block\s*!important;\s*\}', '#mobile-toggle-btn { display: inline-block !important; }')
        
        Set-Content -Path $p -Value $c -Encoding UTF8
        Write-Host "Updated $f"
    }
}
