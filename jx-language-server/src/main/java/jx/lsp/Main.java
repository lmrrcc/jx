package jx.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;

/** Standard input/output JSON-RPC entry point. All human-readable logging goes to stderr. */
public final class Main {
    private Main() {}
    public static void main(String[] args) throws Exception {
        try (var server = new JxLanguageServer(() -> System.exit(0))) {
            var launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
            server.connect(launcher.getRemoteProxy());
            launcher.startListening().get();
        }
    }
}
