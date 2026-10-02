$ErrorActionPreference = 'Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$work=Join-Path $root '.workspace-tools\source-analysis'
$source=Join-Path $root 'docs\assets\reference\elden_ring\promised_consort_radahn\dependencies'
[void][Reflection.Assembly]::LoadFrom((Join-Path $work 'soulsformats.dll'))
$definitions=Get-Content -Raw -LiteralPath (Join-Path $work 'er-common.emedf.json') | ConvertFrom-Json
$lookup=@{}
foreach($bank in $definitions.main_classes) { foreach($instruction in $bank.instrs) { $lookup[($bank.index.ToString()+':'+$instruction.index)]=$instruction } }
$emevd=[SoulsFormats.EMEVD]::Read((Join-Path $source 'expanded\event\m20_01_00_00.emevd\m20_01_00_00.emevd'))
$events=foreach($event in $emevd.Events) {
    $instructions=for($index=0;$index -lt $event.Instructions.Count;$index++) {
        $instruction=$event.Instructions[$index]
        $definition=$lookup[($instruction.Bank.ToString()+':'+$instruction.ID)]
        $args=[ordered]@{}
        if($definition) {
            $types=New-Object 'System.Collections.Generic.List[SoulsFormats.EMEVD+Instruction+ArgType]'
            foreach($arg in $definition.args) { $types.Add([SoulsFormats.EMEVD+Instruction+ArgType]$arg.type) }
            $values=$instruction.UnpackArgs($types,$false)
            for($a=0;$a -lt $definition.args.Count;$a++) { $args[$definition.args[$a].name]=$values[$a] }
        }
        [pscustomobject]@{ Index=$index; Bank=$instruction.Bank; ID=$instruction.ID; Name=$definition.name; Args=$args; RawHex=([BitConverter]::ToString($instruction.ArgData).Replace('-','')); Layer=$instruction.Layer }
    }
    [pscustomobject]@{ ID=$event.ID; RestBehavior=$event.RestBehavior.ToString(); Parameters=@($event.Parameters); Instructions=@($instructions) }
}
$events | ConvertTo-Json -Depth 9 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $source 'decompiled\m20_01_00_00.events.json')
Write-Output ('Decoded '+$emevd.Events.Count+' map events')
