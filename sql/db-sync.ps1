param(
    [Parameter(Position=0)]
    [ValidateSet("new", "status", "run", "export")]
    [string]$Action = "status",
    
    [Parameter(Position=1)]
    [string]$Env = "docker",   # docker / k8s
    
    [Parameter(Position=2)]
    [string]$Desc = ""         # 变更说明（new 时必填）
)

$MIGRATIONS_DIR = "D:\RuoyiProject\RuoYi-Cloud\sql\migrations"
$DB_NAME = "ry-cloud"

# 数据库连接
function Get-ConnStr($target) {
    if ($target -eq "docker") {
        return @{Host="localhost"; Port=3307; User="root"; Pass="password"}
    } elseif ($target -eq "k8s") {
        return @{Host="localhost"; Port=3308; User="root"; Pass="password"}
    }
}

function Exec-Sql($target, $sql) {
    $conn = Get-ConnStr $target
    $sql | kubectl exec -n ruoyi -i ruoyi-mysql-54db4f6fc-82dp5 -- mysql -uroot -ppassword $DB_NAME 2>&1
}
# ====== new ======
function New-Migration {
    if (-not $Desc) { Write-Host "请填写变更说明"; return }
    if (-not (Test-Path $MIGRATIONS_DIR)) { New-Item -ItemType Directory -Path $MIGRATIONS_DIR -Force | Out-Null }
    
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $safeName = $Desc -replace '[^a-zA-Z0-9\u4e00-\u9fa5]', '_'
    $filename = "$timestamp`_$safeName.sql"
    $filepath = Join-Path $MIGRATIONS_DIR $filename
    
@"
-- ========================================
-- 时间: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
-- 作者: $env:USERNAME
-- 说明: $Desc
-- ========================================

-- 请在此处编写 SQL（支持多条语句）

-- 示例:
-- ALTER TABLE sys_dept ADD COLUMN ...
-- CREATE TABLE IF NOT EXISTS ...
-- UPDATE sys_menu SET ...

"@ | Out-File -FilePath $filepath -Encoding UTF8
    
    Write-Host "[OK] 已创建迁移文件:" -ForegroundColor Green
    Write-Host "     $filepath" -ForegroundColor Cyan
}

# ====== status ======
function Show-Status {
    if (-not (Test-Path $MIGRATIONS_DIR)) {
        Write-Host "暂无迁移文件" -ForegroundColor Yellow
        return
    }
    
    $files = Get-ChildItem $MIGRATIONS_DIR -Filter "*.sql" | Sort-Object Name
    
    if ($files.Count -eq 0) {
        Write-Host "暂无迁移文件" -ForegroundColor Yellow
        return
    }
    
    Write-Host "`n===== 迁移文件列表 =====`n" -ForegroundColor Cyan
    
    # 检查已执行的文件
    $executed = @()
    try {
        $result = kubectl exec -n ruoyi ruoyi-mysql-54db4f6fc-82dp5 -- mysql -uroot -ppassword $DB_NAME -B -e "SELECT filename FROM _migrations ORDER BY filename" 2>$null
        $executed = $result -split "`n" | Where-Object { $_ -and $_ -ne "filename" }
    } catch {}
    
    foreach ($file in $files) {
        $done = $executed -contains $file.Name
        $status = if ($done) { " [DONE]" } else { " [PENDING]" }
        $color = if ($done) { "Green" } else { "Yellow" }
        Write-Host ("  " + $file.Name + $status) -ForegroundColor $color
    }
    Write-Host ""
}

# ====== run ======
function Run-Migrations {
    if (-not (Test-Path $MIGRATIONS_DIR)) {
        Write-Host "没有迁移文件可执行" -ForegroundColor Yellow
        return
    }
    
    # 确保 _migrations 表存在
    Exec-Sql $Env @"
CREATE TABLE IF NOT EXISTS _migrations (
    filename VARCHAR(255) PRIMARY KEY,
    executed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    checksum VARCHAR(64)
);
"@
    
    $files = Get-ChildItem $MIGRATIONS_DIR -Filter "*.sql" | Sort-Object Name
    $count = 0
    
    foreach ($file in $files) {
        # 检查是否已执行
        $check = Exec-Sql $Env "SELECT COUNT(*) FROM _migrations WHERE filename='$($file.Name)'"
        if ($check -gt 0) { continue }
        
        Write-Host "  执行: $($file.Name) ... " -NoNewline
        
        # 计算校验和
        $md5 = (Get-FileHash $file.FullName -Algorithm MD5).Hash
        
        # 执行 SQL
        $sql = Get-Content $file.FullName -Raw -Encoding UTF8
        $result = Exec-Sql $Env $sql
        
        if ($LASTEXITCODE -eq 0 -or $? -eq $true) {
            # 记录执行记录
            Exec-Sql $Env "INSERT INTO _migrations VALUES ('$($file.Name)', NOW(), '$md5')"
            Write-Host "OK" -ForegroundColor Green
            $count++
        } else {
            Write-Host "FAILED" -ForegroundColor Red
            Write-Host $result -ForegroundColor Red
        }
    }
    
    if ($count -eq 0) {
        Write-Host "没有新迁移需要执行" -ForegroundColor Cyan
    } else {
        Write-Host "[OK] 已执行 $count 个迁移" -ForegroundColor Green
    }
}

# ====== export（从 Docker 导出当前表结构到迁移文件）=====
function Export-Schema {
    if (-not (Test-Path $MIGRATIONS_DIR)) { New-Item -ItemType Directory -Path $MIGRATIONS_DIR -Force | Out-Null }
    
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $filepath = Join-Path $MIGRATIONS_DIR "$timestamp`_export_schema.sql"
    
    docker exec ruoyi-mysql mysqldump -uroot -ppassword --no-data --skip-comments $DB_NAME 2>$null | 
        Select-String -Pattern "^CREATE TABLE" -SimpleMatch | 
        Out-File -FilePath $filepath -Encoding UTF8
    
    Write-Host "[OK] 表结构已导出: $filepath" -ForegroundColor Green
}

# ====== Main ======
if (-not (Test-Path $MIGRATIONS_DIR)) {
    New-Item -ItemType Directory -Path $MIGRATIONS_DIR -Force | Out-Null
}

switch ($Action) {
    "new"    { New-Migration }
    "status" { Show-Status }
    "run"    { Run-Migrations }
    "export" { Export-Schema }
}
