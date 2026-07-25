# Fix Vietnamese text corruption in HTML template files
# This script replaces corrupted text (U+FFFD replacement characters) with correct Vietnamese

$files = @(
    "d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\ticket-management.html",
    "d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\user-home.html",
    "d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\ticket-modal-fragment.html"
)

# Build replacement pairs as array of arrays [find, replace]
$replacements = @()

# We'll use a different strategy: read backup files and compare line by line
# For ticket-management.html, user-home.html - use backups
$backupBase = "d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\backup_codebase_extracted\src\main\resources\templates"

foreach ($filePath in $files) {
    $fileName = [System.IO.Path]::GetFileName($filePath)
    $backupPath = Join-Path $backupBase $fileName
    
    if (-not (Test-Path $filePath)) {
        Write-Output "SKIP (not found): $filePath"
        continue
    }
    
    $content = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
    $hasCorruption = $content.Contains([char]0xFFFD)
    
    if (-not $hasCorruption) {
        Write-Output "CLEAN: $filePath"
        continue
    }
    
    if (Test-Path $backupPath) {
        $backupContent = [System.IO.File]::ReadAllText($backupPath, [System.Text.Encoding]::UTF8)
        $backupHasCorruption = $backupContent.Contains([char]0xFFFD)
        
        if (-not $backupHasCorruption) {
            Write-Output "Using backup for: $fileName"
            # Compare structures - get current lines and backup lines
            $currentLines = $content -split "`n"
            $backupLines = $backupContent -split "`n"
            
            # For lines that have corruption in current but not in backup,
            # try to find matching backup line
            $fixedCount = 0
            for ($i = 0; $i -lt $currentLines.Count; $i++) {
                if ($currentLines[$i].Contains([char]0xFFFD)) {
                    # Try to find matching line in backup by looking at non-corrupted parts
                    $currentTrimmed = $currentLines[$i].Trim()
                    # Extract HTML structure (tags) to match
                    $currentStructure = $currentTrimmed -replace '[^\x00-\x7F]', '?'
                    
                    $bestMatch = $null
                    $bestScore = 0
                    
                    foreach ($backupLine in $backupLines) {
                        $backupTrimmed = $backupLine.Trim()
                        $backupStructure = $backupTrimmed -replace '[^\x00-\x7F]', '?'
                        
                        if ($currentStructure -eq $backupStructure -and $backupTrimmed.Length -gt 5) {
                            # Perfect structural match - use the backup line
                            $bestMatch = $backupLine
                            break
                        }
                    }
                    
                    if ($bestMatch) {
                        $currentLines[$i] = $bestMatch
                        $fixedCount++
                    }
                }
            }
            
            if ($fixedCount -gt 0) {
                $newContent = $currentLines -join "`n"
                $utf8NoBom = New-Object System.Text.UTF8Encoding $false
                [System.IO.File]::WriteAllText($filePath, $newContent, $utf8NoBom)
                Write-Output "FIXED $fixedCount lines in: $fileName"
            }
            
            # Check remaining corruptions
            $remaining = ([System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8) | Select-String -Pattern "`u{FFFD}" -AllMatches).Matches.Count
            Write-Output "Remaining corrupted chars: $remaining"
        } else {
            Write-Output "Backup also corrupted for: $fileName"
        }
    } else {
        Write-Output "No backup for: $fileName"
    }
}
