package com.likeazusa2.dgmodules.client;

/**
 * Per-client laser activity mirror, driven by server-authoritative
 * S2CLaserState via {@link ClientTickHandler#applyServerState}.
 * Each client has exactly one player, so a simple static boolean is correct.
 */
public class ClientLaserState {
    private static boolean active = false;

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean v) {
        active = v;
    }
}
