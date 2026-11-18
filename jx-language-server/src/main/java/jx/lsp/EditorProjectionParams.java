package jx.lsp;

/** Full-document synchronization must precede this request. Version pins an open authored snapshot. */
public record EditorProjectionParams(String uri, Integer version) {}
