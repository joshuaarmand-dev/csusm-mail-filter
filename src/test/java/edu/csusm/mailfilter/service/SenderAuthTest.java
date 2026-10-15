package edu.csusm.mailfilter.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenderAuthTest {

    @Test
    void dmarcPassIsPass() {
        SenderAuth auth = evaluate("csusm.edu",
                "mx.google.com; dkim=pass header.i=@csusm.edu header.s=s1 header.b=x; "
                        + "spf=pass smtp.mailfrom=a@csusm.edu; "
                        + "dmarc=pass (p=REJECT sp=REJECT dis=NONE) header.from=csusm.edu");

        assertEquals(SenderAuth.Status.PASS, auth.status);
        assertEquals("DMARC pass (csusm.edu)", auth.summary);
    }

    @Test
    void dmarcFailIsFail() {
        SenderAuth auth = evaluate("csusm.edu",
                "mx.google.com; spf=fail smtp.mailfrom=x@evil.example; "
                        + "dmarc=fail (p=QUARANTINE dis=QUARANTINE) header.from=csusm.edu");

        assertEquals(SenderAuth.Status.FAIL, auth.status);
    }

    @Test
    void alignedDkimPassWithoutDmarcIsPass() {
        // Domains with no DMARC record still get verified through DKIM.
        SenderAuth auth = evaluate("partner.org",
                "mx.google.com; dkim=pass header.i=@mail.partner.org header.s=s1 header.b=x; "
                        + "spf=neutral smtp.mailfrom=bounce@esp.example");

        assertEquals(SenderAuth.Status.PASS, auth.status);
        assertEquals("DKIM pass (mail.partner.org)", auth.summary);
    }

    @Test
    void dkimPassForADifferentDomainDoesNotCount() {
        // Signed by the attacker's own domain, not by csusm.edu.
        SenderAuth auth = evaluate("csusm.edu",
                "mx.google.com; dkim=pass header.i=@evil.example header.s=s1 header.b=x; "
                        + "spf=pass smtp.mailfrom=x@evil.example; dmarc=none header.from=csusm.edu");

        assertEquals(SenderAuth.Status.UNKNOWN, auth.status);
    }

    @Test
    void dmarcPassForADifferentFromDomainDoesNotCount() {
        SenderAuth auth = evaluate("csusm.edu",
                "mx.google.com; dmarc=pass (p=NONE) header.from=evil.example");

        assertEquals(SenderAuth.Status.UNKNOWN, auth.status);
    }

    @Test
    void headersFromOtherServersAreIgnored() {
        SenderAuth auth = evaluate("csusm.edu",
                "attacker.example; dmarc=pass header.from=csusm.edu");

        assertEquals(SenderAuth.Status.UNKNOWN, auth.status);
    }

    @Test
    void topmostGmailHeaderWins() {
        SenderAuth auth = SenderAuth.evaluate(List.of(
                "mx.google.com; dmarc=fail header.from=csusm.edu",
                "mx.google.com; dmarc=pass header.from=csusm.edu"), "csusm.edu");

        assertEquals(SenderAuth.Status.FAIL, auth.status);
    }

    @Test
    void noHeadersIsUnknown() {
        SenderAuth auth = SenderAuth.evaluate(List.of(), "csusm.edu");

        assertEquals(SenderAuth.Status.UNKNOWN, auth.status);
        assertTrue(auth.summary.startsWith("Not verified"));
    }

    @Test
    void alignmentRules() {
        assertTrue(SenderAuth.aligned("csusm.edu", "csusm.edu"));
        assertTrue(SenderAuth.aligned("mail.csusm.edu", "csusm.edu"));
        assertTrue(SenderAuth.aligned("csusm.edu", "mail.csusm.edu"));
        assertFalse(SenderAuth.aligned("evil-csusm.edu", "csusm.edu"));
        assertFalse(SenderAuth.aligned("csusm.edu", ""));
    }

    private static SenderAuth evaluate(String senderDomain, String header) {
        return SenderAuth.evaluate(List.of(header), senderDomain);
    }
}