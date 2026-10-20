param([Parameter(Mandatory = $true)][string]$Base)

# DDD layered refactor: move dirs, rewrite package paths, rename files by type name
$ErrorActionPreference = 'Stop'
$map = @{
  "entity"     = "infrastructure\persistence\po"
  "mapper"     = "infrastructure\persistence\mapper"
  "handler"    = "infrastructure\persistence\handler"
  "config"     = "infrastructure\config"
  "security"   = "infrastructure\security"
  "util"       = "infrastructure\util"
  "service"    = "application\service"
  "controller" = "interfaces\controller"
  "dto"        = "interfaces\dto"
}
foreach ($k in $map.Keys) {
  $src = Join-Path $Base $k
  $dst = Join-Path $Base $map[$k]
  if (Test-Path $src) {
    $parent = Split-Path $dst
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
    Move-Item -Path $src -Destination $dst -Force
    Write-Output ("moved " + $k)
  }
  else { Write-Output ("skip(missing) " + $k) }
}

Remove-Item -Path (Join-Path $Base "application\service\BaseService.java") -Force -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $Base "application\service\impl\BaseServiceImpl.java") -Force -ErrorAction SilentlyContinue

$rules = @(
  @("com.example.admin.service.impl", "com.example.admin.application.service.impl"),
  @("com.example.admin.service", "com.example.admin.application.service"),
  @("com.example.admin.entity", "com.example.admin.infrastructure.persistence.po"),
  @("com.example.admin.mapper", "com.example.admin.infrastructure.persistence.mapper"),
  @("com.example.admin.handler", "com.example.admin.infrastructure.persistence.handler"),
  @("com.example.admin.config", "com.example.admin.infrastructure.config"),
  @("com.example.admin.security", "com.example.admin.infrastructure.security"),
  @("com.example.admin.util", "com.example.admin.infrastructure.util"),
  @("com.example.admin.controller", "com.example.admin.interfaces.controller"),
  @("com.example.admin.dto", "com.example.admin.interfaces.dto"),
  @("SysUserService", "UserAppService"),
  @("SysRoleService", "RoleAppService"),
  @("SysMenuService", "MenuAppService"),
  @("SysDeptService", "DeptAppService"),
  @("SysLoginLogService", "LoginLogAppService"),
  @("DashboardService", "DashboardAppService"),
  @("AuthService", "AuthAppService")
)

$files = Get-ChildItem -Path $Base -Recurse -Filter *.java
$changed = 0
foreach ($f in $files) {
  $text = [System.IO.File]::ReadAllText($f.FullName)
  $orig = $text
  foreach ($r in $rules) { $text = $text.Replace($r[0], $r[1]) }
  if ($text -ne $orig) {
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($f.FullName, $text, $utf8)
    $changed++
  }
}
Write-Output ("rewrote " + $changed + " / " + $files.Count + " java files")

foreach ($f in Get-ChildItem -Path $Base -Recurse -Filter *.java) {
  $m = [regex]::Match([System.IO.File]::ReadAllText($f.FullName), '\b(?:public\s+)?(?:final\s+)?(class|interface|enum|record)\s+(\w+)')
  if ($m.Success) {
    $typeName = $m.Groups[2].Value
    if ($f.BaseName -ne $typeName) {
      $newPath = Join-Path $f.DirectoryName ($typeName + ".java")
      if (Test-Path $newPath) { Remove-Item $newPath -Force }
      Move-Item -Path $f.FullName -Destination $newPath -Force
      Write-Output ("renamed " + $f.Name + " -> " + $typeName + ".java")
    }
  }
}
Write-Output "DONE"
