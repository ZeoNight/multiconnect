package net.earthcomputer.multiconnect.protocols.v1_14_4.mixin;

import net.earthcomputer.multiconnect.api.Protocols;
import net.earthcomputer.multiconnect.impl.ConnectionInfo;
import net.earthcomputer.multiconnect.impl.Utils;
import net.earthcomputer.multiconnect.protocols.generic.IUserDataHolder;
import net.earthcomputer.multiconnect.protocols.v1_14_4.Protocol_1_14_4;
import net.earthcomputer.multiconnect.transformer.UnsignedByte;
import net.earthcomputer.multiconnect.transformer.VarInt;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.MobSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler {

    @Shadow public abstract void onEntityTrackerUpdate(EntityTrackerUpdateS2CPacket packet);

    @Inject(method = "onMobSpawn", at = @At("RETURN"))
    private void onOnMobSpawn(MobSpawnS2CPacket packet, CallbackInfo ci) {
        applyPendingEntityTrackerValues(packet.getId(), ((IUserDataHolder) packet).multiconnect_getUserData(Protocol_1_14_4.DATA_TRACKER_ENTRIES_KEY));
    }

    @Inject(method = "onPlayerSpawn", at = @At("RETURN"))
    private void onOnPlayerSpawn(PlayerSpawnS2CPacket packet, CallbackInfo ci) {
        applyPendingEntityTrackerValues(packet.getId(), ((IUserDataHolder) packet).multiconnect_getUserData(Protocol_1_14_4.DATA_TRACKER_ENTRIES_KEY));
    }

    @Unique
    private void applyPendingEntityTrackerValues(int entityId, List<DataTracker.Entry<?>> entries) {
        if (ConnectionInfo.protocolVersion <= Protocols.V1_14_4) {
            if (entries != null) {
                var packet = Utils.createPacket(EntityTrackerUpdateS2CPacket.class, EntityTrackerUpdateS2CPacket::new, Protocols.V1_15, buf -> {
                    buf.pendingRead(VarInt.class, new VarInt(entityId));
                    if (ConnectionInfo.protocolVersion <= Protocols.V1_8) {
                        buf.pendingRead(Byte.class, (byte)127); // terminating byte
                    } else {
                        buf.pendingRead(UnsignedByte.class, new UnsignedByte((short) 255)); // terminating byte
                    }
                    buf.applyPendingReads();
                });
                ((TrackerUpdatePacketAccessor) packet).setTrackedValues(entries);
                onEntityTrackerUpdate(packet);
            }
        }
    }

}
