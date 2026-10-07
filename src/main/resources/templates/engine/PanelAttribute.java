package net.tamashi.fomekcore.api.guisystems;

/**
 * PanelAttribute — Attributes applied to panels (go into the attribute mutator).
 *
 * Draggable: defines a zone (local coordinates) where clicking+dragging moves the panel.
 *   Optional check IDs gate whether the zone is actually draggable — all must return true.
 * Empty: no attribute (default)
 */
public interface PanelAttribute {
    void applyTo(VirtualGuiElement element);

    record Draggable(float x1, float y1, float x2, float y2, String[] checkIds, boolean dragOut, boolean dragIn,
                     boolean highlight, int highlightColor,
                     boolean hasDragBounds, float dragBoundsX1, float dragBoundsY1, float dragBoundsX2, float dragBoundsY2) implements PanelAttribute {
        public Draggable(Box box, String[] checkIds, boolean dragOut, boolean dragIn, boolean highlight, int highlightColor,
                         boolean hasDragBounds, float dbX1, float dbY1, float dbX2, float dbY2) {
            this(box.x1(), box.y1(), box.x2(), box.y2(), checkIds, dragOut, dragIn, highlight, highlightColor,
                 hasDragBounds, dbX1, dbY1, dbX2, dbY2);
        }

        /** Constructor with drag bounds as a Box (used by FomekMenus FTL). */
        public Draggable(Box box, String[] checkIds, boolean dragOut, boolean dragIn, boolean highlight, int highlightColor,
                         boolean hasDragBounds, Box dragBoundsBox) {
            this(box.x1(), box.y1(), box.x2(), box.y2(), checkIds, dragOut, dragIn, highlight, highlightColor,
                 hasDragBounds, dragBoundsBox.x1(), dragBoundsBox.y1(), dragBoundsBox.x2(), dragBoundsBox.y2());
        }

        /** Back-compat constructor without drag bounds. */
        public Draggable(Box box, String[] checkIds, boolean dragOut, boolean dragIn, boolean highlight, int highlightColor) {
            this(box, checkIds, dragOut, dragIn, highlight, highlightColor, false, 0f, 0f, 0f, 0f);
        }

        /** Back-compat constructor without highlight params. */
        public Draggable(Box box, String[] checkIds, boolean dragOut, boolean dragIn) {
            this(box, checkIds, dragOut, dragIn, true, Integer.MIN_VALUE, false, 0f, 0f, 0f, 0f);
        }

        /** Convenience constructor for check-only (no drag out/in) — back-compat. */
        public Draggable(Box box, String[] checkIds) {
            this(box, checkIds, false, false, true, Integer.MIN_VALUE, false, 0f, 0f, 0f, 0f);
        }

        /** Convenience constructor for check-only (no drag out/in) — back-compat. */
        public Draggable(float x1, float y1, float x2, float y2, String[] checkIds) {
            this(x1, y1, x2, y2, checkIds, false, false, true, Integer.MIN_VALUE, false, 0f, 0f, 0f, 0f);
        }

        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setMovable(true);
            element.setDraggableZone(x1, y1, x2, y2);
            if (checkIds != null) {
                element.setDragZoneCheckIds(checkIds);
            }
            element.setCanDragOut(dragOut);
            element.setCanDragIn(dragIn);
            element.setDragHighlight(highlight);
            element.setDragHighlightColor(highlightColor);
            if (hasDragBounds) {
                element.setDragBounds(dragBoundsX1, dragBoundsY1, dragBoundsX2, dragBoundsY2);
            }
        }
    }

    /**
     * DraggableWhole: makes the ENTIRE element draggable (no zone needed).
     * Used for items, textures, and other elements where the whole thing
     * should be grabbable — not just a specific sub-region like a title bar.
     */
    record DraggableWhole(String[] checkIds, boolean dragOut, boolean dragIn, boolean highlight, int highlightColor) implements PanelAttribute {
        /** Back-compat constructor without highlight params. */
        public DraggableWhole(String[] checkIds, boolean dragOut, boolean dragIn) {
            this(checkIds, dragOut, dragIn, true, Integer.MIN_VALUE);
        }

        /** Convenience constructor for check-only (no drag out/in) — back-compat. */
        public DraggableWhole(String[] checkIds) {
            this(checkIds, false, false, true, Integer.MIN_VALUE);
        }

        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setMovable(true);
            // Don't set a drag zone — isInDragZone falls through to isHitDirect
            // (whole element is draggable, but not on top of children)
            if (checkIds != null) {
                element.setDragZoneCheckIds(checkIds);
            }
            element.setCanDragOut(dragOut);
            element.setCanDragIn(dragIn);
            element.setDragHighlight(highlight);
            element.setDragHighlightColor(highlightColor);
        }
    }

    /**
     * Resize: makes the panel resizable from edges/corners (like Windows windows).
     * Click within resizeBorderWidth pixels of an edge to grab and resize.
     * Corners resize both dimensions. Takes priority over Draggable.
     * Optional checks gate whether resizing is allowed.
     * Optional min/max resize X/Y constrain how small/large the panel can get
     * (a value of -1 means no constraint on that axis).
     */
    record Resize(String[] checkIds, float minResizeX, float minResizeY, float maxResizeX, float maxResizeY,
                  boolean highlight, int highlightColor,
                  boolean cornerOnly, boolean aspectRatio,
                  boolean hasResizeBounds, float resizeBoundsX1, float resizeBoundsY1, float resizeBoundsX2, float resizeBoundsY2) implements PanelAttribute {
        /** Convenience constructor for check-only (no min/max constraints). */
        public Resize(String[] checkIds) {
            this(checkIds, -1f, -1f, -1f, -1f, true, Integer.MIN_VALUE, false, false, false, 0f, 0f, 0f, 0f);
        }

        /** Back-compat constructor without highlight params. */
        public Resize(String[] checkIds, float minResizeX, float minResizeY, float maxResizeX, float maxResizeY) {
            this(checkIds, minResizeX, minResizeY, maxResizeX, maxResizeY, true, Integer.MIN_VALUE, false, false, false, 0f, 0f, 0f, 0f);
        }

        /** Back-compat constructor with highlight but without corner/aspect params. */
        public Resize(String[] checkIds, float minResizeX, float minResizeY, float maxResizeX, float maxResizeY,
                      boolean highlight, int highlightColor) {
            this(checkIds, minResizeX, minResizeY, maxResizeX, maxResizeY, highlight, highlightColor, false, false, false, 0f, 0f, 0f, 0f);
        }

        /** Back-compat constructor with corner/aspect but without resize bounds. */
        public Resize(String[] checkIds, float minResizeX, float minResizeY, float maxResizeX, float maxResizeY,
                      boolean highlight, int highlightColor, boolean cornerOnly, boolean aspectRatio) {
            this(checkIds, minResizeX, minResizeY, maxResizeX, maxResizeY, highlight, highlightColor, cornerOnly, aspectRatio, false, 0f, 0f, 0f, 0f);
        }

        /** Constructor with resize bounds as a Box (used by FomekMenus FTL). */
        public Resize(String[] checkIds, float minResizeX, float minResizeY, float maxResizeX, float maxResizeY,
                      boolean highlight, int highlightColor, boolean cornerOnly, boolean aspectRatio,
                      boolean hasResizeBounds, Box resizeBoundsBox) {
            this(checkIds, minResizeX, minResizeY, maxResizeX, maxResizeY, highlight, highlightColor, cornerOnly, aspectRatio,
                 hasResizeBounds, resizeBoundsBox.x1(), resizeBoundsBox.y1(), resizeBoundsBox.x2(), resizeBoundsBox.y2());
        }

        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setResizable(true);
            if (checkIds != null) {
                element.setResizeCheckIds(checkIds);
            }
            element.setMinResizeX(minResizeX);
            element.setMinResizeY(minResizeY);
            element.setMaxResizeX(maxResizeX);
            element.setMaxResizeY(maxResizeY);
            element.setResizeHighlight(highlight);
            element.setResizeHighlightColor(highlightColor);
            element.setCornerResizeOnly(cornerOnly);
            element.setMaintainAspectRatio(aspectRatio);
            if (hasResizeBounds) {
                element.setResizeBounds(resizeBoundsX1, resizeBoundsY1, resizeBoundsX2, resizeBoundsY2);
            }
        }
    }

    /**
     * Button: makes the panel act as a clickable button.
     * If combined with Draggable, click-vs-drag is auto-resolved:
     * drag if mouse moves beyond threshold, click if released without moving.
     */
    record Button(String actionId, String[] checkIds) implements PanelAttribute {
        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setHasButtonAttribute(true);
            element.setButtonActionId(actionId);
            if (checkIds != null) {
                element.setButtonCheckIds(checkIds);
            }
        }
    }

    /**
     * Grid: makes the panel/scroll-view a snap grid for dragged children.
     * box defines the grid area (local coordinates relative to the panel).
     * orientation: "horizontal", "vertical", or "both".
     * snapToEnd: true = snap to end of list (like folders); false = snap to closest cell center (like Windows desktop).
     * cellSize: grid cell size (used for both x/y). -1 = auto-compute from biggest child.
     * hasCellSize: whether cellSize was explicitly set by the user.
     */
    record Grid(Box box, String orientation, String direction, int cellsAmount, int rowsAmount,
                float beginX, float beginY,
                boolean snapToEnd, float cellSize, boolean hasCellSize,
                boolean renderGrid, int renderColor) implements PanelAttribute {
        
        /** Back-compat constructor for old generated code (pre-v26 grid signature). */
        public Grid(Box box, String orientation, boolean snapToEnd, float cellSize, boolean hasCellSize,
                    boolean renderGrid, int renderColor) {
            this(box, orientation, "horizontal", 0, 0, 0f, 0f, snapToEnd, cellSize, hasCellSize, renderGrid, renderColor);
        }
        
        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setGrid(box, orientation, direction, cellsAmount, rowsAmount,
                    beginX, beginY,
                    snapToEnd, cellSize, hasCellSize, renderGrid, renderColor);
        }
    }

    /**
     * DropTarget: marks this element as accepting drops from other draggable
     * elements. When a draggable element is released with its center point
     * within this element's bounds, the dropActionId is called.
     * The action can cancel (cancelAction) to snap the dragged element back.
     */
    record DoubleClick(String actionId, String[] checkIds) implements PanelAttribute {
        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setDoubleClickActionId(actionId);
        }
    }

    record RightClick(String actionId, String[] checkIds) implements PanelAttribute {
        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setRightClickActionId(actionId);
        }
    }

    record DropTarget(String dropActionId, String[] checkIds) implements PanelAttribute {
        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setDropTarget(true);
            element.setDropActionId(dropActionId);
            if (checkIds != null) {
                element.setDropCheckIds(checkIds);
            }
        }
    }

    /**
     * ExcludeFromGrid: prevents this element from being snapped to a parent's
     * grid during drag/drop. Also prevents reparenting into a grid panel.
     * Use for floating windows that should float freely over a grid panel.
     */
    record ExcludeFromGrid(String[] checkIds) implements PanelAttribute {
        public ExcludeFromGrid() {
            this(new String[0]);
        }

        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setExcludeFromGrid(true);
        }
    }

    /**
     * DragBounds: constrains where the panel can be dragged (tighter than
     * screen/boundary). Use to exclude areas like taskbars from the drag zone.
     * box is in absolute screen coordinates.
     */
    record DragBounds(Box box, String[] checkIds) implements PanelAttribute {
        public DragBounds(Box box) { this(box, new String[]{}); }
        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setDragBounds(box.x1(), box.y1(), box.x2(), box.y2());
        }
    }

    /**
     * ResizeBounds: constrains where the panel can be resized (the panel's
     * edges cannot extend beyond this box). Tighter than screen/boundary bounds.
     */
    record ResizeBounds(Box box, String[] checkIds) implements PanelAttribute {
        public ResizeBounds(Box box) { this(box, new String[]{}); }
        @Override
        public void applyTo(VirtualGuiElement element) {
            element.setResizeBounds(box.x1(), box.y1(), box.x2(), box.y2());
        }
    }

    record Empty() implements PanelAttribute {
        @Override
        public void applyTo(VirtualGuiElement element) {}
    }
}
