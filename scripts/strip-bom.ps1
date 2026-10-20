## =============================================================================
## strip-bom.ps1
## 作用：清除源码文件的 UTF-8 BOM。
## 背景：部分编辑工具写文件时会带 BOM（EF BB BF），javac 遇到 BOM 会报
##      “非法字符: '\ufeff'”，因此在编译前统一清理。
## 用法：powershell -File scripts/strip-bom.ps1
## =============================================================================
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$extensions = @('.java', '.sql', '.xml', '.yml', '.yaml', '.properties', '.ts', '.vue', '.scss', '.css', '.json', '.md', '.html')
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$count = 0

Get-ChildItem -Path $root -Recurse -File -Include '*' |
    Where-Object { $extensions -contains $_.Extension.ToLower() -and $_.FullName -notmatch '[\\/]node_modules[\\/]' -and $_.FullName -notmatch '[\\/]target[\\/]' -and $_.FullName -notmatch '[\\/]\.tools[\\/]' } |
    ForEach-Object {
        $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            $text = [System.IO.Text.Encoding]::UTF8.GetString($bytes, 3, $bytes.Length - 3)
            [System.IO.File]::WriteAllText($_.FullName, $text, $utf8NoBom)
            $count++
        }
    }

Write-Output "BOM stripped files: $count"
