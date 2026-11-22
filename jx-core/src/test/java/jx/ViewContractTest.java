package jx;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewContractTest {
    @Test void rejectsExecutableAttributesAndUnknownScalarRepresentations() {
        Attributes attributes = Attributes.create();
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("onClick", "alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("dangerouslySetInnerHTML", "<b>raw</b>"));
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("dangerouslySetInnerHTML", RawHtml.unsanitized("<b>raw</b>")),
                "raw HTML goes through its own method only");
        assertEquals("<b>raw</b>", ((RawHtml) attributes.dangerouslySetInnerHTML(RawHtml.unsanitized("<b>raw</b>")).entries().get(0).value()).html());
        assertThrows(NullPointerException.class, () -> RawHtml.unsanitized(null));
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("href", " jAvA\nScRiPt:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("__proto__", "value"));
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("count", new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("title", Style.create()));
        assertThrows(IllegalArgumentException.class, () -> attributes.attr("ref", "element"));
        assertEquals(1, attributes.ref((jx.RefCallback) element -> null).entries().size());
        assertSame(attributes, attributes.ref((DomRef) null));
        assertThrows(IllegalArgumentException.class, () -> Style.create().property("width", Double.NaN));
    }
    @Test void immutableDescriptionsKeepOwnershipAndNeverExecuteChildren() {
        Attributes initial = Attributes.create().attr("id", "original");
        Attributes changed = initial.attr("id", "changed");
        assertEquals("original", initial.entries().getFirst().value());
        assertEquals("changed", changed.entries().getFirst().value());
        var children = new ArrayList<View>(); children.add(new View.Text("one"));
        View.Fragment fragment = new View.Fragment(children); children.clear();
        assertEquals(1, fragment.children().size());
        ComponentType<String> component = new ComponentType<>("Deferred", props -> { fail("Composition must not invoke render"); return Views.fragment(); });
        View composed = Views.component(component, "props", "stable");
        assertInstanceOf(View.ComponentView.class, composed);
        assertThrows(IllegalArgumentException.class, () -> new View.Element("bad<tag", initial, List.of()));
    }
    @Test void childSchemaPreservesTextAndRejectsArbitraryObjects() {
        View.Fragment view = (View.Fragment) Views.fragment(null, false, "<script>", List.of(1, 2));
        // Null and boolean children keep their position; a collection is one position.
        assertEquals(List.of(View.Empty.INSTANCE, View.Empty.INSTANCE, new View.Text("<script>"),
                new View.Items(List.of(new View.Text("1"), new View.Text("2")))), view.children());
        assertEquals(5, Children.count(view));
        assertEquals(List.of(new View.Text("<script>"), new View.Text("1"), new View.Text("2")), Children.toList(view));
        assertFalse(Views.isValidElement(View.Empty.INSTANCE));
        assertThrows(IllegalArgumentException.class, () -> Views.fragment(new Object()));
    }
}
