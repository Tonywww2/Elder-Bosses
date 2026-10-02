param(
    [string]$GameDir = 'F:\SteamLibrary\steamapps\common\ELDEN RING\Game',
    [string[]]$Paths = @('/action/script/c0000.hks', '/chr/c0000.anibnd.dcx', '/chr/c0000_dlc02.anibnd.dcx',
        '/chr/c0000_dlc01.anibnd.dcx', '/chr/c0000_a00_hi.anibnd.dcx', '/chr/c0000_a0x.anibnd.dcx')
)

# Read the archive and binder in memory. Retain only the requested motion,
# matching TAE, skeleton and compendium; never expand the whole player bank.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$work = Join-Path $root '.workspace-tools\source-analysis'
$output = Join-Path $root 'tools\consort\rig\player_grab_source'
[void][IO.Directory]::CreateDirectory($output)
$uxm = [Reflection.Assembly]::LoadFrom((Join-Path $work 'uxm\UXM Selective Unpack 2.4.2.0\UXM Selective Unpack.exe'))
foreach ($name in @('soulsformats', 'bouncycastle.crypto')) {
    [void][Reflection.Assembly]::LoadFrom((Join-Path $work ($name + '.dll')))
}
$souls = [Reflection.Assembly]::LoadFrom((Join-Path $work 'soulsformats.dll'))
$keys = $uxm.GetType('UXM.ArchiveKeys', $true).GetField('EldenRingKeys', [Reflection.BindingFlags]'Static,Public').GetValue($null)
$decrypt = $uxm.GetType('UXM.CryptographyUtility', $true).GetMethod('DecryptRsa', [Reflection.BindingFlags]'Static,Public')
$readBhd = $souls.GetType('SoulsFormats.BHD5', $true).GetMethod('Read', [Reflection.BindingFlags]'Static,Public')
$game = [Enum]::Parse($souls.GetType('SoulsFormats.BHD5+Game', $true), 'EldenRing')
Add-Type -TypeDefinition @'
public static class ConsortGrabPathHash {
    public static ulong Compute(string path) {
        ulong hash = 0;
        foreach (char c in path.Replace((char)92, '/').ToLowerInvariant())
            unchecked { hash = hash * 0x85UL + c; }
        return hash;
    }
}
'@
Add-Type -MemberDefinition '[DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr LoadLibrary(string path);' -Name Loader -Namespace ConsortGrabSource
if ([ConsortGrabSource.Loader]::LoadLibrary((Join-Path $work 'oo2core_6_win64.dll')) -eq [IntPtr]::Zero) {
    throw 'Unable to load retained Oodle dependency'
}
function SHA256([byte[]]$Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function HasGrabTAE([byte[]]$Bytes) {
    if ($Bytes.Length -lt 96 -or [Text.Encoding]::ASCII.GetString($Bytes, 0, 4) -ne 'TAE ') { return $false }
    if ([BitConverter]::ToInt32($Bytes, 8) -ne 0x1000D) { throw 'Unexpected player TAE version' }
    $count = [BitConverter]::ToInt32($Bytes, 0x54)
    $offset = [BitConverter]::ToInt64($Bytes, 0x58)
    for ($i = 0; $i -lt $count; $i++) {
        if ([BitConverter]::ToInt64($Bytes, [int]($offset + $i * 16)) % 1000000 -eq 70890) { return $true }
    }
    return $false
}
$wanted = @{}
$history = @()
$manifestPath = Join-Path $output 'extraction_manifest.json'
if (Test-Path -LiteralPath $manifestPath) {
    $history = @((Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json).scanned)
}
$cached = @{}
foreach ($item in $history) {
    $valid = $true
    foreach ($entry in $item.retained) {
        $filePath = Join-Path $output $entry.file
        if (!(Test-Path -LiteralPath $filePath) -or (Get-FileHash -LiteralPath $filePath).Hash.ToLowerInvariant() -ne $entry.sha256) {
            $valid = $false; break
        }
    }
    if ($valid) { $cached[$item.source] = $item }
}
foreach ($relative in $Paths) {
    if (!$cached.ContainsKey($relative)) { $wanted[[ConsortGrabPathHash]::Compute($relative)] = $relative }
}
$located = @{}
foreach ($archive in @('DLC', 'Data0', 'Data1', 'Data2', 'Data3')) {
    if (!$wanted.Count) { break }
    $arguments = [object[]]@([string](Join-Path $GameDir ($archive + '.bhd')), [string]$keys[$archive])
    $stream = $decrypt.Invoke($null, $arguments)
    try { $bhd = $readBhd.Invoke($null, [object[]]@($stream, $game)) }
    finally { $stream.Dispose() }
    foreach ($bucket in $bhd.Buckets) {
        foreach ($header in $bucket) {
            $hash = [UInt64]$header.FileNameHash
            if (!$wanted.ContainsKey($hash)) { continue }
            $relative = $wanted[$hash]
            $located[$relative] = [pscustomobject]@{ archive = $archive; header = $header }
            $wanted.Remove($hash)
        }
    }
}
$manifest = New-Object 'System.Collections.Generic.List[object]'
$foundMotion = @($cached.Values | ForEach-Object retained | Where-Object role -eq 'target_motion').Count -gt 0
$foundTAE = @($cached.Values | ForEach-Object retained | Where-Object role -eq 'matching_tae').Count -gt 0
foreach ($relative in $Paths) {
    $unreadScripts = @($Paths | Where-Object {
        $scriptSource = $_
        !$scriptSource.EndsWith('.dcx') -and !$cached.ContainsKey($scriptSource) -and
            @($manifest.ToArray() | Where-Object source -eq $scriptSource).Count -eq 0
    })
    if ($foundMotion -and $foundTAE -and !$unreadScripts.Count) { break }
    if ($cached.ContainsKey($relative)) { $manifest.Add($cached[$relative]); continue }
    if (!$located.ContainsKey($relative)) { continue }
    $location = $located[$relative]
    $bdt = [IO.File]::OpenRead((Join-Path $GameDir ($location.archive + '.bdt')))
    try { $raw = $location.header.ReadFile($bdt) }
    finally { $bdt.Dispose() }
    $rawSHA = SHA256 $raw
    if (!$relative.EndsWith('.dcx')) {
        $name = [IO.Path]::GetFileName($relative)
        [IO.File]::WriteAllBytes((Join-Path $output $name), $raw)
        $manifest.Add([pscustomobject]@{ source = $relative; archive = $location.archive;
            compressed_bytes = $raw.Length; compressed_sha256 = $rawSHA; binder_entries = 0;
            retained = @([pscustomobject]@{ binder_id = $null; name = $name; file = $name;
                bytes = $raw.Length; sha256 = $rawSHA; role = 'player_action_script' }); nearby_grab_names = @() })
        Write-Output ("Read {0}: {1} bytes" -f $relative, $raw.Length)
        continue
    }
    $bytes = [SoulsFormats.DCX]::Decompress($raw)
    $binder = [SoulsFormats.BND4]::Read($bytes)
    $folderName = [IO.Path]::GetFileName($relative).Replace('.anibnd.dcx', '')
    $folder = Join-Path $output $folderName
    $kept = New-Object 'System.Collections.Generic.List[object]'
    $nearby = New-Object 'System.Collections.Generic.List[string]'
    $matched = @($binder.Files | Where-Object {
        [IO.Path]::GetFileName($_.Name) -match '(^|_)0*70890\.hkx$'
    })
    foreach ($entry in $binder.Files) {
        $name = [IO.Path]::GetFileName($entry.Name)
        if ($name -match '_07[01]\d{3}\.hkx$') { $nearby.Add($name) }
        $isMotion = $name -match '(^|_)0*70890\.hkx$'
        $isTAE = $name.EndsWith('.tae') -and (HasGrabTAE $entry.Bytes)
        $isDependency = ($matched.Count -gt 0 -and ($name.EndsWith('.compendium') -or $name -eq 'skeleton.hkx')) -or
            ($relative -eq '/chr/c0000.anibnd.dcx' -and ($name -eq 'skeleton.hkx' -or $name.EndsWith('.compendium')))
        if (!$isMotion -and !$isTAE -and !$isDependency) { continue }
        [void][IO.Directory]::CreateDirectory($folder)
        # Include binder ID so duplicate basenames cannot silently overwrite.
        $fileName = $entry.ID.ToString() + '_' + $name
        [IO.File]::WriteAllBytes((Join-Path $folder $fileName), $entry.Bytes)
        $kept.Add([pscustomobject]@{ binder_id = $entry.ID; name = $entry.Name;
            file = $folderName + '/' + $fileName; bytes = $entry.Bytes.Length;
            sha256 = (SHA256 $entry.Bytes); role = $(if ($isMotion) { 'target_motion' } elseif ($isTAE) { 'matching_tae' } else { 'dependency' }) })
        if ($isMotion) { $foundMotion = $true }
        if ($isTAE) { $foundTAE = $true }
    }
    $manifest.Add([pscustomobject]@{ source = $relative; archive = $location.archive;
        compressed_bytes = $raw.Length; compressed_sha256 = $rawSHA;
        binder_entries = $binder.Files.Count; retained = @($kept.ToArray()); nearby_grab_names = @($nearby.ToArray()) })
    Write-Output ("Scanned {0}: {1} binder entries; retained {2}" -f $relative, $binder.Files.Count, $kept.Count)
    $raw = $null; $bytes = $null; $binder = $null
    [GC]::Collect()
    if ($foundMotion -and $foundTAE) { break }
}
foreach ($item in $history) {
    if (@($manifest.ToArray() | Where-Object source -eq $item.source).Count -eq 0) { $manifest.Add($item) }
}
[ordered]@{ requested_player_animation = 70890; found_motion = $foundMotion; found_tae = $foundTAE;
    full_player_banks_retained = $false; old_animation_inputs = @(); scanned = @($manifest.ToArray());
    missing_archive_paths = @($wanted.Values) } | ConvertTo-Json -Depth 9 |
    Set-Content -LiteralPath $manifestPath -Encoding UTF8
if (!$foundMotion -or !$foundTAE) { throw 'Player 70890 extraction is incomplete; see targeted manifest.' }
