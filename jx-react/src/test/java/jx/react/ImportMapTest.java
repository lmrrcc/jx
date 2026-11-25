package jx.react;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportMapTest {
    @Test void nestedInputsAreCopiedAndBlockedMappingsRemainPresent() {
        var imports = new LinkedHashMap<String, String>();
        imports.put("module", "/first.mjs");
        imports.put("blocked", null);
        var scoped = new LinkedHashMap<>(Map.of("module", "/scoped.mjs"));
        var scopes = new LinkedHashMap<String, Map<String, String>>();
        scopes.put("/scope/", scoped);
        var integrity = new LinkedHashMap<>(Map.of("/first.mjs", "sha256-example"));
        var map = new ImportMap(imports, scopes, integrity);
        imports.put("module", "/changed.mjs");
        scoped.clear(); scopes.clear(); integrity.clear();
        assertEquals("/first.mjs", map.imports().get("module"));
        assertTrue(map.imports().containsKey("blocked"));
        assertNull(map.imports().get("blocked"));
        assertEquals("/scoped.mjs", map.scopes().get("/scope/").get("module"));
        assertEquals("sha256-example", map.integrity().get("/first.mjs"));
        assertThrows(UnsupportedOperationException.class, () -> map.imports().put("x", "/x"));
        assertThrows(UnsupportedOperationException.class, () -> map.scopes().get("/scope/").clear());
        assertThrows(UnsupportedOperationException.class, () -> map.integrity().clear());
        assertNull(new ImportMap(null, null).imports());
        assertEquals(Map.of(), new ImportMap(Map.of(), null).imports());
    }

    @Test void boundsAndSchemaRejectInvalidConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new ImportMap(Map.of("", "/x"), null));
        assertThrows(IllegalArgumentException.class, () -> new ImportMap(null, Map.of("/", Map.of("", "/x"))));
        var baseReferences = new ImportMap(null, Map.of("", Map.of("module", "/x")), null);
        assertTrue(baseReferences.scopes().containsKey(""));
        assertThrows(IllegalArgumentException.class, () -> new ImportMap(null, null, Map.of("", "sha256-example")));
        var nullScope = new LinkedHashMap<String, Map<String, String>>();
        nullScope.put("/", null);
        assertThrows(NullPointerException.class, () -> new ImportMap(null, nullScope));
        var nullIntegrity = new LinkedHashMap<String, String>();
        nullIntegrity.put("/a", null);
        assertThrows(NullPointerException.class, () -> new ImportMap(null, null, nullIntegrity));
        assertThrows(IllegalArgumentException.class, () -> new ImportMap(Map.of("x", "a".repeat(1_048_576)), null));
        var many = new LinkedHashMap<String, String>();
        for (int i = 0; i < 4096; i++) many.put("entry" + i, "/a");
        assertEquals(4096, new ImportMap(many, null).imports().size());
        assertThrows(IllegalArgumentException.class, () -> new ImportMap(null, Map.of("/", many)));
    }

    @Test void copiesRetainImportMapAndResumeRejectsItBeforeNativeExecution() {
        var map = new ImportMap(Map.of("module", "/main.mjs"), null);
        var options = ReactServer.StreamOptions.defaults().withImportMap(map).withNonce("nonce")
                .withIdentifierPrefix("id").withBootstrapScriptContent("").withBootstrapModules(java.util.List.of("/boot.mjs"))
                .withBootstrapScripts(java.util.List.of()).withError(error -> {}).withHeaders(headers -> {})
                .withMaxHeadersLength(100).withNonces(new ReactServer.Nonces("script", "style"));
        assertSame(map, options.importMap());
        assertNull(options.withImportMap(null).importMap());
        var initial = ReactServer.StreamOptions.defaults().withImportMap(map);
        assertThrows(IllegalArgumentException.class, () -> ReactServer.resume(null, null, initial));
        assertThrows(IllegalArgumentException.class, () -> ReactServer.resumeToPipeableStream(null, null, initial));
        assertThrows(IllegalArgumentException.class, () -> ReactStatic.resumeAndPrerender(null, null, initial));
        assertThrows(IllegalArgumentException.class, () -> ReactStatic.resumeAndPrerenderToNodeStream(null, null, initial));
    }
}
