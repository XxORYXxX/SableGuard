package dev.sableguard.tools;

/** Outcome of an admin tool, shown to the command source. */
public record ToolResult(boolean success, String message) {
    public static ToolResult ok(final String message) {
        return new ToolResult(true, message);
    }

    public static ToolResult fail(final String message) {
        return new ToolResult(false, message);
    }
}
