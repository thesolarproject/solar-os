package com.solar.launcher;

interface ISolarOverlayState {
    boolean isSolarAlive();
    int policyRevision();
    android.os.Bundle getPowerMenuSnapshot();
    android.os.Bundle getContextMenuSnapshot(String sessionId);
    boolean dispatchAction(String sessionId, int actionIndex);
}
