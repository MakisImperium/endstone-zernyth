package dev.zernyth.example;

import java.util.List;
import java.util.Objects;

import dev.zernyth.api.command.PlayerSender;
import dev.zernyth.api.event.EventPriority;
import dev.zernyth.api.event.Registration;
import dev.zernyth.api.form.ActionForm;
import dev.zernyth.api.form.ActionForm.Button;
import dev.zernyth.api.network.PacketFilter;
import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.scheduler.TaskHandle;
import dev.zernyth.api.world.BlockPosition;

/**
 * Example diagnostic commands and lifecycle-owned cleanup. Running this plugin is not an automated BDS
 * validation result.
 */
public final class HelloZernyth extends JavaPlugin {

    private Registration networkDiagnostics;
    private long observedPackets;
    private TaskHandle asyncDemo;

    @Override
    public void onEnable() {
        getLogger().info("Zernyth Java Plugin enabled!");
        getServer().registerCommand("zernythasync", "start/cancel/status CPU demo", "zernyth.diagnostics",
                (sender, command, args) -> {
                    var runtime = getServer().getAsyncRuntime();
                    if (args.length == 1 && args[0].equals("start")) {
                        if (asyncDemo != null && !asyncDemo.snapshot().terminal()) {
                            sender.sendMessage("Task still active.");
                            return true;
                        }
                        asyncDemo = runtime.submitCpu(token -> {
                            long sum = 0;
                            for (int i = 0; i < 1_000_000; i++) {
                                if ((i & 1023) == 0) {
                                    token.throwIfCancellationRequested();
                                }
                                sum += i;
                            }
                            long result = sum;
                            token.throwIfCancellationRequested();
                            runtime.returnToServer(() -> getLogger().info("Async demo result: " + result));
                        });
                    } else if (args.length == 1 && args[0].equals("cancel") && asyncDemo != null) {
                        asyncDemo.cancel();
                    }
                    sender.sendMessage("Async runtime: " + runtime.snapshot()
                            + "; task: " + (asyncDemo == null ? "none" : asyncDemo.snapshot()));
                    sender.sendMessage(
                            "JVM: " + getServer().getPerformanceManager().getRuntimeMemorySnapshot());
                    return true;
                });
        getServer().registerCommand("zernythdiag", "Own plugin status and resource counts",
                "zernyth.diagnostics", (sender, command, args) -> {
                    var d = getDiagnostics();
                    sender.sendMessage(d.name() + ": " + d.status() + ", load ns=" + d.loadNanos()
                            + ", enable ns=" + d.enableNanos() + ", tasks=" + d.tasks() + ", services="
                            + d.services() + ", listeners=" + (d.eventListeners() + d.packetListeners()));
                    return true;
                });
        getServer().registerCommand("zernythnet", "Toggle read-only packet counting/status",
                "zernyth.network.diagnostics", (sender, command, args) -> {
                    var network = getServer().getNetworkManager();
                    if (args.length == 1 && args[0].equals("start")) {
                        if (networkDiagnostics == null) {
                            networkDiagnostics = network.listen(PacketFilter.all(),
                                    EventPriority.MONITOR, packet -> ++observedPackets);
                        }
                    } else if (args.length == 1 && args[0].equals("stop")) {
                        if (networkDiagnostics != null) {
                            networkDiagnostics.close();
                            networkDiagnostics = null;
                        }
                    }
                    sender.sendMessage("Protocol: " + network.getProtocol() + ", capabilities: "
                            + network.getCapabilities() + ", observed: " + observedPackets);
                    return true;
                });
        getServer().registerCommand("zernythdemo", "Explicit inventory/world/forms demonstration",
                "zernyth.demo", (sender, command, args) -> {
                    if (!(sender instanceof PlayerSender playerSender)) {
                        sender.sendMessage("Run this command as a player.");
                        return true;
                    }
                    var player = playerSender.getPlayer();
                    var at = player.getLocation();
                    sender.sendMessage("Inventory slots: " + player.getInventory().getSize() + ", occupied: "
                            + player.getInventory()
                            .getContents()
                            .stream()
                            .filter(Objects::nonNull)
                            .count());
                    var gift = getServer().createItemStack("minecraft:diamond", 1);
                    var left = player.getInventory().addItems(List.of(gift));
                    sender.sendMessage(
                            left.isEmpty() ? "One diamond added." : "Inventory full; item was not added.");
                    var world = getServer().getWorlds().getFirst();
                    sender.sendMessage("World: " + world.getName() + ", position: " + at);
                    var dimension = world.getDimension(at.dimension()).orElseThrow();
                    var position = new BlockPosition(
                            (int) Math.floor(at.x()), (int) Math.floor(at.y()) - 1, (int) Math.floor(at.z()));
                    var block = dimension.getBlock(position);
                    sender.sendMessage(block.isPresent() ? "Block below: " + block.get().getType()
                            : "Chunk is not loaded.");
                    var entity = world.getEntities().stream().findFirst();
                    sender.sendMessage(entity.isPresent() ? "Existing entity: " + entity.get().getType()
                            + " #" + entity.get().getId()
                            : "No entities available.");
                    var form =
                            new ActionForm("Zernyth 0.7.0", "Native Java gameplay demo",
                                    List.of(new Button("OK")));
                    player.showForm(form).response().thenAccept(
                            response -> getLogger().info("Form completed: " + response.status()));
                    return true;
                });
    }

    @Override
    public void onDisable() {
        if (asyncDemo != null) {
            asyncDemo.cancel();
        }
        asyncDemo = null;
        networkDiagnostics = null;
        getLogger().info("Zernyth Java Plugin disabled!");
    }
}
