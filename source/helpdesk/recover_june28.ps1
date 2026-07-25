
# =====================================================
# RECOVER JUNE 28 CODE FROM CONVERSATION TRANSCRIPTS
# =====================================================
# Reads all transcript JSONL files from June 28 conversations
# Extracts write_to_file / replace_file_content calls
# Also captures VIEW_FILE snapshots as fallback
# Outputs to: recovered_june28\

$transcriptPaths = @(
    "C:\Users\LENOVO\.gemini\antigravity-ide\brain\c98c96d2-b02f-44b1-8550-c7b5f19ff053\.system_generated\logs\transcript.jsonl",
    "C:\Users\LENOVO\.gemini\antigravity-ide\brain\c8686d29-6ac6-4cee-aac5-1657e8957596\.system_generated\logs\transcript.jsonl",
    "C:\Users\LENOVO\.gemini\antigravity-ide\brain\38583f59-4332-4d8b-b9ea-b09ce37171c8\.system_generated\logs\transcript.jsonl",
    "C:\Users\LENOVO\.gemini\antigravity-ide\brain\fdf493a1-ffcb-490b-8de7-f53445c1a444\.system_generated\logs\transcript.jsonl"
)

$outDir = "d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\recovered_june28"
$filesContent = @{}  # relPath -> content

function Get-RelPath($targetFile) {
    # Normalize: extract relative path from helpdesk/helpdesk/
    $norm = $targetFile -replace '\\', '/'
    if ($norm -match 'helpdesk/helpdesk/(.+)$') {
        return $matches[1]
    }
    return $null
}

function Apply-Replace($content, $target, $replacement) {
    if ($content -and $target -and $content.Contains($target)) {
        return $content.Replace($target, $replacement)
    }
    return $content
}

function Clean-ViewContent($raw) {
    # Remove line number prefixes like "123: "
    $lines = $raw -split "`n"
    $cleaned = @()
    foreach ($line in $lines) {
        if ($line -match '^\d+: (.*)$') {
            $cleaned += $matches[1]
        } elseif ($line -match '^\d+:$') {
            $cleaned += ''
        } else {
            # not a numbered line, skip (header/footer lines)
        }
    }
    return ($cleaned -join "`n")
}

Write-Host "Starting recovery from June 28 transcripts..." -ForegroundColor Cyan
Write-Host ""

foreach ($tPath in $transcriptPaths) {
    if (-not (Test-Path $tPath)) {
        Write-Host "  [SKIP] Not found: $tPath" -ForegroundColor Yellow
        continue
    }
    
    $convId = Split-Path (Split-Path (Split-Path $tPath)) -Leaf
    Write-Host "Processing conversation: $convId" -ForegroundColor Green
    
    $lineCount = 0
    $writeCount = 0
    $viewCount = 0
    
    Get-Content $tPath -Encoding UTF8 | ForEach-Object {
        $lineCount++
        $jsonLine = $_
        
        try {
            $data = $jsonLine | ConvertFrom-Json -ErrorAction Stop
        } catch {
            return
        }
        
        # ---- Extract VIEW_FILE snapshots ----
        if ($data.type -eq 'VIEW_FILE') {
            $raw = $data.content
            if ($raw -match 'File Path: `file:///([^`]+)`') {
                $filePath = $matches[1] -replace '%20', ' '
                $relPath = Get-RelPath $filePath
                if ($relPath) {
                    # Extract the content between header and "The above content..."
                    if ($raw -match '(?s)Showing lines.*?\n.*?\n(.*?)(?:The above content|$)') {
                        $rawContent = $matches[1]
                        $cleaned = Clean-ViewContent $rawContent
                        if ($cleaned.Length -gt 50) {
                            $filesContent[$relPath] = $cleaned
                            $viewCount++
                        }
                    }
                }
            }
        }
        
        # ---- Extract tool calls ----
        if ($data.tool_calls) {
            foreach ($tc in $data.tool_calls) {
                $name = $tc.name
                $args = $tc.args
                
                if ($name -eq 'write_to_file') {
                    $targetFile = $args.TargetFile
                    if (-not $targetFile) { $targetFile = $args.targetFile }
                    $code = $args.CodeContent
                    if (-not $code) { $code = $args.codeContent }
                    
                    if ($targetFile -and $code) {
                        $relPath = Get-RelPath $targetFile
                        if ($relPath -and $relPath -notmatch '(recover|restore|extract|fix_|FixEncoding|PrintBroken|ReplayEdits|FullRestorer|ComprehensiveRestore|RestoreV2|ReconstructFrom)') {
                            $filesContent[$relPath] = $code
                            $writeCount++
                            Write-Host "    [WRITE] $relPath" -ForegroundColor White
                        }
                    }
                }
                elseif ($name -eq 'replace_file_content') {
                    $targetFile = $args.TargetFile
                    if (-not $targetFile) { $targetFile = $args.targetFile }
                    $targetContent = $args.TargetContent
                    if (-not $targetContent) { $targetContent = $args.targetContent }
                    $replacementContent = $args.ReplacementContent
                    if (-not $replacementContent) { $replacementContent = $args.replacementContent }
                    
                    if ($targetFile) {
                        $relPath = Get-RelPath $targetFile
                        if ($relPath -and $filesContent.ContainsKey($relPath)) {
                            $newContent = Apply-Replace $filesContent[$relPath] $targetContent $replacementContent
                            if ($newContent -ne $filesContent[$relPath]) {
                                $filesContent[$relPath] = $newContent
                                $writeCount++
                                Write-Host "    [REPLACE] $relPath" -ForegroundColor Cyan
                            }
                        }
                    }
                }
                elseif ($name -eq 'multi_replace_file_content') {
                    $targetFile = $args.TargetFile
                    if (-not $targetFile) { $targetFile = $args.targetFile }
                    $chunks = $args.ReplacementChunks
                    if (-not $chunks) { $chunks = $args.replacementChunks }
                    
                    if ($targetFile -and $chunks) {
                        $relPath = Get-RelPath $targetFile
                        if ($relPath -and $filesContent.ContainsKey($relPath)) {
                            foreach ($chunk in $chunks) {
                                $tc2 = $chunk.TargetContent
                                if (-not $tc2) { $tc2 = $chunk.targetContent }
                                $rc2 = $chunk.ReplacementContent
                                if (-not $rc2) { $rc2 = $chunk.replacementContent }
                                $filesContent[$relPath] = Apply-Replace $filesContent[$relPath] $tc2 $rc2
                            }
                            $writeCount++
                            Write-Host "    [MULTI-REPLACE] $relPath" -ForegroundColor Cyan
                        }
                    }
                }
            }
        }
    }
    
    Write-Host "  Lines processed: $lineCount | View snapshots: $viewCount | Write ops: $writeCount" -ForegroundColor Gray
    Write-Host ""
}

Write-Host "Total files recovered: $($filesContent.Count)" -ForegroundColor Yellow
Write-Host ""

# Filter - only keep project files (Java, HTML, properties)
$projectFiles = @{}
foreach ($kv in $filesContent.GetEnumerator()) {
    $path = $kv.Key
    if ($path -match '\.(java|html|properties|xml|css|js)$') {
        # Skip tool/utility files
        if ($path -notmatch '(ComprehensiveRestore|ExtractHistory|FixEncoding2?|FullRestorer|PrintBroken|ReconstructFrom|ReplayEdits|RestoreV2|ExtractHistorySmart)') {
            $projectFiles[$path] = $kv.Value
        }
    }
}

Write-Host "Project files to restore: $($projectFiles.Count)" -ForegroundColor Green
$projectFiles.Keys | Sort-Object | ForEach-Object { Write-Host "  $_" }
Write-Host ""

# Write out
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

foreach ($kv in $projectFiles.GetEnumerator()) {
    $relPath = $kv.Key -replace '/', '\'
    $fullPath = Join-Path $outDir $relPath
    $dir = Split-Path $fullPath
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    
    try {
        [System.IO.File]::WriteAllText($fullPath, $kv.Value, [System.Text.Encoding]::UTF8)
        Write-Host "  Written: $($kv.Key)" -ForegroundColor DarkGreen
    } catch {
        Write-Host "  ERROR writing $($kv.Key): $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "Done! Files written to: $outDir" -ForegroundColor Cyan
