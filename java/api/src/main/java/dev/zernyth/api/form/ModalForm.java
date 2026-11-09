package dev.zernyth.api.form;

import java.util.List;
import java.util.Objects;

/**
 * Immutable custom form containing at most 256 copied controls. Response values follow control order.
 *
 * @param title the form title
 * @param controls the ordered modal controls, copied
 */
public record ModalForm(String title, List<Control> controls) implements Form {

    /**
     * Copies the ordered controls and rejects more than 256 entries.
     *
     * @param title the form title
     * @param controls the ordered modal controls, copied
     */
    public ModalForm {
        Objects.requireNonNull(title);
        controls = List.copyOf(controls);
        if (controls.size() > 256) {
            throw new IllegalArgumentException("Too many controls");
        }
    }

    /**
     * Immutable custom-form control description, safe to construct on workers.
     */
    public sealed interface Control permits TextField, Toggle, Slider, Dropdown, StepSlider {

        /**
         * Returns the modal control label.
         *
         * @return the control label
         */
        String label();
    }

    /**
     * Text-input control with a label, placeholder and initial value.
     *
     * @param label the control label
     * @param placeholder the empty-field hint
     * @param defaultValue the initial control value or optional argument default
     */
    public record TextField(String label, String placeholder, String defaultValue) implements Control {

        /**
         * Requires non-null label, placeholder and initial text; empty text is permitted.
         *
         * @param label the control label
         * @param placeholder the empty-field hint
         * @param defaultValue the initial control value or optional argument default
         */
        public TextField {
            Objects.requireNonNull(label);
            Objects.requireNonNull(placeholder);
            Objects.requireNonNull(defaultValue);
        }
    }

    /**
     * Boolean form control with an initial value.
     *
     * @param label the control label
     * @param defaultValue the initial control value or optional argument default
     */
    public record Toggle(String label, boolean defaultValue) implements Control {

        /**
         * Requires a label and captures the initial boolean selection.
         *
         * @param label the control label
         * @param defaultValue the initial control value or optional argument default
         */
        public Toggle {
            Objects.requireNonNull(label);
        }
    }

    /**
     * Numeric form control. Bounds, step and default must be finite; step must be positive and the default
     * must be in range.
     *
     * @param label the control label
     * @param min the inclusive lower bound
     * @param max the inclusive upper bound
     * @param step the positive slider increment
     * @param defaultValue the initial control value or optional argument default
     */
    public record Slider(String label, float min, float max, float step, float defaultValue)
            implements Control {

        /**
         * Rejects nonfinite values, reversed bounds, a nonpositive step or an out-of-range initial value.
         *
         * @param label the control label
         * @param min the inclusive lower bound
         * @param max the inclusive upper bound
         * @param step the positive slider increment
         * @param defaultValue the initial control value or optional argument default
         */
        public Slider {
            Objects.requireNonNull(label);
            if (!Float.isFinite(min) || !Float.isFinite(max) || !Float.isFinite(step)
                    || !Float.isFinite(defaultValue) || min > max || step <= 0 || defaultValue < min
                    || defaultValue > max) {
                throw new IllegalArgumentException("Invalid slider");
            }
        }
    }

    /**
     * Selection control with one to 256 copied options and a valid zero-based default index.
     *
     * @param label the control label
     * @param options the immutable task, profiler or control settings
     * @param defaultIndex the valid zero-based initial option index
     */
    public record Dropdown(String label, List<String> options, int defaultIndex) implements Control {

        /**
         * Copies the options and validates a zero-based default index within one to 256 choices.
         *
         * @param label the control label
         * @param options the immutable task, profiler or control settings
         * @param defaultIndex the valid zero-based initial option index
         */
        public Dropdown {
            Objects.requireNonNull(label);
            options = List.copyOf(options);
            choice(options, defaultIndex);
        }
    }

    /**
     * Stepped selection control with one to 256 copied options and a valid zero-based default index.
     *
     * @param label the control label
     * @param options the immutable task, profiler or control settings
     * @param defaultIndex the valid zero-based initial option index
     */
    public record StepSlider(String label, List<String> options, int defaultIndex) implements Control {

        /**
         * Copies the steps and validates a zero-based default index within one to 256 choices.
         *
         * @param label the control label
         * @param options the immutable task, profiler or control settings
         * @param defaultIndex the valid zero-based initial option index
         */
        public StepSlider {
            Objects.requireNonNull(label);
            options = List.copyOf(options);
            choice(options, defaultIndex);
        }
    }

    private static void choice(List<String> options, int index) {
        if (options.isEmpty() || options.size() > 256 || index < 0 || index >= options.size()) {
            throw new IllegalArgumentException("Invalid selection control");
        }
    }
}
