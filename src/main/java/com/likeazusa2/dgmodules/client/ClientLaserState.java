package com.likeazusa2.dgmodules.client;

public class ClientLaserState {
    private static boolean active = false;

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean v) {
        active = v;
    }
}
