package com.likeazusa2.dgmodules.util;

/**
 * Accessor for per-player Phase Shield state.
 * Implemented by MixinPhaseShieldPickable on Player.
 */
public interface DGPhaseShieldDataAccess {
    boolean dgmodules$isPhaseActive();
    int dgmodules$getPhaseSeconds();
}
