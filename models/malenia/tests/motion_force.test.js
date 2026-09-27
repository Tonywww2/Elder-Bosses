const assert=require('node:assert/strict'),fs=require('node:fs');
const {sample}=require('../../shared/motion_pose.js');
const clips=JSON.parse(fs.readFileSync('models/malenia/animations/malenia.animation.json')).animations;
const manifest=JSON.parse(fs.readFileSync('models/malenia/animation_manifest.json'));
let checks=0;
for(const [name,clip] of Object.entries(clips)) {
    for(const value of Object.values(clip.bones.prosthetic_forearm_r.rotation)) {
        assert(value[0]<=-5.999&&value[0]>=-100.001,name+' elbow reverses');
        assert(Math.abs(value[1])<=8.001&&Math.abs(value[2])<=6.001,name+' elbow twists');checks+=2;
    }
    for(const value of Object.values(clip.bones.blade_mount.rotation)) {
        assert(value[0]>=-18.001&&value[0]<=48.001&&Math.abs(value[1])<=20.001&&Math.abs(value[2])<=12.001,name+' wrist folds');checks++;
    }
    for(const [start,end] of manifest.clips[name.split('.').pop()].acceleration_windows||[]) {
        const motion=t=>['pelvis','body','chest','prosthetic_arm_r','prosthetic_forearm_r','upper_arm_l','head']
            .flatMap(bone=>sample(clip.bones[bone].rotation,t));
        const a=motion(start),b=motion(start+(end-start)*.3),c=motion(start+(end-start)*.7),d=motion(end);
        const distance=(u,v)=>Math.hypot(...u.map((n,i)=>n-v[i]));
        assert(distance(c,d)>distance(a,b)*1.2,name+' lacks acceleration at '+end);checks++;
    }
}
const water=clips['animation.malenia.waterfowl_dance'];
assert(Object.values(water.bones.pelvis.rotation).every(v=>Math.abs(v[1])<100),'Waterfowl spins like a propeller');checks++;
console.log('Anatomical joint limits and attack acceleration checks passed: '+checks);
