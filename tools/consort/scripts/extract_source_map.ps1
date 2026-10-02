param([string]$GameDir='F:\SteamLibrary\steamapps\common\ELDEN RING\Game')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$toolRoot=Join-Path $root '.workspace-tools/source-analysis'
$output=Join-Path $root 'tools/consort/rig/source_map'
[void][IO.Directory]::CreateDirectory($output)
$uxm=[Reflection.Assembly]::LoadFrom((Join-Path $toolRoot 'uxm/UXM Selective Unpack 2.4.2.0/UXM Selective Unpack.exe'))
foreach($name in @('soulsformats','bouncycastle.crypto')) {[void][Reflection.Assembly]::LoadFrom((Join-Path $toolRoot ($name+'.dll')))}
$souls=[Reflection.Assembly]::LoadFrom((Join-Path $toolRoot 'soulsformats.dll'))
$keys=$uxm.GetType('UXM.ArchiveKeys',$true).GetField('EldenRingKeys',[Reflection.BindingFlags]'Static,Public').GetValue($null)
$decrypt=$uxm.GetType('UXM.CryptographyUtility',$true).GetMethod('DecryptRsa',[Reflection.BindingFlags]'Static,Public')
$readBhd=$souls.GetType('SoulsFormats.BHD5',$true).GetMethod('Read',[Reflection.BindingFlags]'Static,Public')
$game=[Enum]::Parse($souls.GetType('SoulsFormats.BHD5+Game',$true),'EldenRing')
Add-Type -TypeDefinition @'
public static class ConsortSfxPathHash {
    public static ulong Compute(string path) {
        ulong h=0;
        foreach(char c in path.Replace((char)92,'/').ToLowerInvariant()) unchecked {h=h*0x85UL+c;}
        return h;
    }
}
'@
Add-Type -MemberDefinition '[DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr LoadLibrary(string path);' -Name Loader -Namespace ConsortSfxSource
[void][ConsortSfxSource.Loader]::LoadLibrary((Join-Path $toolRoot 'oo2core_6_win64.dll'))
function SHA256([byte[]]$Bytes) {
    $sha=[Security.Cryptography.SHA256]::Create()
    try {return ([BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant()} finally {$sha.Dispose()}
}
$wanted=@{}
foreach($relative in @('/map/MapStudio/m20_01_00_00.msb.dcx')) {$wanted[[ConsortSfxPathHash]::Compute($relative)]=$relative}
$history=New-Object 'System.Collections.Generic.List[object]'
foreach($archive in @('DLC','Data0','Data1','Data2','Data3')) {
    if(!$wanted.Count) {break}
    $stream=$decrypt.Invoke($null,[object[]]@([string](Join-Path $GameDir ($archive+'.bhd')),[string]$keys[$archive]))
    try {$bhd=$readBhd.Invoke($null,[object[]]@($stream,$game))} finally {$stream.Dispose()}
    foreach($bucket in $bhd.Buckets) {foreach($header in $bucket) {
        $hash=[UInt64]$header.FileNameHash
        if(!$wanted.ContainsKey($hash)) {continue}
        $relative=$wanted[$hash];$wanted.Remove($hash)
        $bdt=[IO.File]::OpenRead((Join-Path $GameDir ($archive+'.bdt')))
        try {$raw=$header.ReadFile($bdt)} finally {$bdt.Dispose()}
        $rawHash=SHA256 $raw
        $destination=Join-Path $output 'm20_01_00_00.msb'
        [IO.File]::WriteAllBytes($destination,[SoulsFormats.DCX]::Decompress($raw))
        [ordered]@{source=$relative;archive=$archive;compressed_sha256=$rawHash;uncompressed_sha256=(Get-FileHash -LiteralPath $destination).Hash} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'extraction_manifest.json') -Encoding UTF8
        Write-Output 'Extracted original arena MSB; parse with read_source_map.py.'
        $raw=$null;[GC]::Collect()
    }}
}
if($wanted.Count) {throw 'Original MSB path was not found'}
