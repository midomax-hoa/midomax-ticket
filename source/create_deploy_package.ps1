param(
    [string]$MysqlUser = "root",
    [Parameter(Mandatory = $true)][string]$MysqlPassword
)

$timestamp = Get-Date -Format "yyyyMMdd_HHmm"
$backupName = "MIDOMAX_HELPDESK_DEPLOY_$timestamp"
$rootDir    = "D:\Midomax\MIDOMAX PROJECT\helpdesk"
$stagingDir = "$rootDir\$backupName"
$zipFile    = "$rootDir\${backupName}.zip"
$mysqlDump  = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
$dbDumpName = "helpdesk_db_$timestamp.sql"
$jarSource  = "$rootDir\helpdesk\target\helpdesk-0.0.1-SNAPSHOT.jar"

# Thu muc va file can LOAI TRU (backup cu, .git, target, deploy cu)
$excludeNames = @(
    "backup_20260714_124607",
    "backup_20260716_114612",
    "backup_20260716_114612.zip",
    "backup_20260717_085827",
    "backup_20260717_085827.zip",
    "backup_20260718_1352.zip",
    "backup_20260720_1540.zip",
    "backup_20260720_1949",
    "backup_20260720_1949.zip",
    "backup_20260721_1031",
    "backup_20260721_1031.zip",
    "backup_20260725_0922",
    "backup_20260725_0922.zip",
    "backup_truoc_khi_restore_20260720_0907.zip",
    "backup_truoc_khi_xoa_ticket_20260717.sql",
    "backup_db_20260720_1540.sql",
    "backup_db_20260720_1949.sql",
    "backup_db_20260721_1031.sql",
    "backup_db_20260725_0922.sql",
    ".git",
    ".claude",
    "MIDOMAX_HELPDESK_DEPLOY_20260725_0950",
    "MIDOMAX_HELPDESK_DEPLOY_20260725_0950.zip"
)

Write-Host "============================================"
Write-Host " MIDOMAX HELPDESK - FULL LINUX DEPLOY PKG  "
Write-Host "============================================"
Write-Host "Timestamp : $timestamp"
Write-Host "ZIP Output: $zipFile"
Write-Host ""

# --- 1. Tao thu muc staging ---
if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }
New-Item -ItemType Directory -Path "$stagingDir\source"       -Force | Out-Null
New-Item -ItemType Directory -Path "$stagingDir\jar_deploy"   -Force | Out-Null
New-Item -ItemType Directory -Path "$stagingDir\database"     -Force | Out-Null
New-Item -ItemType Directory -Path "$stagingDir\deploy_guide" -Force | Out-Null

# --- 2. Copy source code (tru backup) ---
Write-Host "[1/5] Copying source code (excluding backups)..."
$allItems = Get-ChildItem -Path $rootDir -Force
foreach ($item in $allItems) {
    if ($excludeNames -contains $item.Name) {
        Write-Host "   SKIP: $($item.Name)"
        continue
    }
    if ($item.Name -like "MIDOMAX_HELPDESK_DEPLOY_*") { continue }
    if ($item.PSIsContainer) {
        Copy-Item -Path $item.FullName -Destination "$stagingDir\source\$($item.Name)" -Recurse -Force
    } else {
        Copy-Item -Path $item.FullName -Destination "$stagingDir\source\$($item.Name)" -Force
    }
}
# Xoa target trong helpdesk project
$innerTarget = "$stagingDir\source\helpdesk\target"
if (Test-Path $innerTarget) { Remove-Item $innerTarget -Recurse -Force }
Write-Host "   Source code copied OK"

# --- 3. Copy JAR (neu da build xong) ---
Write-Host "[2/5] Copying JAR file..."
if (Test-Path $jarSource) {
    Copy-Item -Path $jarSource -Destination "$stagingDir\jar_deploy\helpdesk.jar" -Force
    $jarSize = [math]::Round((Get-Item $jarSource).Length / 1MB, 2)
    Write-Host "   JAR copied: helpdesk.jar ($jarSize MB)"
} else {
    Write-Host "   WARNING: JAR not found at $jarSource"
    Write-Host "   (Build may not be complete yet)"
}

# --- 4. Dump database ---
Write-Host "[3/5] Exporting database (UTF-8)..."
$dbDumpPath = "$stagingDir\database\$dbDumpName"
& $mysqlDump -u $MysqlUser "-p$MysqlPassword" --routines --triggers --events --default-character-set=utf8mb4 helpdesk --result-file=$dbDumpPath 2>$null
if (Test-Path $dbDumpPath) {
    $dbSize = [math]::Round((Get-Item $dbDumpPath).Length / 1KB, 0)
    Write-Host "   DB exported: $dbDumpName (${dbSize}KB)"
} else {
    Write-Host "   ERROR: Database export failed!"
}

# --- 5. Tao scripts Linux ---
Write-Host "[4/5] Creating Linux deploy scripts..."

# Script khoi dong Linux
$startScript = @'
#!/bin/bash
# =============================================
# MIDOMAX HELPDESK - Linux Startup Script
# =============================================
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$APP_DIR/helpdesk.jar"
LOG="$APP_DIR/app.log"
PID_FILE="$APP_DIR/app.pid"

echo "=== Starting Midomax Helpdesk ==="

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Already running with PID $PID"
        exit 1
    fi
fi

nohup java -jar "$JAR" \
    --spring.config.additional-location="$APP_DIR/application.properties" \
    >> "$LOG" 2>&1 &

echo $! > "$PID_FILE"
echo "Started with PID $(cat "$PID_FILE")"
echo "Logs: $LOG"
'@

$stopScript = @'
#!/bin/bash
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$APP_DIR/app.pid"
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    kill "$PID" && echo "Stopped PID $PID" && rm -f "$PID_FILE"
else
    echo "No PID file found"
fi
'@

$startScript  | Out-File -FilePath "$stagingDir\jar_deploy\start.sh"  -Encoding UTF8 -NoNewline
$stopScript   | Out-File -FilePath "$stagingDir\jar_deploy\stop.sh"   -Encoding UTF8 -NoNewline
Write-Host "   Linux scripts created: start.sh, stop.sh"

# --- 6. Huong dan deploy ---
$guide = @"
============================================
 HUONG DAN DEPLOY MIDOMAX HELPDESK
 Linux (Ubuntu/CentOS/Debian)
 Ngay tao: $(Get-Date -Format "dd/MM/yyyy HH:mm")
============================================

== CAU TRUC GOI DEPLOY ==
  source/               -> Toan bo source code Spring Boot (de build)
  jar_deploy/           -> File JAR da build san + scripts chay Linux
    helpdesk.jar        -> Ung dung chinh (chay ngay, khong can build)
    start.sh            -> Script khoi dong (Linux)
    stop.sh             -> Script dung
    application.properties -> Cau hinh (SUA truoc khi chay)
  database/             -> SQL dump MySQL
    $dbDumpName
  deploy_guide/         -> Tai lieu nay

============================================
CACH 1: CHAY NHANH BANG JAR (KHUYEN DUNG)
============================================

Buoc 1 - Cai dat Java 17:
  Ubuntu/Debian:
    sudo apt update
    sudo apt install -y openjdk-17-jre-headless
  CentOS/RHEL:
    sudo yum install -y java-17-openjdk

Buoc 2 - Cai MySQL 8:
  Ubuntu:
    sudo apt install -y mysql-server
    sudo systemctl start mysql
    sudo mysql_secure_installation

Buoc 3 - Import database:
  sudo mysql -u root -p -e "CREATE DATABASE helpdesk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  sudo mysql -u root -p --default-character-set=utf8mb4 helpdesk < database/$dbDumpName

Buoc 4 - Cau hinh ung dung:
  Chinh sua file: jar_deploy/application.properties
  Thay doi:
    spring.datasource.password=<MAT_KHAU_MYSQL>
    server.port=8080

Buoc 5 - Phan quyen va chay:
  chmod +x jar_deploy/start.sh jar_deploy/stop.sh
  ./jar_deploy/start.sh

  Kiem tra log:
    tail -f jar_deploy/app.log

  Dung:
    ./jar_deploy/stop.sh

Buoc 6 - Truy cap:
  http://[IP_MAY_CHU]:8080

============================================
CACH 2: BUILD TU SOURCE
============================================
  Cai them Maven 3.8+ va JDK 17+
  cd source/helpdesk
  mvn clean package -DskipTests
  java -jar target/helpdesk-0.0.1-SNAPSHOT.jar

============================================
MO PORT TUONG LUA (neu can)
============================================
  Ubuntu: sudo ufw allow 8080
  CentOS: sudo firewall-cmd --add-port=8080/tcp --permanent && firewall-cmd --reload

============================================
CHAY NHU SERVICE (systemd)
============================================
  Tao file /etc/systemd/system/helpdesk.service:
  
    [Unit]
    Description=Midomax Helpdesk
    After=network.target mysql.service

    [Service]
    User=www-data
    WorkingDirectory=/opt/helpdesk
    ExecStart=/usr/bin/java -jar /opt/helpdesk/helpdesk.jar
    SuccessExitStatus=143
    Restart=on-failure

    [Install]
    WantedBy=multi-user.target

  Kich hoat:
    sudo systemctl daemon-reload
    sudo systemctl enable helpdesk
    sudo systemctl start helpdesk

Ho tro: Midomax IT Team
"@
$guide | Out-File -FilePath "$stagingDir\deploy_guide\HUONG_DAN_DEPLOY_LINUX.txt" -Encoding UTF8

# Copy application.properties mau vao jar_deploy
$appProps = "$rootDir\helpdesk\src\main\resources\application.properties"
if (Test-Path $appProps) {
    Copy-Item $appProps "$stagingDir\jar_deploy\application.properties" -Force
    Write-Host "   Copied application.properties"
}

# --- 7. Nen ZIP ---
Write-Host "[5/5] Compressing to ZIP..."
if (Test-Path $zipFile) { Remove-Item $zipFile -Force }
Compress-Archive -Path "$stagingDir\*" -DestinationPath $zipFile -Force

$zipSizeMB = [math]::Round((Get-Item $zipFile).Length / 1048576, 2)

Write-Host ""
Write-Host "============================================"
Write-Host " DONE! Full Linux deploy package ready!"
Write-Host " File: ${backupName}.zip"
Write-Host " Size: $zipSizeMB MB"
Write-Host " Path: $zipFile"
Write-Host "============================================"
