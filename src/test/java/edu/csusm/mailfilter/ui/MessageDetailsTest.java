package edu.csusm.mailfilter.ui;

import edu.csusm.mailfilter.model.Category;
import edu.csusm.mailfilter.model.Msg;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDetailsTest {

    @Test
    void noSelectionShowsHint() {
        assertTrue(MessageDetails.describe(null).startsWith("Select a message"));
    }

    @Test
    void unsafeMessageListsReasonsAuthAndLinks() {
        Msg msg = new Msg("1", "Reset your password", "it@csusm.edu", "2026-10-13 15:00", "https://x");
        msg.category = Category.UNSAFE;
        msg.auth = "DMARC fail";
        msg.reasons = List.of("Claims to be from csusm.edu but failed authentication (DMARC fail)",
                "Redirector: bit.ly");
        msg.links = List.of("https://bit.ly/reset");

        String text = MessageDetails.describe(msg);

        assertTrue(text.contains("Tab:       Unsafe"));
        assertTrue(text.contains("Sender:    DMARC fail"));
        assertTrue(text.contains("\u2022 Redirector: bit.ly"));
        assertTrue(text.contains("Links checked (1):"));
        assertTrue(text.contains("https://bit.ly/reset"));
    }

    @Test
    void cleanMessageSaysSo() {
        Msg msg = new Msg("1", "Hi", "a@example.com", "", "https://x");

        String text = MessageDetails.describe(msg);

        assertTrue(text.contains("No risk indicators found."));
        assertTrue(text.contains("No web links found."));
        assertTrue(text.contains("Sender:    Not checked"));
    }

    @Test
    void longLinkListsAreTruncated() {
        List<String> links = new ArrayList<>();

        for (int i = 0; i < MessageDetails.MAX_LINKS_SHOWN + 5; i++) {
            links.add("https://example.com/" + i);
        }

        Msg msg = new Msg("1", "News", "a@example.com", "", "https://x");
        msg.links = links;

        String text = MessageDetails.describe(msg);

        assertTrue(text.contains("and 5 more"));
        assertFalse(text.contains("https://example.com/" + (MessageDetails.MAX_LINKS_SHOWN + 1)));
    }
}