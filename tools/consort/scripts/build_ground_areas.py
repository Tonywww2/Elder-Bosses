"""Hand-authored MC ground envelopes for every original melee/grab segment.

These are gameplay adapters, not sampled sword paths or claimed ER hitboxes.
Dimensions are metres and become current common-config fields.
"""
import json
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
W=Path(__file__).resolve().parents[1]
ROOT=W.parents[1]
# kind, reach/radius, width/arc, forward offset, yaw offset, height
S=lambda r=8,a=160,y=0: ('SECTOR',r,a,0,y,4)
R=lambda l=9,w=3.5: ('RECTANGLE',l,w,0,0,4)
C=lambda r=6,f=0,h=6: ('CIRCLE',r,0,f,0,h)
PROFILES={
    3000:S(7.5,150,-20),3001:S(7.5,150,20),
    3002:{120:S(8,170),121:R(8,3.5),122:C(6,2),123:C(6,2)},
    3003:S(8,300),3004:R(9,3.5),3005:{150:R(8,3.5),151:C(6,2)},
    3006:{160:R(9,4),161:C(6.5,2)},3007:S(8,170),3008:S(8,200),
    3010:C(6.5),3011:C(6.5),3013:{230:S(8,130),231:C(7,2),232:C(7,2)},
    3014:S(8,180),3015:C(8.5),3016:C(8),3017:S(8,180),
    3018:S(8,180),3019:S(8,170),3020:S(4,75),3024:C(18,0,12),
    3025:C(7),3028:{160:R(9,4),161:C(6.5,2)},3030:C(8),
    3031:R(12,4),3032:S(9,210),3033:R(10,4),3034:R(10,4),
    3035:{431:S(8,170),432:R(9,4),433:S(8,180)},3036:{434:S(8,180),435:C(7,2)},
    20002:S(7.5,150),20003:S(8,180),20004:S(8,150),20005:R(9,4),
    20006:C(7),20007:C(6),20008:C(6),20009:C(6),20010:C(8.5),20013:C(6),
}
def main():
    source=json.loads((DATA/'source_contracts.json').read_text(encoding='utf-8'))
    areas=[]
    for clip in source['animations']:
        tae=clip['tae_id']
        for event in clip['events']:
            if event['type']!=1: continue
            profile=PROFILES[tae]
            if isinstance(profile,dict):profile=profile[event['reference_id']]
            kind,size,second,forward,yaw,height=profile
            areas.append(dict(key=f'a{tae}_e{event["index"]}',tae_id=tae,event_index=event['index'],behavior_judge=event['reference_id'],shape=kind,
                length=size,width=second if kind=='RECTANGLE' else 1,angle=second if kind=='SECTOR' else 360,forward=forward,yaw=yaw,height=height))
    result=dict(schema_version=1,policy='Authored approximate ground envelopes; simple mode uses these envelopes directly. Original source event timing and damage remain intact.',areas=areas)
    text=json.dumps(result,indent=2)+'\n'
    (DATA/'source_ground_areas.json').write_text(text,encoding='utf-8')
    (ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/source_ground_areas.json').write_text(text,encoding='utf-8')
    print(f'{len(areas)} individually mapped original ground attack/grab areas')
if __name__=='__main__':main()
