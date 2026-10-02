param(
    [string]$SourceRelative='docs/assets/reference/elden_ring/promised_consort_radahn/dependencies/expanded/parts/bd_m_0000.partsbnd/BD_M_0000.flver',
    [string]$OutputRelative='tools/consort/rig/player_model_attachments.json'
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
[void][Reflection.Assembly]::LoadFrom((Join-Path $root '.workspace-tools/source-analysis/soulsformats.dll'))
$relative=$SourceRelative
$path=Join-Path $root $relative
$model=[SoulsFormats.FLVER2]::Read($path)
function Vector($value) {return @([double]$value.X,[double]$value.Y,[double]$value.Z)}
$bones=for($i=0;$i -lt $model.Bones.Count;$i++) {
    $bone=$model.Bones[$i]
    [ordered]@{index=$i;name=$bone.Name;parent_index=[int]$bone.ParentIndex;
        translation=(Vector $bone.Translation);rotation_radians=(Vector $bone.Rotation);scale=(Vector $bone.Scale)}
}
$dummies=for($i=0;$i -lt $model.Dummies.Count;$i++) {
    $dummy=$model.Dummies[$i]
    [ordered]@{index=$i;reference_id=[int]$dummy.ReferenceID;parent_bone_index=[int]$dummy.ParentBoneIndex;
        attach_bone_index=[int]$dummy.AttachBoneIndex;position=(Vector $dummy.Position);
        forward=(Vector $dummy.Forward);upward=(Vector $dummy.Upward);use_upward_vector=$dummy.UseUpwardVector}
}
[ordered]@{source=$relative;sha256=(Get-FileHash -LiteralPath $path).Hash;bones=@($bones);dummies=@($dummies)} |
    ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $root $OutputRelative) -Encoding UTF8
"Read $($model.Bones.Count) player model bones and $($model.Dummies.Count) dummy points."
