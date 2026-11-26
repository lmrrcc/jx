package jx.example.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TodoContractTest {
    @Test void identifiersStayOpaqueWhilePathsCannotIntroduceUrlSyntax() {
        assertEquals(new Todo("opaque", "Title", false, 0), new Todo("opaque", "Title", false, 0));
        assertEquals("/api/todos/a-b_2.~", TodoApi.item("a-b_2.~"));
        assertEquals("/api/todos/a?version=2147483647", TodoApi.deletion("a", Integer.MAX_VALUE));
        for (String invalid : new String[] {"", ".", "..", "../other", "a?x=1", "a#b", "%2f", "a b", "a".repeat(129)}) {
            assertThrows(IllegalArgumentException.class, () -> TodoApi.item(invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> TodoApi.deletion("a", -1));
        assertThrows(IllegalArgumentException.class, () -> new Todo("a", "Title", false, -1));
        assertThrows(NullPointerException.class, () -> new Todo("a", null, false, 0));
    }
}
