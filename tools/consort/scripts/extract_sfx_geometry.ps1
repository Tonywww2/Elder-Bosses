param([string]$GameDir='F:\SteamLibrary\steamapps\common\ELDEN RING\Game',
      [string[]]$BankPaths=@('/sfx/sfxbnd_commoneffects.ffxbnd.dcx','/sfx/sfxbnd_commoneffects_dlc01.ffxbnd.dcx','/sfx/sfxbnd_commoneffects_dlc02.ffxbnd.dcx'))
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$toolRoot=Join-Path $root '.workspace-tools/source-analysis'
$output=Join-Path $root 'tools/consort/rig/source_sfx_geometry'
[void][IO.Directory]::CreateDirectory($output)
$fxOutput=Join-Path $root 'tools/consort/rig/source_shared_sfx'
[void][IO.Directory]::CreateDirectory($fxOutput)
$textureOutput=Join-Path $root 'tools/consort/rig/source_sfx_textures'
[void][IO.Directory]::CreateDirectory($textureOutput)
$contract=Get-Content -LiteralPath (Join-Path $root 'src/main/resources/assets/elder_bosses/boss/promised_consort/source_contracts.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$runtime=Get-Content -LiteralPath (Join-Path $root 'src/main/resources/assets/elder_bosses/boss/promised_consort/source_runtime_contracts.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$known=Get-Content -LiteralPath (Join-Path $root 'src/main/resources/assets/elder_bosses/boss/promised_consort/source_fxr_contracts.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$fxIDs=New-Object 'System.Collections.Generic.HashSet[int]'
foreach($a in $contract.animations) {foreach($e in $a.events) {if($e.type -in @(96,118)) {[void]$fxIDs.Add([int]$e.fields.'FFX ID')}}}
foreach($b in $runtime.bullets) {foreach($key in @('sfxId_Bullet','sfxId_Hit','sfxId_Flick')) {[void]$fxIDs.Add([int]$b.cells.$key)}}
$status=Get-Content -LiteralPath (Join-Path $root 'src/main/resources/assets/elder_bosses/boss/promised_consort/source_status_vfx.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach($row in $status.rows | Where-Object {$_.id -in @(6,54215,20050560)}) {foreach($field in @('midstSfxId','initSfxId','finishSfxId')) {[void]$fxIDs.Add([int]$row.cells.$field)}}
foreach($e in $known.effects) {[void]$fxIDs.Remove([int]$e.id)}
[void]$fxIDs.Remove(-1)
$modelIDs=New-Object 'System.Collections.Generic.HashSet[int]'
$textureNames=New-Object 'System.Collections.Generic.HashSet[string]'
$textureIDs=New-Object 'System.Collections.Generic.HashSet[int]'
foreach($fx in $known.effects) {
    foreach($m in $fx.models) {foreach($id in $m.curve.constant) {[void]$modelIDs.Add([int]$id)}}
    foreach($resource in $fx.resources) {if($resource -match '^(s(\d+)_[^.]+)\.tif$') {[void]$textureNames.Add($Matches[1]);[void]$textureIDs.Add([int]$Matches[2])}}
}
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
foreach($relative in $BankPaths) {$wanted[[ConsortSfxPathHash]::Compute($relative)]=$relative}
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
        $binder=[SoulsFormats.BND4]::Read([SoulsFormats.DCX]::Decompress($raw))
        $retained=New-Object 'System.Collections.Generic.List[object]'
        foreach($entry in $binder.Files) {
            $name=[IO.Path]::GetFileName($entry.Name)
            $isFx=$name -match '^f(\d+)\.(fxr|ffxreslist)$' -and $fxIDs.Contains([int]$Matches[1])
            $isModel=$name -match 's(\d+)\.(flver|sib)(\.dcx)?$' -and $modelIDs.Contains([int]$Matches[1])
            $isTexture=$name -match 's(\d+)(_[^.]+)?\.tpf(\.dcx)?$' -and $textureIDs.Contains([int]$Matches[1])
            if($isTexture) {
                $tpf=[SoulsFormats.TPF]::Read($entry.Bytes)
                foreach($texture in $tpf.Textures) {
                    $textureName=[IO.Path]::GetFileNameWithoutExtension($texture.Name)
                    if(!$textureNames.Contains($textureName)) {continue}
                    [IO.File]::WriteAllBytes((Join-Path $textureOutput ($textureName+'.dds')),$texture.Bytes)
                    $retained.Add([ordered]@{id=$entry.ID;name=$entry.Name;file=($textureName+'.dds');bytes=$texture.Bytes.Length;sha256=(SHA256 $texture.Bytes)})
                }
            }
            if(!$isFx -and !$isModel) {continue}
            $fileName=if($isFx) {$name} else {$entry.ID.ToString()+'_'+$name}
            $directory=if($isFx) {$fxOutput} else {$output}
            [IO.File]::WriteAllBytes((Join-Path $directory $fileName),$entry.Bytes)
            $retained.Add([ordered]@{id=$entry.ID;name=$entry.Name;file=$fileName;bytes=$entry.Bytes.Length;sha256=(SHA256 $entry.Bytes)})
        }
        $history.Add([ordered]@{source=$relative;archive=$archive;compressed_sha256=$rawHash;entries=$binder.Files.Count;retained=@($retained.ToArray())})
        Write-Output ("Scanned {0}: {1} entries; retained {2} source model resources." -f $relative,$binder.Files.Count,$retained.Count)
        $raw=$null;$binder=$null;[GC]::Collect()
    }}
}
[ordered]@{requested_model_ids=@(83022,84008);whole_shared_banks_retained=$false;scanned=@($history.ToArray());missing_paths=@($wanted.Values)} |
    ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output ('extraction_'+($BankPaths[0] -replace '[^a-zA-Z0-9]','_')+'.json')) -Encoding UTF8
[ordered]@{requested_fxr_ids=@($fxIDs);scanned=@($history.ToArray());whole_shared_banks_retained=$false} |
    ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $fxOutput ('extraction_'+($BankPaths[0] -replace '[^a-zA-Z0-9]','_')+'.json')) -Encoding UTF8
