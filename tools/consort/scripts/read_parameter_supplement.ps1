$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$toolRoot = Join-Path $projectRoot '.workspace-tools\source-analysis'
$sourceRoot = Join-Path $projectRoot 'docs\assets\reference\elden_ring\promised_consort_radahn\dependencies\params'
[void][Reflection.Assembly]::LoadFrom((Join-Path $toolRoot 'soulsformats.dll'))
$requests = @{
    'AtkParam_Npc' = @{ Def='AtkParam'; IDs=@(0,1,4) }
    'Bullet' = @{ Def='BulletParam'; IDs=@(205220301) }
    'SpEffectParam' = @{ Def='SpEffect.readonly'; IDs=@(0,10690) }
}
$sourceContract=Get-Content -LiteralPath (Join-Path $projectRoot 'src/main/resources/assets/elder_bosses/boss/promised_consort/source_contracts.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$eventEffects=@($sourceContract.animations | ForEach-Object {$_.events} | Where-Object {$_.type -in @(66,67) -and $_.reference_id -ge 0} | ForEach-Object reference_id | Sort-Object -Unique)
$requests.SpEffectParam.IDs=@($requests.SpEffectParam.IDs)+$eventEffects
$result = [ordered]@{ Source='original full parameter binaries'; Params=[ordered]@{}; Missing=@() }
foreach ($name in $requests.Keys) {
    $path = Join-Path $sourceRoot ($name + '.param')
    $param = [SoulsFormats.PARAM]::Read($path)
    $defPath = if ($name -eq 'SpEffectParam') { Join-Path $toolRoot 'SpEffect.readonly.xml' } else { Join-Path $toolRoot ('paramdex\defs\' + $requests[$name].Def + '.xml') }
    $definition = [SoulsFormats.PARAMDEF]::XmlDeserialize($defPath)
    if (!$param.ApplyParamdefCarefully($definition)) { throw ('Parameter definition mismatch: ' + $name) }
    $wantedIDs = New-Object 'System.Collections.Generic.HashSet[int]'
    foreach ($id in $requests[$name].IDs) { [void]$wantedIDs.Add($id) }
    if ($name -eq 'SpEffectParam') {
        do {
            $added = $false
            foreach ($row in $param.Rows) {
                if (!$wantedIDs.Contains($row.ID)) { continue }
                foreach ($cell in $row.Cells) {
                    if ($cell.Def.InternalName -in @('replaceSpEffectId','cycleOccurrenceSpEffectId','atkOccurrenceSpEffectId') -and [int]$cell.Value -ge 0) {
                        if ($wantedIDs.Add([int]$cell.Value)) { $added = $true }
                    }
                }
            }
        } while ($added)
    }
    $selected = @($param.Rows | Where-Object { $wantedIDs.Contains($_.ID) })
    $rows = foreach ($row in $selected) {
        $cells = [ordered]@{}
        foreach ($cell in $row.Cells) { if ($cell.Def.DisplayType.ToString() -ne 'dummy8') { $cells[$cell.Def.InternalName]=$cell.Value } }
        [pscustomobject]@{ ID=$row.ID; Cells=$cells }
    }
    $result.Params[$name] = @{ SHA256=(Get-FileHash -LiteralPath $path).Hash; Rows=@($rows) }
    foreach ($id in $requests[$name].IDs) { if ($id -notin @($selected | ForEach-Object ID)) { $result.Missing += ($name + ':' + $id) } }
}
$path = Join-Path $projectRoot 'tools\consort\inputs\source_parameter_supplement.json'
$result | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath $path -Encoding UTF8
Write-Output ('Read original parameter supplement; missing: ' + ($result.Missing -join ', '))
