param(
    [Parameter(Mandatory=$true)][ValidateSet('phone','vehicle')][string]$Device,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory=$true)][ValidateSet('start','invalidPeer','disconnect','publish','awaitMessage','awaitLink',
        'awaitCanonical','edit','add','delete','queue','intervals','weatherFixture','detect','cancel','dismiss',
        'tapNotification','reject','rawClient','rawHost','closeRaw','assert','status','stop')][string]$Command,
    [string]$ExtraJson='{}',
    [ValidatePattern('^bt-[A-Za-z0-9_-]+$')][string]$RunId='bt-20261113',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$EvidenceSubdirectory='2026-11-13-s24-tabs7'
)
$ErrorActionPreference='Stop'
$adbPath=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$commandArgs=@('-s',$Serial,'shell','am','broadcast','-a','com.vemoring.companion.physicaltest.CONTROL',
    '--es','runId',$RunId,'--es','command',$Command)
$extras=ConvertFrom-Json $ExtraJson
foreach($property in $extras.PSObject.Properties) {
    if($property.Name -notmatch '^[a-zA-Z][a-zA-Z0-9]*$') {throw 'Invalid control field name.'}
    $value=$property.Value
    if($value -is [bool]) {$commandArgs+=@('--ez',$property.Name,$value.ToString().ToLowerInvariant())}
    elseif($value -is [int] -or $value -is [long]) {$commandArgs+=@('--ei',$property.Name,[string]$value)}
    else {
        if([string]$value -notmatch '^[a-zA-Z0-9._:-]+$') {throw 'Use simple ASCII control values; fixture display text belongs in the test source.'}
        $commandArgs+=@('--es',$property.Name,[string]$value)
    }
}
$output=& $adbPath @commandArgs 2>&1
if($LASTEXITCODE -ne 0) {throw ($output -join "`n")}
$projectRoot=Split-Path $PSScriptRoot -Parent
$evidenceDir=Join-Path (Join-Path $projectRoot 'build\diagnostics\bluetooth') $EvidenceSubdirectory
New-Item -ItemType Directory -Force $evidenceDir | Out-Null
$stamp=Get-Date -Format 'HHmmssfff'
$output | Set-Content -Encoding utf8 (Join-Path $evidenceDir "$stamp-$Device-$Command.txt")
$line=$output | Where-Object {$_ -match '^Broadcast completed:'} | Select-Object -Last 1
if($line -notmatch 'data="(.*)"$') {throw "No harness response: $($output -join ' ')"}
$state=ConvertFrom-Json $Matches[1]
if(!$state.ok) {throw ($state | ConvertTo-Json -Depth 20 -Compress)}
[pscustomobject]@{Device=$Device;Event=$state.event;Active=$state.active;Message=$state.message;
    Received=$state.receivedMessage;Vehicle=$state.receivedVehicle;Outgoing=$state.outgoingVehicle;
    Revision=$state.receivedRevision;ReceivedAt=$state.receivedAt;States=$state.states;History=$state.history;Detail=$state.detail}
