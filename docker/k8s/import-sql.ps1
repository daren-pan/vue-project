# 从 Docker Compose MySQL 导入数据到 K8s MySQL
$sqlFiles = @(
    "D:\RuoyiProject\RuoYi-Cloud\tmp_ry_config.sql",
    "D:\RuoyiProject\RuoYi-Cloud\tmp_ry_cloud.sql"
)
$podName = "ruoyi-mysql-54db4f6fc-82dp5"
$ns = "ruoyi"

# 用 .NET Process 启动 kubectl，避免 PowerShell 管道编码问题
function Import-SqlFile($filePath) {
    $fileName = Split-Path $filePath -Leaf
    Write-Host "Importing $fileName to K8s MySQL pod: $podName ..."
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "kubectl"
$psi.Arguments = "exec -n $ns -i $podName -- mysql -uroot -ppassword"
$psi.RedirectStandardInput = $true
$psi.UseShellExecute = $false

$proc = [System.Diagnostics.Process]::Start($psi)

# 用 StreamWriter 以 UTF-8 写入 SQL 内容
$writer = $proc.StandardInput
$content = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
$writer.Write($content)
$writer.Close()

$proc.WaitForExit()

if ($proc.ExitCode -eq 0) {
    Write-Host "  [OK] $fileName imported!"
} else {
    Write-Host "  [FAIL] $fileName failed with exit code $($proc.ExitCode)!"
}
}

# ====== Main ======
foreach ($f in $sqlFiles) {
    if (Test-Path $f) {
        Import-SqlFile $f
    } else {
        Write-Host "[SKIP] File not found: $f"
    }
}

Write-Host "[DONE] All imports completed!"
