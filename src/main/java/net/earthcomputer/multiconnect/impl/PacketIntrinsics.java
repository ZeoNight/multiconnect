package net.earthcomputer.multiconnect.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.network.PacketByteBuf;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public final class PacketIntrinsics {
    private PacketIntrinsics() {}

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    public static MethodHandle findSetterHandle(Class<?> ownerClass, String fieldName, Class<?> fieldType) {
        try {
            return MethodHandles.lookup().findSetter(ownerClass, fieldName, fieldType);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not find setter method handle, this indicates a compiler bug!", e);
        }
    }

    public static int readVarInt(ByteBuf buf) {
        int result = 0;
        int shift = 0;
        int b;
        do {
            if (shift >= 32) {
                throw new IndexOutOfBoundsException("varint too big");
            }
            b = buf.readUnsignedByte();
            result |= (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    public static long readVarLong(ByteBuf buf) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            if (shift >= 64) {
                throw new IndexOutOfBoundsException("varlong too big");
            }
            b = buf.readUnsignedByte();
            result |= (long) (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return result;
    }

    public static String readString(ByteBuf buf) {
        int length = readVarInt(buf);
        if (length > PacketByteBuf.DEFAULT_MAX_STRING_LENGTH * 4) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + length + " > " + PacketByteBuf.DEFAULT_MAX_STRING_LENGTH * 4 + ")");
        } else if (length < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        }
        String string = buf.toString(buf.readerIndex(), length, StandardCharsets.UTF_8);
        buf.readerIndex(buf.readerIndex() + length);
        if (string.length() > PacketByteBuf.DEFAULT_MAX_STRING_LENGTH) {
            throw new DecoderException("The received string length is longer than maximum allowed (" + length + " > " + PacketByteBuf.DEFAULT_MAX_STRING_LENGTH + ")");
        }
        return string;
    }

    public static NbtCompound readNbtCompound(ByteBuf buf) {
        int index = buf.readerIndex();
        byte b = buf.readByte();
        if (b == 0) {
            return null;
        }
        buf.readerIndex(index);
        try {
            return NbtIo.read(new ByteBufInputStream(buf), new NbtTagSizeTracker(2097152));
        } catch (IOException e) {
            throw new DecoderException(e);
        }
    }

    public static BitSet readBitSet(ByteBuf buf) {
        int length = readVarInt(buf);
        if (length > buf.readableBytes() / 8) {
            throw new DecoderException("LongArray with size " + length + " is bigger than allowed " + buf.readableBytes() / 8);
        }
        long[] array = new long[length];
        for (int i = 0; i < length; i++) {
            array[i] = buf.readLong();
        }
        return BitSet.valueOf(array);
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        do {
            int bits = value & 0x7f;
            value >>>= 7;
            buf.writeByte(bits | ((value != 0) ? 0x80 : 0));
        } while (value != 0);
    }

    public static void writeVarLong(ByteBuf buf, long value) {
        do {
            int bits = (int) (value & 0x7f);
            value >>>= 7;
            buf.writeByte(bits | ((value != 0) ? 0x80 : 0));
        } while (value != 0);
    }

    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static void writeNbtCompound(ByteBuf buf, NbtCompound value) {
        if (value == null) {
            buf.writeByte(0);
        } else {
            try {
                NbtIo.write(value, new ByteBufOutputStream(buf));
            } catch (IOException e) {
                throw new EncoderException(e);
            }
        }
    }

    public static void writeBitSet(ByteBuf buf, BitSet value) {
        long[] longs = value.toLongArray();
        writeVarInt(buf, longs.length);
        for (long element : longs) {
            buf.writeLong(element);
        }
    }
}