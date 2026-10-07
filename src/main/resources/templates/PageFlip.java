package net.tamashi.fomekcore.api.guisystems;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

/**
 * FomekMenus PageFlip — animated book page-turn effect for menu panels.
 * Same technique as the Legends Mod "Book of Legends" flip:
 *   - While idle, every frame the finished overlay frame is blitted
 *     (glBlitFramebuffer) into an offscreen texture ("previous").
 *   - When a flip starts, the panel content must switch to the new page
 *     immediately (the user changes their page variable). The first
 *     animated frame blits that new state into a second texture ("next").
 *   - During the flip the effect redraws the region on top of the screen:
 *     a stationary half + a "lifting" page made of 24 vertical strips
 *     sampled from the snapshots, with bend, shading, UV mirroring past
 *     the midpoint and a soft shadow band at the spine.
 *
 * Two driving modes:
 *   - Timed:  start(forward, durationMs) — plays once by itself.
 *   - Manual: grab(forward) + setAmount(0..1) every frame + release(mode).
 *     Bind the amount to mouse movement to let the player physically
 *     grab a page corner and wiggle it back and forth.
 *
 * Works with ALL menu components (render rect/texture/text/item, sliders,
 * buttons, scroll views...) — the effect replays pixels of the rendered
 * frame, so anything drawn on the panel flips with the page.
 *
 * Injected automatically into this package by the FomekMenus Java plugin
 * (see .fomekmenus_pageflip_stamp). Generated menu render triggers call
 * beginRender/endRender around your procedure, so update() knows which
 * GuiGraphicsExtractor to draw on. Page flip blocks therefore only need to be
 * placed inside menu render procedures (menu system / menu part).
 */
public final class PageFlip {

    private static final int STRIPS = 24;

    /**
     * Opaque color drawn under the book art wherever the art has a
     * transparent cutout (the paper hole) and no sheet lies on that
     * half — the "inside of the book" look. Without it the cutout shows
     * the live game world (the art is drawn directly over the world),
     * which reads as a hole straight through the book. ARGB.
     * Change this to tint the empty cover sides differently.
     */
    public static int BACKING_COLOR = 0xFF2A1C10;

    /**
     * v2.10.19: the solid backing quad under the book art is now OFF by
     * default. With fully opaque custom book art it was never visible,
     * and page textures with transparent padding showed it as a brown
     * rectangle behind the book (idle AND during flips, since the flip
     * path also draws backing under the moving sheet). Set to true to
     * restore the pre-2.10.19 look.
     */
    public static boolean DRAW_BACKING = false;

    /**
     * SPINE OFFSET for the Book system, in TEXTURE pixels of the book
     * art (book.png): this many pixels of the art's inner (spine-side)
     * edge are cut off before display, and the page faces follow the
     * same transform — the two page halves then meet (and slightly
     * overlap) at the center with no duplicated binding strip between
     * them. 0 disables the crop. Set per-book by Book.spineOffset();
     * default 7 — the exact measured margin on the reference book.png
     * / page.png pair (146x181 / 132x165: (146-132)/2 = 7), so stock
     * removes the brown binding strip completely with zero overlap.
     */
    private static int spineOffsetPx = 7;

    /** Set the spine offset (book art texture px). Negative counts as 0. */
    public static void setSpineOffset(int texturePx) {
        spineOffsetPx = Math.max(0, texturePx);
    }

    /** Effective crop, clamped so at least one texture column remains. */
    private static int spineCrop(int textureWidth) {
        if (textureWidth <= 1) return 0;
        return Math.min(spineOffsetPx, textureWidth - 1);
    }

    /**
     * Screen x of texture coordinate texX inside a half rect, honoring
     * the spine crop. Mirrored halves (left pages) map right-to-left,
     * so the texture's spine side always lands on the screen spine side.
     */
    private static float spineMapX(float texX, int texW, float x, float w, boolean mirrored) {
        float s = spineCrop(texW);
        float k = w / (texW - s);
        return mirrored ? (x + w - (texX - s) * k) : (x + (texX - s) * k);
    }

    /**
     * Displayed u fraction (0..1 within the SAMPLED region) of a texture
     * x coordinate, honoring the spine crop.
     */
    private static float spineU(float texX, int texW) {
        float s = spineCrop(texW);
        return (texX - s) / (texW - s);
    }

    /**
     * Book-art quad with the spine crop applied: samples the texture
     * from the crop line to the outer edge, so the inner binding strip
     * (brown part) is never displayed. Mirroring is handled by
     * drawQuadUV as usual — the same crop works for both halves.
     */
    private static void drawQuadSpined(GuiGraphicsExtractor g, String texture,
                                       float x, float y, float w, float h, boolean mirrored) {
        int[] size = pngSize(texture);
        float u0 = size[0] > 0 ? spineCrop(size[0]) / (float) size[0] : 0f;
        drawQuadUV(g, texture, x, y, w, h, u0, 0f, 1f, 1f, mirrored);
    }

    /**
     * One half's LOOK for the Book system: what a half of the open book
     * shows beneath the flipping sheet — the frame art (bg), the paper
     * cutout geometry (face, only used to compute the ring), and whether
     * a sheet lies on this half (sheet). Built by Book for the old and
     * the new spread; see update(x, y, pw, h, oldLeft, ...).
     */
    public static final class Look {
        /** Frame art texture for this half (the book texture). */
        public String bg;
        /** Face texture lying on this half (defines the paper cutout). */
        public String face;
        /** True if a sheet lies on this half (ring-only overlay). */
        public boolean sheet;

        public Look() {}

        public Look(String bg, String face, boolean sheet) {
            this.bg = bg;
            this.face = face;
            this.sheet = sheet;
        }
    }

    // Offscreen snapshots of the whole main framebuffer (screen-sized).
    private static TextureTarget previous;
    private static TextureTarget next;
    /**
     * The book art's horizontal "offset to center" slide at the moment
     * each snapshot was captured. The flip composite draws at the
     * CURRENT slide but must sample the snapshots where their content
     * actually sits, so every capture records its slide here. Both stay
     * 0 for books without the offset feature (and for every normal page
     * flip), making the sample offsets 0 — behavior identical to before.
     */
    private static float prevCaptureSlide = 0f;
    private static float nextCaptureSlide = 0f;
    /**
     * v2.10.26: the FROM side of a flip now has its own CLEAN offscreen
     * buffer. `previous` is a glBlitFramebuffer copy of the WHOLE main
     * framebuffer — world, GUI dim and all — so replaying the from-spread
     * from it dragged the captured BACKGROUND along with the page/cover
     * (transparent art pixels showed stale, frozen world; a sliding book
     * showed the background from its capture-time position). The outgoing
     * pass renders ONLY the from-spread's paper, cover faces and page
     * content into `out`, cleared to (0,0,0,0) — exactly like the
     * incoming pass does for `next`. The flip composite then replays the
     * from side from `out` (and the sweeping sheet's front face too), so
     * transparent pixels reveal the LIVE book interior drawn beneath
     * instead of a frozen screenshot. `previous` remains only as a
     * fallback if offscreen capture is unavailable in the environment.
     */
    private static TextureTarget out;
    private static float outCaptureSlide = 0f;

    // ── Timed mode ────────────────────────────────────────────────────────────
    private static long started = 0L;
    private static long durationMs = 320L;

    // ── Manual (grabbed) mode ──────────────────────────────────────────────────
    private static boolean manual = false;
    private static float manualAmount = 0f;

    // ── Release tween (manual grab let go) ─────────────────────────────────────
    private static long releaseStarted = 0L;
    private static long releaseDurationMs = 160L;
    private static float releaseFrom = 0f;
    private static float releaseTo = 1f;

    private static boolean forward = true;
    private static boolean captureNext = false;
    private static boolean nextFresh = false;
    // Guards the captureNext race: on the exact frame a flip starts, the
    // destination page's face texture may not be registered/ready yet
    // (it was often JUST switched to in that same tick). Capturing "next"
    // on that frame bakes a blank/placeholder page into the snapshot that
    // then replays BLANK for the entire flip (the capture only happens
    // once). So capture is postponed, frame by frame, until the face
    // texture(s) actually drawn this frame resolve a real size — capped
    // so a texture that genuinely never resolves can't stall the flip
    // forever.
    private static int captureNextWaitFrames = 0;
    private static final int CAPTURE_NEXT_MAX_WAIT_FRAMES = 20;
    /**
     * v2.10.22: destination-spread capture. The incoming page (the side
     * that is about to be revealed / the back of the turning sheet) is
     * rendered into `next` while that FBO is bound, so its content is
     * never visible on the live screen. incomingBound = FBO currently
     * bound; incomingStarted = cleared at least once this flip;
     * incomingReady = `next` holds the destination spread.
     */
    private static boolean incomingBound = false;
    private static boolean incomingStarted = false;
    private static boolean incomingReady = false;
    /**
     * v2.10.24: flips to false the first time the offscreen bind fails.
     * While false, incoming capture is disabled ENTIRELY — critically,
     * this also stops Page blocks from drawing the destination spread
     * onto the LIVE screen (a failed bind means every "offscreen" draw
     * lands on screen: the book instantly shows content from the side
     * that is about to flip, the exact "renders on the other side
     * already" bug). With capture disabled, a flip falls back to a
     * blank back side instead — ugly, but visible in the log and never
     * a live leak.
     */
    private static boolean incomingSupported = true;
    // v2.10.26: outgoing pass state — mirrors the incoming pass.
    private static boolean outgoingBound = false;
    private static boolean outgoingStarted = false;
    private static boolean outgoingReady = false;
    private static boolean outgoingSupported = true;
    // Most recent face texture drawn per side (set by drawPageTexture via
    // the mirrored flag: true = left, false = right), used only to check
    // texture readiness before capturing "next".
    private static String lastLeftFace = null;
    private static String lastRightFace = null;

    // The GuiGraphicsExtractor of the menu render event currently being drawn
    // (set by the generated triggers via beginRender/endRender).
    private static GuiGraphicsExtractor currentGui = null;

    // Mouse position of the current menu render event (GUI-scaled px),
    // set by the generated triggers via beginRender(gui, mx, my).
    private static double mouseX = 0.0;
    private static double mouseY = 0.0;

    // ── Debug logging ─────────────────────────────────────────────────────────
    // Logs to the mod log under the "FomekMenus" logger (latest.log).
    // SPAM-PROOF: a line identical to the previous one is printed only
    // once (so per-frame calls like setAmount/update produce a single
    // line, not a flood), and even changing lines are hard-capped at
    // 8 lines per second. Set DEBUG = false to silence completely.
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("FomekMenus");
    /**
     * Master switch — set to false to silence FomekMenus logging.
     * v2.10.24: automatically ON when a file named "fomekmenus-debug.txt"
     * exists in the game's run directory (same switch as Book.DEBUG).
     */
    public static boolean DEBUG = new java.io.File("fomekmenus-debug.txt").exists();
    private static String lastLog = null;
    private static long logWindowStart = 0L;
    private static int logCountInWindow = 0;
    private static String lastGeometryKey = "";
    private static long exceptionCount = 0L;

    private static void debug(String message, Object... args) {
        if (!DEBUG) return;
        String full = formatLog("[PageFlip] ", message, args);
        if (full.equals(lastLog)) return; // unchanged -> print only once
        long now = System.currentTimeMillis();
        if (now - logWindowStart > 1000L) {
            logWindowStart = now;
            logCountInWindow = 0;
        }
        if (logCountInWindow >= 8) return; // hard cap: max 8 lines/sec
        logCountInWindow++;
        lastLog = full;
        LOG.info(full);
    }

    /** Tiny {} formatter (slf4j formats lazily; we need the final string). */
    private static String formatLog(String prefix, String message, Object... args) {
        StringBuilder sb = new StringBuilder(prefix);
        int argIdx = 0, from = 0, idx;
        while (args != null && argIdx < args.length
                && (idx = message.indexOf("{}", from)) != -1) {
            sb.append(message, from, idx);
            sb.append(args[argIdx++]);
            from = idx + 2;
        }
        sb.append(message.substring(from));
        return sb.toString();
    }

    private PageFlip() {}

    // ── Public API (called from generated procedures) ────────────────────────

    /** True while a page flip is running (timed, grabbed, or releasing). */
    public static boolean isActive() {
        return manual || isReleasing() || isTimedRunning();
    }

    /**
     * Current flip position from 0 (old page flat) to 1 (new page flat),
     * eased. In manual mode this is exactly the last value passed to
     * setAmount(). Returns 0 when no flip is running.
     */
    public static float getAmount() {
        if (manual) return manualAmount;
        if (isReleasing()) {
            float t = clamp((Util.getMillis() - releaseStarted) / (float) releaseDurationMs, 0f, 1f);
            return lerp(releaseFrom, releaseTo, smoothstep(t));
        }
        if (isTimedRunning()) {
            return smoothstep((Util.getMillis() - started) / (float) durationMs);
        }
        return 0f;
    }

    /** Alias of getAmount(). */
    public static float getProgress() {
        return getAmount();
    }

    /** Human-readable current state, for logging. */
    public static String state() {
        if (manual) return "MANUAL grab at " + manualAmount;
        if (isReleasing())
            return "RELEASING " + releaseFrom + " -> " + releaseTo;
        if (isTimedRunning())
            return "TIMED flip at " + getAmount() + " of " + durationMs + "ms";
        return "IDLE";
    }

    /**
     * TIMED flip: begin a page turn that plays by itself.
     * Call this when the player presses the flip button, right before/after
     * switching your content to the next page.
     *
     * @param isForward true = flip left-to-right (next page), false = back
     * @param durMs     animation length in milliseconds (e.g. 320)
     */
    public static void start(boolean isForward, float durMs) {
        ensureTargets();
        forward = isForward;
        durationMs = Math.max(50L, (long) durMs);
        // Incoming spread is rendered offscreen on the next page pass.
        // Never blit the live framebuffer over that capture.
        captureNext = false;
        nextFresh = false;
        incomingReady = false;
        incomingStarted = false;
        incomingBound = false;
        outgoingReady = false;
        outgoingStarted = false;
        outgoingBound = false;
        manual = false;
        releaseStarted = 0L;
        started = Util.getMillis();
        debug("start(forward={}, durationMs={}) — timed flip started, will captureNext={}",
                isForward, durationMs, captureNext);
    }

    /**
     * MANUAL flip: "grab" the page corner. The page is now driven entirely
     * by setAmount() every frame — bind it to mouse movement to let the
     * player drag the page around. Switch your page variable in the same
     * tick, exactly like with start().
     *
     * @param isForward true = grabbed the RIGHT page (turns left, next),
     *                  false = grabbed the LEFT page (turns right, previous)
     */
    public static void grab(boolean isForward) {
        ensureTargets();
        forward = isForward;
        // v2.10.22: if the incoming spread was already rendered into
        // `next` offscreen, do NOT blit the live framebuffer over it.
        captureNext = !nextFresh && !incomingReady;
        manual = true;
        manualAmount = 0f;
        started = 0L;
        releaseStarted = 0L;
        debug("grab(forward={}) — manual grab started (captureNext={}), drive with setAmount()",
                isForward, captureNext);
    }

    /**
     * Drive a grabbed page. Values outside 0..1 are clamped.
     * Does nothing unless a grab is active.
     *
     * Example for a forward grab: amount = (grabStartX - mouseX) / (2 * pageWidth)
     */
    public static void setAmount(float amount) {
        if (manual) {
            manualAmount = clamp(amount, 0f, 1f);
            debug("setAmount -> amount={} (mouse=({},{}) gui={})",
                    manualAmount, (int) mouseX, (int) mouseY, currentGui != null);
        } else {
            // No amount in the message on purpose: identical lines are
            // logged only once, so a per-frame call stays silent after
            // the first line.
            debug("setAmount IGNORED — no active grab (manual=false)");
        }
    }

    /**
     * Let go of a grabbed page. The page tweens to its end position:
     *   "AUTO"     — completes if amount >= 0.5, otherwise snaps back
     *   "COMPLETE" — always finishes the turn
     *   "SNAP_BACK"— always returns to the old page
     *
     * If it snaps back, revert your page variable in the same tick
     * (the old content must be live again when the tween ends).
     */
    public static void release(String mode) {
        if (!manual) return;
        float amount = manualAmount;
        boolean complete;
        switch (mode == null ? "AUTO" : mode) {
            case "COMPLETE":  complete = true;  break;
            case "SNAP_BACK": complete = false; break;
            default:         complete = amount >= 0.5f; break; // AUTO
        }
        manual = false;
        releaseFrom = amount;
        releaseTo = complete ? 1f : 0f;
        // Shorter distance -> shorter tween.
        releaseDurationMs = Math.max(60L, (long) (220L * Math.abs(releaseTo - releaseFrom)));
        releaseStarted = Util.getMillis();
        debug("release(mode={}) — amount={}, will {}, tween {}ms",
                mode, amount, complete ? "COMPLETE the turn" : "SNAP BACK", releaseDurationMs);
    }

    /** Cancel any running flip and free the snapshot textures. */
    public static void stop() {
        debug("stop() — flip cancelled, snapshots freed (was active={})", isActive());
        closeTargets();
        started = 0L;
        manual = false;
        manualAmount = 0f;
        releaseStarted = 0L;
        captureNext = false;
        nextFresh = false;
        captureNextWaitFrames = 0;
        incomingBound = false;
        incomingReady = false;
        incomingStarted = false;
    }

    /**
     * Must be called EVERY frame from your menu render procedure (put it
     * after all your component drawing). Handles all cases automatically:
     * idle  -> snapshots the rendered panel into "previous"
     * active -> captures the new state once, then draws the flip on top.
     *
     * Only does something while a menu render event is active (the
     * FomekMenus triggers register their GuiGraphicsExtractor automatically).
     *
     * @param x         left edge of the whole two-page panel (screen px)
     * @param y         top edge of the panel
     * @param pageWidth width of ONE page (the panel is 2 * pageWidth wide)
     * @param height    panel height
     */
    /**
     * BOOK variant of update(). In addition to the panel geometry it
     * receives the LOOK of each half for the OLD spread (the state the
     * flip started from) and the NEW spread (the state it ends in).
     * With looks, the active frame is composited in clean layers:
     *   1. opaque backing + the PAPER replay (from the paper-only
     *      snapshots) + the frame art for both halves — so no game
     *      world can ever bleed through a half;
     *   2. the flipping sheet on top — sampled ONLY from the paper-only
     *      snapshots, so the frame art (book.png) can never ride along
     *      inside the moving page;
     *   3. the caller redraws the frame ring afterwards (static look).
     *
     * Snapshots are captured BEFORE any frame art is drawn this frame
     * (Book.end() calls this before its overlay), so "previous"/"next"
     * contain paper + content only — that is what makes layer 2 work.
     */
    public static void update(float x, float y, int pageWidth, int height,
                              Look oldLeft, Look oldRight, Look newLeft, Look newRight) {
        update(x, y, pageWidth, height, 0f, oldLeft, oldRight, newLeft, newRight);
    }

    /**
     * BOOK update with the "offset to center" slide: x is the art's
     * slided draw position (x already includes slide), slide is the
     * raw slide value used to keep snapshot sampling capture-correct.
     */
    public static void update(float x, float y, int pageWidth, int height, float slide,
                              Look oldLeft, Look oldRight, Look newLeft, Look newRight) {
        update(x, y, pageWidth, height, slide, oldLeft, oldRight, newLeft, newRight, false);
    }

    /**
     * BOOK update with the "flat sheet" flag (v2.10.21): a COVER flip
     * passes flat=true — the cover is a solid board, so it sweeps
     * straight across horizontally instead of bending like paper.
     */
    public static void update(float x, float y, int pageWidth, int height, float slide,
                              Look oldLeft, Look oldRight, Look newLeft, Look newRight,
                              boolean flatSheet) {
        GuiGraphicsExtractor gui = currentGui;
        if (gui == null) {
            debug("update IGNORED — no menu render event active (beginRender was not "
                    + "called). Is the Book/Update block inside a menu render procedure?");
            return;
        }
        String key = (int) x + "/" + (int) y + "/" + pageWidth + "/" + height;
        if (!key.equals(lastGeometryKey)) {
            lastGeometryKey = key;
            debug("update() panel geometry: x={}, y={}, pageWidth={}, height={}",
                    (int) x, (int) y, pageWidth, height);
        }
        ensureTargets();
        if (previous == null) return;
        runUpdate(gui, x, y, pageWidth, height, slide, oldLeft, oldRight, newLeft, newRight, flatSheet);
    }

    public static void update(float x, float y, int pageWidth, int height) {
        update(x, y, pageWidth, height, 0f);
    }

    public static void update(float x, float y, int pageWidth, int height, float slide) {
        GuiGraphicsExtractor gui = currentGui;
        if (gui == null) {
            // Deduped: the same geometry prints once, not every frame.
            debug("update IGNORED — no menu render event active (beginRender was not "
                    + "called). Is the Book/Update block inside a menu render procedure?");
            return;
        }
        String key = (int) x + "/" + (int) y + "/" + pageWidth + "/" + height;
        if (!key.equals(lastGeometryKey)) {
            lastGeometryKey = key;
            debug("update() panel geometry: x={}, y={}, pageWidth={}, height={}",
                    (int) x, (int) y, pageWidth, height);
        }
        ensureTargets();
        if (previous == null) return;
        runUpdate(gui, x, y, pageWidth, height, slide, null, null, null, null, false);
    }

    /**
     * Called by generated FomekMenus triggers before user render code —
     * registers the menu render event's GuiGraphicsExtractor and mouse position.
     * Do not call from Blockly.
     */
    public static void beginRender(GuiGraphicsExtractor gui, double mx, double my) {
        currentGui = gui;
        mouseX = mx;
        mouseY = my;
    }

    /** Backwards-compatible overload (mouse position unknown). */
    public static void beginRender(GuiGraphicsExtractor gui) {
        beginRender(gui, 0.0, 0.0);
    }

    /** Called by generated FomekMenus triggers after user render code. */
    public static void endRender() {
        currentGui = null;
    }

    /** Submit queued page controls before the alpha mask and snapshot. */
    public static void flushRender() {
        if (currentGui != null) currentGui.flush();
    }

    /** True until the destination spread has been rendered into `next`. */
    public static boolean needsIncomingCapture() {
        return incomingSupported && !incomingReady && !nextFresh;
    }

    /**
     * v2.10.26: true while the FROM side of the running/pending flip
     * still needs to be rendered offscreen into `out`.
     */
    public static boolean needsOutgoingRender() {
        return outgoingSupported && !outgoingReady;
    }

    /**
     * Bind the offscreen `next` target so Page children can draw the
     * destination spread into it. First call of a flip clears to
     * transparent; later calls (the other half of the same spread)
     * rebind without clearing. Always pair with endIncomingPass().
     */
    public static boolean beginIncomingPass() {
        if (incomingReady) return false;
        if (incomingBound) return true;
        if (currentGui != null) currentGui.flush();
        ensureTargets();
        if (next == null || !incomingSupported) return false;
        try {
            if (!incomingStarted) {
                next.setClearColor(0f, 0f, 0f, 0f);
                next.clear(Minecraft.ON_OSX);
                incomingStarted = true;
            }
            // clear() may unbind the framebuffer: bind AFTER clearing.
            next.bindWrite(true);
            incomingBound = true;
            return true;
        } catch (Throwable t) {
            // v2.10.24: this was a silent debug() line before — if the
            // offscreen bind ever failed, every destination-spread draw
            // leaked onto the LIVE screen with NO trace in the log. It
            // now logs at ERROR level unconditionally (visible even
            // with debug logging off) and permanently disables the
            // incoming pass for the session so draws can never leak.
            incomingSupported = false;
            incomingBound = false;
            LOG.error("[PageFlip] beginIncomingPass FAILED — offscreen capture "
                    + "is broken in this environment, destination content will "
                    + "NOT be captured (and will NOT leak onto the live screen). "
                    + "Page flips will show a blank back side until this is "
                    + "investigated. Cause: {}", t.toString(), t);
            try {
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * Unbind the incoming FBO back to the main framebuffer so visible
     * (old-spread) draws go to the screen. Does not mark the capture
     * complete — more incoming sides may still render this frame.
     */
    public static void endIncomingPass() {
        if (!incomingBound) return;
        if (currentGui != null) currentGui.flush();
        incomingBound = false;
        try {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        } catch (Throwable t) {
            debug("endIncomingPass unbind failed: {}", t.toString());
        }
    }

    /**
     * Close the incoming pass for this frame: if anything was drawn
     * into `next`, mark it fresh so update() will not blit the live
     * screen over it.
     */
    public static void finalizeIncomingPass(float slide) {
        endIncomingPass();
        if (incomingStarted && !incomingReady) {
            incomingReady = true;
            nextFresh = true;
            captureNext = false;
            nextCaptureSlide = slide;
        }
    }

    /**
     * v2.10.26: OUTGOING pass — renders the FROM spread (the side the
     * sheet is lifting OFF) into the clean `out` buffer. Mirrors the
     * incoming pass exactly: first call of a flip clears to transparent,
     * every draw between begin/end lands in the offscreen buffer, and a
     * failed bind can NEVER leak onto the live screen.
     */
    public static boolean beginOutgoingPass() {
        if (outgoingReady) return false;
        if (outgoingBound) return true;
        if (currentGui != null) currentGui.flush();
        ensureTargets();
        if (out == null || !outgoingSupported) return false;
        try {
            if (!outgoingStarted) {
                out.setClearColor(0f, 0f, 0f, 0f);
                out.clear(Minecraft.ON_OSX);
                outgoingStarted = true;
            }
            // clear() may unbind the framebuffer: bind AFTER clearing.
            out.bindWrite(true);
            outgoingBound = true;
            return true;
        } catch (Throwable t) {
            outgoingSupported = false;
            incomingSupported = false; // one broken FBO means both passes are broken
            outgoingBound = false;
            LOG.error("[PageFlip] beginOutgoingPass FAILED — offscreen capture of the "
                    + "FROM side is broken in this environment. The flip will fall back "
                    + "to the old live-screen snapshot (which can drag captured "
                    + "background through transparent art). Cause: {}", t.toString(), t);
            try {
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void endOutgoingPass() {
        if (!outgoingBound) return;
        if (currentGui != null) currentGui.flush();
        outgoingBound = false;
        try {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        } catch (Throwable t) {
            debug("endOutgoingPass unbind failed: {}", t.toString());
        }
    }

    public static void finalizeOutgoingPass(float slide) {
        endOutgoingPass();
        if (outgoingStarted && !outgoingReady) {
            outgoingReady = true;
            outCaptureSlide = slide;
        }
    }

    /** Mouse X of the current menu render event, in GUI-scaled pixels. */
    public static float getMouseX() {
        return (float) mouseX;
    }

    /** Mouse Y of the current menu render event, in GUI-scaled pixels. */
    public static float getMouseY() {
        return (float) mouseY;
    }

    /**
     * True while the left mouse button is held (raw GLFW poll).
     * Used by Book's built-in page grab so the mouse-driven flip needs
     * no wiring in procedures.
     */
    public static boolean isLeftMouseDown() {
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static boolean isTimedRunning() {
        return started != 0L && Util.getMillis() - started < durationMs;
    }

    private static boolean isReleasing() {
        return releaseStarted != 0L && Util.getMillis() - releaseStarted < releaseDurationMs;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static void ensureTargets() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (previous == null || previous.width != main.width || previous.height != main.height) {
            closeTargets();
            previous = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            next = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            out = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            main.bindWrite(false);
            started = 0L;
            manual = false;
            releaseStarted = 0L;
            nextFresh = false;
            prevCaptureSlide = 0f;
            nextCaptureSlide = 0f;
            incomingBound = false;
            incomingReady = false;
            incomingStarted = false;
            outgoingBound = false;
            outgoingReady = false;
            outgoingStarted = false;
            outCaptureSlide = 0f;
        }
    }

    private static void closeTargets() {
        if (previous != null) previous.destroyBuffers();
        if (next != null) next.destroyBuffers();
        if (out != null) out.destroyBuffers();
        previous = null;
        next = null;
        out = null;
    }

    /** Blit the current main framebuffer into the given snapshot target. */
    private static void capture(TextureTarget target) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height,
                0, 0, target.width, target.height,
                GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
        main.bindWrite(false);
    }

    /**
     * True once every face texture actually drawn THIS frame (left/right,
     * whichever is non-null) resolves a real PNG size — i.e. the resource
     * is registered and safe to sample. Used to postpone capturing "next"
     * until the destination page's art is actually ready, instead of
     * baking a blank frame into the snapshot that would replay for the
     * whole flip. If neither side drew a face this frame, there's nothing
     * to wait for.
     */
    private static boolean facesReadyForCapture() {
        if (lastLeftFace != null && pngSize(lastLeftFace)[0] <= 0) return false;
        if (lastRightFace != null && pngSize(lastRightFace)[0] <= 0) return false;
        return true;
    }

    private static void runUpdate(GuiGraphicsExtractor g, float x, float y, int pageWidth, int height,
                                  float slide, Look oldLeft, Look oldRight, Look newLeft, Look newRight,
                                  boolean flatSheet) {
        final boolean bookLooks = oldLeft != null || oldRight != null
                || newLeft != null || newRight != null;
        try {
            // Submit everything batched so far — the snapshot must
            // contain the fully drawn panel.
            g.flush();

            if (isActive()) {
                if (captureNext && next != null) {
                    captureNextWaitFrames++;
                    if (facesReadyForCapture() || captureNextWaitFrames > CAPTURE_NEXT_MAX_WAIT_FRAMES) {
                        if (captureNextWaitFrames > CAPTURE_NEXT_MAX_WAIT_FRAMES) {
                            debug("captureNext: giving up waiting for face texture(s) to "
                                    + "register after {} frames, capturing anyway (left={}, right={})",
                                    captureNextWaitFrames, lastLeftFace, lastRightFace);
                        } else if (captureNextWaitFrames > 1) {
                            debug("captureNext: face texture(s) now ready after {} frame(s) "
                                    + "(left={}, right={}) — capturing", captureNextWaitFrames,
                                    lastLeftFace, lastRightFace);
                        }
                        capture(next);
                        nextCaptureSlide = slide;
                        captureNext = false;
                        captureNextWaitFrames = 0;
                        nextFresh = true;
                    } else {
                        debug("captureNext: delaying capture, face texture(s) not registered "
                                + "yet this frame (left={}, right={})", lastLeftFace, lastRightFace);
                    }
                }
                if (bookLooks) {
                    drawBookFlip(g, x, y, pageWidth, height, getAmount(), slide,
                            oldLeft, oldRight, newLeft, newRight, flatSheet);
                } else {
                    drawFlip(g, x, y, pageWidth, height, getAmount());
                }
            } else {
                capture(previous);
                prevCaptureSlide = slide;
                nextFresh = false; // flip fully over — next is stale now
                incomingReady = false;
                incomingStarted = false;
                incomingBound = false;
                outgoingReady = false; // out is stale too — recapture next flip
                outgoingStarted = false;
                outgoingBound = false;
            }
        } catch (Exception e) {
            // Never let a broken flip crash the render — but LOG it.
            // A silently swallowed exception here makes the flip system
            // quietly reset every frame, which looks exactly like
            // "the flip/grab does nothing".
            exceptionCount++;
            if (DEBUG || exceptionCount <= 3) {
                LOG.error("[PageFlip] runUpdate FAILED (occurrence {}) — flip was stopped, "
                        + "active={}, manual={}", exceptionCount, isActive(), manual, e);
            }
            try { stop(); } catch (Exception ignored) {}
        }
    }

    /**
     * BOOK flip: composites the panel in clean layers so the frame art
     * (book.png) can never end up inside the moving page and the game
     * world can never bleed through a half.
     *
     * Layering per half:
     *   - destination half (forward = left, backward = right) shows the
     *     OLD spread: backing + full static frame UNDER old paper replay;
     *   - source half shows the NEW spread (revealed as the sheet lifts):
     *     backing + full static frame UNDER new paper replay.
     * Then the flipping sheet (paper-only strips) is drawn on top.
     * Nothing from book.png is ever drawn ON TOP of the paper.
     */
    private static void drawBookFlip(GuiGraphicsExtractor g, float left, float top, int pageWidth, int height,
                                     float progress, float slide,
                                     Look oldLeft, Look oldRight,
                                     Look newLeft, Look newRight, boolean flatSheet) {
        if (previous == null || next == null || pageWidth <= 0 || height <= 0) return;
        progress = clamp(progress, 0f, 1f);

        float angle = (float) Math.PI * progress;
        float extent = (float) Math.cos(angle) * (float) pageWidth;
        float lift = (float) Math.sin(angle);
        float spine = left + pageWidth;
        // Snapshot content sits where it was CAPTURED; the halves are
        // drawn where the slide has them NOW. Sample each snapshot at
        // its capture position (0 offset for non-sliding books).
        float offPrev = prevCaptureSlide - slide;
        float offNext = nextCaptureSlide - slide;
        // v2.10.26: the FROM side replays from `out` — a CLEAN buffer
        // holding only the from-spread's paper/cover + content. The old
        // `previous` (a blit of the whole live framebuffer) dragged the
        // captured world/GUI background along with the art and caused
        // the ghost/stale-slide artifacts. It stays as a fallback when
        // offscreen capture is unavailable in this environment.
        boolean fromClean = outgoingReady && out != null;
        TextureTarget fromPaper = fromClean ? out : previous;
        float offFrom = fromClean ? (outCaptureSlide - slide) : offPrev;

        // 1) Both halves, fully composited (opaque — no world bleed):
        //    destination half = OLD look, source half = NEW look.
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Look leftLook = forward ? oldLeft : newLeft;
        Look rightLook = forward ? newRight : oldRight;
        TextureTarget leftPaper = forward ? fromPaper : next;
        TextureTarget rightPaper = forward ? next : fromPaper;
        drawHalfLook(g, left, top, pageWidth, height, leftLook, leftPaper, true,
                leftPaper == next ? offNext : offFrom);
        drawHalfLook(g, spine, top, pageWidth, height, rightLook, rightPaper, false,
                rightPaper == next ? offNext : offFrom);

        // 2) The flipping sheet: PAPER-ONLY strips (the snapshots contain
        //    no frame art, so book.png can never ride inside the page).
        TextureTarget face = progress < 0.5f ? fromPaper : next;
        float sourceX = forward ? (progress < 0.5f ? spine : left) : (progress < 0.5f ? left : spine);
        float direction = forward ? 1.0f : -1.0f;
        float faceOff = face == next ? offNext : offFrom;

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (flatSheet) {
            // v2.10.21: COVER flip — the cover is a solid board: ONE
            // flat quad sweeping straight across, no bend, no curl
            // shading (just a touch of edge darkening as it passes
            // vertical).
            float u0 = extent * direction >= 0.0f ? 0.0f : 1.0f;
            float u1 = extent * direction >= 0.0f ? 1.0f : 0.0f;
            float shade = 1.0f - lift * 0.10f;
            RenderSystem.setShaderColor(shade, shade, shade, 1.0f);
            drawStrip(g, face,
                    spine, spine + direction * extent,
                    top, top, top + height, top + height,
                    sourceX + u0 * pageWidth + faceOff,
                    sourceX + u1 * pageWidth + faceOff, top, height);
        } else {
            for (int strip = 0; strip < STRIPS; ++strip) {
                float a = (float) strip / STRIPS;
                float b = (float) (strip + 1) / STRIPS;
                float x0 = spine + direction * extent * a;
                float x1 = spine + direction * extent * b;
                float bend0 = (float) Math.sin(a * Math.PI) * lift * 9.0f;
                float bend1 = (float) Math.sin(b * Math.PI) * lift * 9.0f;
                float u0 = extent * direction >= 0.0f ? a : 1.0f - a;
                float u1 = extent * direction >= 0.0f ? b : 1.0f - b;
                float shade = 1.0f - lift * (0.12f + 0.22f * a);
                RenderSystem.setShaderColor(shade, shade, shade, 1.0f);
                drawStrip(g, face,
                        x0, x1, top - bend0, top - bend1, top + height + bend0, top + height + bend1,
                        sourceX + u0 * pageWidth + faceOff, sourceX + u1 * pageWidth + faceOff, top, height);
            }
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    /**
     * One half of the book during a BOOK flip: opaque backing + full
     * static frame UNDER the paper replay (if a sheet lies here).
     * The frame is never drawn on top of the paper — transparent
     * corners of page.png show the live frame from below.
     */
    private static void drawHalfLook(GuiGraphicsExtractor g, float x, float top, int pageWidth, int height,
                                     Look look, TextureTarget paper, boolean mirrored, float sampleOffset) {
        float w = (float) pageWidth;
        float h = (float) height;
        if (look == null || w <= 0f || h <= 0f) return;
        drawPageBacking(look.bg, x, top, w, h, mirrored);
        if (look.sheet && paper != null) {
            drawStrip(g, paper,
                    x, x + w, top, top, top + h, top + h,
                    x + sampleOffset, x + w + sampleOffset, top, height);
        }
    }

    /**
     * One untextured, solid, opaque quad in ARGB color — immediate mode,
     * so it always layers with the other immediate draws regardless of
     * what is still sitting in the GuiGraphicsExtractor batch.
     */
    private static void drawSolidQuad(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
        if (w <= 0f || h <= 0f) return;
        float a = (argb >>> 24 & 0xFF) / 255f;
        float r = (argb >>> 16 & 0xFF) / 255f;
        float gr = (argb >>> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, gr, b, a);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        Matrix4f matrix = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(matrix, x, y + h, 0.0f);
        buffer.addVertex(matrix, x + w, y + h, 0.0f);
        buffer.addVertex(matrix, x + w, y, 0.0f);
        buffer.addVertex(matrix, x, y, 0.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Draw the flip. {@code progress} is the eased position 0..1
     * (0 = old page lying flat, 0.5 = page vertical at the spine,
     * 1 = new page lying flat).
     */
    private static void drawFlip(GuiGraphicsExtractor g, float left, float top, int pageWidth, int height,
                                float progress) {
        if (previous == null || next == null || pageWidth <= 0 || height <= 0) return;
        progress = clamp(progress, 0f, 1f);

        float angle = (float) Math.PI * progress;
        float extent = (float) Math.cos(angle) * (float) pageWidth;
        float lift = (float) Math.sin(angle);
        float spine = left + pageWidth;

        // 1) Stationary half — the half not covered by the moving page.
        int stationaryX = forward ? (int) left : (int) spine;
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        drawStrip(g, previous,
                stationaryX, stationaryX + pageWidth, top, top, top + height, top + height,
                stationaryX, stationaryX + pageWidth, top, height);

        // 2) Soft brown shadow band near the moving fold edge.
        int shadowWidth = Math.max(1, Math.round(14.0f * lift));
        int shadowX = (int) (spine + (forward ? 1 : -1) * extent);
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 450.0f);
        for (int i = shadowWidth; i > 0; --i) {
            int alpha = Math.round(18.0f * lift * (1.0f - (float) i / (shadowWidth + 1)));
            g.fill(shadowX - i, (int) top + 3, shadowX + i, (int) top + height - 3, alpha << 24 | 0x392819);
        }
        g.pose().popPose();
        g.flush();

        // 3) The flipping sheet: 24 strips from the old state, then (past
        //    the midpoint) from the new state, mirrored, bent and shaded.
        TextureTarget face = progress < 0.5f ? previous : next;
        float sourceX = forward ? (progress < 0.5f ? spine : left) : (progress < 0.5f ? left : spine);
        float direction = forward ? 1.0f : -1.0f;

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int strip = 0; strip < STRIPS; ++strip) {
            float a = (float) strip / STRIPS;
            float b = (float) (strip + 1) / STRIPS;
            float x0 = spine + direction * extent * a;
            float x1 = spine + direction * extent * b;
            float bend0 = (float) Math.sin(a * Math.PI) * lift * 9.0f;
            float bend1 = (float) Math.sin(b * Math.PI) * lift * 9.0f;
            float u0 = extent * direction >= 0.0f ? a : 1.0f - a;
            float u1 = extent * direction >= 0.0f ? b : 1.0f - b;
            float shade = 1.0f - lift * (0.12f + 0.22f * a);
            RenderSystem.setShaderColor(shade, shade, shade, 1.0f);
            drawStrip(g, face,
                    x0, x1, top - bend0, top - bend1, top + height + bend0, top + height + bend1,
                    sourceX + u0 * pageWidth, sourceX + u1 * pageWidth, top, height);
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    /**
     * Draw one textured quad strip sampled from a screen snapshot.
     * Coordinates are in GUI-scaled pixels; snapshot UVs are computed with
     * the GUI scale factor.
     */
    private static void drawStrip(GuiGraphicsExtractor g, TextureTarget texture,
                                  float x0, float x1, float top0, float top1,
                                  float bottom0, float bottom1,
                                  float sourceX0, float sourceX1,
                                  float sourceY, int sourceHeight) {
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float u0 = sourceX0 * scale / (float) texture.width;
        float u1 = sourceX1 * scale / (float) texture.width;
        float v0 = 1.0f - sourceY * scale / (float) texture.height;
        float v1 = 1.0f - (sourceY + sourceHeight) * scale / (float) texture.height;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture.getColorTextureId());

        Matrix4f matrix = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x0, bottom0, 0.0f).setUv(u0, v1);
        buffer.addVertex(matrix, x1, bottom1, 0.0f).setUv(u1, v1);
        buffer.addVertex(matrix, x1, top1, 0.0f).setUv(u1, v0);
        buffer.addVertex(matrix, x0, top0, 0.0f).setUv(u0, v0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /**
     * Draw a resource texture stretched to (x, y, w, h), optionally
     * MIRRORED horizontally. Used by the Book system for the left page
     * face: page cutouts (e.g. the vanilla book texture) are
     * right-oriented, and the left half of an open book shows them
     * mirrored — same as the vanilla book GUI does. Must run inside a
     * render pass (beginRender/endRender), before the side panel's
     * content so children draw on top.
     */
    public static void drawPageTexture(String texture, float x, float y, float w, float h, boolean mirrored) {
        drawPageTexture(texture, null, x, y, w, h, mirrored);
    }

    /**
     * Draw one face of the openable book COVER (Book system): opaque
     * backing under the cover art + the art itself with the spine crop
     * applied, exactly like the page faces so flip snapshots capture a
     * consistent look. mirrored=true = the cover's INSIDE (drawn on the
     * left half once the book is open), false = the OUTSIDE (the only
     * thing visible while the book is closed). Records the face for the
     * capture-readiness check, same as drawPageTexture.
     */
    public static void drawCoverTexture(String texture, float x, float y,
                                        float w, float h, boolean mirrored) {
        if (mirrored) lastLeftFace = texture; else lastRightFace = texture;
        GuiGraphicsExtractor g = currentGui;
        if (g == null || texture == null || w <= 0f || h <= 0f) return;
        // v2.10.22: covers fill the half with the texture AS-IS. No brown
        // backing quad (that showed as leather corners behind rounded
        // covers) and no spine crop (that was for the stock book.png
        // binding strip — custom covers must not be sliced).
        drawQuad(g, texture, x, y, w, h, mirrored);
    }

    /** Draw the static book layer beneath a page face and its controls. */
    public static void drawPageBacking(String bookTexture,
                                       float x, float y, float w, float h, boolean mirrored) {
        GuiGraphicsExtractor g = currentGui;
        if (g == null || bookTexture == null || w <= 0f || h <= 0f) return;
        // v2.10.22: never paint the brown leather quad. The backing is
        // the cover interior texture itself.
        drawQuad(g, bookTexture, x, y, w, h, mirrored);
    }

    /**
     * Draws ONLY the paper (face) of a page, fitted inside the page rect
     * at its native proportions — scaled by the same factors as
     * fitReference (the book frame art) and centered. The frame itself
     * is NOT drawn here: it is drawn by drawBookOverlay AFTER the flip
     * animation, so the book frame stays STATIC and only the paper
     * flips. When fitReference is missing or unreadable the face simply
     * fills the page rect.
     */
    public static void drawPageTexture(String faceTexture, String fitReference,
                                       float x, float y, float w, float h, boolean mirrored) {
        // Record what's actually being drawn this frame per side, so
        // runUpdate() can check the right texture(s) are registered
        // before capturing "next" (see facesReadyForCapture()).
        if (mirrored) lastLeftFace = faceTexture; else lastRightFace = faceTexture;
        GuiGraphicsExtractor g = currentGui;
        if (g == null || w <= 0f || h <= 0f) return;
        if (faceTexture == null) {
            // No page texture set: the book art fills the page (it then
            // flips with the page, like before the layered design).
            if (fitReference != null) {
                drawQuad(g, fitReference, x, y, w, h, mirrored);
                stampAlpha(g, fitReference, x, y, w, h, mirrored);
            }
            return;
        }
        if (fitReference == null || faceTexture.equals(fitReference)) {
            drawQuad(g, faceTexture, x, y, w, h, mirrored);
            stampAlpha(g, faceTexture, x, y, w, h, mirrored);
            return;
        }
        // v2.10.21: the page face draws at its NATIVE pixel size,
        // centered on the half — never enlarged to fill the half box
        // and never stretched to a foreign aspect ratio. A face larger
        // than the half is scaled DOWN to fit (aspect preserved). This
        // is what lets page art keep its own margins/padding: what you
        // drew is what you get, at 1 texture px = 1 screen px.
        int[] face = pngSize(faceTexture);
        if (face[0] <= 0) {
            drawQuad(g, faceTexture, x, y, w, h, mirrored);
            stampAlpha(g, faceTexture, x, y, w, h, mirrored);
            return;
        }
        float scale = Math.min(1f, Math.min(w / face[0], h / face[1]));
        float fw = face[0] * scale;
        float fh = face[1] * scale;
        float fox = x + (w - fw) / 2f;
        float foy = y + (h - fh) / 2f;
        drawQuad(g, faceTexture, fox, foy, fw, fh, mirrored);
        stampAlpha(g, faceTexture, fox, foy, fw, fh, mirrored);
    }

    /**
     * Stamps TEXTURE's own alpha channel into the already-drawn frame at
     * the same rect, overwriting it rather than blending it — WITHOUT
     * touching the colors that are already there.
     *
     * Why this is needed: the page texture (page.png) is not a perfect
     * rectangle — it has real transparent pixels (worn/dog-eared
     * corners). On a normal idle frame that's fine: the transparent
     * pixels just blend with whatever's live behind the GUI. But the
     * flip animation does not re-render that live background every
     * frame — it replays a screen snapshot taken with glBlitFramebuffer
     * (see capture()). That snapshot is a flat copy of the FINAL,
     * already-composited frame, where the page's transparent pixels
     * were already blended over the (opaque) game world — and blending
     * anything over an opaque background always yields an opaque
     * result. By the time PageFlip reads the frame, there is no
     * "this pixel used to be transparent paper" information left in it
     * to filter — the alpha is baked to ~1 everywhere, corners
     * included. That's why a frozen smear of old background shows up
     * at the ragged corners once a flip starts.
     *
     * The fix is to put that information back by force: right after
     * the face is drawn normally (for the correct idle look), redraw
     * the SAME quad with color writes masked to ALPHA ONLY and
     * blending off, so the framebuffer's alpha channel at exactly this
     * rect gets overwritten with the texture's real per-pixel alpha —
     * 0 at the transparent corners, 1 elsewhere — leaving the RGB the
     * idle draw just produced completely untouched. The next
     * glBlitFramebuffer snapshot then keeps that true shape, and the
     * existing blend-enabled strip drawing in drawFlip()/drawStrip()
     * naturally lets 0-alpha corners show whatever is actually behind
     * them at flip time (the live game, or the static frame drawn
     * after update()) instead of a frozen pixel.
     */
    private static void stampAlpha(GuiGraphicsExtractor g, String texture,
                                   float x, float y, float w, float h, boolean mirrored) {
        if (w <= 0f || h <= 0f) return;
        Identifier loc;
        try {
            loc = Identifier.parse(texture);
        } catch (Exception invalidLocation) {
            return; // nothing touched yet, safe to just bail
        }
        // GUARD: colorMask/blend are global GL state. If ANYTHING below
        // throws (e.g. the texture resource isn't registered yet — same
        // race as the pngSize "first frame" issue) before we restore
        // them, every draw for the REST of this frame (and potentially
        // later frames, since nothing else in this plugin resets
        // colorMask) would silently stop writing RGB — which reads
        // exactly like "the other page shows raw world/sky". try/finally
        // guarantees the restore always runs, so a failed stamp can only
        // ever skip ITS OWN alpha write, never corrupt anything else.
        boolean masked = false;
        try {
            float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
            if (mirrored) { float tmp = u0; u0 = u1; u1 = tmp; }

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();                       // overwrite, not blend
            RenderSystem.colorMask(false, false, false, true); // ALPHA channel only
            masked = true;
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, loc);
            Matrix4f matrix = g.pose().last().pose();
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buffer = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(matrix, x, y + h, 0.0f).setUv(u0, v1);
            buffer.addVertex(matrix, x + w, y + h, 0.0f).setUv(u1, v1);
            buffer.addVertex(matrix, x + w, y, 0.0f).setUv(u1, v0);
            buffer.addVertex(matrix, x, y, 0.0f).setUv(u0, v0);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } catch (Exception e) {
            debug("stampAlpha FAILED for {} ({}x{} @ {},{}) — alpha not stamped this frame, "
                    + "GL state restored: {}", texture, (int) w, (int) h, (int) x, (int) y,
                    e.toString());
        } finally {
            if (masked) RenderSystem.colorMask(true, true, true, true); // restore ALWAYS
            RenderSystem.enableBlend();                                 // restore ALWAYS
        }
    }

    /**
     * STATIC BOOK FRAME — drawn on top of everything (including the flip
     * animation) at the end of every book render, so it NEVER flips.
     * Halves with a sheet on them get only the frame's border ring
     * (computed from the paper cutout), letting the flipping paper show
     * inside. Empty halves get the FULL frame texture — the static book
     * body — so the book always looks open, even where no page lies.
     */
    public static void drawBookOverlay(float x, float y, int pageWidth, int height,
                                       String leftBg, String rightBg,
                                       String leftFace, String rightFace,
                                       boolean leftSheet, boolean rightSheet) {
        GuiGraphicsExtractor g = currentGui;
        if (g == null || pageWidth <= 0 || height <= 0) return;
        drawHalfOverlay(g, leftBg, leftFace, leftSheet,
                x, y, (float) pageWidth, (float) height, true);
        drawHalfOverlay(g, rightBg, rightFace, rightSheet,
                x + pageWidth, y, (float) pageWidth, (float) height, false);
    }

    /**
     * IDLE-frame variant of the overlay: draws ONLY the halves with no
     * sheet on them (opaque backing + full frame art — the "closed"
     * side of the book; this is the one place an empty half is ever
     * drawn). Halves that HOLD a sheet get nothing on top here: their
     * frame art was already painted UNDER the page content by
     * Book.begin()'s pre-backing, pixel-identical, so drawing any part
     * of it again above the page would only risk touching the paper —
     * in particular the shared coincident edge, where the redrawn top
     * band could eat the paper's topmost rim row. The on-top frame ring
     * remains exclusively part of the ACTIVE flip composite, where it
     * is genuinely needed: the bending sheet is replayed from snapshot
     * strips and sweeps across the frame region, and the ring drawn
     * last is what keeps the frame perfectly still while the paper
     * moves — the core of the effect.
     */
    public static void drawBookOverlayIdle(float x, float y, int pageWidth, int height,
                                           String leftBg, String rightBg,
                                           boolean leftSheet, boolean rightSheet) {
        drawBookOverlayIdle(x, y, pageWidth, height, leftBg, rightBg,
                leftSheet, rightSheet, false, false);
    }

    /**
     * Idle overlay with optional HIDDEN halves (openable books): a
     * closed book is only half as wide, so the half "outside" the stack
     * draws nothing at all — no backing, no frame art, the game world
     * shows through exactly like around any other menu element.
     * hideLeft/hideRight suppress that half entirely.
     */
    public static void drawBookOverlayIdle(float x, float y, int pageWidth, int height,
                                           String leftBg, String rightBg,
                                           boolean leftSheet, boolean rightSheet,
                                           boolean hideLeft, boolean hideRight) {
        GuiGraphicsExtractor g = currentGui;
        if (g == null || pageWidth <= 0 || height <= 0) return;
        if (!leftSheet && !hideLeft) drawHalfOverlay(g, leftBg, null, false,
                x, y, (float) pageWidth, (float) height, true);
        if (!rightSheet && !hideRight) drawHalfOverlay(g, rightBg, null, false,
                x + pageWidth, y, (float) pageWidth, (float) height, false);
    }

    /**
     * Restore a face alpha mask after its panel children have rendered.
     *
     * v2.10.25: this used an OLD spine-crop positioning formula (scale by
     * w/(refWidth-spineCrop), offset by the reference texture's own
     * cropped-center math) left over from the pre-2.10.21 design where the
     * book FRAME was drawn on top and the page's alpha had to align to
     * ITS cropped coordinate system. drawPageTexture (the COLOR draw this
     * is supposed to restore the silhouette for) was rewritten in 2.10.21
     * to a completely different, simpler fit: scale the face by its own
     * aspect to fit w x h, centered, no reference/spine math at all. The
     * two formulas agree only by coincidence — normally the alpha mask
     * stamps at a DIFFERENT x (and a different size) than where the color
     * was actually drawn, so the page's real transparent silhouette lands
     * offset from its own art: exactly "the page isn't anchored, it
     * slides/offsets" as the book (and the mismatch) moves on screen.
     * Fix: use the IDENTICAL fit math as drawPageTexture, so the alpha
     * mask always lands exactly on top of the color it belongs to.
     */
    public static void stampPageAlpha(String faceTexture, String fitReference,
                                      float x, float y, float w, float h, boolean mirrored) {
        if (faceTexture == null || w <= 0f || h <= 0f) return;
        // The static book backing was drawn before the page so transparent
        // paper pixels look right while idle. It must not enter the page
        // snapshot, though: clear the entire page rect's alpha first, then
        // put back only the real alpha silhouette of page.png below.
        clearAlpha(currentGui, x, y, w, h);
        if (fitReference == null || faceTexture.equals(fitReference)) {
            stampAlpha(currentGui, faceTexture, x, y, w, h, mirrored);
            return;
        }
        int[] face = pngSize(faceTexture);
        if (face[0] <= 0) {
            stampAlpha(currentGui, faceTexture, x, y, w, h, mirrored);
            return;
        }
        // Same fit as drawPageTexture: native size, scaled down only if
        // larger than the half, centered — no reference/spine math.
        float scale = Math.min(1f, Math.min(w / face[0], h / face[1]));
        float fw = face[0] * scale;
        float fh = face[1] * scale;
        float fox = x + (w - fw) / 2f;
        float foy = y + (h - fh) / 2f;
        stampAlpha(currentGui, faceTexture, fox, foy, fw, fh, mirrored);
    }

    /** Clear only framebuffer alpha in a rectangle; leave its RGB untouched. */
    private static void clearAlpha(GuiGraphicsExtractor g, float x, float y, float w, float h) {
        if (g == null || w <= 0f || h <= 0f) return;
        try {
            RenderSystem.disableBlend();
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.setShaderColor(1f, 1f, 1f, 0f);
            RenderSystem.setShader(GameRenderer::getPositionShader);
            Matrix4f matrix = g.pose().last().pose();
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buffer = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            buffer.addVertex(matrix, x, y + h, 0f);
            buffer.addVertex(matrix, x + w, y + h, 0f);
            buffer.addVertex(matrix, x + w, y, 0f);
            buffer.addVertex(matrix, x, y, 0f);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableBlend();
        }
    }

    private static void drawHalfOverlay(GuiGraphicsExtractor g, String bg, String face,
                                       boolean sheet, float x, float y,
                                       float w, float h, boolean mirrored) {
        if (bg == null) return;
        if (!sheet) {
            // No page lies here: the full frame art shows, statically.
            if (DRAW_BACKING) drawSolidQuad(g, x, y, w, h, BACKING_COLOR);
            drawQuadSpined(g, bg, x, y, w, h, mirrored);
            return;
        }
        if (face == null || face.equals(bg)) return; // nothing to outline
        int[] bgS = pngSize(bg);
        int[] fS = pngSize(face);
        if (bgS[0] <= 0 || fS[0] <= 0) return;
        float fy = h / bgS[1];
        float fhTrue = fS[1] * fy;
        if (fhTrue >= h) return;
        // Spine offset: the inner (spine-side) strip of the art is cut
        // off before display; every horizontal position below follows
        // the same linear mapping (see spineOffsetPx). c0/c1 are the
        // paper cutout's TRUE texture-space edges (centered horizontally
        // in the FULL, uncropped art — the same assumption the face fit
        // in drawPageTexture uses).
        int s = spineCrop(bgS[0]);
        float k = w / (bgS[0] - s);
        float fwTrue = fS[0] * k;
        if (s <= 0 && fwTrue >= w && fhTrue >= h) return; // face covers the frame anyway
        float c0 = (bgS[0] - fS[0]) / 2f;  // cutout inner (spine) edge, texture px
        float c1 = (bgS[0] + fS[0]) / 2f;  // cutout outer edge, texture px
        // The static frame ring ends EXACTLY at the paper's true edge —
        // it neither recedes from it (v2.10.13's sign bug left a 2px gap
        // for the game world to leak through) nor overlaps into it
        // (covering the paper's worn rim kills the page's look). A
        // coincident edge is safe now because the SAME art is painted
        // UNDER the paper on every path (idle pre-backing in
        // Book.begin(), and the active flip's drawHalfLook): any
        // sub-pixel rounding at the seam just shows the identical pixels
        // from the layer below, so nothing peeks through and nothing is
        // covered. The UV endpoints sit exactly at the true texture
        // boundary — no epsilon in either direction.
        final float overlap = 0f;
        float oyTrue = (h - fhTrue) / 2f;   // screen y of the true cutout's top edge
        float byTrue = oyTrue + fhTrue;     // screen y of the true cutout's bottom edge
        // v maps 1:1 to the screen-y fraction of h (no vertical crop) —
        // cvT/cvB are both the TRUE cutout's v boundary AND oyTrue/h,
        // byTrue/h respectively.
        float cvT = oyTrue / h;
        float cvB = byTrue / h;
        float cu0 = spineU(c0, bgS[0]);  // u of the true cutout's inner (spine) edge
        float cu1 = spineU(c1, bgS[0]);  // u of the true cutout's outer edge
        // Top / bottom bands: span the half's full width, overlapping
        // the paper's true top/bottom edge by `overlap` px.
        drawQuadUV(g, bg, x, y, w, oyTrue + overlap, 0f, 0f, 1f, cvT, mirrored);
        drawQuadUV(g, bg, x, y + byTrue - overlap, w, h - byTrue + overlap, 0f, cvB, 1f, 1f, mirrored);
        // Inner (spine-side) and outer ring strips, in the row between
        // the top/bottom bands (which already cover the corners, being
        // full-width). Mirrored halves put the spine-side strip at the
        // screen spine edge, so the cut binding strip always disappears
        // at the CENTER of the book, never at an outer edge.
        float midY = y + oyTrue + overlap;
        float midH = fhTrue - overlap * 2f;
        if (midH > 0f) {
            float innerW = Math.max(0f, (c0 - s) * k);  // screen width to the true inner edge
            float outerW = Math.max(0f, (bgS[0] - c1) * k); // screen width of the true outer margin
            if (innerW > 0f) {
                if (mirrored) {
                    // Screen-right (x+w) is the true spine edge (u=0);
                    // screen-left is extended `overlap` further INTO the
                    // paper, at the true inner-edge u (cu0, no epsilon).
                    drawQuadUV(g, bg, x + w - innerW - overlap, midY, innerW + overlap, midH,
                            0f, cvT, cu0, cvB, true);
                } else {
                    drawQuadUV(g, bg, x, midY, innerW + overlap, midH,
                            0f, cvT, cu0, cvB, false);
                }
            }
            if (outerW > 0f) {
                if (mirrored) {
                    drawQuadUV(g, bg, x, midY, outerW + overlap, midH,
                            cu1, cvT, 1f, cvB, true);
                } else {
                    drawQuadUV(g, bg, x + w - outerW - overlap, midY, outerW + overlap, midH,
                            cu1, cvT, 1f, cvB, false);
                }
            }
        }
    }

    /** One textured quad (optionally mirrored horizontally). */
    private static void drawQuad(GuiGraphicsExtractor g, String texture,
                                 float x, float y, float w, float h, boolean mirrored) {
        drawQuadUV(g, texture, x, y, w, h, 0f, 0f, 1f, 1f, mirrored);
    }

    /** One textured quad sampling a sub-rectangle (u/v in 0..1). */
    private static void drawQuadUV(GuiGraphicsExtractor g, String texture,
                                   float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1,
                                   boolean mirrored) {
        if (w <= 0f || h <= 0f) return;
        Identifier loc;
        try {
            loc = Identifier.parse(texture);
        } catch (Exception invalidLocation) {
            return;
        }
        if (mirrored) { float tmp = u0; u0 = u1; u1 = tmp; }
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, loc);
        Matrix4f matrix = g.pose().last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y + h, 0.0f).setUv(u0, v1);
        buffer.addVertex(matrix, x + w, y + h, 0.0f).setUv(u1, v1);
        buffer.addVertex(matrix, x + w, y, 0.0f).setUv(u1, v0);
        buffer.addVertex(matrix, x, y, 0.0f).setUv(u0, v0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** PNG IHDR size cache ("path" -> {w, h}; {-1,-1} if unreadable). */
    private static final java.util.Map<String, int[]> PNG_SIZES = new java.util.HashMap<>();

    /** Reads a texture PNG's pixel size (cached; {-1,-1} on failure). */
    private static int[] pngSize(String texture) {
        String key = texture;
        int[] cached = PNG_SIZES.get(key);
        if (cached != null) return cached;
        int[] size = new int[]{-1, -1};
        try {
            Identifier loc = Identifier.parse(texture);
            var resource = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (resource.isPresent()) {
                try (var in = resource.get().open()) {
                    byte[] header = new byte[24];
                    int read = 0;
                    while (read < 24) {
                        int r = in.read(header, read, 24 - read);
                        if (r < 0) break;
                        read += r;
                    }
                    boolean png = read == 24 && header[0] == (byte) 0x89
                            && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
                    if (png && header[12] == 'I' && header[13] == 'H'
                            && header[14] == 'D' && header[15] == 'R') {
                        int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                                | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                        int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                                | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
                        if (w > 0 && h > 0) size = new int[]{w, h};
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // Only cache SUCCESSFUL reads. A failure usually means the
        // texture resource wasn't registered/ready yet on this call
        // (e.g. the very first frame) — caching that permanently would
        // leave the frame ring missing for the rest of the session even
        // once the texture becomes available, which read as "the
        // border stays transparent" even while completely idle.
        if (size[0] > 0) PNG_SIZES.put(key, size);
        return size;
    }
}
