package org.texboobcat.wcpu.region;

// Thrown on a region worker that asked for a chunk which is not loaded yet. Vanilla callers expect a
// chunk, so something has to abort the call; this is not a safety violation. Parallel ticking has
// already been paused briefly by the time it is thrown, so the failure handlers ignore it.
public final class ChunkUnavailableException extends IllegalStateException {
    public ChunkUnavailableException() {
        super("Should always be able to create a chunk!", null);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    public static boolean causedBy(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ChunkUnavailableException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
