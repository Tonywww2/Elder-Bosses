$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$tools = Join-Path $root '.workspace-tools\source-analysis'
[void][Reflection.Assembly]::LoadFrom((Join-Path $tools 'soulsformats.dll'))
$relative = 'docs/assets/reference/elden_ring/promised_consort_radahn/expanded/c5220.chrbnd/c5220.flver'
$source = Join-Path $root $relative
$model = [SoulsFormats.FLVER2]::Read($source)
function Vector($value) { return @([double]$value.X, [double]$value.Y, [double]$value.Z) }
$bones = for ($i=0; $i -lt $model.Bones.Count; $i++) {
    $bone=$model.Bones[$i]
    [ordered]@{ index=$i; name=$bone.Name; parent_index=[int]$bone.ParentIndex;
        translation=(Vector $bone.Translation); rotation_radians=(Vector $bone.Rotation); scale=(Vector $bone.Scale) }
}
$dummies = for ($i=0; $i -lt $model.Dummies.Count; $i++) {
    $dummy=$model.Dummies[$i]
    [ordered]@{ index=$i; reference_id=[int]$dummy.ReferenceID;
        parent_bone_index=[int]$dummy.ParentBoneIndex; attach_bone_index=[int]$dummy.AttachBoneIndex;
        position=(Vector $dummy.Position); forward=(Vector $dummy.Forward); upward=(Vector $dummy.Upward);
        use_upward_vector=$dummy.UseUpwardVector; flag1=$dummy.Flag1 }
}
$out = Join-Path $root 'tools\consort\rig\source_model_attachments.json'
[ordered]@{ source=$relative; sha256=(Get-FileHash -LiteralPath $source).Hash;
    status='raw_flver_attachment_evidence_not_world_space_runtime_points';
    bones=@($bones); dummies=@($dummies) } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $out -Encoding utf8
"Read $($model.Bones.Count) model bones and $($model.Dummies.Count) source dummy points."
