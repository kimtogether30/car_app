# Creates a source-only ZIP, including current uncommitted feature changes.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$destinationDir = [IO.Path]::GetFullPath((Join-Path $root 'build\handoff'))
if (-not $destinationDir.StartsWith($root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid output directory' }
[IO.Directory]::CreateDirectory($destinationDir) | Out-Null
$destination = Join-Path $destinationDir ('VEMORING-source-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
if (Test-Path -LiteralPath $destination) { throw 'Output already exists' }
$files = @(& git -C $root -c core.quotepath=false ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate project files' }
$zip = [IO.Compression.ZipFile]::Open($destination,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($relative in ($files | Sort-Object -Unique)) {
        $entry = $relative.Replace('\','/')
        if ($entry -match '(^|/)(\.git|\.idea|\.gradle|\.kotlin|\.agents|\.codex|\.aws|build|captures)(/|$)') { continue }
        if ($entry -match '(^|/)(local\.properties|keystore\.properties|\.env[^/]*|[^/]*(secret|credential)[^/]*)$') { continue }
        if ($entry -match '\.(jks|keystore|p12|pfx|pem|key)$') { continue }
        $absolute = [IO.Path]::GetFullPath((Join-Path $root $relative))
        if (-not $absolute.StartsWith($root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside project' }
        if ((Test-Path -LiteralPath $absolute -PathType Leaf)) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$absolute,$entry,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
} finally { $zip.Dispose() }
Write-Output $destination
Get-FileHash -LiteralPath $destination -Algorithm SHA256
