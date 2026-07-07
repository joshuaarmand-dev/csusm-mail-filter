package edu.csusm.mailfilter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailUtilsTest {

    @Test
    void extractEmailReturnsEmailInsideAngleBrackets() {
        String result = EmailUtils.extractEmail("John Doe <john@csusm.edu>");
        assertEquals("john@csusm.edu", result);
    }

    @Test
    void extractEmailReturnsTrimmedHeaderWhenNoAngleBrackets() {
        String result = EmailUtils.extractEmail(" student@csusm.edu ");
        assertEquals("student@csusm.edu", result);
    }

    @Test
    void extractEmailReturnsEmptyStringForNullInput() {
        String result = EmailUtils.extractEmail(null);
        assertEquals("", result);
    }

    @Test
    void senderDomainReturnsDomainAfterAtSymbol() {
        String result = EmailUtils.senderDomain("student@csusm.edu");
        assertEquals("csusm.edu", result);
    }

    @Test
    void senderDomainReturnsLowercaseDomain() {
        String result = EmailUtils.senderDomain("student@CSUSM.EDU");
        assertEquals("csusm.edu", result);
    }

    @Test
    void senderDomainReturnsEmptyStringWhenEmailHasNoAtSymbol() {
        String result = EmailUtils.senderDomain("not-an-email");
        assertEquals("", result);
    }

    @Test
    void isCsusmReturnsTrueForCsusmEmail() {
        assertTrue(EmailUtils.isCsusm("student@csusm.edu"));
    }

    @Test
    void isCsusmReturnsTrueForSubdomainCsusmEmail() {
        assertTrue(EmailUtils.isCsusm("person@mail.csusm.edu"));
    }

    @Test
    void isCsusmReturnsFalseForExternalEmail() {
        assertFalse(EmailUtils.isCsusm("person@gmail.com"));
    }

    @Test
    void isCsusmReturnsFalseForFakeCsusmLookalikeDomain() {
        assertFalse(EmailUtils.isCsusm("person@csusm.edu.fake.com"));
    }
}