let revision = "impact_motion_v2";
let clamp = (value, minimum, maximum) => Math.max(minimum, Math.min(maximum, value));
let smooth = value => {
    let bounded = clamp(value, 0, 1);
    return bounded * bounded * (3 - 2 * bounded);
};

function releaseTime(tick, start, contact, end, strength = 0.45) {
    if (tick <= start || tick >= end || tick === contact) return tick;
    if (tick < contact) {
        let progress = (tick - start) / (contact - start);
        return start + (contact - start) * ((1 - strength) * progress + strength * progress * progress);
    }
    let progress = (tick - contact) / (end - contact);
    return contact + (end - contact) * (progress + strength * progress * (1 - progress));
}

function impulse(tick, start, contact, end) {
    if (tick <= start || tick >= end) return 0;
    if (tick < contact) return smooth((tick - start) / (contact - start));
    return 1 - smooth((tick - contact) / (end - contact));
}

function weightShift(tick, window) {
    let heldUntil = window.contact + window.hold;
    let before = tick <= window.contact ? smooth((tick-window.start)/(window.contact-window.start))
        : tick <= heldUntil ? 1 : 1-smooth((tick-heldUntil)/(window.drive_end-heldUntil));
    let recoilStart = heldUntil;
    let recoilPeak = Math.min(window.drive_end - 1, heldUntil + 3);
    let recoil = recoilPeak > recoilStart ? impulse(tick, recoilStart, recoilPeak, window.drive_end) : 0;
    return {
        forward: before * window.forward,
        down: before * window.down,
        side: before * (window.side || 0),
        lean: before * window.lean,
        recoil
    };
}

let releases = {
    left_combo_cross: [[11,"l"],[21,"r"],[44,"both"]],
    right_combo_cross: [[11,"r"],[41,"both"]],
    right_combo_left_twin: [[18,"r"],[40,"l"],[55,"l"]],
    right_combo_tempest: [[12,"r"],[33,"l"],[56,"r"],[82,"both"],[112,"both"]],
    right_combo_earthheave: [[16,"r"],[42,"l"],[54,"r"],[87,"plant"],[105,"lift"]],
    cross_slash: [[17,"both"]],
    left_combo_bloodflame: [[15,"l"],[35,"l"]],
    lion_claw: [[32,"slam"]], lion_claw_double: [[26,"slam"]], stomp: [[20,"foot"]],
    gravity_dive: [[38,"slam"]], spiral_assault: [[35,"both"],[43,"slam"]],
    starcaller_cry: [[18,"cast"],[58,"slam"]], gravity_meteor: [[90,"cast"]],
    enhanced_earthheave: [[28,"plant"],[47,"lift"]], light_of_miquella: [[110,"cast"]],
    ring_of_light: [[26,"r"]], lightspeed_slash: [[69,"slam"]],
    lightspeed_dash: [[73,"both"]], lightspeed_side_dash: [[65,"r"]],
    promised_consort: [[27,"r"],[41,"l"],[54,"both"],[70,"both"],[111,"slam"]],
    consort_meteor: [[43,"cast"],[212,"slam"]]
};

function windows(name, duration, contacts) {
    let events = releases[name];
    if (!events) throw new Error("Missing impact profile: " + name);
    let anchors = [...new Set([0, ...contacts, ...events.map(event => event[0]), duration])].sort((first, second) => first - second);
    return events.map(([contact, kind], releaseIndex) => {
        let index = anchors.indexOf(contact);
        let before = anchors[index - 1], after = anchors[index + 1];
        let grounded = kind === "plant" || kind === "lift";
        let heavy = kind === "slam" || kind === "foot";
        let nextRelease = events[releaseIndex+1]?.[0] || duration;
        let driveEnd = Math.min(contact+18,contact+(nextRelease-contact)*0.45);
        let strength = name === "left_combo_cross" || name === "cross_slash" ? 0.08
            : kind === "cast" || grounded ? 0.18 : 0.35;
        return {contact, kind, start: Math.max(contact - 6, before + (contact - before) * 0.5),
            end: Math.min(contact + 9, contact + (after - contact) * 0.45), strength,
            drive_end: driveEnd, hold: Math.min(heavy ? 5 : 1.5,(driveEnd-contact)*0.4),
            forward: grounded ? 2 : kind === "cast" ? 3.2 : heavy ? 5.2 : 7.2,
            down: grounded ? 0.15 : heavy ? 3.6 : kind === "cast" ? 0.8 : 1.8,
            lean: grounded ? 0 : heavy ? 4 : kind === "cast" ? 1 : 3,
            side: kind === "l" ? -1.5 : kind === "r" ? 1.5 : 0,
            arm_degrees: ["l","r","both"].includes(kind) ? (name === "left_combo_cross" ? 10 : 14) : 0};
    });
}

function sample(tick, windows) {
    let result = {time:tick, forward:0, down:0, side:0, lean:0, recoil:0, arms:{r:0,l:0}, cape:0};
    for (let window of windows) {
        if (tick < window.start || tick > window.drive_end) continue;
        if (tick <= window.end) result.time = releaseTime(tick,window.start,window.contact,window.end,window.strength);
        let shift = weightShift(tick,window);
        result.forward += shift.forward;
        result.down += shift.down;
        result.side += shift.side;
        result.lean += shift.lean;
        result.recoil += shift.recoil;
        let progress = tick < window.contact ? (tick-window.start)/(window.contact-window.start)
            : (tick-window.contact)/(window.drive_end-window.contact);
        let spread = Math.sin(Math.PI * clamp(progress,0,1)) ** 2 * window.arm_degrees;
        if (window.kind === "both" || window.kind === "r") result.arms.r += spread;
        if (window.kind === "both" || window.kind === "l") result.arms.l += spread;
        result.cape += impulse(tick,window.contact,Math.min(window.drive_end-0.1,window.contact+2),window.drive_end) * 5;
    }
    return result;
}

let supportNames = ["idle", "idle_phase_two", "walk", "walk_phase_two", "run", "run_phase_two",
    "hurt", "stunned", "death", "transition", "intro", "turn_left", "turn_right", "turn_left_phase_two", "turn_right_phase_two",
    "clone_overhead_3", "clone_side_fan_3", "clone_meteor_4", "clone_starcaller_2", "clone_dash_4", "clone_cross_return_2"];

function supportSample(name, tick, duration) {
    if (!supportNames.includes(name)) throw new Error("Missing support impact profile: " + name);
    let phase = tick / duration * Math.PI * 2;
    let result = {body:[0,0,0],chest:[0,0,0],cape_middle:[0,0,0],cape_end:[0,0,0]};
    if (name.startsWith("idle")) {
        result.body[0] = (1-Math.cos(phase)) * 0.35;
        result.chest[0] = -result.body[0] * 0.55;
    } else if (name.startsWith("walk") || name.startsWith("run")) {
        let intensity = name.startsWith("run") ? 1.5 : 0.8;
        result.body = [intensity * Math.sin(phase) ** 2, 0, intensity * Math.sin(phase)];
        result.chest = [-result.body[0] * 0.3, 0, -result.body[2] * 0.45];
        result.cape_middle[0] = -intensity * Math.sin(phase) ** 2;
        result.cape_end[0] = -intensity * 1.4 * Math.sin(phase) ** 2;
    } else {
        let peak = name.startsWith("clone_") ? 4 : name.startsWith("turn_") ? 10
            : {hurt:2,stunned:18,death:48,transition:127,intro:40}[name];
        let start = Math.max(0,peak-(name === "hurt" ? 2 : 6)), end = Math.min(duration,peak+10);
        let amount = impulse(tick,start,peak,end);
        let side = name.includes("left") ? -1 : name.includes("right") ? 1 : 0;
        result.body = [amount * (name === "hurt" ? -2 : 2), amount * side, 0];
        result.chest = [-amount * 0.6, -amount * side * 0.35, 0];
        result.cape_middle[0] = -amount * 1.5;
        result.cape_end[0] = -amount * 2.0;
        if (name.startsWith("clone_") || name === "hurt") {
            result.position = [0, -amount*0.3, amount*(name === "hurt" ? 0.8 : -1.8)];
        }
    }
    if (tick === 0 || tick === duration) {
        for (let key of Object.keys(result)) result[key] = [0,0,0];
    }
    return result;
}

function trackSample(values, time) {
    if (values[String(time)]) return [...values[String(time)]];
    let entries = Object.entries(values).sort((first,second)=>Number(first[0])-Number(second[0]));
    let next = entries.findIndex(entry=>Number(entry[0])>=time);
    if (next === 0) return [...entries[0][1]];
    if (next < 0) return [...entries[entries.length-1][1]];
    let before=entries[next-1],after=entries[next];
    let weight=(time-Number(before[0]))/(Number(after[0])-Number(before[0]));
    return before[1].map((value,axis)=>value+(after[1][axis]-value)*weight);
}

function enhanceSupport(name, original) {
    let clip=JSON.parse(JSON.stringify(original));
    let channels=[...["body","chest","cape_middle","cape_end"].map(bone=>[bone,"rotation"])];
    if (name.startsWith("clone_") || name === "hurt") channels.push(["pelvis","position"]);
    for (let [bone,channel] of channels) {
        let source=original.bones[bone]?.[channel];
        if (!source) throw new Error("Missing support channel: "+name+"/"+bone+"/"+channel);
        let times=new Set(Object.keys(source).map(Number));
        for (let tick=0;tick<=original.animation_length*20;tick+=0.5) times.add(tick/20);
        let values={};
        for (let time of [...times].sort((first,second)=>first-second)) {
            let extra=supportSample(name,time*20,original.animation_length*20);
            let offset=channel === "position" ? extra.position : extra[bone];
            values[String(time)]=trackSample(source,time).map((value,axis)=>value+(offset?.[axis]||0));
        }
        clip.bones[bone][channel]=values;
    }
    return {clip,channels};
}

module.exports = {revision, releaseTime, impulse, weightShift, windows, sample, releases, supportNames, supportSample, enhanceSupport};

if (require.main === module) {
    let fs = require("node:fs"), path = require("node:path"), assert = require("node:assert/strict");
    let crypto = require("node:crypto"), spawn = require("node:child_process").spawnSync;
    let workspace = path.resolve(__dirname,"..");
    let read = file => JSON.parse(fs.readFileSync(path.join(workspace,file),"utf8"));
    let animationFile = "animations/promised_consort.animation.json";
    let suffix = ".pre-impact-v1";
    assert(process.argv.includes("--apply"), "Use --apply to generate the complete impact revision");
    if (read("motion_refinement.json").impact_revision !== revision) {
        assert(!fs.existsSync(path.join(workspace,animationFile+".pre-impact-v2")), "Second revision baseline already exists");
        for (let file of [animationFile,"promised_consort.bbmodel","motion_refinement.json","runtime_validation.json",
            "export_validation.json","preview_validation.json","revision_validation.json","motion_continuity.json"]) {
            fs.copyFileSync(path.join(workspace,file),path.join(workspace,file+".pre-impact-v2"));
        }
    }
    for (let name of Object.keys(releases)) {
        let result = spawn(process.execPath,[path.join(__dirname,"rebuild_motion.js"),name,"--impact","--write","--apply","--batch-authorized"],
            {encoding:"utf8",maxBuffer:8*1024*1024});
        if (result.status !== 0) throw new Error(result.stderr || result.error?.message || "Impact generation failed");
        let report = JSON.parse(result.stdout);
        process.stdout.write(name + ": " + report.duration_ticks + " ticks, " + report.impact_windows.length + " release impulses\n");
    }
    let baseline = read(animationFile + suffix), library = read(animationFile);
    let oldProject = read("promised_consort.bbmodel" + suffix), project = read("promised_consort.bbmodel");
    let motion = read("motion_refinement.json"), prefix = "animation.promised_consort.";
    let support = {};
    for (let name of supportNames) {
        let original = baseline.animations[prefix + name];
        let enhanced = enhanceSupport(name,original);
        library.animations[prefix + name] = enhanced.clip;
        let editor = project.animations.find(clip=>clip.name === prefix + name);
        for (let [bone,channel] of enhanced.channels) {
            let animator = Object.values(editor.animators).find(value=>value.name === bone);
            let template = animator.keyframes.find(key=>key.channel === channel);
            let signs = channel === "rotation" ? [-1,-1,1] : [-1,1,1];
            assert(template,"Missing editor impact channel: " + name + "/" + bone);
            animator.keyframes = animator.keyframes.filter(key=>key.channel !== channel);
            for (let [time,values] of Object.entries(enhanced.clip.bones[bone][channel])) {
                animator.keyframes.push({...template,uuid:crypto.randomUUID(),time:Number(time),interpolation:"linear",
                    data_points:[{x:String(values[0]*signs[0]),y:String(values[1]*signs[1]),z:String(values[2]*signs[2])}]});
            }
        }
        support[name] = {channels:enhanced.channels,endpoint_and_loop_preserved:true};
    }
    for (let key of ["elements","groups","outliner","textures"]) assert.deepEqual(project[key],oldProject[key],"Protected model changed: " + key);
    for (let [name,clip] of Object.entries(library.animations)) {
        assert.equal(clip.animation_length,baseline.animations[name].animation_length,"Impact changed duration: " + name);
        assert.equal(clip.loop,baseline.animations[name].loop,"Impact changed loop: " + name);
    }
    assert.deepEqual(read("choreography.json"),read("choreography.json" + suffix),"Impact changed event choreography");
    assert.deepEqual(read("animation_manifest.json"),read("animation_manifest.json" + suffix),"Impact changed manifest");
    let content = require("../../shared/animation_json.js")(library);
    motion.animation_sha256 = crypto.createHash("sha256").update(content).digest("hex");
    motion.impact_support = {baseline:animationFile + suffix,clips:support};
    motion.impact_revision = revision;
    motion.impact_feedback = {previous_revision:"impact_motion_v1",accepted:false,
        requests:["larger body displacement","wider arm arcs","heavier slams, landing and recovery"],
        previous_animation:"animations/promised_consort.animation.json.pre-impact-v2",
        world_run_record:"runtime_validation.json.pre-impact-v2"};
    motion.runtime_feedback.accepted_in_world = false;
    motion.runtime_feedback.accepted_scoped_checks = [];
    motion.runtime_feedback.feedback = "Impact revision awaits review; prior acceptance retained in .pre-impact-v1 records";
    motion.runtime_feedback.prototype_approved_skill_ids = [];
    fs.writeFileSync(path.join(workspace,animationFile),content);
    fs.writeFileSync(path.join(workspace,"promised_consort.bbmodel"),JSON.stringify(project,null,2)+"\n");
    fs.writeFileSync(path.join(workspace,"motion_refinement.json"),JSON.stringify(motion,null,2)+"\n");
    process.stdout.write(JSON.stringify({revision:motion.impact_revision,animation_sha256:motion.animation_sha256,
        skills:Object.keys(releases).length,support_clips:supportNames.length,events_and_durations_unchanged:true,geometry_and_textures_unchanged:true})+"\n");
}