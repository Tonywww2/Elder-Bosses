package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;

public final class ClientConsortEnergyRenderer {
    private static final Map<VisualKey, IndicatorSnapshotPacket> AFTERGLOWS = new LinkedHashMap<>();
    private static Object effectLevel;

    private ClientConsortEnergyRenderer() {
    }

    public static void render(PoseStack poses, Camera camera, long gameTick, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var config = ElderBossesCommonConfig.VALUES.skillVfx();
        if (effectLevel != minecraft.level || !config.enabled()) {
            AFTERGLOWS.clear();
            effectLevel = minecraft.level;
        }
        if (!config.enabled() || !ConsortEnergyShader.ready() || minecraft.level == null) return;
        Vec3 cameraPosition = camera.getPosition();
        double time = gameTick + partialTick;
        renderDefenses(poses,cameraPosition,time,config.renderDistance());
        renderGravityRocks(poses, cameraPosition, time, partialTick, config.renderDistance());
        var current = ClientIndicatorStateStore.activeSnapshots(gameTick, partialTick).stream()
                .filter(snapshot -> minecraft.level.getEntity(snapshot.bossEntityId()) instanceof PromisedConsortEntity)
                .filter(snapshot -> snapshot.slot() != IndicatorSnapshotPacket.SegmentSlot.NEXT)
                .filter(snapshot -> snapshot.state() != IndicatorSnapshotPacket.IndicatorState.EXPIRED)
                .filter(snapshot -> time < snapshot.endTick() && time >= snapshot.lockTick())
                .filter(snapshot -> anchor(snapshot).distanceToSqr(cameraPosition) <= config.renderDistance() * config.renderDistance())
                .sorted(Comparator.comparingDouble((IndicatorSnapshotPacket snapshot) -> anchor(snapshot).distanceToSqr(cameraPosition)).reversed())
                .toList();
        for (var snapshot : current) {
            if (afterglowTicks(snapshot) > 0 && time >= snapshot.activeTick()) {
                AFTERGLOWS.put(new VisualKey(snapshot.bossEntityId(), snapshot.indicatorId()), snapshot);
            }
        }
        AFTERGLOWS.entrySet().removeIf(entry -> {
            var snapshot = entry.getValue();
            var entity = minecraft.level.getEntity(snapshot.bossEntityId());
            return time >= visualEndTick(snapshot) || !(entity instanceof PromisedConsortEntity boss) || !boss.isAlive();
        });
        while (AFTERGLOWS.size() > 256) AFTERGLOWS.remove(AFTERGLOWS.keySet().iterator().next());
        Map<VisualKey, IndicatorSnapshotPacket> visible = new LinkedHashMap<>(AFTERGLOWS);
        current.forEach(snapshot -> visible.put(new VisualKey(snapshot.bossEntityId(), snapshot.indicatorId()), snapshot));
        var snapshots = visible.values().stream()
                .filter(snapshot -> time < visualEndTick(snapshot) && time >= snapshot.lockTick())
                .filter(snapshot -> anchor(snapshot).distanceToSqr(cameraPosition) <= config.renderDistance() * config.renderDistance())
                .sorted(Comparator.comparingInt((IndicatorSnapshotPacket snapshot) -> time >= snapshot.endTick() ? 1 : 0)
                        .thenComparingDouble(snapshot -> anchor(snapshot).distanceToSqr(cameraPosition)))
                .toList();
        if (snapshots.isEmpty()) return;
        Map<Integer, Integer> budgets = new HashMap<>();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            for (var snapshot : snapshots) {
                int count = budgets.merge(snapshot.bossEntityId(), 1, Integer::sum);
                if (count > 32) continue;
                String style = snapshot.styleRole().name();
                boolean active = time >= snapshot.activeTick();
                if (!active && !style.contains("HOLY") && !style.contains("GRAVITY") && !style.contains("BLOOD")
                    && !style.contains("CLONE") && !isStomp(snapshot)) continue;
                float progress = (float) Math.max(0.0, Math.min(1.0, (time - snapshot.activeTick()) / Math.max(1L, visualEndTick(snapshot) - snapshot.activeTick())));
                float strength = active ? (float) (0.9 * (1.0 - Math.pow(progress, 3))) : 0.18F;
                Vec3 center = anchor(snapshot).add(0, 0.11, 0);
                double first = snapshot.ranges().get(0);
                double outer = snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.ANNULUS ? snapshot.ranges().get(1) : first;
                float inner = snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.ANNULUS ? (float) (first / Math.max(0.01, outer)) : 0.0F;
                int rgb = snapshot.styleRole().rgb();
                if(snapshot.indicatorId().endsWith(":reprisal")) {
                    renderReprisal(buffers,poses.last(),snapshot,time);
                    continue;
                }
                if (time >= snapshot.activeTick() && time < snapshot.endTick()) {
                    float release = (float) ((time - snapshot.activeTick()) / Math.max(1, snapshot.endTick() - snapshot.activeTick()));
                    ConsortEnergyShader.configure(14, (float) ((time % 24000) / 20), release, 0);
                    attackVolume(buffers.getBuffer(ConsortEnergyShader.ENERGY), poses.last(), snapshot, release, rgb,
                            ElderBossesCommonConfig.VALUES.indicators().maxSegmentsPerShape());
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                }
                int mode = style.contains("GRAVITY") ? 4 : style.contains("BLOOD") ? 3 : active ? 5 : 1;
                ConsortEnergyShader.configure(mode, (float) ((time % 24000) / 20), progress, inner);
                VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                switch (snapshot.shapeType()) {
                    case CIRCLE, ZONE, ANNULUS -> ground(consumer, poses.last(), center, outer, outer, 0, rgb, strength);
                    case RECTANGLE, CAPSULE -> {
                        double width = snapshot.ranges().get(1);
                        Vec3 forward = forward(snapshot.directionYawDegrees());
                        ConsortEnergyShader.configure(style.contains("BLOOD") ? 3 : 0, (float) ((time % 24000) / 20), progress, 0);
                        ground(consumer, poses.last(), center.add(forward.scale(first / 2)), width / 2, first / 2,
                                snapshot.directionYawDegrees(), rgb, strength * 0.65F);
                    }
                    case PATH -> {
                        ConsortEnergyShader.configure(0, (float) ((time % 24000) / 20), progress, 0);
                        double width = first * 0.5;
                        for (int index = 1; index < snapshot.pathPoints().size(); index++) {
                            var start = snapshot.pathPoints().get(index - 1);
                            var end = snapshot.pathPoints().get(index);
                            ribbon(consumer, poses.last(), new Vec3(start.x(), start.y() + 0.18, start.z()),
                                    new Vec3(end.x(), end.y() + 0.18, end.z()), width, rgb, strength);
                        }
                    }
                    case SECTOR -> {
                        if (!active || snapshot.semantic() == IndicatorSnapshotPacket.Semantic.PHYSICAL) break;
                        ConsortEnergyShader.configure(0, (float) ((time % 24000) / 20), progress, 0);
                        double radians = Math.toRadians(snapshot.ranges().get(1));
                        double yaw = Math.toRadians(snapshot.directionYawDegrees());
                        for (int index = 0; index < 20; index++) {
                            double start = yaw - radians / 2 + radians * index / 20;
                            double end = yaw - radians / 2 + radians * (index + 1) / 20;
                            ribbon(consumer, poses.last(), center.add(-Math.sin(start) * first, 0.16, Math.cos(start) * first),
                                    center.add(-Math.sin(end) * first, 0.16, Math.cos(end) * first), 0.13, rgb, strength * 0.35F);
                        }
                    }
                }
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                if (style.contains("BLOOD") && (snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.RECTANGLE
                    || snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.CAPSULE)) {
                    Vec3 depth = forward(snapshot.directionYawDegrees());
                    Vec3 tearCenter = center.add(depth.scale(first * 0.5)).add(0, active ? 1.1 : 1.6, 0);
                    double halfWidth = snapshot.ranges().get(1) * 0.5;
                    Vec3 half = new Vec3(depth.z, 0, -depth.x).scale(halfWidth);
                    double height = active ? 1.35 * (1.0 - progress * 0.6) : 0.09;
                    ConsortEnergyShader.configure(9, (float) ((time % 24000) / 20), progress, 0);
                    consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    quad(consumer, poses.last(), tearCenter.subtract(half).add(0, -height, 0), tearCenter.add(half).add(0, -height, 0),
                        tearCenter.add(half).add(0, height, 0), tearCenter.subtract(half).add(0, height, 0),
                        rgb, active ? strength : 0.66F);
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                }
                if (style.contains("HOLY") && (snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.CIRCLE
                        || snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.ZONE)) {
                    ConsortEnergyShader.configure(2, (float) ((time % 24000) / 20), progress, 0);
                    consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    double width = Math.min(2.4, Math.max(0.35, outer * 0.45));
                    double height = Math.min(14.0, Math.max(4.0, outer * 1.7));
                    Vec3 across = cameraPosition.subtract(center).multiply(1, 0, 1).normalize().cross(new Vec3(0, 1, 0)).scale(width);
                    quad(consumer, poses.last(), center.subtract(across), center.add(across),
                            center.add(across).add(0, height, 0), center.subtract(across).add(0, height, 0), rgb, strength);
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                }
                if (style.contains("HOLY") || style.contains("CLONE")) {
                    holyLayers(buffers, poses.last(), snapshot, cameraPosition, time, active, progress, strength);
                }
                if (isStomp(snapshot)) stompLayers(buffers, poses.last(), snapshot, time, active, progress);
                if (style.contains("BLOOD")) bloodflameLayers(buffers, poses.last(), snapshot, cameraPosition, time, active, progress);
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            poses.popPose();
        }
    }

    private record VisualKey(int entityId, String indicatorId) {
    }

    private static void renderGravityRocks(PoseStack poses, Vec3 view, double time, float partialTick, double distance) {
        var minecraft = Minecraft.getInstance();
        var buffers = minecraft.renderBuffers().bufferSource();
        int count = 0;
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            for (var entity : minecraft.level.entitiesForRendering()) {
                if (!(entity instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity rock)
                        || rock.isRemoved() || rock.distanceToSqr(view) > distance * distance) continue;
                if (++count > 48) break;
                Vec3 center = new Vec3(net.minecraft.util.Mth.lerp(partialTick, rock.xo, rock.getX()),
                        net.minecraft.util.Mth.lerp(partialTick, rock.yo, rock.getY()) + 0.2,
                        net.minecraft.util.Mth.lerp(partialTick, rock.zo, rock.getZ()));
                ConsortEnergyShader.configure(4, (float) ((time % 24000) / 20), 0, 0);
                var consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                rockAura(consumer, poses.last(), center, view, time, rock.isHeld());
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                if (!rock.isHeld() && rock.getDeltaMovement().lengthSqr() > 0.0001) {
                    ConsortEnergyShader.configure(8, (float) ((time % 24000) / 20), 0, 0);
                    consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    ribbon(consumer, poses.last(), center.subtract(rock.getDeltaMovement().normalize().scale(1.3)), center, 0.15, 0xB788FC, 0.65F);
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                }
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            poses.popPose();
        }
    }

    public static void rockAura(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 view, double time, boolean held) {
        double radius = (held ? 0.5 : 0.38) * (1 + Math.sin(time * 0.5) * 0.08);
        defenseSurface(consumer, pose, center, radius, radius, 0, 360, 0xB584FF, 0.35F);
        sparkle(consumer, pose, center, view, radius * 0.5, 0xE2C4FF, 0.7F);
    }

    private static void renderDefenses(PoseStack poses, Vec3 view, double time, double distance) {
        var minecraft=Minecraft.getInstance();
        var buffers=minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-view.x,-view.y,-view.z);
        try {
            for(var entity:minecraft.level.entitiesForRendering()) {
                if(!(entity instanceof PromisedConsortEntity boss) || boss.isRemoved() || !boss.rangedDefending()
                        || boss.distanceToSqr(view)>distance*distance) continue;
                var action=boss.actionId().orElseThrow();
                Vec3 center=boss.position().add(0,0.15,0);
                double radius=boss.rangedDefenseRadius();
                int color=boss.miquellaVisible()?0xDEC5FF:0x9987EE;
                ConsortEnergyShader.configure(13,(float)((time%24000)/20),0,0);
                var consumer=buffers.getBuffer(ConsortEnergyShader.ENERGY);
                if(action==com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_REFLECTION) {
                    defenseSurface(consumer,poses.last(),boss.getBoundingBox().getCenter(),radius,radius,boss.getYRot(),boss.rangedDefenseArc(),color,0.42F);
                } else if(action==com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_BULWARK) {
                    defenseSurface(consumer,poses.last(),boss.getBoundingBox().getCenter(),radius,boss.getBbHeight()*0.5,
                            boss.getYRot(),boss.rangedDefenseArc(),color,0.52F);
                }
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                if(action==com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_REPRISAL) {
                    ConsortEnergyShader.configure(7,(float)((time%24000)/20),boss.rangedCharge(),0);
                    consumer=buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    Vec3 core=center.add(forward(boss.getYRot()).scale(boss.getBbWidth()*0.8)).add(0,boss.getBbHeight()*0.65,0);
                    sparkle(consumer,poses.last(),core,view,0.6+boss.rangedCharge()*1.4,color,0.95F);
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                }
            }
        } finally {
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableBlend(); poses.popPose();
        }
    }

    private static void renderReprisal(MultiBufferSource.BufferSource buffers,PoseStack.Pose pose,IndicatorSnapshotPacket snapshot,double time) {
        if(time<snapshot.activeTick() || time>=snapshot.endTick()) return;
        Vec3 direction=forward(snapshot.directionYawDegrees());
        double progress=Math.max(0,Math.min(1,(time-snapshot.activeTick())/Math.max(1,snapshot.endTick()-snapshot.activeTick())));
        double length=snapshot.ranges().get(0),width=snapshot.ranges().get(1);
        double ticks=Math.max(1,snapshot.endTick()-snapshot.activeTick());
        double slice=Math.floor(time-snapshot.activeTick());
        Vec3 start=anchor(snapshot).add(direction.scale(length*slice/ticks)).add(0,0.1,0);
        Vec3 center=anchor(snapshot).add(direction.scale(length*Math.min(1,(slice+1)/ticks))).add(0,0.1,0);
        Vec3 across=new Vec3(direction.z,0,-direction.x).scale(width*0.5);
        var boss=Minecraft.getInstance().level.getEntity(snapshot.bossEntityId());
        double height=boss instanceof PromisedConsortEntity consort?consort.rangedWaveHeight():4;
        ConsortEnergyShader.configure(14,(float)((time%24000)/20),(float)progress,0);
        var consumer=buffers.getBuffer(ConsortEnergyShader.ENERGY);
        quad(consumer,pose,center.subtract(across),center.add(across),center.add(across).add(0,height,0),
                center.subtract(across).add(0,height,0),0xB8A0FA,0.88F);
        quad(consumer,pose,start.subtract(across).add(0,height*0.4,0),start.add(across).add(0,height*0.4,0),
            center.add(across).add(0,height*0.4,0),center.subtract(across).add(0,height*0.4,0),0x9C85EC,0.55F);
        buffers.endBatch(ConsortEnergyShader.ENERGY);
    }

    public static void defenseSurface(VertexConsumer consumer,PoseStack.Pose pose,Vec3 center,double radius,double height,
                                      double yaw,double arc,int color,float alpha) {
        for(int vertical=0;vertical<8;vertical++) {
            double bottom=-Math.PI/2+Math.PI*vertical/8,top=-Math.PI/2+Math.PI*(vertical+1)/8;
            for(int segment=0;segment<32;segment++) {
                double from=Math.toRadians(yaw-arc/2+arc*segment/32),to=Math.toRadians(yaw-arc/2+arc*(segment+1)/32);
                quad(consumer,pose,shieldPoint(center,radius,height,bottom,from),shieldPoint(center,radius,height,bottom,to),
                        shieldPoint(center,radius,height,top,to),shieldPoint(center,radius,height,top,from),color,alpha);
            }
        }
    }

    private static Vec3 shieldPoint(Vec3 center,double radius,double height,double elevation,double yaw) {
        return center.add(-Math.sin(yaw)*Math.cos(elevation)*radius,Math.sin(elevation)*height,
                Math.cos(yaw)*Math.cos(elevation)*radius);
    }

    public static void attackVolume(VertexConsumer consumer, PoseStack.Pose pose, IndicatorSnapshotPacket snapshot,
                                    float progress, int color, int segments) {
        var mesh = ClientIndicatorGeometry.create(snapshot, segments);
        boolean physical = snapshot.semantic() == IndicatorSnapshotPacket.Semantic.PHYSICAL;
        double bottom = physical ? 0.45 : 0.15;
        double top = physical ? 1.35 - progress * 0.45 : 2.2 - progress * 0.7;
        for (var fill : mesh.fills()) {
            quad(consumer, pose, lifted(fill.first(), top), lifted(fill.second(), top), lifted(fill.third(), top),
                    lifted(fill.fourth(), top), color, physical ? 0.58F : 0.72F);
        }
        for (var edge : mesh.borders()) {
            quad(consumer, pose, lifted(edge.from(), bottom), lifted(edge.to(), bottom), lifted(edge.to(), top),
                    lifted(edge.from(), top), color, physical ? 0.42F : 0.62F);
        }
    }

    private static Vec3 lifted(ClientIndicatorGeometry.Vertex vertex, double height) {
        return new Vec3(vertex.x(), vertex.y() + height, vertex.z());
    }

    private static boolean isStomp(IndicatorSnapshotPacket snapshot) {
        return snapshot.indicatorId().endsWith(":stomp");
    }

    public static int afterglowTicks(IndicatorSnapshotPacket snapshot) {
        return switch (snapshot.styleRole()) {
            case HOLY_IVORY -> 16;
            case CLONE_GOLD -> 10;
            case BLOODFLAME_RED -> 22;
            default -> isStomp(snapshot) ? 20 : 0;
        };
    }

    public static long visualEndTick(IndicatorSnapshotPacket snapshot) {
        return snapshot.endTick() + afterglowTicks(snapshot);
    }

    private static void holyLayers(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose, IndicatorSnapshotPacket snapshot,
                                   Vec3 view, double time, boolean active, float progress, float strength) {
        Vec3 center = anchor(snapshot).add(0, 0.16, 0), direction = forward(snapshot.directionYawDegrees());
        double length = snapshot.ranges().get(0);
        boolean corridor = snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.RECTANGLE
                || snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.CAPSULE;
        boolean ring = snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.ANNULUS;
        double radius = ring ? snapshot.ranges().get(1) : length;
        float clock = (float) ((time % 24000) / 20);
        ConsortEnergyShader.configure(10, clock, progress, ring ? (float) (length / Math.max(0.01, radius)) : 0);
        VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        if (corridor) {
            double width = snapshot.ranges().get(1);
            ground(consumer, pose, center.add(direction.scale(length / 2)), width / 2, length / 2,
                    snapshot.directionYawDegrees(), 0xFFDF91, active ? strength : 0.35F);
        } else ground(consumer, pose, center, radius, radius, 0, 0xFFDF91, active ? strength : 0.35F);
        buffers.endBatch(ConsortEnergyShader.ENERGY);
        if (ring) {
            ConsortEnergyShader.configure(13, clock, progress, 0);
            consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
            ringWall(consumer, pose, center, (length + radius) / 2, active ? 2.6 * (1 - progress) + 0.2 : 0.3,
                    0xFFF0B5, active ? strength * 0.8F : 0.25F, 48);
            buffers.endBatch(ConsortEnergyShader.ENERGY);
        }
        ConsortEnergyShader.configure(2, clock, progress, 0);
        consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        int columns = corridor ? Math.min(10, Math.max(3, (int) Math.ceil(length / 2))) : radius > 5 ? 12 : 6;
        for (int index = 0; index < columns; index++) {
            double ratio = (index + 0.5) / columns, angle = ratio * Math.PI * 2;
            Vec3 point = corridor ? center.add(direction.scale(length * ratio))
                    : center.add(Math.cos(angle) * radius * (ring ? 0.88 : 0.66), 0, Math.sin(angle) * radius * (ring ? 0.88 : 0.66));
            double height = active ? (3 + Math.min(16, radius) * (index % 2 == 0 ? 1.0 : 0.65)) * (1 - progress * 0.75) : 0.55;
            pillar(consumer, pose, point, view, active ? 0.45 + Math.min(0.7, radius * 0.06) : 0.14,
                    height, 0xFFF0B5, active ? strength : 0.4F);
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
        if (!active) return;
        ConsortEnergyShader.configure(7, clock, 1 - progress, 0);
        consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        Vec3 sparkleCenter = corridor ? center.add(direction.scale(length * 0.55)) : center;
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4 + progress;
            Vec3 point = sparkleCenter.add(Math.cos(angle) * (1 + progress * 2.5), 0.8 + progress * 5 + index % 3,
                    Math.sin(angle) * (1 + progress * 2.5));
            sparkle(consumer, pose, point, view, 0.18 + (1 - progress) * 0.35, 0xFFF8DA, strength);
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
    }

    private static void stompLayers(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose, IndicatorSnapshotPacket snapshot,
                                    double time, boolean active, float progress) {
        if (snapshot.shapeType() != IndicatorSnapshotPacket.ShapeType.RECTANGLE) return;
        Vec3 origin = anchor(snapshot).add(0, 0.2, 0), direction = forward(snapshot.directionYawDegrees());
        Vec3 across = new Vec3(direction.z, 0, -direction.x);
        double length = snapshot.ranges().get(0), width = snapshot.ranges().get(1);
        float strength = active ? 1 - progress : 0.28F;
        ConsortEnergyShader.configure(12, (float) ((time % 24000) / 20), progress, 0);
        VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        ground(consumer, pose, origin.add(direction.scale(length / 2)), width / 2, length / 2,
                snapshot.directionYawDegrees(), 0xEFC78A, strength);
        if (active) for (int row = 0; row < 4; row++) {
            double advance = Math.min(1, progress * 2.5) - row * 0.13;
            if (advance < 0) continue;
            Vec3 ridge = origin.add(direction.scale(length * advance));
            for (int piece = 0; piece < 7; piece++) {
                Vec3 start = ridge.add(across.scale(width * ((piece + 0.5) / 7 - 0.5)));
                Vec3 half = across.scale(width / 19), rise = new Vec3(0, (0.7 + piece % 3 * 0.35) * strength, 0);
                quad(consumer, pose, start.subtract(half), start.add(half), start.add(half).add(rise),
                        start.subtract(half).add(rise), 0xD6BA88, strength);
            }
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
    }

    private static void bloodflameLayers(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose, IndicatorSnapshotPacket snapshot,
                                        Vec3 view, double time, boolean active, float progress) {
        if (snapshot.ranges().size() < 2 || snapshot.shapeType() != IndicatorSnapshotPacket.ShapeType.RECTANGLE
                && snapshot.shapeType() != IndicatorSnapshotPacket.ShapeType.CAPSULE) return;
        Vec3 origin = anchor(snapshot).add(0, 0.18, 0), direction = forward(snapshot.directionYawDegrees());
        double length = snapshot.ranges().get(0), width = snapshot.ranges().get(1);
        float clock = (float) ((time % 24000) / 20), strength = active ? 1 - progress * progress : 0.4F;
        ConsortEnergyShader.configure(11, clock, progress, 0);
        VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        int tongues = Math.min(14, Math.max(5, (int) Math.ceil(length * 1.3)));
        for (int index = 0; index < tongues; index++) {
            Vec3 point = origin.add(direction.scale(length * (index + 0.5) / tongues));
            double height = active ? (2.8 + index % 3 * 0.65) * (1 - progress * 0.65) : 0.45;
            pillar(consumer, pose, point, view, Math.max(0.4, width * 0.45), height, 0xC9213B, strength);
            if (active) pillar(consumer, pose, point.add(0, 0.08, 0), view, Math.max(0.2, width * 0.23),
                    height * 0.65, 0xFF9C47, strength * 0.88F);
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
        if (!active) return;
        ConsortEnergyShader.configure(7, clock, 0, 0);
        consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        for (int index = 0; index < 12; index++) {
            double rise = (progress + index / 12.0) % 1;
            Vec3 point = origin.add(direction.scale(length * (index + 0.5) / 12)).add(Math.sin(index * 2.4) * width * 0.4,
                    rise * 4.5 + 0.5, Math.cos(index * 2.4) * width * 0.4);
            sparkle(consumer, pose, point, view, 0.12 + (1 - rise) * 0.12, 0xFF803B, strength * (float) (1 - rise));
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
    }

    public static void ringWall(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, double radius, double height,
                                int color, float alpha, int segments) {
        for (int index = 0; index < segments; index++) {
            double first = index * Math.PI * 2 / segments, second = (index + 1) * Math.PI * 2 / segments;
            Vec3 start = center.add(Math.cos(first) * radius, 0, Math.sin(first) * radius);
            Vec3 end = center.add(Math.cos(second) * radius, 0, Math.sin(second) * radius);
            quad(consumer, pose, start, end, end.add(0, height, 0), start.add(0, height, 0), color, alpha);
        }
    }

    public static void pillar(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 view, double width,
                              double height, int color, float alpha) {
        Vec3 across = view.subtract(center).multiply(1, 0, 1).normalize().cross(new Vec3(0, 1, 0));
        if (across.lengthSqr() < 0.01) across = new Vec3(1, 0, 0);
        for (Vec3 half : List.of(across.scale(width), new Vec3(-across.z, 0, across.x).scale(width * 0.65))) {
            quad(consumer, pose, center.subtract(half), center.add(half), center.add(half).add(0, height, 0),
                    center.subtract(half).add(0, height, 0), color, alpha);
        }
    }

    public static void sparkle(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 view, double size, int color, float alpha) {
        Vec3 facing = view.subtract(center).normalize(), across = facing.cross(new Vec3(0, 1, 0)).normalize();
        if (across.lengthSqr() < 0.01) across = new Vec3(1, 0, 0);
        Vec3 horizontal = across.scale(size), vertical = across.cross(facing).normalize().scale(size);
        quad(consumer, pose, center.subtract(horizontal).subtract(vertical), center.add(horizontal).subtract(vertical),
                center.add(horizontal).add(vertical), center.subtract(horizontal).add(vertical), color, alpha);
    }

    private static Vec3 anchor(IndicatorSnapshotPacket snapshot) {
        return new Vec3(snapshot.anchor().x(), snapshot.anchor().y(), snapshot.anchor().z());
    }

    private static Vec3 forward(double yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(-Math.sin(angle), 0, Math.cos(angle));
    }

    private static void ground(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center,
                               double width, double length, double yaw, int rgb, float alpha) {
        Vec3 depth = forward(yaw).scale(length);
        Vec3 across = new Vec3(depth.z, 0, -depth.x).normalize().scale(width);
        quad(consumer, pose, center.subtract(across).subtract(depth), center.add(across).subtract(depth),
                center.add(across).add(depth), center.subtract(across).add(depth), rgb, alpha);
    }

    public static void ribbon(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end, double width, int rgb, float alpha) {
        Vec3 across = end.subtract(start).normalize().cross(new Vec3(0, 1, 0)).scale(width);
        quad(consumer, pose, start.subtract(across), start.add(across), end.add(across), end.subtract(across), rgb, alpha);
    }

    public static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, int rgb, float alpha) {
        vertex(consumer, pose, first, 0, 0, rgb, alpha);
        vertex(consumer, pose, second, 1, 0, rgb, alpha);
        vertex(consumer, pose, third, 1, 1, rgb, alpha);
        vertex(consumer, pose, fourth, 0, 1, rgb, alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, float horizontal, float vertical, int rgb, float alpha) {
        PlatformVertexConsumer.addPositionColorUv(consumer, pose, (float) point.x, (float) point.y, (float) point.z,
                rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Math.max(0, Math.min(255, Math.round(alpha * 255))), horizontal, vertical);
    }
}