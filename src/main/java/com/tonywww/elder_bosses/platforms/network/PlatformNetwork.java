package com.tonywww.elder_bosses.platforms.network;

import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;
import com.tonywww.elder_bosses.network.PlayerRotSnapshotPacket;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
//?} else {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
*///?}

public final class PlatformNetwork {
    private static final String PROTOCOL_VERSION = "1";
        private static Consumer<MaleniaCombatSnapshotPacket> combatClientHandler = packet -> {
        };
        private static Consumer<PlayerRotSnapshotPacket> rotClientHandler = packet -> {
        };
        private static Consumer<IndicatorSnapshotPacket> indicatorClientHandler = packet -> {
        };

    //? if forge {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            PlatformResourceLocation.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    //?}

    private PlatformNetwork() {
    }

    public static void register(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        //? if forge {
        int messageId = 0;
        CHANNEL.messageBuilder(
                        MaleniaCombatSnapshotPacket.class,
                        messageId++,
                        NetworkDirection.PLAY_TO_CLIENT
                )
                .encoder(MaleniaCombatSnapshotPacket::write)
                .decoder(MaleniaCombatSnapshotPacket::read)
                .consumerMainThread((packet, contextSupplier) -> {
                    handleOnClient(packet);
                    contextSupplier.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(
                        PlayerRotSnapshotPacket.class,
                        messageId++,
                        NetworkDirection.PLAY_TO_CLIENT
                )
                .encoder(PlayerRotSnapshotPacket::write)
                .decoder(PlayerRotSnapshotPacket::read)
                .consumerMainThread((packet, contextSupplier) -> {
                    handleOnClient(packet);
                    contextSupplier.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(
                        IndicatorSnapshotPacket.class,
                        messageId,
                        NetworkDirection.PLAY_TO_CLIENT
                )
                .encoder(IndicatorSnapshotPacket::write)
                .decoder(IndicatorSnapshotPacket::read)
                .consumerMainThread((packet, contextSupplier) -> {
                    handleOnClient(packet);
                    contextSupplier.get().setPacketHandled(true);
                })
                .add();
        //?} else {
        /*modBus.addListener(PlatformNetwork::registerPayloadHandlers);
        *///?}
    }

    public static void sendTo(ServerPlayer player, MaleniaCombatSnapshotPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        //? if forge {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        //?} else {
        /*PacketDistributor.sendToPlayer(player, new MaleniaCombatPayload(packet));
        *///?}
    }

    public static void sendTo(ServerPlayer player, PlayerRotSnapshotPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        //? if forge {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        //?} else {
        /*PacketDistributor.sendToPlayer(player, new PlayerRotPayload(packet));
        *///?}
    }

    public static void sendTo(ServerPlayer player, IndicatorSnapshotPacket packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        //? if forge {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        //?} else {
        /*PacketDistributor.sendToPlayer(player, new IndicatorPayload(packet));
        *///?}
    }

        public static synchronized void installClientHandlers(
                        Consumer<MaleniaCombatSnapshotPacket> combatHandler,
                        Consumer<PlayerRotSnapshotPacket> rotHandler,
                        Consumer<IndicatorSnapshotPacket> indicatorHandler
        ) {
                combatClientHandler = Objects.requireNonNull(combatHandler, "combatHandler");
                rotClientHandler = Objects.requireNonNull(rotHandler, "rotHandler");
                indicatorClientHandler = Objects.requireNonNull(indicatorHandler, "indicatorHandler");
        }

        private static void handleOnClient(MaleniaCombatSnapshotPacket packet) {
                combatClientHandler.accept(packet);
    }

    private static void handleOnClient(PlayerRotSnapshotPacket packet) {
                rotClientHandler.accept(packet);
    }

    private static void handleOnClient(IndicatorSnapshotPacket packet) {
                indicatorClientHandler.accept(packet);
    }

    //? if neoforge {
    /*private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                MaleniaCombatPayload.TYPE,
                MaleniaCombatPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> handleOnClient(payload.packet())
                )
        );
        registrar.playToClient(
                PlayerRotPayload.TYPE,
                PlayerRotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> handleOnClient(payload.packet())
                )
        );
        registrar.playToClient(
                IndicatorPayload.TYPE,
                IndicatorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> handleOnClient(payload.packet())
                )
        );
    }

    private record MaleniaCombatPayload(MaleniaCombatSnapshotPacket packet)
            implements CustomPacketPayload {
        private static final Type<MaleniaCombatPayload> TYPE = new Type<>(
                PlatformResourceLocation.id("malenia_combat_snapshot")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, MaleniaCombatPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> payload.packet().write(buffer),
                        buffer -> new MaleniaCombatPayload(MaleniaCombatSnapshotPacket.read(buffer))
                );

        private MaleniaCombatPayload {
            Objects.requireNonNull(packet, "packet");
        }

        @Override
        public Type<MaleniaCombatPayload> type() {
            return TYPE;
        }
    }

    private record PlayerRotPayload(PlayerRotSnapshotPacket packet)
            implements CustomPacketPayload {
        private static final Type<PlayerRotPayload> TYPE = new Type<>(
                PlatformResourceLocation.id("player_rot_snapshot")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, PlayerRotPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> payload.packet().write(buffer),
                        buffer -> new PlayerRotPayload(PlayerRotSnapshotPacket.read(buffer))
                );

        private PlayerRotPayload {
            Objects.requireNonNull(packet, "packet");
        }

        @Override
        public Type<PlayerRotPayload> type() {
            return TYPE;
        }
    }

    private record IndicatorPayload(IndicatorSnapshotPacket packet)
            implements CustomPacketPayload {
        private static final Type<IndicatorPayload> TYPE = new Type<>(
                PlatformResourceLocation.id("indicator_snapshot")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, IndicatorPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> payload.packet().write(buffer),
                        buffer -> new IndicatorPayload(IndicatorSnapshotPacket.read(buffer))
                );

        private IndicatorPayload {
            Objects.requireNonNull(packet, "packet");
        }

        @Override
        public Type<IndicatorPayload> type() {
            return TYPE;
        }
    }
    *///?}
}