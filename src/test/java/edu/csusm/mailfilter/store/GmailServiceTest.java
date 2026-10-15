package edu.csusm.mailfilter.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GmailServiceTest {

    @Test
    void followsPageTokensPast500() throws IOException {
        FakeInbox inbox = new FakeInbox(1200);

        List<String> ids = GmailService.collectIds(1000, inbox);

        assertEquals(1000, ids.size());
        assertEquals("m0", ids.get(0));
        assertEquals("m999", ids.get(999));
        assertEquals(List.of(500, 500), inbox.requestedPageSizes);
    }

    @Test
    void lastPageAsksOnlyForWhatIsLeft() throws IOException {
        FakeInbox inbox = new FakeInbox(1200);

        GmailService.collectIds(600, inbox);

        assertEquals(List.of(500, 100), inbox.requestedPageSizes);
    }

    @Test
    void stopsWhenInboxRunsOut() throws IOException {
        FakeInbox inbox = new FakeInbox(30);

        List<String> ids = GmailService.collectIds(100, inbox);

        assertEquals(30, ids.size());
        assertEquals(1, inbox.requestedPageSizes.size());
    }

    @Test
    void emptyInboxGivesNoIds() throws IOException {
        assertEquals(List.of(), GmailService.collectIds(100, new FakeInbox(0)));
    }

    /** Pretends to be messages.list over an inbox of n messages. */
    private static final class FakeInbox implements GmailService.PageFetcher {
        private final int size;
        final List<Integer> requestedPageSizes = new ArrayList<>();

        FakeInbox(int size) {
            this.size = size;
        }

        @Override
        public GmailService.Page fetch(String pageToken, int pageSize) {
            requestedPageSizes.add(pageSize);

            int start = (pageToken == null) ? 0 : Integer.parseInt(pageToken);
            int end = Math.min(size, start + Math.min(pageSize, GmailService.MAX_PAGE_SIZE));

            List<String> ids = new ArrayList<>();

            for (int i = start; i < end; i++) {
                ids.add("m" + i);
            }

            String next = (end < size) ? String.valueOf(end) : null;
            return new GmailService.Page(ids, next);
        }
    }
}