$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
[void][Reflection.Assembly]::LoadFrom((Join-Path $root '.workspace-tools/source-analysis/soulsformats.dll'))
$models=foreach($file in Get-ChildItem (Join-Path $root 'tools/consort/rig/source_sfx_geometry') -Filter '*.flver') {
    $model=[SoulsFormats.FLVER2]::Read($file.FullName)
    $meshes=foreach($mesh in $model.Meshes) {
        $vertices=foreach($v in $mesh.Vertices) {
            [ordered]@{position=@([double]$v.Position.X,[double]$v.Position.Y,[double]$v.Position.Z);
                normal=@([double]$v.Normal.X,[double]$v.Normal.Y,[double]$v.Normal.Z);
                uv=@([double]$v.UVs[0].X,[double]$v.UVs[0].Y)}
        }
        $faces=$mesh.FaceSets[0].Triangulate($true,$false)
        $material=$model.Materials[$mesh.MaterialIndex]
        $textures=@($material.Textures | ForEach-Object {[ordered]@{type=$_.Type;path=$_.Path}})
        [ordered]@{vertices=@($vertices);indices=@($faces);source_material=$material.Name;material_textures=$textures}
    }
    [ordered]@{id=[int]([regex]::Match($file.Name,'s(\d+)').Groups[1].Value);source=$file.FullName.Substring($root.Length+1).Replace('\','/');
        sha256=(Get-FileHash -LiteralPath $file.FullName).Hash;meshes=@($meshes)}
}
$doc=[ordered]@{schema_version=1;models=@($models);geometry_units_are_source_world_units=$true;materials='Minecraft energy/rock adaptation; source vertex positions and UVs unchanged'} | ConvertTo-Json -Depth 10 -Compress
foreach($relative in @('src/main/resources/assets/elder_bosses/boss/promised_consort/source_sfx_geometry.json','src/main/resources/assets/elder_bosses/boss/promised_consort/source_sfx_geometry.json')) {
    [IO.File]::WriteAllText((Join-Path $root $relative),$doc,[Text.UTF8Encoding]::new($false))
}
"Exported $(@($models).Count) original source effect models."
