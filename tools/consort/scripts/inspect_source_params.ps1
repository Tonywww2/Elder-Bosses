param(
    [string]$GameDir = 'F:\SteamLibrary\steamapps\common\ELDEN RING\Game',
    [string]$Python = 'C:\Users\Tony\AppData\Local\Programs\Python\Python313\python.exe'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$work = Join-Path $root '.workspace-tools\source-analysis'
$out = Join-Path $root 'docs\assets\reference\elden_ring\promised_consort_radahn\dependencies\params'
[void][IO.Directory]::CreateDirectory($out)
[void][Reflection.Assembly]::LoadFrom((Join-Path $work 'soulsformats.dll'))
[void][Reflection.Assembly]::LoadFrom((Join-Path $work 'bouncycastle.crypto.dll'))
Add-Type -MemberDefinition '[DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr LoadLibrary(string path);' -Name Loader -Namespace RadahnParams
[void][RadahnParams.Loader]::LoadLibrary((Join-Path $work 'oo2core_6_win64.dll'))
$regulation = Join-Path $GameDir 'regulation.bin'
$utility=[SoulsFormats.SFUtil]
$flags=[Reflection.BindingFlags]'Static,NonPublic'
$arguments=New-Object 'System.Object[]' 2
$arguments[0]=$utility.GetField('erRegulationKey',$flags).GetValue($null)
$arguments[1]=[IO.File]::ReadAllBytes($regulation)
$decrypted=$utility.GetMethod('DecryptByteArray',$flags).Invoke($null,$arguments)
$dcx=Join-Path $work 'regulation.decrypted.dcx'
$bnd=Join-Path $work 'regulation.decrypted.bnd'
[IO.File]::WriteAllBytes($dcx,$decrypted)
if([Text.Encoding]::ASCII.GetString($decrypted,40,4) -eq 'ZSTD') {
    $env:PYTHONPATH=Join-Path $root '.workspace-tools\python'
    & $Python (Join-Path $PSScriptRoot 'decompress_source_zstd.py') $dcx $bnd
    if($LASTEXITCODE) { throw 'Regulation DCX decompression failed' }
    $binder=[SoulsFormats.BND4]::Read($bnd)
} else { $binder=[SoulsFormats.BND4]::Read($decrypted) }
$definitions = @{
    'BehaviorParam'='BehaviorParam'; 'AtkParam_Npc'='AtkParam'; 'Bullet'='BulletParam';
    'SpEffectParam'='SpEffect'; 'ThrowParam'='ThrowParam';
    'NpcAiActionParam'='NpcAiActionParam'; 'NpcParam'='NpcParam'
}
$all = [ordered]@{ RegulationSHA256=(Get-FileHash -LiteralPath $regulation).Hash; Params=[ordered]@{} }
foreach($entry in $binder.Files) {
    $name=[IO.Path]::GetFileNameWithoutExtension($entry.Name)
    if(!$definitions.ContainsKey($name)) { continue }
    [IO.File]::WriteAllBytes((Join-Path $out ($name+'.param')), $entry.Bytes)
    $param=[SoulsFormats.PARAM]::Read($entry.Bytes)
    $defPath=Join-Path $work ('paramdex\defs\'+$definitions[$name]+'.xml')
    if($name -eq 'SpEffectParam') {
        # Paramdex leaves three bits unnamed at 0x353. Preserve them as unknown;
        # the older read-only SoulsFormats reader rejects nonzero orphan bits.
        $xml=New-Object Xml.XmlDocument
        $xml.Load($defPath)
        $anchor=$xml.SelectSingleNode('//Field[@Def="u8 unk353_4:1"]')
        $unknown=$xml.CreateElement('Field')
        $unknown.SetAttribute('Def','u8 unresolved353_5:3')
        [void]$anchor.ParentNode.InsertAfter($unknown,$anchor)
        $defPath=Join-Path $work 'SpEffect.readonly.xml'
        $xml.Save($defPath)
    }
    $def=[SoulsFormats.PARAMDEF]::XmlDeserialize($defPath)
    if(!$param.ApplyParamdefCarefully($def)) { throw ('Parameter definition mismatch: '+$name+' data='+$param.ParamdefDataVersion+' size='+$param.DetectedSize+' expected='+$def.GetRowSize()) }
    $names=@{}
    $namesPath=Join-Path $work ('paramdex\names\'+$name+'.txt')
    if(Test-Path -LiteralPath $namesPath) {
        foreach($line in [IO.File]::ReadAllLines($namesPath)) {
            if($line -match '^(-?\d+)\s+(.*)$') { $names[[int]$matches[1]]=$matches[2] }
        }
    }
    $rows=foreach($row in $param.Rows) {
        # Keep relevant IDs plus common AI actions and grab rows for cross references.
        $id=$row.ID
        $keep = switch($name) {
            'BehaviorParam' { $id -ge 252200000 -and $id -lt 252300000 }
            'AtkParam_Npc' { $id -ge 5220000 -and $id -lt 5221000 }
            'Bullet' { $id -ge 205220000 -and $id -lt 205221000 }
            'SpEffectParam' { ($id -ge 20011500 -and $id -le 20011599) -or ($id -ge 19600 -and $id -lt 19700) }
            'NpcParam' { $id -ge 52200000 -and $id -lt 52210000 }
            'NpcAiActionParam' { $true }
            'ThrowParam' { $true }
        }
        if(!$keep) { continue }
        $cells=[ordered]@{}
        foreach($cell in $row.Cells) { if($cell.Def.DisplayType.ToString() -ne 'dummy8') { $cells[$cell.Def.InternalName]=$cell.Value } }
        [pscustomobject]@{ ID=$id; SourceName=$row.Name; CommunityName=$names[$id]; Cells=$cells }
    }
    $all.Params[$name]=[ordered]@{ Type=$param.ParamType; DataVersion=$param.ParamdefDataVersion; RowSize=$param.DetectedSize; Rows=@($rows) }
    Write-Output ($name+': '+@($rows).Count+' relevant rows')
}
$all | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $out 'c5220_param_report.json')
