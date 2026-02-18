param(
    # Если не указано ничего → запускаем менеджер (watcher + sync)
    [Parameter(Position = 0)]
    [string]$Command,

    [Parameter(Position = 1)]
    [string]$Arg1,

    [Parameter(Position = 2)]
    [string]$Arg2,

    [Parameter(ValueFromRemainingArguments = $true)]
    $Rest
)

try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# Путь к корню проекта = где лежит gg.ps1
$ProjectRoot = Split-Path $MyInvocation.MyCommand.Path -Parent
$ManagerDir  = Join-Path $ProjectRoot "gg_manager"
$ManagerExe  = Join-Path $ManagerDir "gg_manager.ps1"
$CtlExe      = Join-Path $ManagerDir "ggctl.ps1"

if (-not $Command) {
    # Без аргументов: просто запускаем менеджер
    & $ManagerExe @Rest
    exit $LASTEXITCODE
}

switch ($Command.ToLowerInvariant()) {
    "manager" {
        & $ManagerExe @Rest
        break
    }
    "run" {
        & $ManagerExe @Rest
        break
    }
    "log" {
        & $CtlExe "log" $Arg1 @Rest
        break
    }
    "show" {
        & $CtlExe "show" $Arg1 $Arg2 @Rest
        break
    }
    "revert" {
        & $CtlExe "revert" $Arg1 @Rest
        break
    }
    default {
        Write-Host "Usage:" -ForegroundColor Yellow
        Write-Host "  .\gg.ps1               # запустить менеджер (watcher + sync + bulk)" -ForegroundColor Gray
        Write-Host "  .\gg.ps1 manager       # то же самое" -ForegroundColor Gray
        Write-Host "  .\gg.ps1 log           # показать последние изменения" -ForegroundColor Gray
        Write-Host "  .\gg.ps1 log MainPage  # лог только по файлам, где есть 'MainPage' в пути" -ForegroundColor Gray
        Write-Host "  .\gg.ps1 revert 12     # откатить файл с ID=12 (см. лог)" -ForegroundColor Gray
        Write-Host "  .\gg.ps1 show feature/src/.../File.kt      # показать текущую версию" -ForegroundColor Gray
        Write-Host "  .\gg.ps1 show feature/src/.../File.kt 2    # показать ревизию 2" -ForegroundColor Gray
        break
    }
}
