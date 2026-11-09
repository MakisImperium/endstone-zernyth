package dev.zernyth.fixture;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.zernyth.api.entity.Entity;
import dev.zernyth.api.form.ActionForm;
import dev.zernyth.api.form.FormRequest;
import dev.zernyth.api.form.FormResponse;
import dev.zernyth.api.form.MessageForm;
import dev.zernyth.api.form.ModalForm;
import dev.zernyth.api.inventory.Inventory;
import dev.zernyth.api.inventory.ItemMeta;
import dev.zernyth.api.inventory.ItemStack;
import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;
import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.world.Block;
import dev.zernyth.api.world.BlockData;
import dev.zernyth.api.world.BlockPosition;
import dev.zernyth.api.world.Dimension;
import dev.zernyth.api.world.World;

/**
 * Assertion-driven gameplay JNI fixture for native mocks. Real BDS/client validation is a separate step.
 */
public final class AdvancedPlugin extends JavaPlugin {

    private Player player;
    private Inventory inventory;
    private ItemStack item;
    private World world;
    private Dimension dimension;
    private Block block;
    private Entity entity;
    private FormRequest action, message, modal, validModal, malformed, closed, disconnected, pending;
    private final AtomicBoolean workerRejected = new AtomicBoolean();
    private final AtomicBoolean disconnectFormRejected = new AtomicBoolean();

    private static void check(boolean test) {
        if (!test) {
            throw new AssertionError("Advanced fixture failed");
        }
    }

    private static void rejected(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected rejection");
        } catch (IllegalStateException expected) {
        }
    }

    private static FormResponse response(FormRequest request) {
        var result = request.response().toCompletableFuture().getNow(null);
        check(result != null);
        return result;
    }

    private ActionForm action() {
        return new ActionForm("Action", "Choose",
                List.of(new ActionForm.Button("First", "textures/items/diamond"),
                        new ActionForm.Button("Second")));
    }

    private ModalForm modal() {
        return new ModalForm("Modal",
                List.of(new ModalForm.TextField("Text", "hint", "default"),
                        new ModalForm.Toggle("Toggle", false), new ModalForm.Slider("Slider", 0, 2, 0.5f, 1),
                        new ModalForm.Dropdown("Dropdown", List.of("A", "B"), 0),
                        new ModalForm.StepSlider("Step", List.of("X", "Y"), 0)));
    }

    @Override
    public void onEnable() {
        String label = getDescription().name().equals("advanced_peer") ? "advanced_peer" : "advanced";
        getServer().registerCommand(label, "Advanced JNI fixture", "", (sender, command, args) -> {
            switch (args[0]) {
                case "init" -> init();
                case "unloaded" -> {
                    check(!dimension.isChunkLoaded(0, 0));
                    check(dimension.getBlock(new BlockPosition(1, 2, 3)).isEmpty());
                    rejected(() -> block.getData());
                }
                case "world-off" -> {
                    rejected(() -> world.getName());
                    rejected(() -> dimension.getLoadedChunks());
                }
                case "world-back" -> {
                    rejected(() -> world.getName());
                    world = getServer().getWorlds().getFirst();
                    dimension = world.getDimension("overworld").orElseThrow();
                }
                case "entity-removed" -> {
                    check(!entity.isValid());
                    rejected(() -> entity.getType());
                }
                case "entity-reused" -> {
                    check(!entity.isValid());
                    check(world.getEntities().stream().anyMatch(
                            e -> e.getId() == 42 && e.getRuntimeId() == 421));
                }
                case "entity-refresh" -> {
                    entity = world.getEntities()
                            .stream()
                            .filter(e -> e.getId() == 42)
                            .findFirst()
                            .orElseThrow();
                    check(entity.isValid());
                }
                case "remove" -> {
                    var current = world.getEntities()
                            .stream()
                            .filter(e -> e.getRuntimeId() == 421)
                            .findFirst()
                            .orElseThrow();
                    current.remove();
                    check(!current.isValid());
                }
                case "forms" -> {
                    action = player.showForm(action());
                    message = player.showForm(new MessageForm("Message", "Content", "Yes", "No"));
                    modal = player.showForm(modal());
                    validModal = player.showForm(modal());
                    malformed = player.showForm(modal());
                }
                case "foreign-pending" -> check(!action.response().toCompletableFuture().isDone());
                case "overflow-form" -> malformed = player.showForm(modal());
                case "overflow-check" ->
                        check(response(malformed).status() == FormResponse.Status.INVALID_RESPONSE);
                case "peer-form" -> {
                    player = getServer().getOnlinePlayers().getFirst();
                    pending = player.showForm(action());
                }
                case "peer-pending" -> check(!pending.response().toCompletableFuture().isDone());
                case "peer-submitted" -> check(response(pending).status() == FormResponse.Status.SUBMITTED);
                case "forms-check" -> {
                    check(response(action).status() == FormResponse.Status.INVALID_RESPONSE);
                    check(response(message).values().equals(List.of(0)));
                    check(response(modal).status() == FormResponse.Status.INVALID_RESPONSE);
                    check(response(validModal).values().equals(List.of("value", true, 1.5, 1, 0)));
                    check(response(malformed).status() == FormResponse.Status.INVALID_RESPONSE);
                    var cancelled = player.showForm(action());
                    cancelled.cancel();
                    check(response(cancelled).status() == FormResponse.Status.CANCELLED);
                    closed = player.showForm(action());
                }
                case "closed" -> {
                    check(response(closed).status() == FormResponse.Status.CLOSED);
                    disconnected = player.showForm(action());
                    disconnected.response().thenAccept(result -> {
                        try {
                            player.showForm(action());
                        } catch (IllegalStateException expected) {
                            disconnectFormRejected.set(true);
                        }
                    });
                }
                case "disconnect" -> {
                    check(response(disconnected).status() == FormResponse.Status.DISCONNECTED
                            && disconnectFormRejected.get());
                    rejected(() -> inventory.getSize());
                }
                case "async" -> check(workerRejected.get());
                case "pending" -> {
                    pending = player.showForm(action());
                    pending.response().thenAccept(result -> {
                        if (result.status() == FormResponse.Status.PLUGIN_DISABLED) {
                            try {
                                Files.writeString(
                                        Path.of("advanced-forms-disabled.marker"), "PLUGIN_DISABLED");
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        }
                    });
                }
                case "invalid" -> invalid();
                default -> throw new AssertionError("Unknown test phase");
            }
            return true;
        });
    }

    private void init() {
        player = getServer().getOnlinePlayers().getFirst();
        inventory = player.getInventory();
        check(inventory.getSize() == 4 && player.getEnderChest().getType() == Inventory.Type.ENDER_CHEST);
        rejected(() -> inventory.getItem(-1));
        rejected(() -> inventory.getItem(4));
        item = getServer().createItemStack("zernyth:test_item", 2);
        check(item.getMaxStackSize() == 64 && item.getMaxDurability() == 100 && item.equals(item.copy()));
        var meta = new ItemMeta(
                "Named 😀", List.of("Line one", "Line two"), 3, true, Map.of("minecraft:sharpness", 2));
        ItemStack changed = item.withItemMeta(meta);
        check(item.getItemMeta().displayName() == null);
        try {
            item.withAmount(65);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
        rejected(() -> getServer().createItemStack("zernyth:test_item", 65));
        rejected(() -> getServer().createItemStack("minecraft:air", 1));
        inventory.setItem(0, changed);
        check(inventory.getItem(0).getItemMeta().equals(meta));
        check(changed.equals(inventory.getItem(0)));
        check(inventory.contains(changed, 2));
        inventory.addItems(List.of(changed.withAmount(1)));
        inventory.removeItems(List.of(changed.withAmount(1)));
        check(inventory.getContents().size() == 4);
        inventory.setContents(Arrays.asList(changed, null));
        inventory.clear(1);
        inventory.clear();
        inventory.setItem(0, changed);
        rejected(() -> inventory.setContents(Arrays.asList(changed, changed, changed, changed, changed)));
        world = getServer().getWorlds().getFirst();
        check(world.getName().equals("test-world"));
        dimension = world.getDimension("overworld").orElseThrow();
        check(dimension.isChunkLoaded(0, 0));
        block = dimension.getBlock(new BlockPosition(1, 2, 3)).orElseThrow();
        check(block.getType().equals("minecraft:stone"));
        check(block.getRelative(1, 0, 0).isPresent());
        check(dimension.getBlock(new BlockPosition(-1, 2, -1)).isEmpty());
        var captured = block.captureState();
        try (captured) {
            check(captured.getData().type().equals("minecraft:stone"));
            captured.setData(new BlockData("minecraft:dirt", Map.of()));
            check(captured.update(true, false));
        }
        rejected(() -> captured.getData());
        block.setType("minecraft:stone");
        entity = world.getEntities().stream().filter(e -> e.getId() == 42).findFirst().orElseThrow();
        check(entity.isValid() && entity.getRuntimeId() == 420 && entity.getType().equals("minecraft:pig"));
        check(entity.getHealth().orElseThrow() == 15 && entity.getVelocity().x() == 1 && !entity.isDead());
        entity.setName("Pig");
        entity.setHealth(7);
        check(entity.getName().equals("Pig") && entity.getHealth().orElseThrow() == 7);
        entity.setRotation(90, 45);
        rejected(() -> entity.setHealth(21));
        check(entity.teleport(new Location("overworld", 2, 3, 4, 5, 6)));
        check(player.asEntity().asPlayer().orElseThrow().getName().equals("Alex"));
        rejected(() -> player.asEntity().remove());
        check(dimension.spawnEntity(new Location("overworld", 2, 3, 4, 0, 0), "minecraft:pig").isPresent());
        getServer().getScheduler().runTaskAsync(() -> {
            try {
                block.getData();
                throw new AssertionError("Worker world access succeeded");
            } catch (IllegalStateException expected) {
                workerRejected.set(true);
            }
        });
    }

    private void invalid() {
        try {
            var field = getServer().getClass().getDeclaredField("owner");
            field.setAccessible(true);
            long owner = field.getLong(getServer());
            var method =
                    Class.forName("dev.zernyth.runtime.Native")
                            .getDeclaredMethod("advanced", long.class, int.class, long.class, byte[].class);
            method.setAccessible(true);
            for (int op : new int[]{21, 31}) {
                try {
                    method.invoke(null, owner, op, Long.MAX_VALUE, new byte[0]);
                    throw new AssertionError("Invalid handle accepted");
                } catch (InvocationTargetException expected) {
                    check(expected.getCause() instanceof IllegalStateException);
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
