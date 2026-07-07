package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.solar.launcher.soulseek.store.ReachDatabase;
import com.solar.launcher.soulseek.store.ReachDbExecutor;

import java.util.Collections;
import java.util.List;

/**
 * 2026-07-05 — Reach DB reads off UI thread (conversation thread + chat rooms).
 * Layman: message screens load in the background so the wheel stays responsive.
 * Technical: generation-token async wrappers; MainActivity delegates here during perf split.
 * Reversal: delete; MainActivity calls SoulseekMessaging.*Sync on main thread again.
 */
public final class ReachScreenHost {

    public interface ThreadCallback {
        void onLoaded(List<SoulseekMessaging.Message> messages);
    }

    public interface RoomsCallback {
        void onLoaded(List<SoulseekWire.RoomEntry> rooms);
    }

    private ReachScreenHost() {}

    /** Load PM thread on ReachDb thread — never block UI. */
    public static void loadThreadAsync(final Context context, final SharedPreferences prefs,
            final String peer, final boolean virtualPeer, final int generation,
            final int expectedGeneration, final Handler uiHandler,
            final ThreadCallback callback) {
        if (context == null || callback == null) return;
        ReachDbExecutor.run(new Runnable() {
            @Override
            public void run() {
                List<SoulseekMessaging.Message> raw;
                if (virtualPeer) {
                    raw = SolarDeveloperMessaging.thread(context, prefs);
                } else {
                    raw = SoulseekMessaging.thread(context, prefs, peer);
                }
                final List<SoulseekMessaging.Message> result =
                        raw != null ? raw : Collections.<SoulseekMessaging.Message>emptyList();
                if (uiHandler == null) {
                    callback.onLoaded(result);
                    return;
                }
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != expectedGeneration) return;
                        callback.onLoaded(result);
                    }
                });
            }
        });
    }

    /** Cached chat room list from SQLite — async for adapter bind. */
    public static void loadRoomsAsync(final Context context, final int generation,
            final int expectedGeneration, final Handler uiHandler,
            final RoomsCallback callback) {
        if (context == null || callback == null) return;
        ReachDbExecutor.run(new Runnable() {
            @Override
            public void run() {
                List<SoulseekWire.RoomEntry> rooms =
                        ReachDatabase.getInstance(context).loadRoomsSync();
                final List<SoulseekWire.RoomEntry> result =
                        rooms != null ? rooms : Collections.<SoulseekWire.RoomEntry>emptyList();
                if (uiHandler == null) {
                    callback.onLoaded(result);
                    return;
                }
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != expectedGeneration) return;
                        callback.onLoaded(result);
                    }
                });
            }
        });
    }

    /** Convenience — post callback on main looper. */
    public static Handler mainHandler() {
        return new Handler(Looper.getMainLooper());
    }
}
