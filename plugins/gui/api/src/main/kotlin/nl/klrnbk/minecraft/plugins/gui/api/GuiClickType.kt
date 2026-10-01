package nl.klrnbk.minecraft.plugins.gui.api

/**
 * How a slot was clicked, derived from the Minecraft container click modes
 * (`PICKUP`, `QUICK_MOVE`, `SWAP`, `CLONE`, `THROW`, `QUICK_CRAFT`, `PICKUP_ALL`) and their buttons.
 */
public enum class GuiClickType {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,

    /** Middle mouse button (only sent by clients in creative mode). */
    MIDDLE,

    /** A hotbar number key (see `GuiClickEvent.hotbarSlot`). */
    NUMBER_KEY,

    /** The offhand-swap key (F by default). */
    OFFHAND_SWAP,

    /** The drop key (Q) over a slot. */
    DROP,

    /** Ctrl + drop key over a slot. */
    CONTROL_DROP,

    /** Double left click: "collect all matching items to the cursor". */
    DOUBLE_CLICK,

    /** Left click outside the window. */
    OUTSIDE_LEFT,

    /** Right click outside the window. */
    OUTSIDE_RIGHT,

    /** A click that is part of a left-button drag. Reported through `GuiDragEvent`. */
    DRAG_LEFT,

    /** A click that is part of a right-button drag. Reported through `GuiDragEvent`. */
    DRAG_RIGHT,

    /** A click that is part of a middle-button drag (creative mode). Reported through `GuiDragEvent`. */
    DRAG_MIDDLE,

    /** A click the framework could not classify (a newer protocol added something). */
    UNKNOWN,
    ;

    public val isLeftClick: Boolean get() = this == LEFT || this == SHIFT_LEFT || this == OUTSIDE_LEFT || this == DOUBLE_CLICK
    public val isRightClick: Boolean get() = this == RIGHT || this == SHIFT_RIGHT || this == OUTSIDE_RIGHT
    public val isShiftClick: Boolean get() = this == SHIFT_LEFT || this == SHIFT_RIGHT
    public val isDrag: Boolean get() = this == DRAG_LEFT || this == DRAG_RIGHT || this == DRAG_MIDDLE

    public companion object {
        /**
         * The click types that trigger an item's plain `onClick { }` handler: the four gestures every
         * platform can produce. Drops, number keys, offhand swaps and so on must be requested explicitly with
         * `onClick(GuiClickType.DROP) { }`, so an accidental Q press cannot run a command.
         */
        public val PRIMARY: Set<GuiClickType> = setOf(LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT)
    }
}

/** Which part of the open screen a click landed on. */
public enum class SlotArea {
    /** A slot of the GUI itself. */
    GUI,

    /** A slot of the player's own inventory, shown below the GUI. */
    PLAYER_INVENTORY,

    /** Outside the window (dropping the cursor item). */
    OUTSIDE,
}
