param(
    [string]$GameDir = 'F:\SteamLibrary\steamapps\common\ELDEN RING\Game',
    [string[]]$Paths = @(
        '/action/script/c5220.hks',
        '/action/script/c9997.hks',
        '/action/eventnameid.txt',
        '/chr/c5220.behbnd.dcx',
        '/script/522000_battle.luabnd.dcx',
        '/event/m20_01_00_00.emevd.dcx',
        '/sfx/sfxbnd_c5220.ffxbnd.dcx'
    )
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$work = Join-Path $root '.workspace-tools\source-analysis'
$output = Join-Path $root 'docs\assets\reference\elden_ring\promised_consort_radahn\dependencies'
$release = Join-Path $work 'uxm\UXM Selective Unpack 2.4.2.0\UXM Selective Unpack.exe'
$uxm = [Reflection.Assembly]::LoadFrom($release)
foreach ($name in @('soulsformats', 'bouncycastle.crypto')) {
    $dll = Join-Path $work ($name + '.dll')
    if (!(Test-Path -LiteralPath $dll)) {
        $resource = $uxm.GetManifestResourceStream('costura.' + $name + '.dll.compressed')
        $deflate = New-Object IO.Compression.DeflateStream($resource, [IO.Compression.CompressionMode]::Decompress)
        $destination = [IO.File]::Create($dll)
        try { $deflate.CopyTo($destination) }
        finally { $destination.Dispose(); $deflate.Dispose(); $resource.Dispose() }
    }
    [void][Reflection.Assembly]::LoadFrom($dll)
}
$souls = [Reflection.Assembly]::LoadFrom((Join-Path $work 'soulsformats.dll'))
$keys = $uxm.GetType('UXM.ArchiveKeys', $true).GetField('EldenRingKeys', [Reflection.BindingFlags]'Static,Public').GetValue($null)
$decrypt = $uxm.GetType('UXM.CryptographyUtility', $true).GetMethod('DecryptRsa', [Reflection.BindingFlags]'Static,Public')
$bhdType = $souls.GetType('SoulsFormats.BHD5', $true)
$game = [Enum]::Parse($souls.GetType('SoulsFormats.BHD5+Game', $true), 'EldenRing')
$readBhd = $bhdType.GetMethod('Read', [Reflection.BindingFlags]'Static,Public')
Add-Type -TypeDefinition @'
public static class RadahnSourcePathHash {
    public static ulong Compute(string path) {
        ulong hash = 0;
        foreach (char c in path.Replace((char)92, '/').ToLowerInvariant())
            unchecked { hash = hash * 0x85UL + c; }
        return hash;
    }
}
'@
$wanted = @{}
foreach ($relative in $Paths) { $wanted[[RadahnSourcePathHash]::Compute($relative)] = $relative }
$found = New-Object 'System.Collections.Generic.List[object]'
foreach ($archive in @('DLC', 'Data0', 'Data1', 'Data2', 'Data3')) {
    if (!$wanted.Count) { break }
    $bhdPath = Join-Path $GameDir ($archive + '.bhd')
    $arguments = New-Object 'System.Object[]' 2
    $arguments[0] = [string]$bhdPath
    $arguments[1] = [string]$keys[$archive]
    $stream = $decrypt.Invoke($null, $arguments)
    try { $bhd = $readBhd.Invoke($null, [object[]]@($stream, $game)) }
    finally { $stream.Dispose() }
    $bdt = [IO.File]::OpenRead((Join-Path $GameDir ($archive + '.bdt')))
    try {
        foreach ($bucket in $bhd.Buckets) {
            foreach ($header in $bucket) {
                $hash = [System.UInt64]$header.FileNameHash
                if (!$wanted.ContainsKey($hash)) { continue }
                $relative = $wanted[$hash]
                $path = Join-Path (Join-Path $output 'raw') $relative.TrimStart('/').Replace('/', '\')
                [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))
                $bytes = $header.ReadFile($bdt)
                [IO.File]::WriteAllBytes($path, $bytes)
                $found.Add([pscustomobject]@{ Archive = $archive; Source = $relative; Bytes = $bytes.Length; SHA256 = (Get-FileHash -LiteralPath $path).Hash })
                $wanted.Remove($hash)
                Write-Output ("Extracted {0}: {1} bytes ({2})" -f $relative, $bytes.Length, $archive)
            }
        }
    } finally { $bdt.Dispose() }
}

$localOodle = Join-Path $work 'oo2core_6_win64.dll'
if (!(Test-Path -LiteralPath $localOodle)) { Copy-Item -LiteralPath (Join-Path $GameDir 'oo2core_6_win64.dll') -Destination $localOodle }
Add-Type -MemberDefinition '[DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr LoadLibrary(string path);' -Name Loader -Namespace RadahnSource
[void][RadahnSource.Loader]::LoadLibrary($localOodle)
foreach ($item in $found) {
    if (!$item.Source.EndsWith('.dcx')) { continue }
    $raw = Join-Path (Join-Path $output 'raw') $item.Source.TrimStart('/').Replace('/', '\')
    $bytes = [SoulsFormats.DCX]::Decompress([IO.File]::ReadAllBytes($raw))
    $folder = Join-Path (Join-Path $output 'expanded') $item.Source.TrimStart('/').Replace('/', '\').Replace('.dcx', '')
    [void][IO.Directory]::CreateDirectory($folder)
    if ([Text.Encoding]::ASCII.GetString($bytes, 0, 4) -eq 'BND4') {
        $bnd = [SoulsFormats.BND4]::Read($bytes)
        $duplicateNames = @{}
        foreach ($file in $bnd.Files) {
            $baseName = [IO.Path]::GetFileName($file.Name)
            if (!$duplicateNames.ContainsKey($baseName)) { $duplicateNames[$baseName] = 0 }
            $duplicateNames[$baseName]++
        }
        $entries = foreach ($file in $bnd.Files) {
            $name = [IO.Path]::GetFileName($file.Name)
            if (!$name) { $name = 'file_' + $file.ID + '.bin' }
            elseif ($duplicateNames[$name] -gt 1) { $name = $file.ID.ToString() + '_' + $name }
            [IO.File]::WriteAllBytes((Join-Path $folder $name), $file.Bytes)
            [pscustomobject]@{ ID = $file.ID; Name = $file.Name; File = $name; Bytes = $file.Bytes.Length }
        }
        $entries | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $folder '_binder_entries.json')
        Write-Output ("Expanded {0}: {1} entries" -f $item.Source, $bnd.Files.Count)
    } else {
        [IO.File]::WriteAllBytes((Join-Path $folder ([IO.Path]::GetFileName($folder))), $bytes)
    }
}
$manifestPath=Join-Path $output 'extraction_manifest.json'
$history=@()
if(Test-Path -LiteralPath $manifestPath) {
    $history=@(Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json | Where-Object { $_.Source -notin @($found | ForEach-Object Source) })
}
@($history) + @($found.ToArray()) | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 -LiteralPath $manifestPath
if ($wanted.Count) { throw ('Missing archive paths: ' + ($wanted.Values -join ', ')) }
