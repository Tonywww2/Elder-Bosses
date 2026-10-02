"""Build both Configured languages and bilingual TOML from the registered native spec.

Refresh registered_defaults.json with ConfigPresentationCheck --export after schema changes.
This script never reads or writes a player's config or world.
"""
from pathlib import Path
import json
import re
import tomllib

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
ASSETS = ROOT / "src/main/resources/assets/elder_bosses"
PREFIX = "config.elder_bosses."


def catalog(name):
    return {parts[0]: parts[1:] for line in (HERE / name).read_text(encoding="utf-8").splitlines()
            if line and not line.startswith("#") for parts in [line.split("|")]}


NAMES = catalog("field_names.tsv")
HELP = catalog("field_help.tsv")
GROUP_NAMES = {
    "equipment": ("装备与饰品", "Equipment and curios"),
    "weapons": ("武器属性", "Weapon attributes"),
    "curios": ("Curios功能槽加成", "Functional Curios slot bonuses"),
    "consecrated_prosthetic_blade": ("奉献义手刀", "Consecrated prosthetic blade"),
    "young_lion_greatsword": ("年轻狮子大剑", "Young lion greatsword"),
    "golden_needle": ("金针", "Golden needle"),
    "circlet_of_fading_light": ("渐隐光冠", "Circlet of fading light"),
    "boss_music": ("Boss音乐（客户端）", "Boss music (client)"),
    "indicators": ("地面技能范围指示器（客户端）", "Ground skill indicators (client)"),
    "skill_vfx": ("技能粒子预算（客户端）", "Skill particle budget (client)"),
    "malenia": ("玛莲妮亚／腐败女神", "Malenia / Goddess of Rot"),
    "promised_consort": ("约定之王拉塔恩", "Radahn, Promised Consort"),
    "general": ("基础属性", "Base attributes"), "debug": ("调试输出", "Debug output"),
    "multiplayer": ("多人缩放与轮换目标", "Multiplayer scaling and target rotation"),
    "targeting": ("锁定与远程判断", "Targeting and ranged detection"),
    "arena": ("竞技场与位置", "Arena and positioning"),
    "stagger": ("架势与失衡", "Stagger"), "instant_guard": ("瞬防与提示", "Instant guard and cues"),
    "healing": ("命中回血", "Healing on hit"), "resistance": ("入伤抗性", "Incoming damage resistance"),
    "source_multiplier": ("入伤来源倍率", "Incoming damage source multipliers"),
    "phase_one": ("一阶段", "Phase one"), "phase_two": ("二阶段", "Phase two"),
    "scarlet_rot": ("猩红腐败状态", "Scarlet rot status"),
    "phase_two_rot": ("二阶段腐败累积", "Phase two rot buildup"),
    "selector": ("选招与连段", "Action selection and combos"),
    "phase_transition": ("转阶段保护与演出", "Phase transition protection and presentation"),
    "performance": ("运行预算", "Runtime budgets"), "dialogue": ("台词与字幕", "Dialogue and subtitles"),
    "nonverbal_audio": ("动作与非语言音效", "Action and nonverbal audio"),
    "skills": ("技能参数", "Skill parameters"), "components": ("组成段时间", "Component timing"),
    "encounter": ("参战、脱战与恢复", "Joining, disengagement and recovery"),
    "incoming_damage": ("受伤来源资格（原版结算）", "Damage eligibility (vanilla resolution)"),
    "presentation": ("血条与架势显示对象", "Boss bar and stagger audience"),
    "burst": ("连续释放节奏", "Burst cadence"), "meteor": ("大荒星陨触发保护", "Consort meteor protection"),
    "light_echo": ("圣光余波", "Holy light echoes"), "visuals": ("重力、陨石与圣光外观", "Gravity, meteor and holy visuals"),
    "hit_detection": ("命中判定模式", "Hit detection mode"), "grab": ("投技与重复魅惑秒杀", "Grab and repeated charm instant kill"),
    "ground_areas": ("各攻击段的地面范围", "Ground area per attack segment"),
    "entries": ("选招", "Action selection"),
    "animations": ("动画段", "Animation segments"),
    "attacks": ("攻击", "Attacks"),
    "projectiles": ("弹丸", "Projectiles"),
    "effects": ("效果", "Effects"),
    "bleed": ("出血累积与脉冲", "Bleed buildup and pulses"), "terrain": ("贴地与台阶适配", "Grounding and step adaptation"),
    "spacing": ("近战站位与间距", "Melee positioning and spacing"),
}
MOVES = {
    "swing_combo": ("横斩连段", "Swing combo"), "vertical_slash": ("竖斩", "Vertical slash"),
    "cross_slash": ("快斩／十字斩", "Quick cross slash"), "stomp": ("踏地", "Stomp"),
    "lion_claw": ("狮子斩", "Lion's claw"), "gravity_pull": ("重力牵引", "Gravity pull"),
    "uppercut_slam": ("上挑掀地", "Uppercut slam"), "vertical_followup": ("竖斩后续", "Vertical follow-up"),
    "bloodflame": ("血焰", "Bloodflame"), "gravity_dive": ("重力突进", "Gravity dive"),
    "cross_swing": ("横扫", "Cross swing"), "clone_slam": ("分身下砸", "Clone slam"),
    "gravity_meteor": ("重力陨石", "Gravity meteor"), "light_of_miquella": ("米凯拉之光", "Light of Miquella"),
    "ring_of_light": ("光环", "Ring of light"), "holy_burst": ("圣光爆发", "Holy burst"),
    "miquella_grab": ("米凯拉抓取", "Miquella grab"), "forward_dash": ("正向光速突进", "Forward light dash"),
    "side_dash": ("侧向光速突进", "Side light dash"), "consort_combo": ("约定之王连段", "Consort combo"),
    "consort_meteor": ("大荒星陨", "Consort meteor"), "charm_death": ("重复魅惑死亡", "Repeated charm death"),
    "single_slash": ("单斩", "Single slash"), "double_slash": ("双斩", "Double slash"),
    "rapid_slashes": ("连续快斩", "Rapid slashes"), "running_slash": ("奔跑斩", "Running slash"),
    "upward_combo": ("上挑连段", "Upward combo"), "kick": ("踢击", "Kick"), "thrust": ("突刺", "Thrust"),
    "grab_impale": ("抓取穿刺", "Grab impale"), "retreat_slash": ("后退斩", "Retreat slash"),
    "waterfowl_dance": ("水鸟乱舞", "Waterfowl dance"), "scarlet_aeonia": ("猩红艾奥尼亚", "Scarlet aeonia"),
    "scarlet_plunge": ("猩红俯冲", "Scarlet plunge"), "flying_slash": ("飞身斩", "Flying slash"),
    "scarlet_phantoms": ("猩红分身", "Scarlet phantoms"), "winged_sweep": ("展翼横扫", "Winged sweep"),
}
ENUM_ZH = {
    "arena_player_and_owned": "场内玩家及其所属实体", "player_only": "仅玩家", "immune": "免疫", "normal": "正常处理",
    "register_only": "只登记参战", "register_then_damage": "先登记再结算伤害", "damage_then_register": "先伤害再登记",
    "damage_without_registration": "结算伤害但不登记", "ignore": "忽略", "allow_without_scaling": "允许但不增加多人缩放",
    "reject_damage": "拒绝伤害", "never_reopen": "退出后不释放名额", "reopen_on_exit": "退出后释放名额",
    "cumulative_unique": "累计不同玩家人数", "current_active": "当前活跃人数", "high_water_mark": "历史最高同时人数",
    "cancel_and_freeze": "中断动作后冻结", "finish_action_then_freeze": "完成当前动作后冻结", "continue": "继续行动",
    "freeze": "冻结冷却", "elapse": "冷却继续流逝", "clear": "清除", "resume": "恢复战斗",
    "reset_dormant": "重置为待战", "remove": "移除Boss", "force_load": "保持区块加载",
    "allow_combat": "允许战斗", "dormant": "保持待战", "allow": "允许", "reject_new": "拒绝新战斗", "replace_old": "替换旧战斗",
    "participants_only": "仅参战玩家", "all_non_immune": "全部符合非免疫条件的来源",
    "nearest_legal_path_then_center": "优先最近合法路径，再返回场心", "center": "返回场心", "cancel": "取消",
    "players_only": "仅玩家", "living_when_no_players": "无玩家时选择其他生物", "all_living": "全部生物",
    "players_and_owned": "玩家及其所属实体", "arena": "场内玩家", "participants": "参战玩家", "follow_range": "追踪范围内玩家",
    "boss_bar": "血条可见玩家", "tracking": "追踪此实体的玩家", "persist": "保留", "stomp": "踏地",
    "continue_combo": "继续连段", "weighted": "按权重选择", "current_action": "当前动作", "same_target": "同一目标", "encounter": "整场战斗",
    "truncate": "伤害截断到门槛", "full_damage": "完整伤害", "leave_one_health": "至少保留1生命", "invulnerable": "无敌",
    "cooldown_forced": "冷却结束后强制释放", "once": "只释放一次", "empty_geo_entity": "使用分身实体渲染", "paths_only": "只显示轨迹",
}


def human(key):
    return re.sub(r"([a-z])([A-Z])", r"\1 \2", key).replace("_", " ").capitalize()


source_dir = ASSETS / "boss/promised_consort"
source = json.loads((source_dir / "source_config_defaults.json").read_text(encoding="utf-8"))
contracts = json.loads((source_dir / "source_contracts.json").read_text(encoding="utf-8"))
runtime = json.loads((source_dir / "source_runtime_contracts.json").read_text(encoding="utf-8"))
TAE_NAMES = {}
for entry in contracts["original_ai_entries"]:
    match = re.search(r"Act(\d+)$", entry["function"])
    if match and str(int(match[1])) in source["entries"]:
        move = source["entries"][str(int(match[1]))]
        for tae in entry["source_ids"]:
            TAE_NAMES.setdefault(tae, MOVES[move])
for tae in [3000, 3001, 3002, 3003]: TAE_NAMES[tae] = MOVES["swing_combo"]
for tae in [3010, 3011]: TAE_NAMES[tae] = MOVES["lion_claw"]
for tae in [3015, 3016]: TAE_NAMES[tae] = MOVES["gravity_dive"]
for tae in [3025, 3028, 3030, 20005, 20006]: TAE_NAMES[tae] = MOVES["clone_slam"]
for tae in [3033, 3034, 3035, 3036]: TAE_NAMES[tae] = MOVES["consort_combo"]
for tae in [3021, 3024, 20007, 20008, 20009, 20013]: TAE_NAMES[tae] = MOVES["consort_meteor"]
for tae in [20002, 20003]: TAE_NAMES[tae] = MOVES["forward_dash"]
TAE_NAMES[20004] = MOVES["side_dash"]
for tae in [3020, 4100]: TAE_NAMES[tae] = MOVES["miquella_grab"]
TAE_NAMES[20012] = MOVES["charm_death"]
TAE_NAMES.update({20: ("战斗待机", "Battle idle"), 2000: ("前行", "Walk forward"),
                  2002: ("侧移", "Strafe"), 2003: ("侧移", "Strafe"), 2100: ("跑动", "Run")})

# Associate parameter IDs with source animation events/launches, without inventing a skill assignment.
REFERENCES = {}
for animation in contracts["animations"]:
    for event in animation["events"]:
        ref = event.get("reference_id", -1)
        if ref > 0: REFERENCES.setdefault(ref, set()).add(animation["tae_id"])
for launch in runtime["launches"]:
    REFERENCES.setdefault(launch["bullet_id"], set()).add(launch["tae_id"])
for bullet in runtime["bullets"]:
    attack = bullet["cells"].get("atkId_Bullet", -1)
    if attack > 0: REFERENCES.setdefault(attack, set()).update(REFERENCES.get(bullet["id"], set()))


def parameter_name(kind, identity):
    return (f"a{identity}", f"a{identity}")


def group_name(path):
    parts = path.split("."); key = parts[-1]
    if len(parts)>1 and parts[-2]=="entries": return (key,key)
    if key in GROUP_NAMES: return GROUP_NAMES[key]
    if key in MOVES: return MOVES[key]
    if key in NAMES:
        labels = NAMES[key]
        return labels[0], labels[1] if len(labels) > 1 else human(key)
    if re.fullmatch(r"stage\d+", key):
        number = key[5:]
        if ".distance_bands." in path:
            return (f"距离档位{number}", f"Distance band {number}")
        return (f"第{number}段", f"Stage {number}")
    if key.startswith("act"):
        code = source["entries"][key[3:]]
        return (code, code)
    if parts[-2] == "animations":
        return (key, key)
    if parts[-2] == "ground_areas":
        return (key, key)
    if parts[-2] in ["attacks", "projectiles", "effects"]: return parameter_name(parts[-2], int(key[1:]))
    raise ValueError(f"Untranslated group {path}")


def timing(path):
    if path.startswith("equipment."):
        return ("生效：重启游戏／服务器；装备值由服务端同步。", "Applies after game/server restart; equipment values are synchronized by the server.")
    if path.split(".")[0] in ["boss_music", "indicators", "skill_vfx"]:
        return ("生效：本地客户端配置重载后。", "Applies locally after client config reload.")
    if re.fullmatch(r"(malenia|promised_consort)\.general\.(base_health|phase_one_health|attack_damage|movement_speed|follow_range|knockback_resistance)", path):
        return ("生效：重启游戏／服务器后，作为新生成实体的基础属性。", "Applies after game/server restart as the base attribute for newly spawned entities.")
    if path.startswith("malenia.arena."):
        return ("此玛莲妮亚竞技场参数当前为预留项。", "This Malenia arena parameter is currently reserved.")
    if ".debug." in path:
        return ("用途：调试信息输出。", "Purpose: debug information output.")
    return ("生效：下一场战斗快照；进行中的战斗保留原值。", "Applies to the next encounter snapshot; an ongoing fight retains its captured values.")


def group_help(path):
    notes = {
        "equipment": ("修改后重启。", "Restart after changes."),
        "promised_consort": ("战斗设置在开战时生效。", "Combat settings apply when an encounter starts."),
        "malenia": ("战斗设置在开战时生效。", "Combat settings apply when an encounter starts."),
        "incoming_damage": ("伤害使用原版结算。", "Damage uses vanilla resolution."),
        "skills": ("时间用tick，距离用格。", "Time in ticks, distance in blocks."),
        "animations": ("动画段代号。20tick为1秒。", "Animation segment codes. 20 ticks = 1 second."),
        "entries": ("技能代号。控制选招和冷却。", "Skill codes. Selection and cooldowns."),
        "attacks": ("攻击代号。伤害为固定值加攻击力乘总权重乘系数除100。", "Attack codes. Damage = flat + attack damage × total channel weight / 100 × attack ratio."),
        "projectiles": ("弹丸代号。时间用秒。", "Projectile codes. Time in seconds."),
        "ground_areas": ("命中范围与地面预警共用。", "Shared by collision and ground warnings."),
        "distance_bands": ("最后一档距离为-1时不限距离。", "A final distance of -1 has no limit."),
    }
    return notes.get(path, notes.get(path.rsplit(".",1)[-1], ("", "")))


def unit(key, path):
    if key.endswith("ticks") or key.endswith("tick") or key in ["start_tick", "end_tick"]:
        return ("单位：游戏tick，20tick＝1秒。", "Unit: game ticks; 20 ticks = 1 second.")
    if key in ["life", "shootInterval", "accelTime", "spreadTime", "intervalCreateWaitTime", "intervalCreateTimeMin", "intervalCreateTimeMax"]:
        return ("单位：秒。", "Unit: seconds.")
    if key in ["initVellocity", "maxVellocity", "minVellocity"]:
        return ("单位：格／秒；保留原参数拼写。", "Unit: blocks/second; source parameter spelling is retained.")
    if key in ["accelInRange", "accelOutRange", "gravityInRange", "gravityOutRange"]:
        return ("单位：格／秒²。", "Unit: blocks/second².")
    if key == "homingAngle": return ("单位：度／秒。", "Unit: degrees/second.")
    if key in ["angle", "yaw", "arc_degrees", "shootAngle", "shootAngleXZ", "shootAngleInterval", "shootAngleXInterval"]:
        return ("单位：度。", "Unit: degrees.")
    if any(t in key for t in ["ratio", "chance", "fraction", "reduction"]) and key != "attack_ratio":
        return ("比例：0.10＝10%。", "Ratio: 0.10 = 10%.")
    if "multiplier" in key: return ("倍率：1为原值，2为两倍。", "Multiplier: 1 preserves the base value; 2 doubles it.")
    if key in ["forward", "width", "length", "height", "dist", "range", "distance", "radius", "surface_offset", "max_step_up", "ground_search_depth", "foot_clearance", "max_ground_drop_per_tick", "hitRadius", "hitRadiusMax", "homingBeginDist", "hit0_Radius", "hit1_Radius", "hit2_Radius", "hit3_Radius"] or key.endswith(("_distance", "_radius", "_range", "_width", "_height", "_extension")):
        return ("单位：格。", "Unit: blocks.")
    return None


choices = {}
text = (ROOT / "src/main/java/com/tonywww/elder_bosses/platforms/config/PromisedConsortConfigValues.java").read_text(encoding="utf-8")
for match in re.finditer(r'putString\("([^"]+)", builder\.choice\(([^\n]+)\)\);', text):
    choices["promised_consort." + match[1]] = re.findall(r'"([^"]+)"', match[2])[2:]


def concise(text):
    text = re.sub(r"（[^）]*）|\([^)]*\)", "", text)
    text = text.replace("原作", "").replace("原参数", "基准参数").replace("Source ", "").replace("source ", "")
    text = text.replace("原BulletParam", "弹丸").replace("BulletParam", "Projectile").replace("原SpEffect", "效果").replace("SpEffect", "Effect")
    return text.strip()


def value_row(row):
    path=row["path"]; key=path.rsplit(".",1)[-1]
    labels=NAMES[key]; zh=concise(labels[0]); en=concise(labels[1] if len(labels)>1 else human(key))
    help_pair=HELP.get(key, ("", ""))
    zh_help, en_help = map(concise, help_pair)
    if path == "promised_consort.general.meteor_health_ratio":
        zh_help = "按总体剩余生命比例触发，默认85%；与二阶段门槛分别计算。"
        en_help = "Remaining total health for the first meteor, default 85%, independent of the phase-two threshold."
    if path == "promised_consort.skills.entries.consort_meteor.cooldown_ticks":
        zh_help = "首次星陨不受此值限制；0关闭后续重复，正数从收招结束开始计时。"
        en_help = "The first meteor is unaffected; 0 disables repeats, positive values count from recovery completion."
    # Units belong in labels; Configured already supplies defaults and bounds.
    units=unit(key,path)
    if units:
        uzh, uen=units
        if "tick" in uzh: suffix=("tick", "ticks")
        elif "格／秒²" in uzh: suffix=("格/秒²", "blocks/s²")
        elif "格／秒" in uzh: suffix=("格/秒", "blocks/s")
        elif "度／秒" in uzh: suffix=("度/秒", "degrees/s")
        elif "单位：秒" in uzh: suffix=("秒", "seconds")
        elif "单位：度" in uzh: suffix=("度", "degrees")
        elif "单位：格" in uzh: suffix=("格", "blocks")
        else: suffix=None
        if suffix: zh+=" · "+suffix[0]; en+=" · "+suffix[1]
    if key in ["flat", "attack_ratio"]:
        zh_help, en_help = (("固定加值。", "Flat addition.") if key=="flat" else ("攻击力系数。", "Attack damage coefficient."))
    if key in ["atkPhys","atkMag","atkFire","atkThun","atkDark"]:
        kind={"atkPhys":("物理","Physical"),"atkMag":("魔法","Magic"),"atkFire":("火焰","Fire"),"atkThun":("雷电","Lightning"),"atkDark":("圣属性","Holy")}[key]
        zh=kind[0]+"权重";en=kind[1]+" weight"
        zh_help,en_help=("雷电合入魔法伤害。", "Lightning joins magic damage.") if key=="atkThun" else ("", "")
    if ".resistance." in path and path.startswith("malenia."):
        zh_help,en_help="入伤倍率：1正常，0免疫。", "Incoming multiplier: 1 normal, 0 immune."
    if path in choices:
        options=choices[path]
        zh_help="；".join(f"{v}: {ENUM_ZH[v]}" for v in options)
        en_help="; ".join(options)
    # Keep semantic notes short; lifecycle and ranges are documented once.
    zh_help=zh_help.split("。",1)[0]+"。" if zh_help else ""
    en_help=en_help.split(". ",1)[0] if en_help else ""
    return dict(zh_name=zh,en_name=en,zh_help=zh_help,en_help=en_help)


def fixed_value(row):
    path=row["path"]; key=path.rsplit(".",1)[-1]
    if path in ["promised_consort.meteor.damage_gate", "promised_consort.meteor.damage_gate_mode"]: return True
    if key.endswith("_tag") or key in ["cue_sound","gravity_projectile_block"]: return True
    if path.startswith("malenia.arena."): return True
    if path.startswith("promised_consort.selector."): return True
    if path.startswith("promised_consort.nonverbal_audio.") and key in ["intro_roar_tick","victory_roar_delay_ticks","hurt_cooldown_ticks"]: return True
    if ".ranged_counter." in path:
        # Removed project-only selector/history knobs; retain live detection and cooldowns.
        return key not in ["enabled","exit_distance","global_cooldown_ticks","defense_shared_cooldown_ticks","accept_owned_projectile_entity","damage_type_tags","additional_damage_type_ids","additional_damage_type_tags","excluded_damage_type_ids","excluded_damage_type_tags"]
    if not path.startswith("promised_consort.skills."): return False
    if ".skills.effects." in path: return True  # State markers are internal, not balance settings.
    match=re.search(r"\.animations\.a(\d+)\.",path)
    if match:
        if key=="targeted_landing" and int(match[1]) in [3028,3031,3032]: return True
        clip=next(a for a in contracts["animations"] if a["tae_id"]==int(match[1]))
        events=clip["events"]
        strikes=any(e["type"]==1 for e in events)
        launches=any(e["type"]==2 for e in events)
        targeted=strikes and any(e["type"]==760 for e in events)
        if key in ["targeted_landing","target_standoff","target_lock_lead_ticks"] and not targeted: return True
        if key=="hyper_armor_active" and not any(e["type"]==0 and e.get("fields",{}).get("Jump Table ID")==24 for e in events): return True
        if not strikes and not launches and int(match[1]) not in [20011,4100,8700]: return True
    return key=="approach_guard_chance"


def toml_value(value):
    if isinstance(value, dict): return "{ " + ", ".join(f"{k} = {toml_value(v)}" for k, v in value.items()) + " }"
    if isinstance(value, list): return "[" + ", ".join(toml_value(v) for v in value) + "]"
    return json.dumps(value, ensure_ascii=False, allow_nan=False)


def main():
    rows = json.loads((HERE / "registered_defaults.json").read_text(encoding="utf-8"))
    fixed_file=HERE / "fixed_defaults.json"
    all_rows=rows+(json.loads(fixed_file.read_text(encoding="utf-8")) if fixed_file.exists() else [])
    for row in all_rows:
        match=re.search(r"\.skills\.entries\.act(\d+)\.",row["path"])
        if match: row["path"]=row["path"].replace(".entries.act"+match[1]+".",".entries."+source["entries"][match[1]]+".")
    rows=[row for row in all_rows if not fixed_value(row)]
    fixed=[row for row in all_rows if fixed_value(row)]
    (HERE / "registered_defaults.json").write_text(json.dumps(rows,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    fixed_file.write_text(json.dumps(fixed,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    groups = {}; values = {}; sections = {}
    for row in rows:
        path = row["path"]; parts = path.split(".")
        for depth in range(1, len(parts)):
            group = ".".join(parts[:depth])
            if group not in groups:
                zh, en = map(concise, group_name(group)); zh_help, en_help = group_help(group)
                groups[group] = dict(zh_name=zh, en_name=en, zh_help=zh_help, en_help=en_help)
        values[path] = {**value_row(row), 'default':row['default']}
        sections.setdefault(path.rsplit(".", 1)[0], []).append(row)
    output = ASSETS / "config/presentation.json"; output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(dict(groups=groups, values=values, fixed_values={r["path"]:r["default"] for r in fixed}), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for locale, lang in [("zh_cn", "zh"), ("en_us", "en")]:
        p = ASSETS / f"lang/{locale}.json"; data = json.loads(p.read_text(encoding="utf-8"))
        data = {k: v for k, v in data.items() if not k.startswith(PREFIX)}
        for path, row in {**groups, **values}.items():
            data[PREFIX + path] = row[lang + "_name"]
            data[PREFIX + path + ".tooltip"] = row[lang + "_help"]
        p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# Elder Bosses / 主配置", "# config/elder_bosses-common.toml",
             "# 20tick = 1秒 / second. 距离 / distance: 格 / blocks.",
             "# 装备和基础属性需重启；战斗设置在下一场生效。",
             "# Restart for equipment and base attributes; combat settings apply to the next encounter.", ""]
    for section, entries in sections.items():
        group=groups[section]
        lines += ["# "+group["zh_name"]+" / "+group["en_name"], "["+section+"]"]
        for row in entries:
            meta=values[row["path"]]
            comment=meta["zh_name"]+" / "+meta["en_name"]
            if meta["zh_help"]: comment+=" — "+meta["zh_help"]+" / "+meta["en_help"]
            lines += ["# "+comment, row["path"].rsplit(".",1)[-1]+" = "+toml_value(row["default"])]
        lines.append("")
    example = ROOT / "docs/config/elder-bosses-common.example.toml"
    example.write_text("\n".join(lines), encoding="utf-8")
    parsed = tomllib.loads(example.read_text(encoding="utf-8"))
    for row in rows:
        actual = parsed
        for key in row["path"].split("."): actual = actual[key]
        assert actual == row["default"], row["path"]
    print(f"Generated {len(values)} bilingual fields and {len(groups)} translated groups; TOML defaults match the native spec.")


if __name__ == "__main__": main()
