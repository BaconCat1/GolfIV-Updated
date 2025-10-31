package org.samo_lego.golfiv.mixin.accessors;

import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPositionS2CPacket.class)
public interface EntityPositionS2CPacketAccessor {
    @Accessor("change")
    PlayerPosition getPlayerPosition();

    @Mutable
    @Accessor("change")
    void setPlayerPosition(PlayerPosition position);
}
