# gg_common.ps1 — общие утилиты для менеджера проекта

# Если по какой-то причине GG_ProjectRoot ещё не задан — возьмём текущую папку
if (-not $Global:GG_ProjectRoot) {
    $Global:GG_ProjectRoot = (Get-Location).Path
}

if (-not $Global:GG_SyncModuleNames) {
    $Global:GG_SyncModuleNames = @("app", "core", "feature", "domain", "server", "rank")
}

# --- UTF-8 helpers ---
function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
    )
    $enc = New-Object System.Text.UTF8Encoding($false, $true)
    [System.IO.File]::WriteAllText($Path, $Text, $enc)
}

function Read-Utf8Strict {
    param([Parameter(Mandatory = $true)][string]$Path)
    $enc = New-Object System.Text.UTF8Encoding($false, $true)
    return [System.IO.File]::ReadAllText($Path, $enc)
}

# --- Нормализация текста (с логикой из старого sync_kt.ps1) ---
function Get-NormalizedText {
    param([Parameter(Mandatory = $true)][string]$Path)

    $text = Read-Utf8Strict -Path $Path

    # Удаляем BOM (если есть)
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) {
        $text = $text.Substring(1)
    }

    # CRLF/CR -> LF
    $text = $text -replace "`r`n?", "`n"

    # Убираем все подряд стартовые строки // Last synced: ...
    $lines = $text -split "`n"
    while ($lines.Count -gt 0 -and ($lines[0] -match '^\s*//\s*Last\s+synced:')) {
        if ($lines.Count -gt 1) {
            $lines = $lines[1..($lines.Count - 1)]
        } else {
            $lines = @()
        }
    }

    # Unicode NFC
    $text = ($lines -join "`n").Normalize([System.Text.NormalizationForm]::FormC)

    # NBSP -> обычный пробел
    $text = $text -replace [char]0x00A0, ' '

    # Trim хвостов на каждой строке
    $lines = $text -split "`n"
    $lines = $lines | ForEach-Object { $_.TrimEnd(" ", "`t", [char]0x00A0) }

    # Убираем пустые строки в конце файла
    while ($lines.Count -gt 0 -and [string]::IsNullOrWhiteSpace($lines[-1])) {
        if ($lines.Count -gt 1) {
            $lines = $lines[0..($lines.Count - 2)]
        } else {
            $lines = @()
        }
    }

    return ($lines -join "`n")
}

function Get-HashForCompare {
    param([Parameter(Mandatory = $true)][string]$Path)
    $norm  = Get-NormalizedText -Path $Path
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($norm)
    $sha   = [System.Security.Cryptography.SHA256]::Create()
    ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
}

# --- Pretty Safe Name (для project_files) ---
function Get-PathKey {
    param([Parameter(Mandatory = $true)][string]$Text)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text.ToLowerInvariant())
    $sha1  = [System.Security.Cryptography.SHA1]::Create()
    (($sha1.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "").Substring(0, 6)
}

function Build-PrettyNameCore {
    param([Parameter(Mandatory = $true)][string]$RelWithRoot)

    $parts = $RelWithRoot -split '[\\/]'
    $file  = $parts[-1]
    $dirs  = @()
    if ($parts.Length -gt 2) {
        $dirs = $parts[1..($parts.Length - 2)]
    }

    $base = [System.IO.Path]::GetFileNameWithoutExtension($file)
    $ext  = [System.IO.Path]::GetExtension($file)

    $tail = @()
    if     ($dirs.Count -ge 2) { $tail = $dirs[($dirs.Count - 2)..($dirs.Count - 1)] }
    elseif ($dirs.Count -eq 1) { $tail = @($dirs[-1]) }

    $dirTokens = $tail | ForEach-Object {
        $t = $_.ToLowerInvariant()
        $t = ($t -replace '[^\p{L}\p{Nd}\s-]', '') -replace '\s+', '_' -replace '-', '_'
        if ([string]::IsNullOrWhiteSpace($t)) { '_' } else { $t }
    }

    $dirPart = ($dirTokens -join "_")
    if (-not [string]::IsNullOrWhiteSpace($dirPart)) {
        $name = "${dirPart}_${base}${ext}"
    } else {
        $name = "${base}${ext}"
    }

    # allow letters/digits/_/.- ; others -> _
    return ($name -replace '[^\p{L}\p{Nd}_\.\-]', '_')
}

function Get-PrettySafeName {
    param(
        [Parameter(Mandatory = $true)][string]$RelWithRoot,
        [Parameter(Mandatory = $true)][hashtable]$UsedNames
    )

    $core = Build-PrettyNameCore -RelWithRoot $RelWithRoot

    if (-not $UsedNames.ContainsKey($core)) {
        $UsedNames[$core] = $RelWithRoot
        return $core
    }
    if ($UsedNames[$core] -eq $RelWithRoot) {
        return $core
    }

    # collision -> append short key
    $key  = Get-PathKey -Text $RelWithRoot
    $base = [System.IO.Path]::GetFileNameWithoutExtension($core)
    $ext  = [System.IO.Path]::GetExtension($core)
    $alt  = "${base}__${key}${ext}"

    if ($UsedNames.ContainsKey($alt) -and $UsedNames[$alt] -ne $RelWithRoot) {
        $key2 = Get-PathKey -Text ($RelWithRoot + '|2')
        $alt  = "${base}__${key}_${key2}${ext}"
    }

    $UsedNames[$alt] = $RelWithRoot
    return $alt
}

# --- Исключения путей ---
function Is-Excluded {
    param([Parameter(Mandatory = $true)][string]$FullPath)
    $p = $FullPath.ToLowerInvariant()
    return (
        $p -like "*\project_files\*" -or
        $p -like "*\meta\*"          -or
        $p -like "*\.gg_meta\*"      -or
        $p -like "*\build\*"         -or
        $p -like "*\.gradle\*"       -or
        $p -like "*\.gradle-local\*" -or
        $p -like "*\.android-local\*" -or
        $p -like "*\.kotlin\*"       -or
        $p -like "*\.idea\*"         -or
        $p -like "*\.git\*"          -or
        $p -like "*\out\*"           -or
        $p -like "*\kapt\*"          -or
        $p -like "*\ksp\*"
    )
}

# --- Helper: относительный путь с '/' ---
function Get-RelPathNormalized {
    param([Parameter(Mandatory = $true)][string]$FullPath)

    $root = $Global:GG_ProjectRoot
    $rel  = $FullPath.Substring($root.Length).TrimStart('\', '/')
    $rel  = $rel -replace '[\\/]+', '/'
    return $rel
}

# Только реальные Kotlin-исходники модулей (без test/androidTest и т.д.)
function Is-GGSyncEligibleKt {
    param([Parameter(Mandatory = $true)][string]$FullPath)

    $rel  = Get-RelPathNormalized -FullPath $FullPath
    $mods = ($Global:GG_SyncModuleNames -join "|")
    $rx   = "^(?:$mods)/src/(?!test/|androidTest/|jvmTest/|commonTest/|integrationTest/).+\.kt$"
    return ($rel -match $rx)
}
