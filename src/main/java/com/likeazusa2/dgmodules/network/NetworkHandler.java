package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.DGModules;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class NetworkHandler {
    public static final String PROTOCOL = "1";

    public static void init(IEventBus modBus) {
        modBus.addListener(NetworkHandler::onRegisterPayloads);
    }

    private static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {

        var registrar = event.registrar(DGModules.MODID + ":" + PROTOCOL);

        registrar.playToServer(
                C2SChaosLaser.TYPE,
                C2SChaosLaser.STREAM_CODEC,
                C2SChaosLaser::handle
        );

        registrar.playToServer(
                C2SFlightTunerInput.TYPE,
                C2SFlightTunerInput.STREAM_CODEC,
                C2SFlightTunerInput::handle
        );

        registrar.playToServer(
                C2SPhaseShieldToggle.TYPE,
                C2SPhaseShieldToggle.STREAM_CODEC,
                C2SPhaseShieldToggle::handle
        );

        registrar.playToClient(
                S2CLaserState.TYPE,
                S2CLaserState.STREAM_CODEC,
                S2CLaserState::handle
        );

        registrar.playToClient(
                S2CDragonGuardWarn.TYPE,
                S2CDragonGuardWarn.STREAM_CODEC,
                S2CDragonGuardWarn::handle);


        registrar.playToClient(
                S2CPhaseShieldState.TYPE,
                S2CPhaseShieldState.STREAM_CODEC,
                S2CPhaseShieldState::handle);

        registrar.playToClient(
                S2CPhaseShieldLoopSound.TYPE,
                S2CPhaseShieldLoopSound.STREAM_CODEC,
                S2CPhaseShieldLoopSound::handle
        );

    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
