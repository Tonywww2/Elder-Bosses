"""Join original FXR trees with the FXR tool author's ER action definitions.

Keep complete curves and unknown fields as evidence. Shape labels are only
derived after decoding the actual appearance/resource actions.
"""
import collections
import hashlib
import json
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
import xml.etree.ElementTree as ET
import yaml

ROOT=Path(__file__).resolve().parents[3]
WORK=Path(__file__).resolve().parents[1]
REF=ROOT/'docs/assets/reference/elden_ring/promised_consort_radahn/dependencies'
DEFS=ROOT/'.workspace-tools/source-analysis/read-fxr/definitions'

def field(node):
    value=node.get('Value')
    return int(value) if node.tag=='Int' else float(value)

def layout(definition,group,game='ER'):
    games=definition.get('games',{})
    entry=games.get(game,{})
    if isinstance(entry,str):return layout(definition,group,entry)
    values=entry.get(group,[])
    return layout(definition,group,values) if isinstance(values,str) else values

def field_layout(definition,group):
    result=[]
    for name in layout(definition,group):
        metadata=definition.get('properties',{}).get(name,{})
        kind=metadata.get('field')
        if isinstance(kind,dict):kind=kind.get('ER',kind.get('SDT',kind.get('DS3')))
        count={'vec2':2,'vec3':3,'vec4':4}.get(kind,1)
        for component in range(count):
            result.append((name,component if count>1 else None))
    return result

def curve(node):
    kinds={'Scalar':1,'Vector2':2,'Vector3':3,'Color':4}
    functions={'Zero':0,'One':1,'Constant':2,'Stepped':3,'Linear':4,'Curve1':5,'Curve2':6,'UnkAc6':7}
    values=[field(f) for f in node.findall('./Fields/*')]
    components=kinds[node.get('PropertyType')];function=functions[node.get('InterpolationType')]
    result={'type':node.get('PropertyType'),'interpolation':node.get('InterpolationType'),
            'function':function,'components':components,'loop':node.get('IsLoop')=='true','values':values,
            'modifiers':ET.tostring(node.find('Modifiers'),encoding='unicode')}
    if function<=2:
        result['constant']=([0]*components if function==0 else [1]*components if function==1 else values)
        assert len(result['constant'])==components
    elif function<=6:
        count=int(values[0]);begin=1+2*components
        assert len(values)==begin+count*(1+components*(3 if function>=5 else 1))
        result['keyframes']=[{'time':values[begin+i],'value':values[begin+count+i*components:begin+count+(i+1)*components],
            **({'in':values[begin+count+count*components+i*components:begin+count+count*components+(i+1)*components],
                'out':values[begin+count+2*count*components+i*components:begin+count+2*count*components+(i+1)*components]} if function>=5 else {})}
            for i in range(count)]
    return result

def main():
    definitions={d['type']:d for p in DEFS.glob('*.yml') if (d:=yaml.safe_load(p.read_text(encoding='utf-8')))}
    result=[]
    for path in sorted((REF/'decompiled/fxr').glob('*.xml')):
        root=ET.parse(path).getroot();fid=int(root.findtext('Id'));nodes=[]
        def action(a):
            typ=int(a.get('Id'));definition=definitions.get(typ,{});groups={}
            for group in ('Fields1','Fields2','Properties1','Properties2'):
                names=layout(definition,group.lower());fields=field_layout(definition,group.lower());values=[]
                for i,value in enumerate(a.findall(f'./{group}/*')):
                    if group.startswith('Fields'):
                        name,component=fields[i] if i<len(fields) else (f'unknown_{i}',None)
                        record={'name':name,'index':i,'value':field(value)}
                        if component is not None:record['component']=component
                    else:
                        name=names[i] if i<len(names) else f'unknown_{i}'
                        metadata=definition.get('properties',{}).get(name,{})
                        record={'name':name,'index':i,**curve(value)}
                        record.update({k:metadata[k] for k in ('default','argument','scale','resource','enum','field') if k in metadata})
                    values.append(record)
                groups[group.lower()]=values
            return {'type':typ,'name':definition.get('name','unclosed_source_action'),'slot':definition.get('slot','unknown'),
                    'appearance':definition.get('meta',{}).get('isAppearance',False),**groups}
        def collect(node,path):
            nodes.append({'path':path,'kind':node.tag,'id':int(node.get('Id')),'actions':[action(a) for a in node.findall('./Actions/Action')]})
            for group,kind in (('Containers','Container'),('Effects','Effect')):
                for i,child in enumerate(node.findall(f'./{group}/{kind}')):collect(child,f'{path}/{group}/{i}')
        collect(root.find('Container'),'root')
        source=REF/'expanded/sfx/sfxbnd_c5220.ffxbnd'/f'f{fid:09}.fxr'
        if not source.exists():source=WORK/'rig/source_shared_sfx'/f'f{fid:09}.fxr'
        resource=source.with_suffix('.ffxreslist')
        models=[]
        for n in nodes:
            for a in n['actions']:
                if a['name'] in ('Model','RichModel'):
                    model=next((v for v in a['properties1'] if v['name']=='model'),None)
                    if model:models.append({'path':n['path'],'type':a['name'],'curve':model})
        result.append({'id':fid,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
                       'resources':resource.read_text(encoding='utf-8').splitlines(),'models':models,'nodes':nodes,
                       'appearance_counts':dict(collections.Counter(a['name'] for n in nodes for a in n['actions'] if a['appearance']))})
    report={'schema_version':2,'original_fxr_files':len(result),'original_resource_lists':len(result),
            'decoder':'SoulsFormatsNEXT FXR3; read-only source tree and curve fields',
            'definition_source':'https://github.com/EvenTorset/fxr/tree/main/src/actions',
            'parser_source':'https://github.com/soulsmods/SoulsFormatsNEXT/blob/master/SoulsFormats/Formats/FXR3.cs',
            'unclosed_source_action_ids':[0],'effects':result,
            'ported_materials_are_Minecraft_adaptations':True,'no_original_game_files_modified':True}
    text=json.dumps(report,ensure_ascii=True,allow_nan=False,separators=(',',':'))+'\n'
    (DATA/'source_fxr_contracts.json').write_text(text,encoding='utf-8')
    (ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/source_fxr_contracts.json').write_text(text,encoding='utf-8')
    print(f'Decoded{len(result)} original FXR trees, {sum(len(e["nodes"]) for e in result)} nodes.')
    for e in result:
        if e['id'] in (652252,652255,652263,652285,652222):print(e['id'],e['appearance_counts'],[(m['type'],m['curve']['values']) for m in e['models']])

if __name__=='__main__':main()
