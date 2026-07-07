package com.solar.launcher.xposed.bridge.extract;

import android.content.DialogInterface;

import java.util.ArrayList;

/**
 * Classifies stock AlertDialog / AlertController shapes and extracts wheel-friendly text payloads.
 * Pure logic — no overlay IPC — so unit tests can mirror field maps without Xposed on device.
 */
public final class AlertDialogExtract {

    /** How the hooked dialog should be replaced (or fail-open). */
    public enum Kind {
        /** Title/message + up to three Holo buttons. */
        PLAIN,
        /** setItems / setSingleChoiceItems with a simple CharSequence[] list. */
        LIST,
        /** setMultiChoiceItems — not yet replaced; leave stock UI. */
        MULTI_CHOICE,
        /** ProgressDialog / horizontal progress — leave stock UI. */
        PROGRESS,
        /** Custom view only with no message — leave stock UI. */
        CUSTOM_VIEW,
        /** Nothing usable to paint — leave stock UI. */
        EMPTY
    }

    /** Parsed dialog content extracted from AlertController + Dialog shell. */
    public static final class Snapshot {
        public Kind kind = Kind.EMPTY;
        public String title;
        public String message;
        public String[] listItems;
        /** Pre-checked row for single-choice lists; -1 when unknown. */
        public int checkedItem = -1;
        public boolean singleChoice;
        public String[] buttonLabels = new String[0];
        public int[] buttonIds = new int[0];
    }

    private AlertDialogExtract() {}

    /**
     * Build a snapshot from a live AlertDialog instance (needs {@code mAlert} controller).
     * Returns {@link Kind#EMPTY} when the dialog cannot be classified safely.
     */
    public static Snapshot fromDialog(Object dialog, ReflectFields fields) {
        Snapshot snap = new Snapshot();
        if (dialog == null || fields == null) return snap;
        Object controller = fields.get(dialog, "mAlert");
        if (controller == null) return snap;
        snap.kind = classifyKind(dialog, controller, fields);
        if (snap.kind == Kind.EMPTY || snap.kind == Kind.MULTI_CHOICE
                || snap.kind == Kind.PROGRESS || snap.kind == Kind.CUSTOM_VIEW) {
            return snap;
        }
        snap.title = readCharSequence(fields.get(controller, "mTitle"));
        snap.message = readCharSequence(fields.get(controller, "mMessage"));
        if (snap.kind == Kind.LIST) {
            snap.listItems = readItemArray(fields.get(controller, "mItems"));
            snap.singleChoice = readBoolean(fields.get(controller, "mIsSingleChoice"));
            snap.checkedItem = readInt(fields.get(controller, "mCheckedItem"), -1);
            if (snap.listItems == null || snap.listItems.length == 0) {
                snap.kind = Kind.EMPTY;
                return snap;
            }
        }
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<Integer> ids = new ArrayList<Integer>();
        collectButton(fields, controller, "mButtonNegativeText", DialogInterface.BUTTON_NEGATIVE,
                labels, ids);
        collectButton(fields, controller, "mButtonPositiveText", DialogInterface.BUTTON_POSITIVE,
                labels, ids);
        collectButton(fields, controller, "mButtonNeutralText", DialogInterface.BUTTON_NEUTRAL,
                labels, ids);
        snap.buttonLabels = labels.toArray(new String[labels.size()]);
        snap.buttonIds = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            snap.buttonIds[i] = ids.get(i);
        }
        if (snap.kind == Kind.PLAIN) {
            if ((snap.message == null || snap.message.length() == 0) && snap.buttonLabels.length == 0) {
                snap.kind = Kind.EMPTY;
            }
        }
        return snap;
    }

    /** Decide whether we can replace this dialog or must fail-open to Holo. */
    public static Kind classifyKind(Object dialog, Object controller, ReflectFields fields) {
        if (dialog == null || controller == null || fields == null) return Kind.EMPTY;
        if (isProgressDialog(dialog, controller, fields)) return Kind.PROGRESS;
        if (readBoolean(fields.get(controller, "mIsMultiChoice"))) return Kind.MULTI_CHOICE;
        // Adapter/cursor/spinner lists cannot be replayed faithfully yet.
        if (fields.get(controller, "mListView") != null) return Kind.EMPTY;
        if (fields.get(controller, "mAdapter") != null) return Kind.EMPTY;
        if (fields.get(controller, "mCursor") != null) return Kind.EMPTY;
        if (fields.get(controller, "mCheckedItems") != null) return Kind.EMPTY;
        Object customView = fields.get(controller, "mView");
        CharSequence message = readCharSequence(fields.get(controller, "mMessage"));
        if (customView != null && (message == null || message.length() == 0)) {
            return Kind.CUSTOM_VIEW;
        }
        Object items = fields.get(controller, "mItems");
        if (items instanceof CharSequence[] && ((CharSequence[]) items).length > 0) {
            return Kind.LIST;
        }
        if (readBoolean(fields.get(controller, "mIsSingleChoice"))) {
            // Single-choice without mItems yet — fail-open rather than show empty overlay.
            return Kind.EMPTY;
        }
        return Kind.PLAIN;
    }

    /** True for ProgressDialog shells and AlertController progress fields. */
    public static boolean isProgressDialog(Object dialog, Object controller, ReflectFields fields) {
        if (dialog == null || fields == null) return false;
        try {
            if ("android.app.ProgressDialog".equals(dialog.getClass().getName())) return true;
        } catch (Throwable ignored) {}
        if (controller == null) return false;
        if (fields.get(controller, "mProgress") != null) return true;
        Object progressPercent = fields.get(controller, "mProgressPercent");
        if (progressPercent instanceof Integer && (Integer) progressPercent > 0) return true;
        Object style = fields.get(dialog, "mProgressStyle");
        return style instanceof Integer && (Integer) style != 0;
    }

    private static void collectButton(ReflectFields fields, Object controller, String textField,
            int buttonId, ArrayList<String> labels, ArrayList<Integer> ids) {
        String text = readCharSequence(fields.get(controller, textField));
        if (text != null && text.length() > 0) {
            labels.add(text);
            ids.add(buttonId);
        }
    }

    private static String[] readItemArray(Object itemsObj) {
        if (!(itemsObj instanceof CharSequence[])) return null;
        CharSequence[] items = (CharSequence[]) itemsObj;
        String[] out = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] != null ? items[i].toString() : "";
        }
        return out;
    }

    private static String readCharSequence(Object value) {
        if (!(value instanceof CharSequence)) return null;
        String s = value.toString().trim();
        return s.length() > 0 ? s : null;
    }

    private static boolean readBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static int readInt(Object value, int fallback) {
        return value instanceof Integer ? (Integer) value : fallback;
    }
}
