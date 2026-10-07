package net.tamashi.fomekcore.api.guisystems;

/**
 * Box — A simple rectangle defined by two corners (x1, y1) and (x2, y2).
 * Used as a value block in MCreator blocks wherever a region/area is needed.
 */
public record Box(float x1, float y1, float x2, float y2) {
    public float getWidth() { return x2 - x1; }
    public float getHeight() { return y2 - y1; }
}
