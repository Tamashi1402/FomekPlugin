package net.tamashi.fomekcore.api.guisystems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FomekMenus Book — an id-based multi-page book, structured like panels.
 *
 * DECLARATION (every frame, inside the menu render procedure):
 *      Book.begin(id, x, y, pageWidth, height, flipDurationMs)
 *      ... Page blocks (each Page block calls book.page(pageId) and, if it
 *          is the current page, renders its Left/Right side as panels ...)
 *      Book.end()        <- runs the grab interaction + PageFlip update
 *
 * The Book block generates begin/end automatically; you only nest Page
 * blocks inside it. Books are registered by id (Book.byId), pages have ids
 * like any other menu object: loop them with getPageIds(), test the
 * current one with isCurrentPageId(...), read it with getCurrentPageId().
 *
 * MODEL: a Page is one SHEET of the book with two faces — a Left side
 * (what the sheet shows while lying on the left half) and a Right side
 * (what it shows while lying on the right half). The book shows the
 * current sheet on the right and the previous sheet on the left: a fresh
 * book (page 0) has an EMPTY left half, exactly like a real book. The
 * PageFlip effect replays rendered pixels, so all components flip with
 * the sheet.
 *
 * INPUT LOCK: while a flip is running (timed, grabbed, or releasing),
 * acceptPageInput() returns false — the Page block declares its side
 * panels with collision disabled, so nothing inside the pages can be
 * clicked/edited mid-animation. Only the page grab itself responds.
 */
public class Book {

    /** All books by id (persist across frames). */
    private static final Map<String, Book> BOOKS = new LinkedHashMap<>();
    /** The book currently being declared (between begin() and end()). */
    private static Book declaring = null;
    /**
     * Book-level textures set via bookTexture(id, ...) BEFORE that book's
     * first begin() ever ran (e.g. an init procedure that runs before the
     * menu's first render frame). Applied automatically the next time
     * begin() runs for that id, so the call is never silently lost —
     * previously this is exactly how a half- or fully-missing book
     * texture happened. Keyed by id -> {left, right, coverOutside,
     * coverInside} (any may be null; the cover slots are v2.10.18).
     */
    private static final Map<String, String[]> PENDING_BOOK_TEXTURE = new LinkedHashMap<>();
    /**
     * Openable flag set via setOpenable(id, ...) BEFORE that book's
     * first begin() (same never-lost pattern). Applied automatically
     * at the next begin() for that id.
     */
    private static final Map<String, Boolean> PENDING_OPENABLE = new LinkedHashMap<>();
    /** Same pending pattern for the offset-to-center animation flag. */
    private static final Map<String, Boolean> PENDING_OFFSET_CENTER = new LinkedHashMap<>();
    /**
     * Spine offsets set via spineOffset(id, ...) BEFORE that book's
     * first begin() ever ran (same pattern as PENDING_BOOK_TEXTURE, so
     * the call is never silently lost). Applied automatically at the
     * next begin() for that id.
     */
    private static final Map<String, Integer> PENDING_SPINE_OFFSET = new LinkedHashMap<>();

    private final String id;
    private final Map<String, BookPage> pagesById = new LinkedHashMap<>();
    private final List<BookPage> order = new ArrayList<>();
    /** Frame counter used to drop pages that were not re-declared. */
    private int frame = 0;

    /** Index of the current page (spread). 0-based. */
    private int current = 0;

    // ── Geometry: absolute screen position + size of ONE page ───────
    private float x = 0f, y = 0f;
    private int pageWidth = 0, height = 0;
    /** Flip animation length in ms. */
    private long flipDurationMs = 320;

    // ── Grabbable pages (built-in mouse interaction) ────────────────
    /** True = the player can grab pages with the mouse. */
    private boolean grabbable = false;
    /**
     * How far from the OUTER edge of a page a grab is accepted,
     * as a fraction of the page width (0.25 = outer quarter strip,
     * 0.5 = outer half, 1.0 = whole page). 0 would be the edge line
     * itself, so it is clamped to a minimum of 0.02.
     */
    private float grabInset = 0.25f;
    /** Check ids that must all pass for a grab to start. */
    private String[] grabChecks = new String[0];

    // Grab session state
    private boolean wasMouseDown = false;
    private boolean pendingGrab = false;   // press detected; grab starts next frame
    private boolean pendingForward = true;
    private int pendingRevert = 0;
    private boolean grabbing = false;
    private boolean grabForward = true;
    private float grabStartX = 0f;
    /**
     * Spread indices of the flip currently running: flipFromIdx = the
     * spread the flip starts from, flipToIdx = the spread it ends in
     * (current += 1 / -= 1). Book.end() uses them to hand PageFlip the
     * OLD and NEW "look" of each half, so the animation can composite
     * the frame art and the two spreads correctly. Set on every grab /
     * timed flip start; unchanged during the release tween (the same
     * old/new pair stays valid while the sheet flies home).
     */
    private int flipFromIdx = 0;
    private int flipToIdx = 0;

    // ── Debug logging ─────────────────────────────────────────────────────────
    // Same spam-proof scheme as PageFlip: identical lines print once,
    // changing lines hard-capped at 8/sec. DEBUG = false silences.
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("FomekMenus");
    /**
     * Master switch — set to false to silence FomekMenus logging.
     * v2.10.24: automatically ON when a file named "fomekmenus-debug.txt"
     * exists in the game's run directory — create that file, relaunch,
     * and the full Book/PageFlip trace lands in the client log.
     */
    public static boolean DEBUG = new java.io.File("fomekmenus-debug.txt").exists();

    static {
        // Printed exactly once per session — the definitive proof that
        // THIS runtime version actually injected into the workspace.
        LOG.info("[FomekMenus] book runtime v2.10.26.1 loaded (debug logging: {})",
                DEBUG);
    }
    private String lastGeometryKey = "";
    private static String lastLog = null;
    private static long logWindowStart = 0L;
    private static int logCountInWindow = 0;

    private void debug(String message, Object... args) {
        if (!DEBUG) return;
        String prefix = "[Book:" + id + "] ";
        StringBuilder sb = new StringBuilder(prefix);
        int argIdx = 0, from = 0, idx;
        while (args != null && argIdx < args.length
                && (idx = message.indexOf("{}", from)) != -1) {
            sb.append(message, from, idx);
            sb.append(args[argIdx++]);
            from = idx + 2;
        }
        sb.append(message.substring(from));
        String full = sb.toString();
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

    private Book(String bookId) {
        this.id = bookId;
    }

    // ── Registry ─────────────────────────────────────────────────────

    /**
     * Start declaring a book (called by the Book block). Creates or
     * refreshes the book's geometry; Page blocks go between this and
     * end().
     *
     * @param ax, ay the ANCHOR position: which point of the book sits
     *               here is defined by pivot (default "center"):
     *               top-left / top / top-right / left / center / right /
     *               bottom-left / bottom / bottom-right. The book is
     *               2 * pageWidth wide.
     */
    public static Book begin(String bookId, float ax, float ay, int pageWidth, int height,
                             long flipDurationMs, String pivot) {
        boolean firstTime = !BOOKS.containsKey(bookId);
        Book b = BOOKS.computeIfAbsent(bookId, Book::new);
        if (firstTime) {
            Integer pendingSpine = PENDING_SPINE_OFFSET.remove(bookId);
            if (pendingSpine != null) b.spineOffset = pendingSpine;
            String[] pending = PENDING_BOOK_TEXTURE.remove(bookId);
            if (pending != null) {
                if (pending[0] != null) b.bookLeftTexture = pending[0];
                if (pending[1] != null) b.bookRightTexture = pending[1];
                if (pending.length > 2 && pending[2] != null) b.coverOutsideTexture = pending[2];
                if (pending.length > 3 && pending[3] != null) b.coverInsideTexture = pending[3];
                if (pending.length > 4 && pending[4] != null) b.backOutsideTexture = pending[4];
                if (pending.length > 5 && pending[5] != null) b.backInsideTexture = pending[5];
                b.debug("begin(): applied book texture that was set before this book's "
                        + "first begin() (left={}, right={}, coverOutside={}, coverInside={})",
                        pending[0], pending[1],
                        pending.length > 2 ? pending[2] : null,
                        pending.length > 3 ? pending[3] : null);
            }
            Boolean pendingOpenable = PENDING_OPENABLE.remove(bookId);
            if (pendingOpenable != null) b.applyOpenable(pendingOpenable);
            Boolean pendingOffset = PENDING_OFFSET_CENTER.remove(bookId);
            if (pendingOffset != null) b.offsetToCenter = pendingOffset;
        }
        b.pageWidth = Math.max(0, pageWidth);
        b.height = Math.max(0, height);
        float w = 2f * b.pageWidth;
        switch (pivot == null ? "center" : pivot) {
            case "top-left":     b.x = ax;          b.y = ay;                    break;
            case "top":          b.x = ax - w / 2f; b.y = ay;                    break;
            case "top-right":    b.x = ax - w;      b.y = ay;                    break;
            case "left":         b.x = ax;          b.y = ay - b.height / 2f;    break;
            case "right":        b.x = ax - w;      b.y = ay - b.height / 2f;    break;
            case "bottom-left":  b.x = ax;          b.y = ay - b.height;         break;
            case "bottom":       b.x = ax - w / 2f; b.y = ay - b.height;         break;
            case "bottom-right": b.x = ax - w;      b.y = ay - b.height;         break;
            default:             b.x = ax - w / 2f; b.y = ay - b.height / 2f;    break; // center
        }
        StudioRuntime.hit(StudioRuntime.key(), b.getLeftX(), b.getTopY(), 2 * b.pageWidth, b.height, bookId);
        MenuStyle studioStyle = StudioRuntime.style();
        if (studioStyle != null && MenuRenderHelper.getGuiGraphics() != null) {
            studioStyle.background(MenuRenderHelper.getGuiGraphics(), (int)b.getLeftX(), (int)b.getTopY(), 2*b.pageWidth, b.height);
        }
        if (flipDurationMs > 0) b.flipDurationMs = Math.max(50L, flipDurationMs);
        // Spine offset (per book, set via the "Set book spine offset"
        // block; default 7): cut this much off the art's inner edge and
        // move the page faces along with it, so the two pages meet at
        // the center with no binding strip between them. Applies for
        // this book's whole render (begin..end), flip included.
        PageFlip.setSpineOffset(b.spineOffset);
        // v2.10.22: the BOOK BODY always sits under the pages. Interior
        // cover art fills each visible half BEFORE Page children run, so
        // transparent padding in page.png shows the book, not the world.
        // Idle frames only — during a flip the composite's looks take over.
        // Closed halves are not drawn here (end() paints the outside cover).
        if (!PageFlip.isActive()) {
            int vis = b.visualIndex();
            float slide = b.slideX();
            boolean closedFront = b.openable && vis == -1;
            boolean closedBack = b.openable && !b.order.isEmpty() && vis == b.closedBackIdx();
            if (!closedFront && !closedBack) {
                String iL = b.interiorLeft();
                String iR = b.interiorRight();
                if (iL != null)
                    PageFlip.drawCoverTexture(iL, b.x + slide, b.y, b.pageWidth, b.height, true);
                if (iR != null)
                    PageFlip.drawCoverTexture(iR, b.x + slide + b.pageWidth, b.y, b.pageWidth, b.height, false);
            }
        }
        // v2.10.24: COVER faces of the DESTINATION spread must be
        // inside the offscreen `next` snapshot. The incoming pass is
        // driven by Page blocks, which only draw PAGE content — so on
        // every cover flip (opening the front cover, closing it, the
        // back-cover open/close steps) the half that will hold a COVER
        // was never captured, and the flipping sheet — which samples
        // `next` for its back face once past 50% — drew a fully
        // TRANSPARENT region: the sheet vanished mid-drag and only
        // reappeared on release. Draw the destination's cover art into
        // the FBO here, BEFORE the Page children, so page art still
        // layers on top and the snapshot holds the complete spread.
        // v2.10.26: the FROM spread's COVER faces must be in the clean
        // `out` buffer too — the flip composite replays the from side
        // (and the sweeping sheet's front face) from it. Without this
        // the from cover would be missing from the replay. Same
        // conditions as the direct draws in end(), evaluated at
        // flipFromIdx instead of the render index.
        if (b.isOutgoingRenderNeeded()) {
            try {
                if (PageFlip.beginOutgoingPass()) {
                try {
                    float s = b.slideX(); // same slide the Page children use this frame
                    int from = b.flipFromIdx;
                    if (b.openable && from == -1 && b.coverOutsideTexture != null) {
                        // from-state closed: the cover lies on the RIGHT half
                        PageFlip.drawCoverTexture(b.coverOutsideTexture,
                                b.x + s + b.pageWidth, b.y, b.pageWidth, b.height, false);
                    }
                    if (b.openable && from == 0 && !b.order.isEmpty()
                            && b.coverInsideTexture != null) {
                        // from-state fresh-open: cover inside on the LEFT half
                        PageFlip.drawCoverTexture(b.coverInsideTexture,
                                b.x + s, b.y, b.pageWidth, b.height, true);
                    }
                    if (b.hasBackCover() && from == b.order.size()
                            && b.backInsideTexture != null) {
                        // from-state back cover open on the RIGHT half
                        PageFlip.drawCoverTexture(b.backInsideTexture,
                                b.x + s + b.pageWidth, b.y, b.pageWidth, b.height, false);
                    }
                    if (b.openable && !b.order.isEmpty()
                            && from == b.closedBackIdx() && b.backOutsideTexture != null) {
                        // from-state closed from the back: outside on the LEFT half
                        PageFlip.drawCoverTexture(b.backOutsideTexture,
                                b.x + s, b.y, b.pageWidth, b.height, true);
                    }
                } finally {
                    PageFlip.endOutgoingPass();
                }
                }
            } catch (Throwable t) {
                b.debug("outgoing cover pre-pass failed: {}", t.toString());
            }
        }
        if (b.isIncomingCaptureNeeded()) {
            try {
                if (PageFlip.beginIncomingPass()) {
                try {
                    float s = b.slideX(); // same slide the Page children use this frame
                    int to = b.flipToIdx;
                    if (b.openable && to == -1 && b.coverOutsideTexture != null) {
                        // closing onto the front cover: the closed cover
                        // lies on the RIGHT half (the sheet's landing side)
                        PageFlip.drawCoverTexture(b.coverOutsideTexture,
                                b.x + s + b.pageWidth, b.y, b.pageWidth, b.height, false);
                    }
                    if (b.openable && to == 0 && !b.order.isEmpty()
                            && b.coverInsideTexture != null) {
                        // opening: the cover's INSIDE lands on the LEFT half
                        PageFlip.drawCoverTexture(b.coverInsideTexture,
                                b.x + s, b.y, b.pageWidth, b.height, true);
                    }
                    if (b.hasBackCover() && to == b.order.size()
                            && b.backInsideTexture != null) {
                        // last page turned: the back cover lies OPEN on
                        // the RIGHT half, inside facing up
                        PageFlip.drawCoverTexture(b.backInsideTexture,
                                b.x + s + b.pageWidth, b.y, b.pageWidth, b.height, false);
                    }
                    if (b.openable && !b.order.isEmpty()
                            && to == b.closedBackIdx() && b.backOutsideTexture != null) {
                        // closing from the back: the back cover's OUTSIDE
                        // lands on the LEFT half
                        PageFlip.drawCoverTexture(b.backOutsideTexture,
                                b.x + s, b.y, b.pageWidth, b.height, true);
                    }
                } finally {
                    PageFlip.endIncomingPass();
                }
                }
            } catch (Throwable t) {
                b.debug("incoming cover pre-pass failed: {}", t.toString());
            }
        }
        b.frame++;
        declaring = b;
        String key = (int) b.x + "/" + (int) b.y + "/" + b.pageWidth + "/" + b.height
                + "/" + b.order.size() + "/" + b.current + "/spine=" + b.spineOffset;
        if (b.DEBUG && !key.equals(b.lastGeometryKey)) {
            b.lastGeometryKey = key;
            b.debug("begin() geometry: x={}, y={}, pageWidth={}, height={}, flipDurationMs={}, "
                    + "spineOffsetPx={}, pages={} (this message only prints when something changes)",
                    (int) b.x, (int) b.y, b.pageWidth, b.height, b.flipDurationMs,
                    b.spineOffset, b.order.size());
        }
        return b;
    }

    /**
     * Finish declaring: drops pages that were not re-declared this frame,
     * runs the grab interaction and the flip animation. Called by the
     * Book block right after its Page children.
     */
    public static Book end() {
        Book b = declaring;
        declaring = null;
        if (b == null) {
            if (DEBUG) LOG.info("[Book] end() without begin() — nothing declared");
            return null;
        }
        b.order.removeIf(p -> p.seenFrame != b.frame);
        b.pagesById.values().removeIf(p -> p.seenFrame != b.frame);
        // current may be 0..size inclusive: size == the END state where
        // the last sheet lies on the LEFT showing its back (cover) and
        // the right half is empty — like a fully paged-through book.
        if (b.current > b.closedBackIdx()) b.current = b.closedBackIdx();
        if (b.current < (b.openable ? -1 : 0)) b.current = b.openable ? -1 : 0;
        // Look of the spread THE USER CONTENT DREW THIS FRAME (current
        // as it was at begin() time). Computed BEFORE handleMouseGrab()
        // so on the press frame — where handleMouseGrab advances
        // current a tick before the grab animation actually starts —
        // the overlay matches the content actually on screen, and the
        // "previous" snapshot PageFlip captures this frame is a clean,
        // consistent old state.
        boolean hasPages = !b.order.isEmpty();
        // v2.10.21 (press-frame fix): while a grab press is pending, the
        // children just rendered the PRE-PRESS state but `current` is
        // already advanced one tick for the grab that starts next frame.
        // Everything end() draws this frame (sheets, cover art, slide,
        // overlay) must follow the state the user actually SEES — the
        // old one — or the cover instantly "teleports" to the other
        // half the moment the mouse goes down (the duplicate bug).
        // v2.10.23 (cover render-order fix): once a grab press is
        // consumed, pendingGrab goes false but the flip is still ACTIVE
        // for its whole duration — current already points at the
        // DESTINATION index that whole time. BookPage content already
        // pins its live display to flipFromIdx for pendingGrab OR an
        // active flip (see visualIndex()/shouldRender()), but this
        // variable only checked pendingGrab, so every cover-art draw
        // below (closedFront / coverOnLeft / backCoverOpen / closedBack)
        // silently switched to the DESTINATION cover the instant the
        // grab started and stayed there, unconditionally, for the whole
        // drag — painted straight to the live screen every frame, UNDER
        // nothing, regardless of how far the player had actually
        // dragged. That's the "destination already there" / "the
        // dragged cover is invisible until I let go" bug: the real
        // in-progress composite (PageFlip.update() below) was doing its
        // job correctly, but this unconditional direct draw of the
        // destination's cover texture punched straight through it. The
        // fix mirrors visualIndex(): pin to the FROM state for the
        // entire active flip, not just the one pendingGrab frame.
        int renderCurrent = (b.pendingGrab || PageFlip.isActive()) ? b.flipFromIdx : b.current;
        boolean leftSheet = hasPages && renderCurrent - 1 >= 0 && renderCurrent - 1 < b.order.size();
        // Openable books: current can also be -1 (CLOSED, cover lying on
        // the right half) — that half holds the COVER, not a page sheet.
        boolean rightSheet = hasPages && renderCurrent >= 0 && renderCurrent < b.order.size();
        // v2.10.21 (reading order): a sheet's LEFT slot is the side you
        // read FIRST — it lies on the RIGHT half (where the sheet starts)
        // and flips over to show its RIGHT slot on the LEFT half. The
        // halves are physical; the slots are reading order.
        String leftFace = leftSheet
                ? b.getRightFaceTexture(b.order.get(renderCurrent - 1)) : null;
        String rightFace = rightSheet
                ? b.getLeftFaceTexture(b.order.get(renderCurrent)) : null;
        // Defensive: the book-level texture is meant to always back the
        // page, even if only one side was ever set (e.g. a "both" call
        // that landed before begin() first ran, or a left-only setup).
        // Mirror the side that IS set onto the side that's missing so a
        // half-set book texture never leaves a visible hole.
        String leftBg = b.bookLeftTexture != null ? b.bookLeftTexture : b.bookRightTexture;
        String rightBg = b.bookRightTexture != null ? b.bookRightTexture : b.bookLeftTexture;
        if (b.bookLeftTexture == null && b.bookRightTexture == null) {
            b.debug("book texture is NOT SET on either side — the frame will not render "
                    + "(use the \"Set book texture\" block with side=both, called AFTER "
                    + "the Book block has run at least once)");
        }
        // Child controls render after their face texture. Reapply the
        // silhouette before capture so a rectangular panel cannot erase the
        // PNG's ragged transparent edge during a flip.
        // ── Openable cover state + "offset to center" slide (v2.10.18).
        // slide is computed ONCE here, from the state that is actually
        // on screen this frame (BEFORE handleMouseGrab can advance the
        // page index), so the cover art, the stamps, the snapshot
        // captures and the grab zones all agree on one position.
        float slide = b.pendingGrab ? b.slideXFor(b.flipFromIdx) : b.slideX();
        boolean closedFront = b.openable && renderCurrent == -1;
        // Books with a dedicated back cover stay OPEN at the end state
        // (the back cover's inside showing) — closing is the step after it.
        boolean backCoverOpen = b.hasBackCover() && renderCurrent == b.order.size();
        boolean closedBack = b.openable && hasPages && renderCurrent == b.closedBackIdx();
        boolean coverOnLeft = b.openable && hasPages && renderCurrent == 0
                && b.coverInsideTexture != null;
        if (closedFront && b.coverOutsideTexture == null) {
            b.debug("book is CLOSED but no cover OUTSIDE texture is set "
                    + "(Set book texture, side=cover outside) — falling back to the "
                    + "empty book interior look");
        }
        PageFlip.flushRender();
        if (leftSheet) PageFlip.stampPageAlpha(leftFace, leftBg,
                b.x + slide, b.y, b.pageWidth, b.height, true);
        if (rightSheet) PageFlip.stampPageAlpha(rightFace, rightBg,
                b.x + slide + b.pageWidth, b.y, b.pageWidth, b.height, false);
        // Cover art draws BEFORE handleMouseGrab/update, so idle "previous"
        // and flip "next" snapshots capture it with the page pixels.
        // v2.10.26: while a flip is ACTIVE the composite owns the screen —
        // it replays the from-cover from the clean `out` buffer. Drawing
        // the cover DIRECTLY as well meant the same semi-transparent art
        // was alpha-blended TWICE (once direct, once through the replay),
        // visibly darkening it, and the direct copy sat at the old
        // position under the sweep. Skip the direct draws while active.
        boolean coverDirectAllowed = !PageFlip.isActive();
        if (coverDirectAllowed && closedFront && b.coverOutsideTexture != null) {
            PageFlip.drawCoverTexture(b.coverOutsideTexture,
                    b.x + slide + b.pageWidth, b.y, b.pageWidth, b.height, false);
        } else if (coverDirectAllowed && coverOnLeft) {
            PageFlip.drawCoverTexture(b.coverInsideTexture,
                    b.x + slide, b.y, b.pageWidth, b.height, true);
        }
        if (coverDirectAllowed && backCoverOpen) {
            // The back cover lies open on the right half — its INSIDE
            // faces up (drawn like a page's right face, not mirrored).
            if (b.backInsideTexture != null)
                PageFlip.drawCoverTexture(b.backInsideTexture,
                        b.x + slide + b.pageWidth, b.y, b.pageWidth, b.height, false);
        } else if (coverDirectAllowed && closedBack && b.backOutsideTexture != null) {
            // Closed from the back: the back cover's OUTSIDE faces up on
            // the left half, mirrored like a page's left face.
            PageFlip.drawCoverTexture(b.backOutsideTexture,
                    b.x + slide, b.y, b.pageWidth, b.height, true);
        }
        b.handleMouseGrab(slide);
        PageFlip.finalizeIncomingPass(slide);
        PageFlip.finalizeOutgoingPass(slide);
        if (PageFlip.isActive()) {
            // ACTIVE FLIP: composite from paper-only snapshots + the
            // old/new looks. Frame art is painted UNDER the paper
            // replay inside drawBookFlip — never on top of it, and
            // never inside the snapshot — so book.png stays still
            // while only page.png (+ content) turns.
            // v2.10.21: the grab may have STARTED in handleMouseGrab
            // above — on that frame `slide` was computed before it
            // was active (0 for the new state). Recompute so the
            // interpolation already matches the flip (amount 0 =
            // the closed slide).
            float activeSlide = b.slideX();
            // v2.10.21: cover flips are FLAT — a cover is a solid
            // board, it sweeps straight across without bending or
            // curling like a paper sheet does.
            boolean coverFlip = b.flipFromIdx == -1 || b.flipToIdx == -1
                    || (b.hasBackCover()
                        && (b.flipFromIdx == b.closedBackIdx()
                            || b.flipToIdx == b.closedBackIdx()));
            PageFlip.Look oldLeftLook = b.lookLeft(b.flipFromIdx);
            PageFlip.Look oldRightLook = b.lookRight(b.flipFromIdx);
            PageFlip.Look newLeftLook = b.lookLeft(b.flipToIdx);
            PageFlip.Look newRightLook = b.lookRight(b.flipToIdx);
            PageFlip.update(b.x + activeSlide, b.y, b.pageWidth, b.height, activeSlide,
                    oldLeftLook, oldRightLook, newLeftLook, newRightLook, coverFlip);
        } else {
            // IDLE: snapshot the paper. Interior cover art was already
            // painted under the pages in begin() — do NOT draw a leather
            // frame overlay or brown quad on top (that was the corner
            // bleed). Empty closed halves stay empty (world shows around
            // the half-wide book).
            PageFlip.update(b.x + slide, b.y, b.pageWidth, b.height, slide);
        }
        // Book-driven panels: hide any side panel whose page did not
        // render this frame (flipped away / book closed) so its content
        // does not keep floating where the page art no longer is.
        // During an active flip, hide ALL page panels — renderElements
        // runs AFTER the procedure and would otherwise stamp live page
        // content on top of the turning sheet (the "content already on
        // the other side" bug). Incoming content was captured offscreen.
        boolean hideAll = PageFlip.isActive();
        for (BookPage p : b.order) {
            VirtualGui.hidePagePanel(b.sidePanelId(p, true) + "_in");
            VirtualGui.hidePagePanel(b.sidePanelId(p, false) + "_in");
            VirtualGui.hidePagePanel(b.sidePanelId(p, true) + "_out");
            VirtualGui.hidePagePanel(b.sidePanelId(p, false) + "_out");
            if (hideAll) {
                VirtualGui.hidePagePanel(b.sidePanelId(p, true));
                VirtualGui.hidePagePanel(b.sidePanelId(p, false));
            } else {
                VirtualGui.hideStalePagePanel(b.sidePanelId(p, true));
                VirtualGui.hideStalePagePanel(b.sidePanelId(p, false));
            }
        }
        return b;
    }

    /** The book currently being declared (used by Page blocks). */
    public static Book current() {
        return declaring;
    }

    /** True when the book has a dedicated BACK COVER (openable with a
     *  back_book_outside texture set). Such books get the extra "back
     *  cover" step: current can reach page count + 1, where the state
     *  at page count shows the back cover's INSIDE instead of closing. */
    public boolean hasBackCover() {
        return openable && backOutsideTexture != null && !order.isEmpty();
    }

    /** The closed-from-the-back state index: page count, or page count
     *  + 1 when the book has a dedicated back cover (whose open state
     *  sits at page count instead). */
    public int closedBackIdx() {
        return order.size() + (hasBackCover() ? 1 : 0);
    }

    /**
     * Look up a book by id. Never returns null: unknown ids give a
     * detached placeholder with no geometry, so all operations no-op
     * safely.
     */
    public static Book byId(String bookId) {
        Book b = BOOKS.get(bookId);
        return b != null ? b : new Book(bookId);
    }

    public String getId() {
        return id;
    }

    // ── Page textures (set with the "Set page texture" block) ────────
    // Keyed by page id and independent of page lifecycle, so they can
    // be set before the Page block runs.
    private final Map<String, String> leftTextures = new LinkedHashMap<>();
    private final Map<String, String> rightTextures = new LinkedHashMap<>();

    /**
     * Set a page's background texture. side: "both", "left" or
     * "right" (the side a sheet shows when lying on the left / right
     * half). Texture = resource location string. Call inside the Book
     * block (static helper null-checks for you). Overrides the
     * book-level default.
     */
    public static void pageTexture(String pageId, String side, String texture) {
        Book b = declaring;
        if (b == null || pageId == null || texture == null) return;
        if ("left".equals(side) || "both".equals(side)) b.leftTextures.put(pageId, texture);
        if (!"left".equals(side)) b.rightTextures.put(pageId, texture);
    }

    // Book-level default texture: every page shares it until a page
    // sets its own.
    private String bookLeftTexture = null;
    private String bookRightTexture = null;

    /**
     * Spine offset for THIS book, in book-art texture px (see
     * PageFlip.setSpineOffset): the inner (spine-side) strip of the
     * art is cut off before display and the page faces follow, so the
     * two pages meet at the center with no binding strip between them.
     * 0 disables. Default 7 — matches the reference book.png's measured
     * margin exactly (146px wide art, 132px wide page face centered ->
     * (146-132)/2 = 7px each side), so stock removes the brown strip
     * completely with the two pages just touching (no overlap yet).
     * Go higher to make them overlap at the center.
     */
    private int spineOffset = 7;

    // ── Openable cover (v2.10.18) ──────────────────────────────────
    /**
     * True = the book starts CLOSED: the front cover lies on the right
     * half (cover-outside up) and the player opens it by grabbing its
     * outer edge, exactly like grabbing a page. Closing works in
     * reverse: grab the cover's outer edge while it lies open on the
     * left (page 0), or flip all the way through to the end state
     * (closed from the back). current then also uses the extra value
     * -1 (closed front). False (default) = always open, exactly the
     * legacy behavior.
     */
    private boolean openable = false;
    /**
     * True = while the cover opens/closes (and while the book sits in
     * either closed state) the whole book art slides horizontally so
     * the half-wide closed book is CENTERED on the book's anchor:
     * closed front shifts -pageWidth/2, closed back +pageWidth/2, and
     * the slide follows the flip progress during the animation.
     * Default TRUE — a closed book must sit in the middle; the block
     * can still turn this off.
     */
    private boolean offsetToCenter = true;
    /** Art shown while closed: the cover's OUTSIDE (full page art). */
    private String coverOutsideTexture = null;
    /** Art shown on the left half once the cover is open: the cover's
     *  INSIDE, oriented like a page's LEFT face (mirrored drawing). */
    private String coverInsideTexture = null;
    /** True while the pending/active grab session is a COVER open/close
     *  (allowed even when the book is not page-grabbable). */
    private boolean pendingCover = false;
    /** Art shown while the book is CLOSED FROM THE BACK: the back
     *  cover's OUTSIDE, drawn on the left half (mirrored like a page's
     *  left face). When set, the book gains a "back cover" step: the
     *  end state (current == page count) shows the back cover's INSIDE
     *  on the right half (backInsideTexture) and stays open — one more
     *  flip closes the back cover over the pages (current == count+1). */
    private String backOutsideTexture = null;
    /** Art on the right half while the back cover lies open (the back
     *  cover's INSIDE). Only used when backOutsideTexture is set. */
    private String backInsideTexture = null;

    /**
     * Set a book's spine offset (Book art texture px). 0 disables.
     * Call anywhere; if the book has not begin()'d yet the value is
     * applied automatically at its first begin() (never lost).
     */
    public static void spineOffset(String bookId, int offsetPx) {
        if (bookId == null) return;
        Book b = BOOKS.get(bookId);
        if (b == null && declaring != null && bookId.equals(declaring.id)) {
            b = declaring;
        }
        if (b != null) {
            b.spineOffset = Math.max(0, offsetPx);
            b.debug("spineOffset set to {} px (book art texture px)", b.spineOffset);
        } else {
            PENDING_SPINE_OFFSET.put(bookId, Math.max(0, offsetPx));
        }
    }

    /**
     * Set the book's default page texture (all pages). side: "both",
     * "left" or "right" — or, for openable books, "cover_outside" /
     * "cover_inside" for the front cover's outside/inside art.
     * Per-page textures (pageTexture) override this. Call inside the
     * Book block, or anywhere after the book has been declared once.
     */
    /** True for side values that target a cover slot (not the left/right
     *  interior frame art): cover/front/back outside/inside. */
    private static boolean isCoverSide(String side) {
        return "cover_outside".equals(side) || "cover_inside".equals(side)
                || "front_book_outside".equals(side) || "front_book_inside".equals(side)
                || "back_book_outside".equals(side) || "back_book_inside".equals(side);
    }

    public static void bookTexture(String bookId, String side, String texture) {
        if (texture == null) return;
        Book b = BOOKS.get(bookId);
        if (b == null && declaring != null && bookId != null && bookId.equals(declaring.id)) {
            b = declaring;
        }
        if (b == null) {
            // Book hasn't begin()'d yet for this id — don't lose the call,
            // apply it the moment begin() first runs.
            String[] pending = PENDING_BOOK_TEXTURE.computeIfAbsent(bookId, k -> new String[6]);
            if ("left".equals(side) || "both".equals(side)) pending[0] = texture;
            if (!isCoverSide(side)) pending[1] = texture;
            if ("cover_outside".equals(side) || "front_book_outside".equals(side)) pending[2] = texture;
            if ("cover_inside".equals(side) || "front_book_inside".equals(side)) pending[3] = texture;
            if ("back_book_outside".equals(side)) pending[4] = texture;
            if ("back_book_inside".equals(side)) pending[5] = texture;
            return;
        }
        if ("left".equals(side) || "both".equals(side)) b.bookLeftTexture = texture;
        if (!isCoverSide(side)) b.bookRightTexture = texture;
        // Front cover (v2.10.18 "cover_*" names kept for old procedures)
        if ("cover_outside".equals(side) || "front_book_outside".equals(side)) b.coverOutsideTexture = texture;
        if ("cover_inside".equals(side) || "front_book_inside".equals(side)) b.coverInsideTexture = texture;
        // Back cover (v2.10.19)
        if ("back_book_outside".equals(side)) b.backOutsideTexture = texture;
        if ("back_book_inside".equals(side)) b.backInsideTexture = texture;
    }

    /** Texture of this page's left face, or null (falls back to Empty). */
    public String getLeftTexture(BookPage p) {
        String t = leftTextures.get(p.getId());
        return t != null ? t : bookLeftTexture;
    }

    /** Per-page LEFT face texture only (may be null — no fallback). */
    public String getLeftFaceTexture(BookPage p) {
        return leftTextures.get(p.getId());
    }

    /** Book-level LEFT background texture (the frame), or null. */
    public String getLeftBackgroundTexture(BookPage p) {
        return bookLeftTexture;
    }

    /** Texture of this page's right face, or null. */
    public String getRightTexture(BookPage p) {
        String t = rightTextures.get(p.getId());
        return t != null ? t : bookRightTexture;
    }

    /** Per-page RIGHT face texture only (may be null — no fallback). */
    public String getRightFaceTexture(BookPage p) {
        return rightTextures.get(p.getId());
    }

    /** Book-level RIGHT background texture (the frame), or null. */
    public String getRightBackgroundTexture(BookPage p) {
        return bookRightTexture;
    }

    // ── Page declaration (called by Page blocks) ─────────────────────

    /** Register/refresh a page by id (insertion order = page order). */
    public BookPage page(String pageId) {
        BookPage p = pagesById.get(pageId);
        if (p == null) {
            p = new BookPage(pageId);
            pagesById.put(pageId, p);
            order.add(p);
        }
        p.seenFrame = frame;
        return p;
    }

    /**
     * True if the given SIDE of this page is visible right now (sheet
     * logic): a sheet lies on the RIGHT when it is the current sheet,
     * and on the LEFT when it is the previous sheet. A fresh book
     * (current = 0) shows only the first page's right side - nothing on
     * the left, exactly like a real book.
     *
     * v2.10.22: while a flip is pending/active this uses the FROM
     * index (what the player still sees) so the destination page's
     * content is not stamped onto the live screen. Incoming content
     * is drawn offscreen via shouldRenderIncoming().
     */
    public boolean shouldRender(BookPage p, boolean left) {
        if (p == null || p.seenFrame != frame || order.isEmpty()) return false;
        // Once the flip composite is running, live page draws would
        // sit under (and after, via renderElements, ON TOP of) the
        // turning sheet. Skip them — snapshots + looks handle it.
        if (PageFlip.isActive() && !pendingGrab) return false;
        int idx = order.indexOf(p);
        int vis = visualIndex();
        return left ? idx == vis - 1 : idx == vis;
    }

    /**
     * True when this page side belongs to the spread the flip is
     * turning TO. Drawn into the offscreen "next" snapshot so the
     * back of the turning sheet / the revealed underside have their
     * real content, without showing that content on the live screen.
     */
    public boolean shouldRenderIncoming(BookPage p, boolean left) {
        if (p == null || p.seenFrame != frame || order.isEmpty()) return false;
        if (!isIncomingCaptureNeeded()) return false;
        int idx = order.indexOf(p);
        int to = flipToIdx;
        return left ? idx == to - 1 : idx == to;
    }

    /** True on the frame(s) we still need to render the destination
     *  spread into the offscreen snapshot. */
    public boolean isIncomingCaptureNeeded() {
        return (pendingGrab || PageFlip.isActive()) && PageFlip.needsIncomingCapture();
    }

    /**
     * v2.10.26: the FROM spread must also render OFFSCREEN (into the
     * clean `out` buffer) for the whole flip — the old code replayed it
     * from a blit of the live screen, which dragged the captured world/
     * GUI background along with the art. Same shape as
     * isIncomingCaptureNeeded().
     */
    public boolean isOutgoingRenderNeeded() {
        return (pendingGrab || PageFlip.isActive()) && PageFlip.needsOutgoingRender();
    }

    /**
     * v2.10.26: true while Page block content for the FROM spread should
     * draw into the offscreen `out` buffer. Mirrors shouldRender()'s slot
     * logic evaluated at flipFromIdx (right half holds sheet
     * flipFromIdx's left slot; left half holds sheet flipFromIdx-1's
     * right slot).
     */
    public boolean shouldRenderOutgoing(BookPage p, boolean left) {
        if (p == null || p.seenFrame != frame || order.isEmpty()) return false;
        if (!pendingGrab && !PageFlip.isActive()) return false;
        if (!PageFlip.needsOutgoingRender()) return false;
        int idx = order.indexOf(p);
        int from = flipFromIdx;
        return left ? idx == from - 1 : idx == from;
    }

    /**
     * The page index the player should SEE this frame. During a pending
     * grab / active flip this is the FROM index — current has already
     * hopped to the destination so the incoming pass can target it.
     */
    public int visualIndex() {
        if (pendingGrab || PageFlip.isActive()) return flipFromIdx;
        return current;
    }

    // ── Geometry helpers (used by the generated Page panels) ─────────

    public float getLeftX()   { return x + slideX(); }
    public float getRightX()  { return x + pageWidth + slideX(); }
    public float getTopY()    { return y; }
    public float getBottomY() { return y + height; }
    public int getPageWidth() { return pageWidth; }

    /**
     * Horizontal art shift from "offset to center" (v2.10.22).
     * The OPEN two-page spread is centered on the anchor (slide 0).
     * The closed front cover is a half-width book on the RIGHT half,
     * so it slides LEFT by pageWidth/2 to sit on the anchor.
     * The closed back cover is a half-width book on the LEFT half,
     * so it slides RIGHT by pageWidth/2.
     * Opening therefore expands OUTWARD from the centered closed book.
     * Always 0 unless the book is openable AND offsetToCenter is on
     * (on by default for openable books).
     */
    public float slideX() {
        if (!openable || !offsetToCenter || pageWidth <= 0) return 0f;
        if (pendingGrab) return slideXFor(flipFromIdx);
        if (PageFlip.isActive()) {
            int end = closedBackIdx();
            boolean fromFront = flipFromIdx == -1;
            boolean fromBack = !order.isEmpty() && flipFromIdx == end;
            boolean toFront = flipToIdx == -1;
            boolean toBack = !order.isEmpty() && flipToIdx == end;
            if (!fromFront && !fromBack && !toFront && !toBack) return 0f;
            float fromSign = fromFront ? -1f : (fromBack ? 1f : 0f);
            float toSign = toFront ? -1f : (toBack ? 1f : 0f);
            // Interpolate the actual signed slides, not a single sign —
            // front-closed (-half) opening to open (0) is not the same
            // path as back-closed (+half). Using one sign made the end
            // state overshoot so the half-book sat off-center.
            float fromSlide = fromSign * halfWidth();
            float toSlide = toSign * halfWidth();
            float t = PageFlip.getAmount();
            return fromSlide + (toSlide - fromSlide) * t;
        }
        return slideXFor(current);
    }

    /** The static "offset to center" slide for a given state index. */
    public float slideXFor(int idx) {
        if (!openable || !offsetToCenter || pageWidth <= 0) return 0f;
        if (idx == -1) return -halfWidth();
        if (!order.isEmpty() && idx == closedBackIdx()) return halfWidth();
        return 0f;
    }

    private float halfWidth() { return pageWidth / 2f; }

    /**
     * Enable/disable the openable cover (v2.10.18 block). When it turns
     * ON, a book sitting at page 0 starts CLOSED (grab the cover's
     * outer edge to open it). Call inside the Book block or before its
     * first begin(); pre-begin calls are never lost.
     */
    public static void setOpenable(String bookId, boolean value) {
        if (bookId == null) return;
        Book b = BOOKS.get(bookId);
        if (b == null && declaring != null && bookId.equals(declaring.id)) {
            b = declaring;
        }
        if (b != null) {
            b.applyOpenable(value);
        } else {
            PENDING_OPENABLE.put(bookId, value);
        }
    }

    private void applyOpenable(boolean value) {
        if (value == openable) return;
        openable = value;
        if (value) {
            if (current == 0) {
                current = -1;
                debug("openable enabled — book starts CLOSED "
                        + "(grab the cover's outer edge to open it)");
            }
        } else if (current < 0) {
            current = 0;
            pendingGrab = false;
            pendingCover = false;
            grabbing = false;
            debug("openable disabled — book forced open at page 0");
        }
    }

    /**
     * Enable/disable the "offset to center" slide (v2.10.18 block):
     * while the cover opens/closes the whole book art slides so the
     * half-wide closed book stays centered on the book's anchor.
     * No effect on non-openable books.
     */
    public static void setOffsetToCenter(String bookId, boolean value) {
        if (bookId == null) return;
        Book b = BOOKS.get(bookId);
        if (b == null && declaring != null && bookId != null && bookId.equals(declaring.id)) {
            b = declaring;
        }
        if (b != null) {
            b.offsetToCenter = value;
        } else {
            PENDING_OFFSET_CENTER.put(bookId, value);
        }
    }

    /** True if this book has the openable cover enabled. */
    public boolean isOpenable() {
        return openable;
    }

    /** True if the offset-to-center slide is enabled. */
    public boolean isOffsetToCenter() {
        return offsetToCenter;
    }

    /** Panel id for one side of a page: <book>_<page>_left / _right. */
    public String sidePanelId(BookPage p, boolean left) {
        return id + "_" + p.getId() + (left ? "_left" : "_right");
    }

    /**
     * False while a flip animation runs (timed, grabbed or releasing).
     * The Page block passes this as the panels' collision flag, so
     * nothing inside the pages can be clicked/edited mid-animation —
     * only the page grab itself responds.
     */
    public boolean acceptPageInput() {
        return !PageFlip.isActive();
    }

    // ── Flip looks (old/new spread handed to PageFlip) ────────────────

    /** Interior art under the LEFT half (front cover inside, else book left). */
    public String interiorLeft() {
        if (coverInsideTexture != null) return coverInsideTexture;
        return bookLeftTexture != null ? bookLeftTexture : bookRightTexture;
    }

    /** Interior art under the RIGHT half (back cover inside, else front
     *  inside, else book right). One interior texture is enough to back
     *  both halves so transparent page padding never shows the world. */
    public String interiorRight() {
        if (backInsideTexture != null) return backInsideTexture;
        if (coverInsideTexture != null) return coverInsideTexture;
        return bookRightTexture != null ? bookRightTexture : bookLeftTexture;
    }

    /**
     * The LOOK of the LEFT half when the book shows spread index idx
     * (0..size): ring + the left face of sheet idx-1 if a sheet lies
     * there, or the full art (empty half) otherwise. Used to composite
     * the under-looks during a book flip.
     */
    public PageFlip.Look lookLeft(int idx) {
        String interior = interiorLeft();
        if (openable && !order.isEmpty()) {
            if (idx == -1) return new PageFlip.Look(null, null, false); // no left half while closed
            if (idx == 0 && coverInsideTexture != null)
                return new PageFlip.Look(interior, coverInsideTexture, true); // open cover lies here
            if (idx == order.size() + 1 && backOutsideTexture != null)
                return new PageFlip.Look(null, backOutsideTexture, true); // closed back cover lies here
            if (idx == order.size())
                return new PageFlip.Look(interior, getRightFaceTexture(order.get(idx - 1)), true);
        }
        boolean sheet = !order.isEmpty() && idx - 1 >= 0;
        String face = null;
        if (sheet) face = getRightFaceTexture(order.get(idx - 1));
        return new PageFlip.Look(interior, face, sheet);
    }

    /** Same as lookLeft for the RIGHT half of spread index idx. */
    public PageFlip.Look lookRight(int idx) {
        String interior = interiorRight();
        if (openable) {
            if (idx == -1) {
                // Closed front: the cover's OUTSIDE is the "sheet" lying
                // on the right half (replayed from the snapshot).
                if (coverOutsideTexture != null)
                    return new PageFlip.Look(null, coverOutsideTexture, true);
                return new PageFlip.Look(interior, null, false);
            }
            if (!order.isEmpty() && idx == order.size() + 1)
                return new PageFlip.Look(null, null, false); // closed from the back: no right half
            if (!order.isEmpty() && idx == order.size() && backOutsideTexture != null) {
                // Back cover lies open here: its INSIDE faces up. Without
                // a back-inside texture the half reads as the plain empty
                // interior (overlay art, no sheet).
                if (backInsideTexture != null)
                    return new PageFlip.Look(interior, backInsideTexture, true);
                return new PageFlip.Look(interior, null, false);
            }
        }
        boolean sheet = !order.isEmpty() && idx < order.size();
        String face = null;
        if (sheet) face = getLeftFaceTexture(order.get(idx));
        return new PageFlip.Look(interior, face, sheet);
    }

    // ── Flipping ─────────────────────────────────────────────────────

    /** True while a flip animation is running. */
    public boolean isFlipping() {
        return PageFlip.isActive();
    }

    /** True while the player is actively dragging a page. */
    public boolean isGrabbing() {
        return grabbing;
    }

    /**
     * Flip forward (next page) or backward (previous page).
     * Does nothing at the book's ends or while another flip runs.
     */
    public void flip(boolean forward) {
        if (PageFlip.isActive() || order.isEmpty()) return;
        flipFromIdx = current;
        if (forward) {
            if (current >= closedBackIdx()) return; // already fully closed from the back
            current += 1;
        } else {
            if (openable && current == 0) {
                current = -1; // close the front cover (v2.10.18)
            } else if (current - 1 < 0) {
                return; // already at the first page
            } else {
                current -= 1;
            }
        }
        flipToIdx = current;
        PageFlip.start(forward, flipDurationMs);
    }

    /**
     * Jump to a page without an animation (clamped; size = end cover).
     * Openable books also accept -1 = closed front.
     */
    public void setCurrentPage(int index) {
        if (!order.isEmpty()) current = clamp(index, openable ? -1 : 0, closedBackIdx());
    }

    /** Index of the current sheet (0-based). Equals getPageCount() at
     *  the end cover state (last sheet flipped over to the left). */
    public int getCurrentPage() {
        return current;
    }

    /** Id of the current page, "" if the book has no pages, sits at the
     *  end cover state (no sheet on the right) or is closed (openable). */
    public String getCurrentPageId() {
        if (order.isEmpty() || current < 0 || current >= order.size()) return "";
        return order.get(current).getId();
    }

    /**
     * True unless the book sits in one of its CLOSED states (openable
     * books only): closed front (-1, cover down on the right half) or
     * closed back (== page count, everything flipped to the left).
     * Always true for non-openable books.
     */
    public boolean isOpen() {
        if (!openable) return true;
        if (current == -1) return false;
        return order.isEmpty() || current != closedBackIdx();
    }

    /** Number of pages. */
    public int getPageCount() {
        return order.size();
    }

    /** All page ids in order (for the built-in "for each item in list"). */
    public List<String> getPageIds() {
        List<String> ids = new ArrayList<>(order.size());
        for (BookPage p : order) ids.add(p.getId());
        return ids;
    }

    /** True if the page with this id is the current page. */
    public boolean isCurrentPageId(String pageId) {
        BookPage p = pagesById.get(pageId);
        return p != null && order.indexOf(p) == current;
    }

    // ── Grabbable pages ──────────────────────────────────────────────

    /**
     * Enable the built-in mouse-grab interaction. The runtime handles
     * everything: pressing a page inside the grab zone grabs it, moving
     * the mouse drives the flip 1:1, releasing tweens the page home
     * (past halfway = completes the turn, before halfway = snaps back,
     * page index reverted automatically).
     *
     * @param inset  how far from the OUTER edge a grab is accepted, as
     *               a fraction of the page width: 0.1 = outer 10% only
     *               (edge/corner feel), 0.5 = outer half, 1.0 = whole
     *               page. Clamped to 0.02..1.
     * @param checks check ids (registered with the Check block) that
     *               must all pass for a grab to start; empty = always
     */
    public void setGrabbable(float inset, String[] checks) {
        float nextInset = clamp(inset, 0.02f, 1.0f);
        String[] nextChecks = checks == null ? new String[0] : checks.clone();
        boolean changed = !this.grabbable
                || this.grabInset != nextInset
                || !java.util.Arrays.equals(this.grabChecks, nextChecks);
        this.grabbable = true;
        this.grabInset = nextInset;
        this.grabChecks = nextChecks;
        if (changed) {
            debug("setGrabbable(inset={}, checks={}) — book is now grabbable", this.grabInset,
                    java.util.Arrays.toString(this.grabChecks));
        }
    }

    /** Disable the built-in mouse grab (e.g. during a cutscene). */
    public void setGrabbable(boolean enabled) {
        this.grabbable = enabled;
        if (!enabled) {
            pendingGrab = false;
            pendingCover = false;
            grabbing = false;
        }
        debug("setGrabbable(enabled={})", enabled);
    }

    public boolean isGrabbable() {
        return grabbable;
    }

    /**
     * Suppresses the normal left-click world action (attack/mine/use)
     * for this tick — the same effect as the "Cancel input" / "Cancel
     * action" blocks, called automatically whenever a grab consumes the
     * click so the player doesn't also swing/mine behind the book.
     * Both calls are defensive (either may already no-op depending on
     * how input reached this tick); failures are logged once, not every
     * frame, and never interrupt the grab itself.
     */
    private void suppressWorldClick() {
        try {
            InputManager.cancelCalledInput();
        } catch (Throwable t) {
            debug("suppressWorldClick: InputManager.cancelCalledInput() failed: {}",
                    t.toString());
        }
        try {
            VirtualGui.cancelAction();
        } catch (Throwable t) {
            debug("suppressWorldClick: VirtualGui.cancelAction() failed: {}", t.toString());
        }
    }

    private void handleMouseGrab(float slide) {
        boolean down = PageFlip.isLeftMouseDown();
        boolean pressed = down && !wasMouseDown;
        wasMouseDown = down;

        // 1) A press was detected last frame: the page index already
        //    advanced, this frame's render shows the new page -> safe to
        //    start the actual PageFlip grab (it snapshots the new state).
        if (pendingGrab) {
            pendingGrab = false;
            boolean wasCoverGrab = pendingCover;
            pendingCover = false;
            if ((grabbable || wasCoverGrab) && down) {
                grabbing = true;
                grabForward = pendingForward;
                grabStartX = PageFlip.getMouseX();
                debug("GRAB STARTED: forward={}, grabStartX={} (mouse=({},{})",
                        grabForward, grabStartX, (int) PageFlip.getMouseX(),
                        (int) PageFlip.getMouseY());
                PageFlip.grab(grabForward);
                suppressWorldClick();
                return;
            }
            // Released within a frame -> plain click, undo the index hop.
            current = pendingRevert;
            debug("press released within a frame — plain click, page index reverted to {}",
                    current);
        }

        // 2) Drive / release an active grab.
        if (grabbing) {
            if (!PageFlip.isActive()) { // flip was stopped underneath us
                grabbing = false;
                debug("grab aborted — PageFlip was stopped underneath us");
                return;
            }
            if (!down) {
                boolean willComplete = PageFlip.getAmount() >= 0.5f;
                debug("mouse released — releasing, state={}, amount={}, will {}",
                        PageFlip.state(), PageFlip.getAmount(),
                        willComplete ? "COMPLETE" : "SNAP BACK");
                PageFlip.release("AUTO");
                if (!willComplete) {
                    current = pendingRevert; // snapped back -> old page live again
                    debug("snapped back — page index reverted to {}", current);
                }
                grabbing = false;
                return;
            }
            float dx = grabForward
                    ? (grabStartX - PageFlip.getMouseX())
                    : (PageFlip.getMouseX() - grabStartX);
            debug("driving grab: mouse={}, dx={}, amount={}",
                    (int) PageFlip.getMouseX(), (int) dx, PageFlip.getAmount());
            PageFlip.setAmount(dx / (2f * pageWidth));
            suppressWorldClick();
            return;
        }

        // 3) Idle: detect a new press inside the grab zones.
        //    Every press is logged WITH THE REASON it was rejected, so a
        //    silent no-grab can always be diagnosed from the log.
        if (pressed) {
            float mx = PageFlip.getMouseX();
            float my = PageFlip.getMouseY();
            boolean inRight = insideGrabZone(mx, my, true, slide);
            boolean inLeft = insideGrabZone(mx, my, false, slide);
            // COVER grabs (openable books): opening the closed cover and
            // closing the open cover work WITHOUT "Set book grabbable" —
            // the cover is its own interaction, inset taken from the
            // grabbable settings (default 0.25).
            boolean coverOpenPress = openable && current == -1 && inRight;
            boolean coverClosePress = openable && current == 0 && inLeft && !order.isEmpty();
            // Back cover (v2.10.19): with a back_book_outside texture the
            // book stays open at the end state (back cover inside showing);
            // grabbing the right half's edge closes the back cover.
            boolean backCoverClosePress = openable && hasBackCover()
                    && current == order.size() && inRight;
            if (!grabbable && !coverOpenPress && !coverClosePress) {
                debug("PRESS REJECTED: mouse=({},{}) — book is NOT grabbable "
                        + "(Set book grabbable block missing or id mismatch?)",
                        (int) PageFlip.getMouseX(), (int) PageFlip.getMouseY());
            } else if (order.isEmpty()) {
                debug("PRESS REJECTED: mouse=({},{}) — book has no Page blocks",
                        (int) PageFlip.getMouseX(), (int) PageFlip.getMouseY());
            } else if (pageWidth <= 0 || height <= 0) {
                debug("PRESS REJECTED: mouse=({},{}) — book geometry invalid "
                        + "(pageWidth={}, height={})", (int) PageFlip.getMouseX(),
                        (int) PageFlip.getMouseY(), pageWidth, height);
            } else if (PageFlip.isActive()) {
                debug("PRESS REJECTED: mouse=({},{}) — a flip is already running "
                        + "(timed={}, grabbed={}, releasing={})",
                        (int) PageFlip.getMouseX(), (int) PageFlip.getMouseY(),
                        net.minecraft.Util.getMillis() - 0L, PageFlip.isActive(), false);
            } else if (!passesGrabChecks()) {
                debug("PRESS REJECTED: mouse=({},{}) — grab checks ({}) did not pass",
                        (int) PageFlip.getMouseX(), (int) PageFlip.getMouseY(),
                        java.util.Arrays.toString(grabChecks));
            } else if (coverOpenPress) {
                debug("PRESS ACCEPTED on the CLOSED COVER: mouse=({},{}) — cover opens, "
                        + "grab starts next frame", (int) mx, (int) my);
                pendingGrab = true;
                pendingCover = true;
                pendingForward = true;
                pendingRevert = current;
                flipFromIdx = current;
                current += 1; // advance FIRST — the open spread must render before the grab starts
                flipToIdx = current;
                suppressWorldClick();
            } else if (backCoverClosePress) {
                    debug("PRESS ACCEPTED on the OPEN BACK COVER: mouse=({},{}) — back cover "
                            + "closes, grab starts next frame", (int) mx, (int) my);
                    pendingGrab = true;
                    pendingCover = true;
                    pendingForward = true;
                    pendingRevert = current;
                    flipFromIdx = current;
                    current += 1; // -> size+1: the closed-back state renders before the grab starts
                    flipToIdx = current;
                    suppressWorldClick();
            } else if (inRight && current < order.size()) {
                    debug("PRESS ACCEPTED in RIGHT zone: mouse=({},{}) — page {} -> {}, "
                            + "grab starts next frame",
                            (int) mx, (int) my, current, current + 1);
                    pendingGrab = true;
                    pendingForward = true;
                    pendingRevert = current;
                    flipFromIdx = current;
                    current += 1; // advance FIRST — the new page must render before the grab starts
                    flipToIdx = current;
                    suppressWorldClick();
                } else if (coverClosePress) {
                    debug("PRESS ACCEPTED on the OPEN COVER: mouse=({},{}) — cover closes, "
                            + "grab starts next frame", (int) mx, (int) my);
                    pendingGrab = true;
                    pendingCover = true;
                    pendingForward = false;
                    pendingRevert = current;
                    flipFromIdx = current;
                    current -= 1; // -> -1: the closed state renders before the grab starts
                    flipToIdx = current;
                    suppressWorldClick();
                } else if (inLeft && current - 1 >= 0) {
                    debug("PRESS ACCEPTED in LEFT zone: mouse=({},{}) — page {} -> {}, "
                            + "grab starts next frame",
                            (int) mx, (int) my, current, current - 1);
                    pendingGrab = true;
                    pendingForward = false;
                    pendingRevert = current;
                    flipFromIdx = current;
                    current -= 1;
                    flipToIdx = current;
                    suppressWorldClick();
                } else {
                    // Press was on the book but not usable: explain exactly why.
                    String why;
                    if (inRight) {
                        why = "pressed in RIGHT grab zone but current >= page count "
                                + "(current=" + current + ", pages=" + order.size()
                                + " — already at the END cover, flip backward instead)";
                    } else if (inLeft) {
                        why = "pressed in LEFT grab zone but current-1 < 0 "
                                + "(current=" + current + " — already at the FIRST page)";
                    } else {
                        why = String.format(
                                "mouse outside grab zones: mouse=(%d,%d), right zone=[%d..%d], "
                                + "left zone=[%d..%d], book y range=[%d..%d] (inset=%.2f of pageWidth=%d, slide=%d)",
                                (int) mx, (int) my,
                                (int) (x + slide + pageWidth * (2f - grabInset)), (int) (x + slide + pageWidth * 2f),
                                (int) (x + slide), (int) (x + slide + pageWidth * grabInset),
                                (int) y, (int) (y + height), grabInset, pageWidth, (int) slide);
                    }
                    debug("PRESS REJECTED: {}", why);
                }
        }
    }

    /**
     * True if (mx, my) lies inside the grab zone of the right (true) or
     * left (false) page. The zone is grabInset of the page width,
     * measured inwards from the OUTER edge.
     */
    private boolean insideGrabZone(float mx, float my, boolean right, float slide) {
        float zx0 = right ? x + slide + pageWidth * (2f - grabInset) : x + slide;
        float zx1 = right ? x + slide + pageWidth * 2f : x + slide + pageWidth * grabInset;
        return mx >= zx0 && mx < zx1 && my >= y && my < y + height;
    }

    /** All check ids pass (a broken check id counts as passing). */
    private boolean passesGrabChecks() {
        for (String checkId : grabChecks) {
            try {
                Object result = VirtualGui.getCheckResult(checkId);
                if (result instanceof Boolean) {
                    if (!(Boolean) result) return false;
                } else if (result instanceof Number) {
                    if (((Number) result).intValue() == 0) return false;
                }
            } catch (Throwable ignored) {
                // Unknown check state -> don't block the grab.
            }
        }
        return true;
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}
