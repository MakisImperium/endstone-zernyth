package dev.zernyth.runtime;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongFunction;

import dev.zernyth.api.entity.Entity;
import dev.zernyth.api.entity.Entity.Velocity;
import dev.zernyth.api.form.ActionForm;
import dev.zernyth.api.form.Form;
import dev.zernyth.api.form.FormRequest;
import dev.zernyth.api.form.FormResponse;
import dev.zernyth.api.form.MessageForm;
import dev.zernyth.api.form.ModalForm;
import dev.zernyth.api.inventory.Inventory;
import dev.zernyth.api.inventory.Inventory.Type;
import dev.zernyth.api.inventory.ItemStack;
import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;
import dev.zernyth.api.world.Block;
import dev.zernyth.api.world.BlockData;
import dev.zernyth.api.world.BlockPosition;
import dev.zernyth.api.world.BlockState;
import dev.zernyth.api.world.Dimension;
import dev.zernyth.api.world.Dimension.ChunkPosition;
import dev.zernyth.api.world.World;

final class Advanced {

    private final long owner;
    private final LongFunction<Player> players;

    Advanced(long owner, LongFunction<Player> players) {
        this.owner = owner;
        this.players = players;
    }

    interface Decoder<T> {

        T read(DataInputStream in) throws IOException;
    }

    private <T> T call(int op, long handle, Wire.Encoder write, Decoder<T> read) {
        byte[] response = Native.advanced(owner, op, handle, Wire.encode(write));
        try (var in = Wire.input(response)) {
            T result = read.read(in);
            if (in.available() != 0) {
                throw new IOException("Trailing native payload");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Invalid native response", e);
        }
    }

    private void action(int op, long handle, Wire.Encoder write) {
        call(op, handle, write, in -> null);
    }

    ItemStack createItem(String type, int amount) {
        Objects.requireNonNull(type);
        if (amount < 1) {
            throw new IllegalArgumentException("Nonpositive item amount");
        }
        return call(1, 0, out -> {
            Wire.text(out, type);
            out.writeInt(amount);
        }, ItemStack::read);
    }

    Inventory inventory(long player, boolean ender) {
        return new Inventory() {
            private void prefix(DataOutput out) throws IOException {
                out.writeBoolean(ender);
            }

            public Type getType() {
                return ender ? Type.ENDER_CHEST : Type.PLAYER;
            }

            private int[] info() {
                return call(10, player, this::prefix, in -> new int[]{in.readInt(), in.readInt()});
            }

            public int getSize() {
                return info()[0];
            }

            public int getMaxStackSize() {
                return info()[1];
            }

            public ItemStack getItem(int slot) {
                return call(11, player, out -> {
                    prefix(out);
                    out.writeInt(slot);
                }, Wire::item);
            }

            public void setItem(int slot, ItemStack item) {
                action(12, player, out -> {
                    prefix(out);
                    out.writeInt(slot);
                    Wire.item(out, item);
                });
            }

            public List<ItemStack> getContents() {
                return call(13, player, this::prefix, in -> {
                    var items = new ArrayList<ItemStack>();
                    for (int n = Wire.count(in); n > 0; n--) {
                        items.add(Wire.item(in));
                    }
                    return Collections.unmodifiableList(items);
                });
            }

            public void setContents(List<ItemStack> items) {
                Objects.requireNonNull(items);
                action(14, player, out -> {
                    prefix(out);
                    out.writeInt(items.size());
                    for (var item : items) {
                        Wire.item(out, item);
                    }
                });
            }

            private Map<Integer, ItemStack> change(List<ItemStack> items, boolean remove) {
                var copy = List.copyOf(items);
                return call(15, player,
                        out
                                -> {
                            prefix(out);
                            out.writeBoolean(remove);
                            out.writeInt(copy.size());
                            for (var item : copy) {
                                item.write(out);
                            }
                        },
                        in -> {
                            var left = new TreeMap<Integer, ItemStack>();
                            for (int n = Wire.count(in); n > 0; n--) {
                                left.put(in.readInt(), ItemStack.read(in));
                            }
                            return Collections.unmodifiableMap(left);
                        });
            }

            public Map<Integer, ItemStack> addItems(List<ItemStack> items) {
                return change(items, false);
            }

            public Map<Integer, ItemStack> removeItems(List<ItemStack> items) {
                return change(items, true);
            }

            public boolean contains(ItemStack item, int amount) {
                if (amount < 0) {
                    throw new IllegalArgumentException("Negative amount");
                }
                Objects.requireNonNull(item);
                return call(16, player, out -> {
                    prefix(out);
                    item.write(out);
                    out.writeInt(amount);
                }, DataInputStream::readBoolean);
            }

            public void clear() {
                action(17, player, out -> {
                    prefix(out);
                    out.writeInt(-1);
                });
            }

            public void clear(int slot) {
                if (slot < 0) {
                    throw new IndexOutOfBoundsException("Negative slot");
                }
                action(17, player, out -> {
                    prefix(out);
                    out.writeInt(slot);
                });
            }
        };
    }

    List<World> worlds() {
        return call(20, 0,
                out
                        -> {
                },
                in -> {
                    var worlds = new ArrayList<World>();
                    for (int n = Wire.count(in); n > 0; n--) {
                        long token = in.readLong();
                        String name = Wire.text(in);
                        worlds.add(new NativeWorld(token, name));
                    }
                    return List.copyOf(worlds);
                });
    }

    private final class NativeWorld implements World {

        final long token;
        final String identifier;

        NativeWorld(long token, String id) {
            this.token = token;
            identifier = id;
        }

        public String getIdentifier() {
            return identifier;
        }

        private String[] info() {
            return call(21, token,
                    out
                            -> {
                    },
                    in -> {
                        int n = Wire.count(in);
                        var data = new String[n];
                        for (int i = 0; i < n; i++) {
                            data[i] = Wire.text(in);
                        }
                        return data;
                    });
        }

        public String getName() {
            return info()[0];
        }

        public List<Dimension> getDimensions() {
            var data = info();
            var result = new ArrayList<Dimension>();
            for (int i = 1; i < data.length; i++) {
                result.add(new NativeDimension(this, data[i]));
            }
            return List.copyOf(result);
        }

        public Optional<Dimension> getDimension(String id) {
            return getDimensions().stream().filter(d -> d.getIdentifier().equals(id)).findFirst();
        }

        public List<Entity> getEntities() {
            return call(30, token,
                    out
                            -> {
                    },
                    in -> {
                        var list = new ArrayList<Entity>();
                        for (int n = Wire.count(in); n > 0; n--) {
                            list.add(new NativeEntity(in.readLong()));
                        }
                        return List.copyOf(list);
                    });
        }
    }

    private final class NativeDimension implements Dimension {

        final NativeWorld world;
        final String name;

        NativeDimension(NativeWorld world, String name) {
            this.world = world;
            this.name = name;
        }

        public World getWorld() {
            return world;
        }

        public String getIdentifier() {
            return name;
        }

        public List<ChunkPosition> getLoadedChunks() {
            return call(22, world.token, out -> Wire.text(out, name), in -> {
                var list = new ArrayList<ChunkPosition>();
                for (int n = Wire.count(in); n > 0; n--) {
                    list.add(new ChunkPosition(in.readInt(), in.readInt()));
                }
                return List.copyOf(list);
            });
        }

        public boolean isChunkLoaded(int x, int z) {
            return getLoadedChunks().contains(new ChunkPosition(x, z));
        }

        public Optional<Block> getBlock(BlockPosition p) {
            Objects.requireNonNull(p);
            return isChunkLoaded(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16))
                    ? Optional.of(new NativeBlock(this, p))
                    : Optional.empty();
        }

        public Optional<Entity> spawnEntity(Location l, String type) {
            Objects.requireNonNull(l);
            if (!name.equals(l.dimension())) {
                throw new IllegalArgumentException("Dimension mismatch");
            }
            long token = call(33, world.token, out -> {
                Wire.text(out, name);
                Wire.loc(out, l);
                Wire.text(out, type);
            }, DataInputStream::readLong);
            return token == 0 ? Optional.empty() : Optional.of(new NativeEntity(token));
        }
    }

    private final class NativeBlock implements Block {

        final NativeDimension dimension;
        final BlockPosition pos;

        NativeBlock(NativeDimension dimension, BlockPosition pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        void address(DataOutput out) throws IOException {
            Wire.text(out, dimension.name);
            Wire.pos(out, pos);
        }

        public BlockPosition getPosition() {
            return pos;
        }

        public Dimension getDimension() {
            return dimension;
        }

        public BlockData getData() {
            return call(23, dimension.world.token, this::address, Wire::block);
        }

        public void setData(BlockData data, boolean physics) {
            action(24, dimension.world.token, out -> {
                address(out);
                Wire.block(out, data);
                out.writeBoolean(physics);
            });
        }

        public Optional<Block> getRelative(int x, int y, int z) {
            return dimension.getBlock(pos.offset(x, y, z));
        }

        public BlockState captureState() {
            long token = call(25, dimension.world.token, this::address, DataInputStream::readLong);
            return new BlockState() {
                public BlockData getData() {
                    return call(26, token, out -> {
                    }, Wire::block);
                }

                public void setData(BlockData data) {
                    action(27, token, out -> Wire.block(out, data));
                }

                public boolean update(boolean force, boolean physics) {
                    return call(28, token, out -> {
                        out.writeBoolean(force);
                        out.writeBoolean(physics);
                    }, DataInputStream::readBoolean);
                }

                public void close() {
                    action(29, token, out -> {
                    });
                }
            };
        }
    }

    Entity playerEntity(long player) {
        return new NativeEntity(call(34, player, out -> {
        }, DataInputStream::readLong));
    }

    private record EntityData(long id, long runtime, String type, boolean valid, boolean dead,
                              Location location, Entity.Velocity velocity, String name, boolean ground,
                              boolean water,
                              boolean lava, OptionalInt health, long player) {

    }

    private final class NativeEntity implements Entity {

        final long token;

        NativeEntity(long token) {
            this.token = token;
        }

        private EntityData data() {
            return call(31, token,
                    out
                            -> {
                    },
                    in
                            -> new EntityData(in.readLong(), in.readLong(), Wire.text(in), in.readBoolean(),
                            in.readBoolean(), Wire.loc(in),
                            new Velocity(in.readDouble(), in.readDouble(), in.readDouble()), Wire.text(in),
                            in.readBoolean(), in.readBoolean(), in.readBoolean(),
                            in.readBoolean() ? OptionalInt.of(in.readInt()) : OptionalInt.empty(),
                            in.readLong()));
        }

        public long getId() {
            return data().id();
        }

        public long getRuntimeId() {
            return data().runtime();
        }

        public String getType() {
            return data().type();
        }

        public boolean isValid() {
            return call(35, token, out -> {
            }, DataInputStream::readBoolean);
        }

        public boolean isDead() {
            return data().dead();
        }

        public Location getLocation() {
            return data().location();
        }

        public Velocity getVelocity() {
            return data().velocity();
        }

        public String getName() {
            return data().name();
        }

        private boolean action(int kind, Wire.Encoder more) {
            return call(32, token, out -> {
                out.writeInt(kind);
                more.write(out);
            }, DataInputStream::readBoolean);
        }

        public boolean teleport(Location l) {
            return action(0, out -> Wire.loc(out, l));
        }

        public void setRotation(float yaw, float pitch) {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Non-finite rotation");
            }
            action(4, out -> {
                out.writeDouble(yaw);
                out.writeDouble(pitch);
            });
        }

        public void setName(String name) {
            action(1, out -> Wire.text(out, name));
        }

        public void remove() {
            action(2, out -> {
            });
        }

        public OptionalInt getHealth() {
            return data().health();
        }

        public void setHealth(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("Negative health");
            }
            action(3, out -> out.writeInt(value));
        }

        public boolean isOnGround() {
            return data().ground();
        }

        public boolean isInWater() {
            return data().water();
        }

        public boolean isInLava() {
            return data().lava();
        }

        public Optional<Player> asPlayer() {
            long token = data().player();
            return token == 0 ? Optional.empty() : Optional.of(players.apply(token));
        }
    }

    FormRequest showForm(long player, Form form) {
        Objects.requireNonNull(form);
        var future = new CompletableFuture<FormResponse>();
        byte[] payload = Wire.encode(out -> {
            int kind = form instanceof ActionForm ? 0 : form instanceof MessageForm ? 1 : 2;
            out.writeInt(kind);
            Wire.text(out, form.title());
            if (form instanceof ActionForm f) {
                Wire.text(out, f.content());
                out.writeInt(f.buttons().size());
                for (var b : f.buttons()) {
                    Wire.text(out, b.text());
                    out.writeBoolean(b.image() != null);
                    if (b.image() != null) {
                        Wire.text(out, b.image());
                    }
                }
            } else if (form instanceof MessageForm f) {
                Wire.text(out, f.content());
                Wire.text(out, f.firstButton());
                Wire.text(out, f.secondButton());
            } else if (form instanceof ModalForm f) {
                out.writeInt(f.controls().size());
                for (var control : f.controls()) {
                    int tag = control instanceof ModalForm.TextField ? 0
                            : control instanceof ModalForm.Toggle ? 1
                            : control instanceof ModalForm.Slider ? 2
                            : control instanceof ModalForm.Dropdown ? 3
                            : 4;
                    out.writeInt(tag);
                    Wire.text(out, control.label());
                    if (control instanceof ModalForm.TextField c) {
                        Wire.text(out, c.placeholder());
                        Wire.text(out, c.defaultValue());
                    } else if (control instanceof ModalForm.Toggle c) {
                        out.writeBoolean(c.defaultValue());
                    } else if (control instanceof ModalForm.Slider c) {
                        out.writeDouble(c.min());
                        out.writeDouble(c.max());
                        out.writeDouble(c.step());
                        out.writeDouble(c.defaultValue());
                    } else {
                        List<String> options = control instanceof ModalForm.Dropdown c
                                ? c.options()
                                : ((ModalForm.StepSlider) control).options();
                        int index = control instanceof ModalForm.Dropdown c
                                ? c.defaultIndex()
                                : ((ModalForm.StepSlider) control).defaultIndex();
                        out.writeInt(options.size());
                        for (var option : options) {
                            Wire.text(out, option);
                        }
                        out.writeInt(index);
                    }
                }
            }
        });
        long token = Native.showForm(owner, player, payload, bytes -> {
            try (var in = Wire.input(bytes)) {
                int status = in.readInt();
                var values = new ArrayList<Object>();
                for (int n = Wire.count(in); n > 0; n--) {
                    values.add(Wire.value(in));
                }
                future.complete(new FormResponse(FormResponse.Status.values()[status], values));
            } catch (Exception error) {
                future.completeExceptionally(error);
            }
        });
        return new FormRequest() {
            public CompletionStage<FormResponse> response() {
                return future.minimalCompletionStage();
            }

            public void cancel() {
                if (!future.isDone()) {
                    Native.cancelForm(owner, token);
                }
            }
        };
    }
}
