package dev.sableguard.mixin.guard;

import dev.ryanhcode.sable.network.udp.SableUDPPacketDecoder;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import dev.sableguard.fix.Fixes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * udp-invalid-packet-guard (SableCat, MIT). Sable throws an {@code IOException} for every UDP datagram whose first
 * byte is not a known packet id, which floods the log on servers that receive stray traffic on that port.
 * The decoder releases the datagram after {@code decode} returns, so cancelling here does not leak it.
 */
@Mixin(SableUDPPacketDecoder.class)
public abstract class SableUDPPacketDecoderMixin {
    @Inject(method = "decode", at = @At("HEAD"), cancellable = true)
    private void sableguard$dropUnknown(final ChannelHandlerContext ctx, final DatagramPacket msg, final List<Object> out, final CallbackInfo ci) {
        if (!Fixes.UDP_PACKET_GUARD.on()) {
            return;
        }
        final ByteBuf buf = msg.content();
        if (buf.readableBytes() > 0 && buf.getUnsignedByte(buf.readerIndex()) >= SableUDPPacketType.VALUES.length) {
            ci.cancel();
        }
    }
}
