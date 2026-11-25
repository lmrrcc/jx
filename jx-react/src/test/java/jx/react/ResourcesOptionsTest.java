package jx.react;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourcesOptionsTest {
    @Test void theExistingConstructorAndImmutableCopiesKeepEveryOption() {
        var existing = new Resources.Options("image", "anonymous", "sha256-proof", "nonce", "high", "image/svg+xml", null);
        var responsive = existing.withReferrerPolicy("no-referrer")
                .withImageSources("small.svg 40w, large.svg 80w", "30px");
        assertEquals(new Resources.Options("image", "anonymous", "sha256-proof", "nonce", "high", "image/svg+xml", null,
                "small.svg 40w, large.svg 80w", "30px", "no-referrer"), responsive);
        assertNull(existing.imageSrcSet());
        assertNull(existing.imageSizes());
        assertNull(existing.referrerPolicy());
        assertEquals(existing, responsive.withImageSources(null, null).withReferrerPolicy(null));
    }

    @Test void aWrongResourceKindCannotReachReactWithImageSettings() {
        assertThrows(IllegalArgumentException.class, () -> Resources.Options.as("style").withImageSources("one.png 1x", null));
        assertThrows(IllegalArgumentException.class, () -> Resources.Options.none().withImageSources(null, "100vw"));
        assertThrows(IllegalArgumentException.class, () -> new Resources.Options("script", null, null, null, null,
                null, null, "one.png 1x", null, null));
        assertEquals(Resources.Options.as("style"), Resources.Options.as("style").withImageSources(null, null));
    }
}
