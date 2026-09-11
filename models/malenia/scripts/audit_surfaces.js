(function () {
    if (!Project || Project.name !== "malenia") throw new Error("Open the malenia project first.");
    let texture = Texture.all[0];
    if (!texture?.img.complete || !texture.img.naturalWidth) throw new Error("Wait for the atlas image to load.");
    let canvas = document.createElement("canvas");
    canvas.width = texture.img.naturalWidth;
    canvas.height = texture.img.naturalHeight;
    let paint = canvas.getContext("2d", {willReadFrequently: true});
    paint.drawImage(texture.img, 0, 0);
    let pixels = paint.getImageData(0, 0, canvas.width, canvas.height).data;
    let stageOne = new Set(["helm", "armor_torso", "armor_shoulder_l", "armor_shoulder_r", "armor_waist", "cape_01", "skirt_front", "skirt_back", "skirt_l", "skirt_r"]);
    let stageTwo = new Set(["wing_root_l", "wing_root_r", "phase_two_body", "phase_two_hair"]);
    let samples = [
        ["idle_phase_one", 0, 1], ["idle_phase_two", 20, 2], ["single_slash", 7, 1],
        ["single_slash", 10, 1], ["single_slash", 12, 1], ["double_slash", 29, 1],
        ["rapid_slashes", 16, 1], ["thrust", 24, 1], ["kick", 10, 1],
        ["grab_impale", 24, 1], ["grab_impale", 44, 1], ["waterfowl_dance", 26, 1],
        ["waterfowl_dance", 38, 1], ["waterfowl_dance", 60, 2], ["upward_combo", 28, 1],
        ["scarlet_plunge", 26, 2], ["flying_slash", 22, 2], ["winged_sweep", 20, 2],
        ["scarlet_aeonia", 74, 2], ["transition", 108, 2], ["stunned", 24, 2],
        ["walk", 0, 1], ["walk", 8, 1], ["walk", 16, 1], ["walk", 24, 1], ["walk", 31, 1],
        ["walk_back", 9, 1], ["walk_back", 27, 1], ["strafe_left", 9, 1], ["strafe_right", 9, 1],
        ["run", 5, 1], ["run", 15, 1]
    ];
    let tolerance = 0.006;
    let previousClip = Animation.selected;
    let previousTime = Timeline.time;
    let ray = new THREE.Raycaster();
    let results = new Map();
    let frames = [];
    function alpha(uv) {
        let horizontal = Math.max(0, Math.min(canvas.width - 1, Math.floor(uv.x * canvas.width)));
        let vertical = Math.max(0, Math.min(canvas.height - 1, Math.floor((1 - uv.y) * canvas.height)));
        return pixels[(vertical * canvas.width + horizontal) * 4 + 3] > 127;
    }
    function visible(cube) {
        for (let ancestor = cube; ancestor && ancestor !== "root"; ancestor = ancestor.parent) {
            if (!ancestor.visibility || ancestor.mesh && (!ancestor.mesh.visible || Math.abs(ancestor.mesh.scale.x * ancestor.mesh.scale.y * ancestor.mesh.scale.z) < 0.000001)) return false;
        }
        return true;
    }
    function area(polygon) {
        let value = 0;
        for (let index = 0; index < polygon.length; index++) {
            let next = polygon[(index + 1) % polygon.length];
            value += polygon[index].x * next.y - polygon[index].y * next.x;
        }
        return value / 2;
    }
    function clip(subject, boundary) {
        let result = subject;
        for (let index = 0; index < boundary.length; index++) {
            let start = boundary[index];
            let end = boundary[(index + 1) % boundary.length];
            let direction = end.clone().sub(start);
            let input = result;
            result = [];
            if (!input.length) break;
            let previous = input[input.length - 1];
            let previousDistance = direction.x * (previous.y - start.y) - direction.y * (previous.x - start.x);
            for (let point of input) {
                let distance = direction.x * (point.y - start.y) - direction.y * (point.x - start.x);
                if ((distance >= 0) !== (previousDistance >= 0)) {
                    result.push(previous.clone().lerp(point, previousDistance / (previousDistance - distance)));
                }
                if (distance >= 0) result.push(point);
                previous = point;
                previousDistance = distance;
            }
        }
        return result;
    }
    function surfaceAlpha(face, point) {
        let offset = point.clone().sub(face.points[0]);
        let horizontal = face.points[1].clone().sub(face.points[0]);
        let vertical = face.points[3].clone().sub(face.points[0]);
        let across = offset.dot(horizontal) / horizontal.lengthSq();
        let down = offset.dot(vertical) / vertical.lengthSq();
        let uv = face.uvs[0].clone().addScaledVector(face.uvs[1].clone().sub(face.uvs[0]), across)
            .addScaledVector(face.uvs[3].clone().sub(face.uvs[0]), down);
        return alpha(uv);
    }
    Modes.options.animate.select();
    for (let [clipName, tick, phase] of samples) {
        Animation.all.find(animation => animation.name === "animation.malenia." + clipName).select();
        Timeline.setTime(tick / 20);
        Animator.preview();
        for (let group of Group.all) {
            group.mesh.visible = true;
            if (phase === 2 && stageOne.has(group.name) || phase === 1 && stageTwo.has(group.name)) group.mesh.visible = false;
            if (group.name === "aeonia_core" && !["scarlet_aeonia", "transition"].includes(clipName)) group.mesh.visible = false;
        }
        let active = Cube.all.filter(visible);
        let meshes = active.map(cube => cube.mesh);
        let surfaces = [];
        for (let cube of active) {
            cube.mesh.updateWorldMatrix(true, false);
            let geometry = cube.mesh.geometry;
            let normalMatrix = new THREE.Matrix3().getNormalMatrix(cube.mesh.matrixWorld);
            for (let [index, direction] of Canvas.face_order.entries()) {
                if (!cube.faces[direction].texture) continue;
                let vertices = [0, 1, 3, 2].map(offset => index * 4 + offset);
                let points = vertices.map(vertex => new THREE.Vector3().fromBufferAttribute(geometry.attributes.position, vertex).applyMatrix4(cube.mesh.matrixWorld));
                let normal = new THREE.Vector3().fromBufferAttribute(geometry.attributes.normal, index * 4).applyMatrix3(normalMatrix).normalize();
                let uvs = vertices.map(vertex => new THREE.Vector2().fromBufferAttribute(geometry.attributes.uv, vertex));
                surfaces.push({cube, direction, points, normal, uvs, bounds: new THREE.Box3().setFromPoints(points).expandByScalar(tolerance)});
            }
        }
        let conflicts = 0;
        for (let firstIndex = 0; firstIndex < surfaces.length; firstIndex++) {
            let first = surfaces[firstIndex];
            for (let secondIndex = firstIndex + 1; secondIndex < surfaces.length; secondIndex++) {
                let second = surfaces[secondIndex];
                if (first.cube === second.cube || !first.bounds.intersectsBox(second.bounds)) continue;
                if (first.normal.dot(second.normal) < 0.999999) continue;
                let separation = Math.abs(first.normal.dot(second.points[0].clone().sub(first.points[0])));
                if (separation > tolerance) continue;
                let tangent = first.points[1].clone().sub(first.points[0]).normalize();
                let bitangent = first.normal.clone().cross(tangent).normalize();
                function project(points) {
                    let polygon = points.map(point => {
                        let offset = point.clone().sub(first.points[0]);
                        return new THREE.Vector2(offset.dot(tangent), offset.dot(bitangent));
                    });
                    if (area(polygon) < 0) polygon.reverse();
                    return polygon;
                }
                let overlap = clip(project(first.points), project(second.points));
                if (overlap.length < 3 || Math.abs(area(overlap)) < 0.08) continue;
                let center = overlap.reduce((sum, point) => sum.add(point), new THREE.Vector2()).divideScalar(overlap.length);
                let probes = [center, ...overlap.map(point => point.clone().lerp(center, 0.25))];
                let exposed = false;
                let opaque = false;
                for (let probe of probes) {
                    let point = first.points[0].clone().addScaledVector(tangent, probe.x).addScaledVector(bitangent, probe.y);
                    if (!surfaceAlpha(first, point) || !surfaceAlpha(second, point)) continue;
                    opaque = true;
                    ray.set(point.clone().addScaledVector(first.normal, 0.02), first.normal);
                    let occluded = ray.intersectObjects(meshes, false).some(hit => hit.object !== first.cube.mesh
                        && hit.object !== second.cube.mesh && hit.distance > 0.01 && hit.uv && alpha(hit.uv));
                    if (!occluded) {
                        exposed = true;
                        break;
                    }
                }
                if (!opaque) continue;
                let pair = [first.cube.name + "/" + first.direction, second.cube.name + "/" + second.direction].sort();
                let key = pair.join(" | ");
                let entry = results.get(key) || {faces: pair, samples: [], exposed_samples: [], minimum_separation: separation};
                entry.samples.push(clipName + "@" + tick);
                if (exposed) {
                    conflicts++;
                    entry.exposed_samples.push(clipName + "@" + tick);
                }
                entry.minimum_separation = Math.min(entry.minimum_separation, separation);
                results.set(key, entry);
            }
        }
        frames.push({clip: clipName, tick, phase, faces: surfaces.length, exposed_conflicts: conflicts});
    }
    let geometry = JSON.parse(Codecs.bedrock.compile());
    let geometryHash = require("crypto").createHash("sha256").update(JSON.stringify(geometry, null, 2) + "\n").digest("hex");
    let animationHash = require("crypto").createHash("sha256").update(require("fs").readFileSync("C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia/animations/malenia.animation.json")).digest("hex");
    let artDirection = JSON.parse(require("fs").readFileSync("C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia/art_direction.json", "utf8"));
    let report = {revision: artDirection.revision, geometry_sha256: geometryHash, animation_sha256: animationHash, tolerance_model_units: tolerance,
        scope: "Same-facing near-coplanar opaque surface intersections in sampled poses; outward-ray exposure filter. Not a proof for every viewing angle or arbitrary animation frame.",
        frames, exposed_pairs: [...results.values()].filter(entry => entry.exposed_samples.length),
        internal_pairs: [...results.values()].filter(entry => !entry.exposed_samples.length)};
    require("fs").writeFileSync("C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia/surface_audit.json", JSON.stringify(report, null, 2) + "\n");
    for (let group of Group.all) group.mesh.visible = true;
    if (previousClip) previousClip.select();
    Timeline.setTime(previousTime);
    Animator.preview();
    return {samples: frames.length, exposedPairs: report.exposed_pairs.length, internalPairs: report.internal_pairs.length,
        exposed: report.exposed_pairs.map(entry => ({faces: entry.faces, samples: entry.exposed_samples.slice(0, 3)}))};
})();