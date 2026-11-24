package jx;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Observable Children contracts, including identity metadata consumed by the React renderer. */
class ChildrenContractTest {
    @Test void writtenFragmentsStayOpaqueEvenWhenTheyContainSeveralChildren() {
        View fragment = Views.fragment(element("a"), element("b"));
        var visited = new ArrayList<View>();
        Children.forEach(fragment, visited::add);
        assertEquals(1, Children.count(fragment));
        assertEquals(List.of(fragment), visited);
        assertSame(fragment, Children.only(fragment));
        List<View> mapped = mapped(Children.map(fragment, child -> child));
        assertEquals(1, mapped.size());
        assertKeyed(mapped.getFirst(), fragment, ".0");
    }

    @Test void onlyRequiresAnElementRatherThanOneFlattenedNonemptyPosition() {
        View element = element("one");
        assertSame(element, Children.only(element));
        assertThrows(IllegalArgumentException.class, () -> Children.only(new View.Items(List.of(element))));
        assertThrows(IllegalArgumentException.class, () -> Children.only(new View.Text("one")));
        assertThrows(IllegalArgumentException.class, () -> Children.only(View.Empty.INSTANCE));
        assertThrows(IllegalArgumentException.class, () -> Children.only(null));
        assertThrows(IllegalArgumentException.class, () -> Children.only(new OpaqueNode("portal")));
    }

    @Test void emptySlotsAreVisitedAndCountedButNullMappingResultsAreOmitted() {
        View a = element("a");
        View text = new View.Text("text");
        View input = new View.Items(List.of(View.Empty.INSTANCE,
                new View.Items(List.of(a, View.Empty.INSTANCE)), text));
        var visited = new ArrayList<View>();
        View result = Children.map(input, child -> {
            visited.add(child);
            return child instanceof View.Empty ? null : child;
        });
        assertEquals(4, Children.count(input));
        assertEquals(List.of(View.Empty.INSTANCE, a, View.Empty.INSTANCE, text), visited);
        List<View> output = mapped(result);
        assertEquals(2, output.size());
        assertKeyed(output.getFirst(), a, ".1:$a");
        assertSame(text, output.getLast());
        assertEquals(0, Children.count(null));
        assertEquals(1, Children.count(View.Empty.INSTANCE));
        assertNull(Children.map(null, child -> fail("Absent children must not invoke the mapper")));
    }

    @Test void anUnkeyedWrapperInheritsItsSourceKeyWithoutMutatingEitherElement() {
        View a = element("a");
        View wrapper = Views.element("section", Attributes.create(), a);
        View mapped = mapped(Children.map(a, ignored -> wrapper)).getFirst();
        assertKeyed(mapped, wrapper, ".$a");
        assertEquals("a", ((View.Element) a).attributes().entries().getFirst().value());
        assertEquals(List.of(), ((View.Element) wrapper).attributes().entries());
    }

    @Test void differentMappedKeysAreComposedAndEqualMappedKeysAreNotRepeated() {
        View original = element("a:b=c");
        View different = element("shell/x");
        assertKeyed(mapped(Children.map(original, ignored -> different)).getFirst(),
                different, "shell//x/.$a=2b=0c");
        View same = element("a:b=c");
        assertKeyed(mapped(Children.map(original, ignored -> same)).getFirst(), same, ".$a=2b=0c");
    }

    @Test void returnedArraysFlattenAndKeepTheirKeysLocalToTheSourceChild() {
        View left = element("left");
        View right = element("right");
        View result = Children.map(new View.Items(List.of(element("a"), element("b"))), child ->
                new View.Items(List.of(left, new View.Items(List.of(View.Empty.INSTANCE, right)))));
        List<View> output = mapped(result);
        assertEquals(4, output.size());
        assertKeyed(output.get(0), left, ".$a/.$left");
        assertKeyed(output.get(1), right, ".$a/.1:$right");
        assertKeyed(output.get(2), left, ".$b/.$left");
        assertKeyed(output.get(3), right, ".$b/.1:$right");
    }

    @Test void returnedFragmentsRemainElementsRatherThanArraysToFlatten() {
        View fragment = Views.fragment(element("left"), element("right"));
        List<View> output = mapped(Children.map(element("a"), child -> fragment));
        assertEquals(1, output.size());
        assertKeyed(output.getFirst(), fragment, ".$a");
    }

    @Test void arrayPrefixesEscapeSlashRunsWithoutChangingLocalKeySegments() {
        View local = element("local/x");
        List<View> output = mapped(Children.map(element("a//b"), child -> new View.Items(List.of(local))));
        assertKeyed(output.getFirst(), local, ".$a///b/.$local/x");
    }

    @Test void toListQualifiesRepeatedKeysByNestingAndRetainsEmptyPositionOffsets() {
        View a = element("same");
        View b = element("same");
        View unkeyed = Views.element("span", Attributes.create());
        View children = new View.Items(List.of(
                new View.Items(List.of(a)), new View.Items(List.of(b)), View.Empty.INSTANCE, unkeyed));
        List<View> output = Children.toList(children);
        assertEquals(3, output.size());
        assertKeyed(output.get(0), a, ".0:$same");
        assertKeyed(output.get(1), b, ".1:$same");
        assertKeyed(output.get(2), unkeyed, ".3");
        assertThrows(UnsupportedOperationException.class, () -> output.add(a));
    }

    @Test void aScalarAndOneItemArrayHaveTheSameTraversalKey() {
        View child = element("a");
        assertKeyed(Children.toList(child).getFirst(), child, ".$a");
        assertKeyed(Children.toList(new View.Items(List.of(child))).getFirst(), child, ".$a");
    }

    @Test void unkeyedPositionsUseBase36AndRekeyingDoesNotAddAnElementBoundary() {
        var children = new ArrayList<View>();
        for (int index = 0; index < 36; index++) children.add(View.Empty.INSTANCE);
        View child = Views.element("span", Attributes.create());
        children.add(child);
        assertKeyed(Children.toList(new View.Items(children)).getFirst(), child, ".10");
        assertKeyed(new View.Keyed(new View.Keyed(child, "first"), "second"), child, "second");
    }

    @Test void childrenNormalizationPreservesTheDistinctionBetweenScalarArrayAndFragment() {
        View child = element("a");
        View fragment = Views.fragment(child);
        assertNull(Views.childrenValue());
        assertNull(Views.childrenValue((Object) null));
        assertSame(child, Views.childrenValue(child));
        assertSame(fragment, Views.childrenValue(fragment));
        assertSame(View.Empty.INSTANCE, Views.childrenValue(false));
        assertEquals(new View.Items(List.of(child)), Views.childrenValue((Object) new View[]{child}));
        assertEquals(new View.Items(List.of(child)), Views.childrenValue(List.of(child)));
        assertEquals(new View.Items(List.of(child, View.Empty.INSTANCE), true), Views.childrenValue(child, null));
        View.Items fixed = assertInstanceOf(View.Items.class, Views.childrenValue(child, fragment));
        assertTrue(fixed.staticChildren(), "Individually authored siblings have fixed positions");
        assertFalse(assertInstanceOf(View.Items.class, Views.childrenValue(List.of(child))).staticChildren(),
                "A collection remains dynamic even when it currently contains one child");
        assertFalse(assertInstanceOf(View.Items.class, Children.map(fixed, value -> value)).staticChildren(),
                "Mapping produces a dynamic collection with composed keys");
    }

    @Test void rekeyingPreservesElementShapeAndRejectsValuesThatAreNotElements() {
        var descriptor = new ComponentType<String>("NeverInvoke", props -> fail("Children must not render"));
        View component = Views.component(descriptor, "props", "component");
        View provider = new Context<>("initial").provide("next", component);
        View nativeElement = new NativeElement("native");
        View opaque = new OpaqueNode("portal");
        assertKeyed(Children.toList(component).getFirst(), component, ".$component");
        assertKeyed(Children.toList(provider).getFirst(), provider, ".0");
        assertKeyed(Children.toList(nativeElement).getFirst(), nativeElement, ".$native");
        assertSame(opaque, Children.toList(opaque).getFirst());
        assertFalse(Views.isValidElement(opaque));
        assertTrue(Views.isValidElement(nativeElement));
        assertThrows(IllegalArgumentException.class, () -> new View.Keyed(new View.Text("text"), "key"));
        assertThrows(IllegalArgumentException.class, () -> new View.Keyed(new View.Items(List.of()), "key"));
        assertThrows(IllegalArgumentException.class, () -> new View.Keyed(View.Empty.INSTANCE, "key"));
        assertThrows(IllegalArgumentException.class, () -> new View.Keyed(opaque, "key"));
        assertThrows(NullPointerException.class, () -> new View.Keyed(component, null));
    }

    @Test void keyedDomElementsCanStillBeClonedAndAnExplicitKeyWins() {
        View child = Views.element("input", Attributes.create().attr("key", "a").attr("value", "draft"));
        View keyed = Children.toList(child).getFirst();
        View retained = Views.cloneElement(keyed, Attributes.create().attr("title", "cloned"));
        assertEquals(".$a", key(retained));
        assertEquals("draft", attribute(unwrap(retained), "value"));
        assertEquals("cloned", attribute(unwrap(retained), "title"));
        View replaced = Views.cloneElement(keyed, Attributes.create().attr("key", "replacement"));
        assertEquals("replacement", key(replaced));
        assertEquals("a", attribute((View.Element) child, "key"));
    }

    @Test void scalarKeysDistinguishAbsentAndExplicitNullAndMatchBrowserNumberSpelling() {
        assertNull(Views.key(Views.element("span", Attributes.create())));
        assertEquals("null", domKey(null));
        assertEquals("true", domKey(true));
        record NumericKey(Object value, String expected) {}
        for (NumericKey sample : List.of(
                new NumericKey(1, "1"), new NumericKey(1.0, "1"), new NumericKey(-0.0, "0"),
                new NumericKey(0.1f, "0.10000000149011612"), new NumericKey(1e-6, "0.000001"),
                new NumericKey(1e-7, "1e-7"), new NumericKey(1e20, "100000000000000000000"),
                new NumericKey(1e21, "1e+21"), new NumericKey(1e23, "1e+23"),
                new NumericKey(1.0000000000000001e18, "1000000000000000100"),
                new NumericKey(Double.MIN_VALUE, "5e-324"),
                new NumericKey(Double.MIN_NORMAL, "2.2250738585072014e-308"),
                new NumericKey(Double.MAX_VALUE, "1.7976931348623157e+308"),
                new NumericKey(Double.NaN, "NaN"), new NumericKey(Double.POSITIVE_INFINITY, "Infinity"),
                new NumericKey(Double.NEGATIVE_INFINITY, "-Infinity"),
                new NumericKey(9_007_199_254_740_991L, "9007199254740991"))) {
            assertEquals(sample.expected(), domKey(sample.value()), "Scalar " + sample.value());
        }
        assertThrows(IllegalArgumentException.class, () -> domKey(9_007_199_254_740_992L));
        assertThrows(IllegalArgumentException.class, () -> domKey(-9_007_199_254_740_992L));
    }

    private static String domKey(Object value) {
        return Views.key(Views.element("span", Attributes.create().attr("key", value)));
    }

    private static View element(String key) {
        return Views.element("span", Attributes.create().attr("key", key));
    }

    private static List<View> mapped(View result) {
        return assertInstanceOf(View.Items.class, result, "Children.map returns an array, not an element").children();
    }

    private static void assertKeyed(View actual, View element, String key) {
        View.Keyed keyed = assertInstanceOf(View.Keyed.class, actual);
        assertSame(element, keyed.element(), "Rekeying preserves the original element description");
        assertEquals(key, keyed.key());
    }

    private static View.Element unwrap(View view) {
        return assertInstanceOf(View.Element.class, view instanceof View.Keyed keyed ? keyed.element() : view);
    }

    private static String key(View view) {
        return view instanceof View.Keyed keyed ? keyed.key() : (String) attribute(unwrap(view), "key");
    }

    private static Object attribute(View.Element element, String name) {
        return element.attributes().entries().stream().filter(entry -> entry.name().equals(name))
                .map(Attributes.Entry::value).findFirst().orElse(null);
    }

    private record NativeElement(String key) implements View.ElementExtension {}
    private record OpaqueNode(String key) implements View.Extension {}
}
