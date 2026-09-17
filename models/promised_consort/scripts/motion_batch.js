let contracts = {
    left_combo_bloodflame: {duration: 66, contacts: [15,35,55], stages: [[15,3,5],[12,4,4],[12,8,3]], start: 146.06666666666666, end: 149.33333333333334},
    lion_claw: {duration: 57, contacts: [32], stages: [[32,5,20]], start: 137.26666666666668, end: 140.13333333333333, airborne: [[13,31]]},
    lion_claw_double: {duration: 61, contacts: [26], stages: [[26,5,30]], start: 142.6, end: 145.66666666666666, airborne: [[7,25]]},
    stomp: {duration: 32, contacts: [20], stages: [[20,3,9]], start: 78.93333333333334, end: 80.53333333333333},
    gravity_dive: {duration: 79, contacts: [38,43], stages: [[38,1,1],[3,1,35]], start: 157.06666666666666, end: 161, airborne: [[14,37]]},
    spiral_assault: {duration: 79, contacts: [35,43], stages: [[35,8,0],[0,1,35]], start: 157.06666666666666, end: 161, airborne: [[15,42]], adaptation: true},
    starcaller_cry: {duration: 109, contacts: [18,58], stages: [[18,8,12],[20,1,50]], start: 161.26666666666668, end: 166.73333333333332},
    gravity_meteor: {duration: 152, contacts: [90,94,98,102,106,110,114,118],
        stages: [[90,4,0],[0,4,0],[0,4,0],[0,4,0],[0,4,0],[0,4,0],[0,4,0],[0,4,0],[0,5,0],[0,5,0],[0,5,0],[0,1,14]],
        start: 149.33333333333334, end: 156.93333333333334, airborne: [[35,137]]},
    enhanced_earthheave: {duration: 83, contacts: [28,47,51,55,59], stages: [[28,5,7],[7,1,3],[0,1,3],[0,1,3],[0,1,23]], start: 91.33333333333333, end: 95.5, adaptation: true},
    light_of_miquella: {duration: 144, contacts: [110], stages: [[110,4,0],[10,1,0],[0,1,0],[0,1,0],[0,1,0],[0,1,0],[0,1,0],[0,1,0],[0,1,12]],
        start: 29.266666666666666, end: 36.46666666666667, secondary: true, airborne: [[25,132]]},
    ring_of_light: {duration: 48, contacts: [26], stages: [[26,5,17]], start: 116.06666666666666, end: 118.46666666666667, secondary: true},
    lightspeed_slash: {duration: 99, contacts: [35,48,61,69], stages: [[35,3,4],[6,3,4],[6,3,1],[4,1,29]],
        start: 103.26666666666667, end: 108.2, secondary: true, airborne: [[12,68]]},
    lightspeed_dash: {duration: 103, contacts: [46,52,58,64,73,80], stages: [[46,2,2],[2,2,2],[2,2,2],[2,2,3],[4,1,3],[3,1,22]],
        start: 86, end: 91.13333333333334, secondary: true},
    lightspeed_side_dash: {duration: 82, contacts: [36,44,52,65], stages: [[36,2,2],[4,2,2],[4,2,5],[6,1,16]],
        start: 47.333333333333336, end: 51.43333333333333, secondary: true, airborne: [[8,24]]},
    promised_consort: {duration: 151, contacts: [27,41,54,70,111,115,120,127], stages: [[27,3,4],[7,3,4],[6,4,4],[8,4,9],[28,1,1],[2,1,2],[2,1,2],[4,1,23]],
        start: 196.53333333333333, end: 204.06666666666666, secondary: true, turning: true, airborne: [[87,110]]},
    consort_meteor: {duration: 280, contacts: [183,202,212], stages: [[0,43,0],[0,140,0],[0,29,0],[0,1,0],[1,1,65]],
        start: 238.26666666666668, end: 252.26666666666668, secondary: true, airborne: [[43,211]]}
};
for (let [name, contract] of Object.entries(contracts)) {
    contract.study = name + "_full_native_batch_v13";
    contract.bvid = contract.secondary ? "BV1sdFFevEXe" : "BV1W3YLe4Eek";
    contract.batch = true;
    contract.review = "Existing native-frame study ledger; new full-sequence pages and batch playback require consolidated review";
}

function profile(name) {
    let lowRight = [[29,47,-1],[0.45,-0.45,-0.77]], lowLeft = [[-29,47,-1],[-0.45,-0.45,-0.77]];
    let highRight = [[23,89,-3],[0.28,0.94,0.18]], highLeft = [[-23,89,-3],[-0.28,0.94,0.18]];
    let plantRight = [[23,38,-17],[0.05,-0.62,-0.78]], plantLeft = [[-23,40,-17],[-0.05,-0.61,-0.79]];
    let openRight = [[37,54,-8],[0.91,-0.18,-0.38]], openLeft = [[-37,54,-8],[-0.91,-0.18,-0.38]];
    let crossRight = [[-3,65,-19],[-0.24,0.96,-0.13]], crossLeft = [[0,66,-18],[0.24,0.96,-0.13]];
    let rightLoad = [[29,83,4],[0.4,0.5,0.77]], leftLoad = [[-30,84,5],[-0.35,0.45,0.82]];
    let driveRight = [[11,58,-24],[0.14,-0.12,-0.98]], driveLeft = [[-11,58,-24],[-0.14,-0.12,-0.98]];
    let groundRight = [12,7.08,-6], groundLeft = [-12,7.08,1];
    function pose(tick, label, turn, lean, hip, right, left, extra = {}) {
        return {tick,label,turn,lean,hip,right,left,rightFoot:groundRight,leftFoot:groundLeft,...extra};
    }
    let ready = (tick, label = "low_ready") => pose(tick,label,0,-4,[0,-2,0],lowRight,lowLeft);
    let crouch = (tick,label = "compression") => pose(tick,label,0,-24,[0,-6,-2],crossRight,crossLeft);
    let raised = (tick,label = "raised_load",extra = {}) => pose(tick,label,0,-3,[0,-2,0],highRight,highLeft,extra);
    let planted = (tick,label = "planted_hold") => pose(tick,label,0,-32,[0,-5,-2],plantRight,plantLeft);
    let spread = (tick,label = "open_recovery") => pose(tick,label,0,-16,[0,-4,-2],openRight,openLeft);
    let glide = (tick,label,extra = {}) => pose(tick,label,0,-28,[0,-5,-2],driveRight,driveLeft,extra);
    let airborne = (rotation,lift = 0) => ({airRotation:rotation,lift,rightFoot:[12,18,0],leftFoot:[-12,15,5],rightFootTilt:[25,0,0],leftFootTilt:[20,0,0]});
    if (name === "left_combo_bloodflame") return [
        ready(0), pose(4,"left_sword_draw",25,-7,[1,-3,0],lowRight,[[-30,64,7],[0.05,0.02,-1]]),
        pose(9,"left_thrust_load",34,-12,[2,-4,1],lowRight,[[-27,60,8],[0.03,-0.06,-1]]),
        pose(12,"left_thrust_commit",22,-19,[1,-4,-1],lowRight,[[-19,59,-8],[0.03,-0.06,-1]]),
        pose(15,"left_thrust_contact",8,-26,[0,-5,-3],lowRight,[[-4,57,-24],[0.03,-0.08,-1]]),
        pose(20,"left_thrust_hold",8,-22,[0,-4,-2],lowRight,[[-4,57,-24],[0.03,-0.08,-1]]),
        pose(26,"same_left_blade_lift",15,-12,[0,-3,-1],lowRight,[[-12,66,-20],[-0.7,0.08,-0.71]]),
        pose(31,"left_tear_load",22,-10,[0,-3,0],lowRight,[[-28,68,-12],[-0.9,0.12,-0.42]]),
        pose(35,"left_transverse_tear",-22,-17,[-1,-4,-2],lowRight,[[-4,59,-24],[0.1,0,-1]]),
        pose(39,"left_sweep_followthrough",-38,-12,[-1,-3,-1],lowRight,[[8,58,-15],[0.95,0,-0.31]]),
        pose(46,"withdraw_from_suspended_tear",-12,-7,[0,-2,0],lowRight,[[-19,53,-8],[-0.75,-0.05,-0.66]]),
        ready(53,"blade_clear_before_burst"), ready(55,"delayed_burst_no_new_swing"),ready(66)
    ];
    if (name === "lion_claw" || name === "lion_claw_double") {
        let followup = name === "lion_claw_double", contact = followup ? 26 : 32, launch = followup ? 7 : 13;
        return [followup ? planted(0,"followup_from_low_plant") : ready(0),
            followup ? crouch(4,"immediate_low_reload") : raised(6,"paired_lift"),crouch(launch-2),
            raised(launch,"takeoff",airborne([0,0,0],6)),raised(launch+4,"forward_tuck",airborne([85,0,0],14)),
            crouchedAir(launch+8,"inverted_somersault",[180,0,0],18),
            raised(contact-7,"unfold_toward_ground",airborne([270,0,0],12)),
            pose(contact-3,"paired_downward_commit",0,-20,[0,-4,-2],driveRight,driveLeft,{airRotation:[345,0,0],lift:4}),
            {...planted(contact,"landing_contact"),airRotation:[360,0,0]},
            {...planted(contact+4,"low_landing_buffer"),airRotation:[360,0,0]},
            {...spread(contact+10),airRotation:[360,0,0]},
            {...ready(contracts[name].duration),airRotation:[360,0,0]}];
    }
    function crouchedAir(tick,label,rotation,lift) { return {...crouch(tick,label),...airborne(rotation,lift)}; }
    if (name === "stomp") return [
        ready(0),pose(4,"left_leg_weight_shift",-7,-5,[-3,-2,0],openRight,lowLeft),
        pose(9,"right_knee_rise",-8,-3,[-3,-2,0],openRight,openLeft,{rightFoot:[12,19,-5]}),
        pose(14,"right_knee_hold",-8,-2,[-3,-1,0],openRight,openLeft,{rightFoot:[12,26,-8],rightFootTilt:[-12,0,0]}),
        pose(17,"foot_drives_down",-4,-10,[-1,-3,-1],openRight,openLeft,{rightFoot:[12,15,-9]}),
        pose(20,"right_foot_contact",2,-21,[1,-5,-2],openRight,openLeft),
        pose(23,"ground_recoil_no_sword_hit",2,-16,[1,-4,-1],openRight,openLeft),ready(32)
    ];
    if (name === "gravity_dive" || name === "spiral_assault") {
        let spin = name === "spiral_assault";
        return [ready(0),crouch(6,"swords_gather"),glide(12,"takeoff_load"),
            glide(16,"forward_axis_departure",airborne([35,0,0],6)),
            glide(22,"first_spiral_quarter",airborne([65,0,110],14)),
            glide(28,"inverted_spiral",airborne([70,0,230],14)),
            glide(33,"unroll_for_arrival",airborne([40,0,335],8)),
            {...glide(35,"spiral_sword_entry"),airRotation:[12,0,360],lift:spin?3:1},
            {...spread(38,spin?"travelling_low_cut":"sword_arrival_contact"),airRotation:[0,0,360]},
            {...planted(43,"ground_impact"),airRotation:[0,0,360]},
            {...planted(48,"landing_hold"),airRotation:[0,0,360]},
            {...spread(57),airRotation:[0,0,360]},
            {...ready(70,"settling"),airRotation:[0,0,360]}, {...ready(79),airRotation:[0,0,360]}];
    }
    if (name === "starcaller_cry") return [ready(0),crouch(7,"crossed_gravity_load"),
        pose(13,"crossed_pull_hold",0,-8,[0,-3,0],crossRight,crossLeft),
        pose(18,"pull_release_not_slam",0,-4,[0,-2,0],crossRight,crossLeft),
        pose(26,"hold_after_pull",0,-4,[0,-2,0],crossRight,crossLeft),
        pose(36,"open_shoulders_for_lift",0,-7,[0,-3,0],[[38,73,-1],[0.74,0.66,-0.12]],[[-38,73,-1],[-0.74,0.66,-0.12]]),
        raised(44),raised(51,"high_pause"),glide(55,"paired_downstroke"),planted(58,"slam_and_spikes_contact"),
        planted(66),spread(77),pose(91,"rise_after_spikes",0,-7,[0,-3,0],openRight,openLeft),ready(109)];
    if (name === "gravity_meteor") return [ready(0),raised(10,"gravity_sword_charge"),
        glide(17,"ground_approach"),planted(21,"swords_in_ground"),planted(29),
        pose(35,"upward_rock_lift",0,-8,[0,-3,0],highRight,highLeft,{lift:3}),
        raised(43,"rising_with_rocks",airborne([0,0,0],12)),raised(63,"suspended_rock_hold",airborne([0,0,0],18)),
        pose(78,"airborne_release_load",-36,-8,[0,-3,0],rightLoad,leftLoad,airborne([0,0,0],18)),
        pose(86,"release_turn",-18,-16,[0,-3,-1],driveRight,highLeft,airborne([0,0,0],18)),
        pose(90,"first_projectile_release",28,-22,[0,-4,-1],driveRight,driveLeft,airborne([0,0,0],18)),
        pose(104,"sustain_volley",30,-12,[0,-3,0],openRight,openLeft,airborne([0,0,0],16)),
        pose(118,"last_rock_release",18,-8,[0,-3,0],openRight,openLeft,airborne([0,0,0],12)),
        raised(128,"clones_after_volley",airborne([0,0,0],8)),spread(138,"touch_down"),ready(152)];
    if (name === "enhanced_earthheave") return [ready(0),raised(9,"asymmetric_high_load"),
        raised(18,"held_pre_slam"),glide(24,"paired_downward_path"),planted(28,"ground_plant_contact"),
        planted(34),planted(39,"withdrawal_compression"),
        pose(43,"upward_blade_path",0,-18,[0,-4,-1],[[23,57,-22],[0.15,0.5,-0.85]],[[-23,57,-22],[-0.15,0.5,-0.85]]),
        raised(47,"extraction_first_light_row"),raised(59,"hold_through_four_rows"),
        pose(69,"lowering_high_blades",0,-4,[0,-2,0],[[29,68,-3],[0.45,0.75,-0.48]],[[-29,68,-3],[-0.45,0.75,-0.48]]),ready(83)];
    if (name === "light_of_miquella") {
        let prayer = (tick,label,lift,lean = -3) => pose(tick,label,0,lean,[0,-2,0],
            [[40,68,-1],[0.98,0.06,-0.18]],[[-40,68,-1],[-0.98,0.06,-0.18]],{lift,rightFoot:[12,10,1],leftFoot:[-12,10,1],rightFootTilt:[15,0,0],leftFootTilt:[15,0,0]});
        return [ready(0),pose(12,"open_chest",0,-3,[0,-2,0],openRight,openLeft),prayer(25,"slow_ascent",1),
            prayer(40,"raised_prayer",10),prayer(70,"suspended_charge",15),prayer(102,"last_charge_hold",15),
            prayer(110,"main_flash",15,-1),prayer(124,"first_afterglow",12),prayer(131,"last_afterglow",7),spread(137,"landing_buffer"),ready(144)];
    }
    if (name === "ring_of_light") return [ready(0),pose(6,"right_casting_raise",-12,-4,[0,-2,0],rightLoad,lowLeft),
        pose(14,"right_sword_overhead",-16,-2,[0,-2,0],highRight,lowLeft),
        pose(22,"right_charge_hold",-16,-2,[0,-2,0],highRight,lowLeft),
        pose(24,"right_release_arc",3,-22,[0,-4.5,-2],[[20,64,-21],[0.35,0.12,-0.93]],lowLeft),
        pose(26,"right_ring_release",18,-23,[1,-5,-2],[[5,54,-23],[-0.06,-0.23,-0.97]],lowLeft),
        pose(30,"right_followthrough",30,-16,[1,-4,-1],[[-9,55,-15],[-0.95,-0.1,-0.3]],lowLeft),
        pose(38,"right_blade_returns",12,-8,[0,-3,0],[[20,52,-11],[0.55,-0.24,-0.8]],lowLeft),ready(48)];
    if (name === "lightspeed_slash") return [ready(0),crouch(7),raised(12,"vertical_departure",airborne([0,0,0],4)),
        raised(22,"high_sword_hover",airborne([0,0,0],24)),raised(35,"first_clone_body_holds",airborne([0,0,0],24)),
        raised(48,"second_clone_body_holds",airborne([0,0,0],24)),raised(61,"third_clone_body_holds",airborne([0,0,0],24)),
        pose(65,"body_descent_commit",0,-14,[0,-4,-1],driveRight,driveLeft,{lift:14,rightFoot:[12,13,-4],leftFoot:[-12,11,1]}),
        planted(69,"body_landing_contact"),planted(76,"low_landing_hold"),spread(85),ready(99)];
    if (name === "lightspeed_dash") return [ready(0),pose(10,"paired_side_gather",-30,-14,[-1,-3,0],rightLoad,crossLeft),
        glide(23,"low_charge"),glide(37,"held_forward_point"),glide(46,"first_clone_body_waits"),
        glide(58,"third_clone_body_waits"),glide(64,"fourth_clone_body_waits"),
        pose(68,"body_drives_forward",12,-36,[0,-7,-3],driveRight,driveLeft,{rightFoot:[12,11,-9],leftFoot:[-12,9,7]}),
        pose(73,"body_thrust_contact",18,-32,[0,-6,-4],driveRight,driveLeft),
        pose(77,"low_thrust_stop",18,-27,[0,-5,-3],driveRight,driveLeft),
        spread(80,"delayed_trail_body_recovering"),spread(89,"held_followthrough"),ready(103)];
    if (name === "lightspeed_side_dash") return [ready(0),crouch(6,"lateral_load"),
        pose(12,"side_jump",-30,-19,[-3,-3,0],rightLoad,lowLeft,airborne([0,0,-12],6)),
        pose(20,"side_jump_land",-25,-20,[-2,-4,0],rightLoad,lowLeft),
        pose(28,"side_attack_line_load",-38,-18,[-1,-4,0],rightLoad,lowLeft),
        pose(36,"first_clone_body_waits",-38,-18,[-1,-4,0],rightLoad,lowLeft),
        pose(52,"third_clone_body_waits",-38,-18,[-1,-4,0],rightLoad,lowLeft),
        pose(60,"right_sweep_commits",-18,-26,[0,-5,-2],[[30,67,-12],[0.8,0,-0.6]],lowLeft),
        pose(65,"right_body_sweep_contact",25,-28,[1,-5,-3],[[4,55,-23],[-0.07,-0.12,-0.99]],lowLeft),
        pose(69,"right_sweep_followthrough",40,-18,[1,-4,-2],[[-9,55,-15],[-0.95,-0.1,-0.3]],lowLeft),ready(82)];
    if (name === "promised_consort") {
        let nodes = [ready(0),raised(10,"distant_holy_charge"),
            pose(20,"right_opening_load",-34,-10,[-1,-3,0],rightLoad,lowLeft),
            pose(24,"right_opening_release",-12,-20,[0,-4,-2],[[29,69,-12],[0.75,0.2,-0.63]],leftLoad),
            pose(27,"right_opening_contact",25,-25,[1,-5,-2],[[4,55,-23],[-0.07,-0.12,-0.99]],leftLoad),
            pose(31,"right_followthrough",40,-17,[1,-4,-1],[[-9,55,-15],[-0.95,-0.1,-0.3]],leftLoad),
            pose(36,"left_opening_load",27,-11,[0,-3,0],rightLoad,leftLoad),
            pose(41,"left_opening_contact",-25,-25,[-1,-5,-2],rightLoad,[[-4,57,-24],[0.07,-0.12,-0.99]]),
            pose(45,"left_followthrough",-40,-16,[-1,-4,-1],rightLoad,[[8,58,-15],[0.95,0,-0.31]]),
            raised(49,"first_turn_reload"),glide(54,"first_paired_turn_contact"),spread(58,"first_turn_followthrough"),
            raised(63,"second_turn_reload"),glide(70,"second_paired_turn_contact"),spread(75,"second_turn_followthrough"),
            crouch(83,"finisher_jump_compression"),raised(90,"finisher_takeoff",airborne([0,0,0],12)),
            raised(100,"finisher_high_hold",airborne([0,0,0],24)),glide(107,"finisher_downward_commit",{lift:10}),
            planted(111,"body_finisher_contact"),planted(115,"first_return_clone"),spread(120,"second_return_clone"),
            spread(127,"holy_ring_after_body"),spread(137,"low_recovery_hold"),ready(151)];
        let turns = new Map([[45,0],[49,100],[54,210],[58,360],[63,470],[70,570],[75,720]]);
        let facing = 0;
        for (let entry of nodes) { if (turns.has(entry.tick)) facing = turns.get(entry.tick); entry.facing = facing; }
        return nodes;
    }
    if (name === "consort_meteor") return [ready(0),raised(10,"sword_charge"),raised(24,"high_charge_hold"),
        crouch(34,"departure_compression"),spread(39,"swords_open_for_departure"),
        raised(43,"depart_arena",airborne([0,0,0],8)),raised(60,"offscreen_ascent",airborne([0,0,0],20)),
        raised(150,"offscreen_wait",airborne([0,0,0],20)),raised(183,"target_locks_offscreen",airborne([0,0,0],20)),
        glide(198,"reentry_load",airborne([30,0,0],16)),glide(202,"reentry_warning",airborne([35,0,0],12)),
        glide(208,"reentry_approach",{airRotation:[15,0,0],lift:5}),planted(212,"meteor_ground_impact"),
        planted(214,"aftershock"),planted(226,"landing_buffer"),spread(245),ready(280)];
    throw new Error("Unknown batch motion: " + name);
}

module.exports = {contracts, profile};