param([string]$GameDir='F:\SteamLibrary\steamapps\common\ELDEN RING\Game')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$toolRoot=Join-Path $root '.workspace-tools/source-analysis'
[void][Reflection.Assembly]::LoadFrom((Join-Path $toolRoot 'soulsformats.dll'))
Add-Type -MemberDefinition '[DllImport("kernel32.dll")] public static extern IntPtr LoadLibrary(string p);' -Name Loader -Namespace ConsortStatusVfx
[void][ConsortStatusVfx.Loader]::LoadLibrary((Join-Path $toolRoot 'oo2core_6_win64.dll'))
$utility=[SoulsFormats.SFUtil];$flags=[Reflection.BindingFlags]'Static,NonPublic'
$arguments=New-Object 'System.Object[]' 2
$arguments[0]=$utility.GetField('erRegulationKey',$flags).GetValue($null)
$regulation=Join-Path $GameDir 'regulation.bin'
$arguments[1]=[IO.File]::ReadAllBytes($regulation)
$decrypted=$utility.GetMethod('DecryptByteArray',$flags).Invoke($null,$arguments)
$scratch=Join-Path $root 'build/consort-reset/status-vfx';[void][IO.Directory]::CreateDirectory($scratch)
$dcx=Join-Path $scratch 'regulation.dcx';$bnd=Join-Path $scratch 'regulation.bnd'
[IO.File]::WriteAllBytes($dcx,$decrypted)
if([Text.Encoding]::ASCII.GetString($decrypted,40,4) -eq 'ZSTD') {
    $env:PYTHONPATH=Join-Path $root '.workspace-tools/python'
    & 'C:/Users/Tony/AppData/Local/Programs/Python/Python313/python.exe' (Join-Path $root 'tools/consort/scripts/decompress_source_zstd.py') $dcx $bnd
    if($LASTEXITCODE) {throw 'Original regulation decompression failed'}
    $binder=[SoulsFormats.BND4]::Read($bnd)
} else {$binder=[SoulsFormats.BND4]::Read($decrypted)}
$entry=$binder.Files | Where-Object {[IO.Path]::GetFileNameWithoutExtension($_.Name) -eq 'SpEffectVfxParam'} | Select-Object -First 1
if(!$entry) {throw 'Missing original SpEffectVfxParam'}
$param=[SoulsFormats.PARAM]::Read($entry.Bytes)
$definition=[SoulsFormats.PARAMDEF]::XmlDeserialize((Join-Path $toolRoot 'paramdex/defs/SpEffectVfx.xml'))
if(!$param.ApplyParamdefCarefully($definition)) {throw 'SpEffectVfx definition mismatch'}
$effects=(Get-Content -LiteralPath (Join-Path $root 'src/main/resources/assets/elder_bosses/boss/promised_consort/source_runtime_contracts.json') -Raw -Encoding UTF8 | ConvertFrom-Json).effects
$ids=@($effects | ForEach-Object {$_.cells.vfxId} | Where-Object {$_ -ge 0} | Sort-Object -Unique)
$rows=foreach($row in $param.Rows) {
    if($row.ID -notin $ids) {continue}
    $cells=[ordered]@{};foreach($cell in $row.Cells) {if($cell.Def.DisplayType.ToString() -ne 'dummy8') {$cells[$cell.Def.InternalName]=$cell.Value}}
    [ordered]@{id=$row.ID;cells=$cells}
}
$destination=Join-Path $root 'src/main/resources/assets/elder_bosses/boss/promised_consort/source_status_vfx.json'
[ordered]@{regulation_sha256=(Get-FileHash -LiteralPath $regulation).Hash;definition_source='https://github.com/soulsmods/Paramdex/blob/master/ER/Defs/SpEffectVfx.xml';rows=@($rows)} |
    ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $destination -Encoding UTF8
Remove-Item -LiteralPath $dcx,$bnd
Write-Output ('Original status VFX rows: '+@($rows).Count)
@($rows | Where-Object id -eq 20050560) | ConvertTo-Json -Depth 5
