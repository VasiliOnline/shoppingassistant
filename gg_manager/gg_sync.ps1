# gg_sync.ps1 — сбор Kotlin-файлов, ZIP и /bulk (замена sync_kt.ps1)

function Initialize-GGSync {
    $Global:GG_ProjectFilesPath = Join-Path $Global:GG_ProjectRoot "project_files"
    $Global:GG_MetaPath         = Join-Path $Global:GG_ProjectRoot "meta"
    $Global:GG_ZipPath          = Join-Path $Global:GG_ProjectRoot "project_files.zip"
    $Global:GG_ReadyZipPath     = Join-Path $Global:GG_ProjectRoot "project_files_ready.zip"
    $Global:GG_DateTimeFile     = Join-Path $Global:GG_ProjectFilesPath "date_and_time.txt"
    $Global:GG_SyncLogFile      = Join-Path $Global:GG_MetaPath "sync_log.txt"
    $Global:GG_SyncStateFile    = Join-Path $Global:GG_MetaPath "state.json"

    foreach ($p in @($Global:GG_ProjectFilesPath, $Global:GG_MetaPath)) {
        if (-not (Test-Path $p)) {
            New-Item -ItemType Directory -Path $p | Out-Null
        }
    }

    Write-Host "[GG-SYNC] Initialized (project_files / meta)" -ForegroundColor DarkYellow
}

# ZIP (атомарно) + готовый zip
function Rebuild-ProjectFilesZips {
    param(
        [Parameter(Mandatory = $true)][string]$FolderPath,
        [Parameter(Mandatory = $true)][string]$ZipPath,
        [Parameter(Mandatory = $true)][string]$ReadyZipPath
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue | Out-Null

    $tmpZip  = Join-Path ([System.IO.Path]::GetDirectoryName($ZipPath))      "project_files.tmp.zip"
    $tmpZip2 = Join-Path ([System.IO.Path]::GetDirectoryName($ReadyZipPath)) "project_files_ready.tmp.zip"

    foreach ($t in @($tmpZip, $tmpZip2)) {
        if (Test-Path $t) {
            Remove-Item -LiteralPath $t -Force -ErrorAction SilentlyContinue
        }
    }

    if (Test-Path $FolderPath) {
        [System.IO.Compression.ZipFile]::CreateFromDirectory(
            $FolderPath, $tmpZip,
            [System.IO.Compression.CompressionLevel]::Optimal,
            $false
        )
        [System.IO.Compression.ZipFile]::CreateFromDirectory(
            $FolderPath, $tmpZip2,
            [System.IO.Compression.CompressionLevel]::Optimal,
            $false
        )

        if (Test-Path $ZipPath)      { Remove-Item -LiteralPath $ZipPath      -Force -ErrorAction SilentlyContinue }
        if (Test-Path $ReadyZipPath) { Remove-Item -LiteralPath $ReadyZipPath -Force -ErrorAction SilentlyContinue }

        Move-Item -LiteralPath $tmpZip  -Destination $ZipPath      -Force
        Move-Item -LiteralPath $tmpZip2 -Destination $ReadyZipPath -Force
    } else {
        if (Test-Path $ZipPath)      { Remove-Item -LiteralPath $ZipPath      -Force -ErrorAction SilentlyContinue }
        if (Test-Path $ReadyZipPath) { Remove-Item -LiteralPath $ReadyZipPath -Force -ErrorAction SilentlyContinue }
    }
}

# POST /bulk на FastAPI (JSON с date/files/delete)
function Send-BulkUpdate {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$ApiKey,
        [Parameter(Mandatory = $true)][string]$DateIso,
        [Parameter(Mandatory = $true)][System.Collections.IEnumerable]$Files,
        [Parameter(Mandatory = $true)][System.Collections.IEnumerable]$Deletes
    )

    $fileArr = @($Files)
    $delArr  = @($Deletes)

    if ($fileArr.Count -eq 0 -and $delArr.Count -eq 0) {
        return
    }

    $payload = @{
        date   = $DateIso
        files  = $fileArr
        delete = $delArr
    }

    $json = $payload | ConvertTo-Json -Depth 5 -Compress

    $headers = @{
        "x-api-key" = $ApiKey
    }

    try {
        Write-Host ("[GG-SYNC] BULK → {0} (files={1}, delete={2})" -f $Url, $fileArr.Count, $delArr.Count) -ForegroundColor Cyan

        $resp = Invoke-RestMethod -Uri $Url `
                                  -Method Post `
                                  -Headers $headers `
                                  -ContentType "application/json; charset=utf-8" `
                                  -Body $json

        $respText = if ($resp -is [string]) { $resp } else { ($resp | ConvertTo-Json -Compress) }
        $msg = "{0} BULK OK: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $respText
        Add-Content -Path $Global:GG_SyncLogFile -Value $msg

        Write-Host "[GG-SYNC] BULK OK" -ForegroundColor Magenta
    } catch {
        $ex  = $_.Exception
        $err = "{0} BULK ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $ex.Message
        Write-Host $err -ForegroundColor Red
        Add-Content -Path $Global:GG_SyncLogFile -Value $err

        $respObj = $ex.Response
        if ($respObj -and $respObj.GetResponseStream) {
            try {
                $reader = New-Object System.IO.StreamReader($respObj.GetResponseStream())
                $body   = $reader.ReadToEnd()
                if (-not [string]::IsNullOrWhiteSpace($body)) {
                    Add-Content -Path $Global:GG_SyncLogFile -Value ("{0} BULK ERROR BODY: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $body)
                }
            } catch {}
        }
    }
}

function Run-GGSyncIteration {
    # 1) Загружаем предыдущий state (SafeName -> Path/Hash/Source)
    $prevStateIndex = @{}
    if (Test-Path $Global:GG_SyncStateFile) {
        try {
            $rawState = Get-Content -LiteralPath $Global:GG_SyncStateFile -Raw
            if (-not [string]::IsNullOrWhiteSpace($rawState)) {
                $prevState = $rawState | ConvertFrom-Json
                if ($prevState -is [System.Collections.IEnumerable]) {
                    foreach ($item in $prevState) {
                        if ($item -and $item.SafeName -and $item.Path) {
                            $prevStateIndex[$item.SafeName] = $item
                        }
                    }
                } elseif ($prevState.SafeName -and $prevState.Path) {
                    $prevStateIndex[$prevState.SafeName] = $prevState
                }
            }
        } catch {
            Write-Host "[GG-SYNC] STATE READ ERROR: $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    $changes     = @()
    $bulkFiles   = @()
    $bulkDeletes = @()

    # 2) Собираем список исходных *.kt
    $sourceKts = Get-ChildItem -Path $Global:GG_ProjectRoot -Recurse -File -Filter *.kt |
                 Where-Object {
                     -not (Is-Excluded -FullPath $_.FullName) -and
                     (Is-GGSyncEligibleKt -FullPath $_.FullName)
                 }

    $rootName  = Split-Path $Global:GG_ProjectRoot -Leaf
    $usedNames = @{}

    $currentState = foreach ($f in $sourceKts) {
        $rel = $f.FullName.Substring($Global:GG_ProjectRoot.Length).TrimStart('\', '/')
        $relForServer = ($rel -replace '[\\/]+', '/')
        $relWithRoot  = ($rootName.Trim('\', '/')) + '\' + ($rel -replace '[\\/]+', '\')

        [PSCustomObject]@{
            Path     = $relForServer
            SafeName = Get-PrettySafeName -RelWithRoot $relWithRoot -UsedNames $usedNames
            Hash     = Get-HashForCompare -Path $f.FullName
            Source   = $f.FullName
        }
    }

    # 3) Текущее состояние архива project_files/*.kt
    $archiveState = Get-ChildItem -Path $Global:GG_ProjectFilesPath -File -Filter *.kt -ErrorAction SilentlyContinue | ForEach-Object {
        [PSCustomObject]@{
            SafeName = $_.Name
            Hash     = Get-HashForCompare -Path $_.FullName
            File     = $_.FullName
        }
    }

    $currIndex = @{}
    foreach ($c in $currentState) { $currIndex[$c.SafeName] = $c }

    $archIndex = @{}
    foreach ($a in $archiveState) { $archIndex[$a.SafeName] = $a }

    # 4) Add / Modify
    foreach ($file in $currentState) {
        $destFilePath = Join-Path $Global:GG_ProjectFilesPath $file.SafeName

        if (-not $archIndex.ContainsKey($file.SafeName)) {
            # Новый Kotlin-файл
            $msg = "{0} ADDED: {1} -> {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $file.Path, $file.SafeName
            $changes += $msg

            $body = Read-Utf8Strict -Path $file.Source
            if ($body -match '^\s*//\s*Last\s+synced:') {
                $body = ($body -replace '^\s*//\s*Last\s+synced:.*?(\r?\n|$)', '')
            }
            $final = ("// Last synced: {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) +
                     "`n" +
                     ($body -replace "`r`n?", "`n")
            Write-Utf8NoBom -Path $destFilePath -Text $final

            $bulkFiles += @{
                path = $file.Path
                text = $final
            }

        } elseif ($archIndex[$file.SafeName].Hash -ne $file.Hash) {
            # Модификация
            try {
                $debugDir = Join-Path $Global:GG_MetaPath "diff_debug"
                if (-not (Test-Path $debugDir)) {
                    New-Item -ItemType Directory -Path $debugDir | Out-Null
                }

                $base    = [System.IO.Path]::GetFileNameWithoutExtension($file.SafeName)
                $srcNorm = Get-NormalizedText -Path $file.Source
                $dstNorm = ""
                if (Test-Path $destFilePath) {
                    $dstNorm = Get-NormalizedText -Path $destFilePath
                }

                Write-Utf8NoBom -Path (Join-Path $debugDir "$base.src.txt") -Text $srcNorm
                Write-Utf8NoBom -Path (Join-Path $debugDir "$base.dst.txt") -Text $dstNorm

                if ($null -eq $srcNorm) { $srcLen = 0 } else { $srcLen = $srcNorm.Length }
                if ($null -eq $dstNorm) { $dstLen = 0 } else { $dstLen = $dstNorm.Length }

                $srcHash = Get-HashForCompare -Path $file.Source
                if (Test-Path $destFilePath) {
                    $dstHash = Get-HashForCompare -Path $destFilePath
                } else {
                    $dstHash = ""
                }

                Write-Host ("[GG-SYNC] MISMATCH: {0} srcHash={1} dstHash={2} srcLen={3} dstLen={4}" -f `
                    $file.SafeName, $srcHash, $dstHash, $srcLen, $dstLen) -ForegroundColor Yellow
            } catch {
                Write-Host ("[GG-SYNC] DIFF DEBUG ERROR: " + $_.Exception.Message) -ForegroundColor Red
            }

            $msg = "{0} MODIFIED: {1} -> {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $file.Path, $file.SafeName
            $changes += $msg

            $body = Read-Utf8Strict -Path $file.Source
            if ($body -match '^\s*//\s*Last\s+synced:') {
                $body = ($body -replace '^\s*//\s*Last\s+synced:.*?(\r?\n|$)', '')
            }
            $final = ("// Last synced: {0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) +
                     "`n" +
                     ($body -replace "`r`n?", "`n")
            Write-Utf8NoBom -Path $destFilePath -Text $final

            $bulkFiles += [PSCustomObject]@{
                path = $file.Path
                text = $final
            }
        }
    }

    # 5) Delete
    foreach ($arch in $archiveState) {
        if (-not $currIndex.ContainsKey($arch.SafeName)) {
            $msg = "{0} DELETED: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $arch.SafeName
            $changes += $msg
            Remove-Item -LiteralPath $arch.File -Force -ErrorAction SilentlyContinue

            if ($prevStateIndex.ContainsKey($arch.SafeName)) {
                $pathForDelete = $prevStateIndex[$arch.SafeName].Path
                if ($pathForDelete) {
                    $bulkDeletes += $pathForDelete
                }
            }
        }
    }

    if ($changes.Count -eq 0) {
        return
    }

    # 6) Лог изменений в meta/sync_log.txt
    foreach ($c in $changes) {
        Write-Host "[GG-SYNC] $c" -ForegroundColor Yellow
        Add-Content -Path $Global:GG_SyncLogFile -Value $c
    }

    # 7) date_and_time.txt
    $nowIso = Get-Date -Format "yyyy-MM-ddTHH:mm"
    try {
        Write-Utf8NoBom -Path $Global:GG_DateTimeFile -Text $nowIso
        $dateMsg = "{0} DATE UPDATED (local): {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $nowIso
        Write-Host "[GG-SYNC] $dateMsg" -ForegroundColor Green
        Add-Content -Path $Global:GG_SyncLogFile -Value $dateMsg
    } catch {
        $err = "{0} DATE ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $_.Exception.Message
        Write-Host "[GG-SYNC] $err" -ForegroundColor Red
        Add-Content -Path $Global:GG_SyncLogFile -Value $err
    }

    # 8) ZIP'ы
    try {
        Rebuild-ProjectFilesZips -FolderPath $Global:GG_ProjectFilesPath -ZipPath $Global:GG_ZipPath -ReadyZipPath $Global:GG_ReadyZipPath
        $zipMsg = "{0} ZIP UPDATED: {1} (and {2})" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Global:GG_ZipPath, $Global:GG_ReadyZipPath
        Write-Host "[GG-SYNC] $zipMsg" -ForegroundColor Cyan
        Add-Content -Path $Global:GG_SyncLogFile -Value $zipMsg
    } catch {
        $err = "{0} ZIP ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $_.Exception.Message
        Write-Host "[GG-SYNC] $err" -ForegroundColor Red
        Add-Content -Path $Global:GG_SyncLogFile -Value $err
    }

    # 9) /bulk → FastAPI
    if ($Global:GG_BulkEnabled -and -not [string]::IsNullOrWhiteSpace($Global:GG_BulkUrl)) {
        Send-BulkUpdate -Url $Global:GG_BulkUrl -ApiKey $Global:GG_BulkApiKey -DateIso $nowIso -Files $bulkFiles -Deletes $bulkDeletes
    }

    # 10) Сохраняем state для sync (SafeName/Path/Hash/Source)
    try {
        $currentState | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $Global:GG_SyncStateFile -Encoding UTF8
    } catch {
        $err = "{0} STATE ERROR: {1}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $_.Exception.Message
        Write-Host "[GG-SYNC] $err" -ForegroundColor Red
        Add-Content -Path $Global:GG_SyncLogFile -Value $err
    }
}
