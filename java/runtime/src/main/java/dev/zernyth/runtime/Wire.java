package dev.zernyth.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.TreeMap;

import dev.zernyth.api.inventory.ItemStack;
import dev.zernyth.api.player.Location;
import dev.zernyth.api.world.BlockData;
import dev.zernyth.api.world.BlockPosition;

/**
 * Bounded internal value transport; one JNI exchange per batch/snapshot.
 */
final class Wire {

    interface Encoder {

        void write(DataOutputStream out) throws IOException;
    }

    static byte[] encode(Encoder encoder) {
        class BoundedBytes extends ByteArrayOutputStream {

            private void limit(int length) {
                if (length < 0 || count > 8 * 1024 * 1024 - length) {
                    throw new IllegalArgumentException("Request exceeds 8 MiB");
                }
            }

            @Override
            public synchronized void write(int value) {
                limit(1);
                super.write(value);
            }

            @Override
            public synchronized void write(byte[] b, int offset, int length) {
                limit(length);
                super.write(b, offset, length);
            }
        }
        try {
            var bytes = new BoundedBytes();
            var out = new DataOutputStream(bytes);
            encoder.write(out);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode request", e);
        }
    }

    static DataInputStream input(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    static void text(DataOutput out, String value) throws IOException {
        byte[] b = Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
        if (b.length > 4194304) {
            throw new IOException("Text too large");
        }
        out.writeInt(b.length);
        out.write(b);
    }

    static String text(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 4194304) {
            throw new IOException("Invalid text size");
        }
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    static int count(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 65536) {
            throw new IOException("Invalid collection size");
        }
        return n;
    }

    static void pos(DataOutput out, BlockPosition p) throws IOException {
        out.writeInt(p.x());
        out.writeInt(p.y());
        out.writeInt(p.z());
    }

    static void loc(DataOutput out, Location l) throws IOException {
        text(out, l.dimension());
        out.writeDouble(l.x());
        out.writeDouble(l.y());
        out.writeDouble(l.z());
        out.writeDouble(l.pitch());
        out.writeDouble(l.yaw());
    }

    static Location loc(DataInput in) throws IOException {
        return new Location(text(in), in.readDouble(), in.readDouble(), in.readDouble(),
                (float) in.readDouble(), (float) in.readDouble());
    }

    static void block(DataOutput out, BlockData data) throws IOException {
        text(out, data.type());
        out.writeInt(data.states().size());
        for (var entry : new TreeMap<>(data.states()).entrySet()) {
            text(out, entry.getKey());
            value(out, entry.getValue());
        }
    }

    static BlockData block(DataInput in) throws IOException {
        String type = text(in);
        var states = new TreeMap<String, Object>();
        for (int n = count(in); n > 0; n--) {
            states.put(text(in), value(in));
        }
        return new BlockData(type, states);
    }

    static void value(DataOutput out, Object v) throws IOException {
        if (v instanceof String s) {
            out.writeByte(0);
            text(out, s);
        } else if (v instanceof Integer n) {
            out.writeByte(1);
            out.writeInt(n);
        } else if (v instanceof Boolean b) {
            out.writeByte(2);
            out.writeBoolean(b);
        } else if (v instanceof Double d) {
            out.writeByte(3);
            out.writeDouble(d);
        } else {
            throw new IOException("Unsupported value");
        }
    }

    static Object value(DataInput in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> text(in);
            case 1 -> in.readInt();
            case 2 -> in.readBoolean();
            case 3 -> in.readDouble();
            default -> throw new IOException("Invalid value tag");
        };
    }

    static void item(DataOutput out, ItemStack item) throws IOException {
        out.writeBoolean(item != null);
        if (item != null) {
            item.write(out);
        }
    }

    static ItemStack item(DataInput in) throws IOException {
        return in.readBoolean() ? ItemStack.read(in) : null;
    }
}
