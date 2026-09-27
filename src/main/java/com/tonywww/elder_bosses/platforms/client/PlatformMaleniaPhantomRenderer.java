package com.tonywww.elder_bosses.platforms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.client.render.MaleniaModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Visual projection only: no entity, AI, animation controller or mutable bone is shared with the boss. */
public final class PlatformMaleniaPhantomRenderer extends GeoObjectRenderer<MaleniaEntity> {
    private static final Set<String> HIDDEN = Set.of("helm", "armor_torso", "armor_shoulder_l", "armor_shoulder_r",
            "armor_waist", "cape_01", "skirt_front", "skirt_back", "skirt_l", "skirt_r", "aeonia_core", "wing_root_l", "wing_root_r");
    private final List<GeoBone> roots = new ArrayList<>();
    private final Map<String, GeoBone> bones = new HashMap<>();
    private BakedGeoModel source;

    public PlatformMaleniaPhantomRenderer() { super(new MaleniaModel()); }

    public org.joml.Matrix4f wingAnchor() {
        return com.tonywww.elder_bosses.client.vfx.MaleniaWingGeometry.anchor(bones.get("chest"));
    }

    public void draw(PoseStack poses, MaleniaEntity boss, MultiBufferSource buffers, double progress, int index, float alpha, boolean detailed) {
        BakedGeoModel baked = getGeoModel().getBakedModel(getGeoModel().getModelResource(boss));
        preparePose(baked, progress, index, detailed);
        RenderType type = RenderType.entityTranslucentEmissive(getTextureLocation(boss));
        var buffer = buffers.getBuffer(type);
        for (GeoBone root : roots) {
            //? if forge {
            renderRecursively(poses, boss, root, type, buffers, buffer, true, 0, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 1.0F, 0.70F, 0.58F, alpha);
            //?} else {
            /*int color = (Math.round(alpha * 255) << 24) | 0xFFB394;
            renderRecursively(poses, boss, root, type, buffers, buffer, true, 0, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, color);
            *///?}
        }
    }

    public void preparePose(BakedGeoModel baked, double progress, int index, boolean detailed) {
        if (source != baked) {
            source = baked; roots.clear(); bones.clear();
            for (GeoBone root : baked.topLevelBones()) roots.add(copy(root, null));
        }
        for (GeoBone bone : bones.values()) {
            var rest = bone.getInitialSnapshot();
            bone.setRotX(rest.getRotX()); bone.setRotY(rest.getRotY()); bone.setRotZ(rest.getRotZ());
            bone.setPosX(0); bone.setPosY(0); bone.setPosZ(0);
            bone.setScaleX(1); bone.setScaleY(1); bone.setScaleZ(1);
            boolean hidden = HIDDEN.contains(bone.getName()) || !detailed && (bone.getName().startsWith("hair_")
                    || bone.getName().startsWith("finger_") || bone.getName().startsWith("wing_membrane"));
            bone.setHidden(hidden); bone.setChildrenHidden(hidden);
        }
        var animation = getGeoModel().getAnimation(null, index < 3 ? "animation.malenia.phantom_slash" : "animation.malenia.phantom_thrust");
        // Existing hand-authored contact poses land exactly when the server strikes (6-tick locked flight).
        double tick = Math.max(0, Math.min(1, progress)) * (index < 3 ? 9 : 6);
        if (animation != null) for (var track : animation.boneAnimations()) {
            GeoBone bone = bones.get(track.boneName());
            if (bone == null) continue;
            for (int channel = 0; channel < 3; channel++) {
                var stack = channel == 0 ? track.rotationKeyFrames() : channel == 1 ? track.positionKeyFrames() : track.scaleKeyFrames();
                var axes = List.of(stack.xKeyframes(), stack.yKeyframes(), stack.zKeyframes());
                float[] value = new float[3]; boolean present = true;
                for (int axis = 0; axis < 3; axis++) {
                    var keys = axes.get(axis);
                    if (keys.isEmpty()) { present = false; break; }
                    value[axis] = (float) keys.get(keys.size() - 1).endValue().get();
                    double cursor = 0;
                    for (var key : keys) {
                        if (tick <= cursor + key.length()) {
                            double fraction = key.length() == 0 ? 1 : Math.max(0, Math.min(1, (tick - cursor) / key.length()));
                            value[axis] = (float) (key.startValue().get() + fraction * (key.endValue().get() - key.startValue().get()));
                            break;
                        }
                        cursor += key.length();
                    }
                }
                if (!present) continue;
                if (channel == 0) {
                    var rest = bone.getInitialSnapshot();
                    bone.setRotX(rest.getRotX() + value[0]); bone.setRotY(rest.getRotY() + value[1]); bone.setRotZ(rest.getRotZ() + value[2]);
                } else if (channel == 1) {
                    bone.setPosX(value[0]); bone.setPosY(value[1]); bone.setPosZ(value[2]);
                } else {
                    bone.setScaleX(value[0]); bone.setScaleY(value[1]); bone.setScaleZ(value[2]);
                }
            }
        }
        // Narrow spectral wings leave the sword arm and target visible.
        for (String wing : List.of("wing_root_l", "wing_root_r")) {
            GeoBone bone = bones.get(wing);
            if (bone != null) { bone.setScaleX(0.5F); bone.setScaleY(0.5F); bone.setScaleZ(0.5F); }
        }
    }

    private GeoBone copy(GeoBone original, GeoBone parent) {
        GeoBone bone = new GeoBone(parent, original.getName(), original.getMirror(), original.getInflate(), original.shouldNeverRender(), original.getReset());
        bone.setPivotX(original.getPivotX()); bone.setPivotY(original.getPivotY()); bone.setPivotZ(original.getPivotZ());
        var rest = original.getInitialSnapshot();
        if (rest != null) {
            bone.setRotX(rest.getRotX()); bone.setRotY(rest.getRotY()); bone.setRotZ(rest.getRotZ());
        } else {
            bone.setRotX(original.getRotX()); bone.setRotY(original.getRotY()); bone.setRotZ(original.getRotZ());
        }
        // Baked cubes are immutable; only the copied hierarchy receives pose changes.
        bone.getCubes().addAll(original.getCubes());
        bone.saveInitialSnapshot(); bones.put(bone.getName(), bone);
        for (GeoBone child : original.getChildBones()) bone.getChildBones().add(copy(child, bone));
        return bone;
    }

}
