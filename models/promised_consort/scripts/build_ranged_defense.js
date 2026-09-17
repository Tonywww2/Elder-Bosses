let fs = require("node:fs");
let path = require("node:path");
let assert = require("node:assert/strict");
let crypto = require("node:crypto");
let math = require("../.tools/node_modules/three");
let serialize = require("../../shared/animation_json.js");
let root = path.resolve(__dirname, "..");
let prefix = "animation.promised_consort.";
let read = file => JSON.parse(fs.readFileSync(path.join(root,file),"utf8"));
let source = read("animations/promised_consort.animation.json").animations;
let sampleTrack = (track,tick,fallback) => {
    if(!track) return fallback;
    let keys=Object.entries(track).map(([time,value])=>[Number(time)*20,value]).sort((first,second)=>first[0]-second[0]);
    let index=keys.findIndex(entry=>entry[0]>=tick);
    if(index<=0) return index<0?keys[keys.length-1][1]:keys[0][1];
    let before=keys[index-1],after=keys[index],weight=(tick-before[0])/(after[0]-before[0]);
    return before[1].map((value,axis)=>value+(after[1][axis]-value)*weight);
};
let poses = {
    rest:["idle",0], crossed:["starcaller_cry",13], brace:["starcaller_cry",18],
    opening:["starcaller_cry",36], raised:["starcaller_cry",44],
    release:["ring_of_light",26], wide:["ring_of_light",30], recovery:["ring_of_light",38]
};
let plans = {
    gravity_bulwark:[[0,"rest"],[6,"crossed"],[10,"brace"],[42,"brace"],[46,"crossed"],[55,"opening"],[64,"rest"]],
    gravity_reflection:[[0,"rest"],[4,"crossed"],[8,"opening"],[12,"wide"],[20,"wide"],[24,"recovery"],[44,"rest"]],
    gravity_reprisal:[[0,"rest"],[8,"crossed"],[12,"brace"],[38,"brace"],[42,"crossed"],[48,"raised"],[55,"release"],[60,"wide"],[61,"wide"],[73,"recovery"],[87,"rest"]]
};
let library={format_version:"1.8.0",animations:{}};
let project=read("promised_consort.bbmodel");
let protectedModel=JSON.stringify([project.elements,project.groups,project.outliner,project.textures]);
let templates=project.animations;
project.animations=[];
let channels=Object.fromEntries(Object.entries(source[prefix+"idle"].bones).map(([name,bone])=>[name,Object.keys(bone).filter(channel=>["rotation","position","scale"].includes(channel))]));
let quaternion=values=>new math.Quaternion().setFromEuler(new math.Euler(...values.map((value,axis)=>value*(axis<2?-1:1)*Math.PI/180),"ZYX"));
function blendRotation(first,second,weight,previous) {
    let angles=new math.Euler().setFromQuaternion(quaternion(first).slerp(quaternion(second),weight),"ZYX");
    let values=[angles.x,angles.y,angles.z].map((value,axis)=>value*180/Math.PI*(axis<2?-1:1));
    let candidates=[values,[values[0]+180,180-values[1],values[2]+180]].map(candidate=>candidate.map((value,axis)=>
        value+360*Math.round(((previous||first)[axis]-value)/360)));
    candidates.sort((left,right)=>left.reduce((sum,value,axis)=>sum+(value-(previous||first)[axis])**2,0)
        -right.reduce((sum,value,axis)=>sum+(value-(previous||first)[axis])**2,0));
    return candidates[0];
}
let checks=0;
for(let [name,nodes] of Object.entries(plans)) {
    let duration=nodes[nodes.length-1][0];
    let clip={loop:false,animation_length:duration/20,bones:{}};
    let maximum=0;
    for(let [bone,available] of Object.entries(channels)) {
        clip.bones[bone]={};
        for(let channel of available) {
            let values={},previous,previousValues;
            let fallback=channel==="scale"?[1,1,1]:[0,0,0];
            for(let tick=0;tick<=duration;tick+=0.5) {
                let next=nodes.findIndex(node=>node[0]>=tick);
                let before=nodes[Math.max(0,next-1)],after=nodes[Math.max(0,next)];
                let fraction=after[0]===before[0]?0:(tick-before[0])/(after[0]-before[0]);
                let weight=fraction*fraction*(3-2*fraction);
                let planted=/^(root|control|pelvis|thigh_[lr]|shin_[lr]|foot_[lr]|toe_[lr]|tasset_[lr])$/.test(bone);
                let from=planted?poses.rest:poses[before[1]],to=planted?poses.rest:poses[after[1]];
                let first=sampleTrack(source[prefix+from[0]].bones[bone]?.[channel],from[1],fallback);
                let second=sampleTrack(source[prefix+to[0]].bones[bone]?.[channel],to[1],fallback);
                let current=channel==="rotation"?blendRotation(first,second,weight,previousValues)
                    :first.map((value,axis)=>value+(second[axis]-value)*weight);
                previousValues=current;
                assert(current.every(Number.isFinite),"Nonfinite defense pose");
                if(channel==="rotation" && /^(upper_arm_|forearm_|hand_)/.test(bone)) {
                    let rotation=quaternion(current);
                    if(previous) maximum=Math.max(maximum,previous.angleTo(rotation)*180/Math.PI);
                    previous=rotation;
                }
                values[String(tick/20)]=current;
                checks+=3;
            }
            clip.bones[bone][channel]=values;
        }
    }
    assert(maximum<45,"Defense joint jump: "+name+" / "+maximum);
    for(let [bone,tracks] of Object.entries(clip.bones)) {
        if(!/^(pelvis|thigh_[lr]|shin_[lr]|foot_[lr]|toe_[lr]|tasset_[lr])$/.test(bone)) continue;
        for(let track of Object.values(tracks)) {
            let values=Object.values(track);
            for(let pose of values) assert(pose.every((value,axis)=>Math.abs(value-values[0][axis])<0.000001),"Defense support drift: "+name+"/"+bone);
        }
    }
    library.animations[prefix+name]=clip;
    let editor=JSON.parse(JSON.stringify(templates.find(animation=>animation.name===prefix+"starcaller_cry")));
    editor.uuid=crypto.randomUUID();editor.name=prefix+name;editor.length=duration/20;editor.loop="once";
    for(let animator of Object.values(editor.animators)) {
        let tracks=clip.bones[animator.name];
        if(!tracks) {animator.keyframes=[];continue;}
        let original=animator.keyframes;
        animator.keyframes=[];
        for(let [channel,track] of Object.entries(tracks)) {
            let template=original.find(key=>key.channel===channel);
            assert(template,"Missing defense editor channel: "+animator.name+"/"+channel);
            let signs=channel==="rotation"?[-1,-1,1]:channel==="position"?[-1,1,1]:[1,1,1];
            for(let [time,vector] of Object.entries(track)) animator.keyframes.push({...template,uuid:crypto.randomUUID(),time:Number(time),interpolation:"linear",
                data_points:[{x:String(vector[0]*signs[0]),y:String(vector[1]*signs[1]),z:String(vector[2]*signs[2])}]});
        }
    }
    project.animations.push(editor);
    process.stdout.write(name+": "+duration+" ticks; maximum half-tick arm rotation "+maximum.toFixed(3)+" degrees\n");
}
assert.equal(JSON.stringify([project.elements,project.groups,project.outliner,project.textures]),protectedModel);
let content=serialize(library);
if(process.argv.includes("--write")) {
    fs.writeFileSync(path.join(root,"animations/promised_consort_ranged.animation.json"),content);
    fs.writeFileSync(path.resolve(root,"../../src/main/resources/assets/elder_bosses/animations/entity/promised_consort_ranged.animation.json"),content);
    fs.writeFileSync(path.join(root,"previews/consort_ranged_defense.bbmodel"),JSON.stringify(project)+"\n");
    fs.writeFileSync(path.join(root,"previews/ranged_defense_animation.json"),JSON.stringify({status:"candidate_unreviewed",checks,plans,
        animation_sha256:crypto.createHash("sha256").update(content).digest("hex"),existing_43_clips_and_model_unchanged:true},null,2)+"\n");
}
process.stdout.write("Defense curve checks passed: "+checks+" values; existing animation library untouched\n");