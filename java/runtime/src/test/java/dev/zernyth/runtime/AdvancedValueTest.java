package dev.zernyth.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.zernyth.api.form.ActionForm;
import dev.zernyth.api.form.ModalForm;
import dev.zernyth.api.inventory.ItemMeta;
import dev.zernyth.api.inventory.ItemStack;
import dev.zernyth.api.world.BlockData;
import dev.zernyth.api.world.BlockPosition;

final class AdvancedValueTest {

    private static void require(boolean b) {
        if (!b) {
            throw new AssertionError("Value API assertion failed");
        }
    }

    private static void invalid(Runnable r) {
        try {
            r.run();
            throw new AssertionError("Invalid value accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    static void run() throws Exception {
        byte[] payload = Wire.encode(out -> {
            Wire.text(out, "minecraft:stone");
            out.writeInt(1);
            out.writeInt(64);
            out.writeInt(0);
            out.writeInt(0);
            out.writeBoolean(false);
            out.writeInt(0);
            out.writeInt(0);
            out.writeBoolean(false);
            out.writeInt(0);
            Wire.text(out, "{\"t\":10,\"v\":{}}");
        });
        ItemStack item = ItemStack.read(Wire.input(payload));
        var lines = new ArrayList<>(List.of("one"));
        var meta = new ItemMeta("Name", lines, 0, false, Map.of());
        lines.add("two");
        ItemStack copy = item.withItemMeta(meta).withAmount(2);
        require(item.getAmount() == 1 && item.getItemMeta().displayName() == null
                && copy.getItemMeta().lore().equals(List.of("one")));
        require(copy.equals(copy.copy()));
        ItemStack decoded = ItemStack.read(Wire.input(Wire.encode(copy::write)));
        require(copy.equals(decoded));
        invalid(() -> item.withAmount(0));
        invalid(() -> item.withAmount(65));
        invalid(() -> item.withItemMeta(meta.withDamage(1)));
        invalid(() -> new ItemMeta(null, List.of(), -1, false, Map.of()));
        invalid(() -> new ItemMeta(null, List.of(), 0, false, Map.of("minecraft:sharpness", 0)));
        invalid(() -> new BlockData("bad identifier", Map.of()));
        invalid(() -> new BlockData("minecraft:stone", Map.of("bad", 2.5)));
        invalid(() -> new ActionForm("", "", List.of()));
        invalid(() -> new ModalForm.Slider("", 0, 1, 0, 0));
        invalid(() -> new ModalForm.Dropdown("", List.of("A"), 1));
        invalid(() -> new ModalForm.StepSlider("", List.of(), 0));
        try {
            new BlockPosition(Integer.MAX_VALUE, 0, 0).offset(1, 0, 0);
            throw new AssertionError();
        } catch (ArithmeticException expected) {
        }
        System.out.println(
                "Advanced Java value/serialization/validation checks passed (no JNI/BDS simulation).");
    }
}
