#Requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('Backup', 'Restore')]
    [string]$Action,

    [Parameter(Position = 1)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandArgumentPassing = 'Standard'
$PSNativeCommandUseErrorActionPreference = $false

$composeFile = Join-Path $PSScriptRoot 'compose.yaml'
$envFile = Join-Path $PSScriptRoot '.env'
$composeOptions = @('--project-directory', $PSScriptRoot, '--file', $composeFile, '--env-file', $envFile)
$operationId = [Guid]::NewGuid().ToString('N')
$containerFile = "/tmp/basketball-$operationId.sql"

# 저장소에 연결된 Compose 명령을 실행하고 실패 시 작업을 중단합니다.
function Invoke-DbCompose {
    param([string[]]$Arguments)

    & docker compose @composeOptions @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose 작업에 실패했습니다. 명령: $($Arguments[0]), 종료 코드: $LASTEXITCODE"
    }
}

# 데이터와 Flyway 이력을 덤프하고 복사가 끝나면 백업 파일을 확정합니다.
function Backup-Database {
    param([string]$Destination)

    $destinationDirectory = [IO.Path]::GetDirectoryName($Destination)
    [IO.Directory]::CreateDirectory($destinationDirectory) | Out-Null
    if (Test-Path -LiteralPath $Destination) {
        throw "백업 경로에 이미 파일 또는 디렉터리가 있습니다. $Destination"
    }

    $partialFile = Join-Path $destinationDirectory ".basketball-$operationId.partial"
    $dumpCommand = @'
umask 077
export MYSQL_PWD="$MARIADB_PASSWORD"
mariadb-dump --user="$MARIADB_USER" --single-transaction --quick --skip-lock-tables \
    --no-tablespaces --skip-add-drop-table --default-character-set=utf8mb4 \
    --result-file="$1" "$MARIADB_DATABASE"
'@
    try {
        Invoke-DbCompose @('exec', '-T', 'mariadb', 'sh', '-ec', ($dumpCommand.Replace([string][char]13, '')), 'sh', $containerFile)
        Invoke-DbCompose @('cp', "mariadb:${containerFile}", $partialFile)
        [IO.File]::Move($partialFile, $Destination)
        Write-Output "백업 완료. $Destination"
    } finally {
        if (Test-Path -LiteralPath $partialFile) {
            Remove-Item -LiteralPath $partialFile
        }
    }
}

# 빈 데이터베이스에만 SQL 덤프를 복원해 기존 테이블을 보호합니다.
function Restore-Database {
    param([string]$Source)

    if (-not (Test-Path -LiteralPath $Source -PathType Leaf) -or (Get-Item -LiteralPath $Source).Length -eq 0) {
        throw "복원할 SQL 백업 파일이 비어 있거나 없습니다. $Source"
    }

    $restoreCommand = @'
export MYSQL_PWD="$MARIADB_PASSWORD"
table_count=$(mariadb --user="$MARIADB_USER" --database="$MARIADB_DATABASE" --batch --skip-column-names \
    --execute='SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()')
if [ "$table_count" != 0 ]; then
    echo 'The target database must be empty. Stop the application before restoring.' >&2
    exit 1
fi
mariadb --user="$MARIADB_USER" --database="$MARIADB_DATABASE" --default-character-set=utf8mb4 --binary-mode < "$1"
'@
    Invoke-DbCompose @('cp', $Source, "mariadb:${containerFile}")
    Invoke-DbCompose @('exec', '-T', 'mariadb', 'sh', '-ec', ($restoreCommand.Replace([string][char]13, '')), 'sh', $containerFile)
    Write-Output "복원 완료. $Source"
}

if ([string]::IsNullOrWhiteSpace($Path)) {
    if ($Action -eq 'Restore') {
        throw '복원할 SQL 파일 경로를 지정하세요.'
    }
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $Path = Join-Path $PSScriptRoot "backups/basketball-$timestamp.sql"
}

if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'docker/.env 파일을 먼저 구성하세요. docker/README.md를 참고하세요.'
}

$fullPath = [IO.Path]::GetFullPath($Path, (Get-Location).ProviderPath)
try {
    if ($Action -eq 'Backup') {
        Backup-Database $fullPath
    } else {
        Restore-Database $fullPath
    }
} finally {
    & docker compose @composeOptions exec -T mariadb rm -f -- $containerFile
    if ($LASTEXITCODE -ne 0) {
        Write-Warning '컨테이너의 임시 SQL 파일을 정리하지 못했습니다.'
    }
}
