package net.tamashi.fomekcore.api.guisystems;

/**
 * FomekMenus BookPage — one page (spread) of a Book, identified by id.
 *
 * A page has two sides, LEFT and RIGHT, each rendered as its own panel
 * (id: "<book>_<page>_left" / "<book>_<page>_right"). The Page block
 * declares the content of both sides; only the current page's sides
 * actually render each frame.
 *
 * Pages behave like any other menu object: they have ids, keep their
 * declaration order, and can be listed / tested from procedures
 * (Book.getPageIds(), Book.isCurrentPageId(...)).
 */
public class BookPage {

    private final String id;

    /** Frame in which this page was last declared (Book drops stale pages). */
    int seenFrame = -1;

    BookPage(String pageId) {
        this.id = pageId;
    }

    public String getId() {
        return id;
    }
}
