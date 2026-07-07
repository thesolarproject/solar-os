package com.solar.launcher;

import com.solar.launcher.xposed.bridge.extract.AlertDialogExtract;
import com.solar.launcher.xposed.bridge.extract.ReflectFields;

import org.junit.Test;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Unit tests for AlertController classification and text extraction (no Xposed on JVM). */
public class AlertDialogExtractTest {

    /** Identity-keyed field reader for tests — mirrors production ReflectFields.Xposed. */
    private static final class MapFields implements ReflectFields {
        private final Map<Object, Map<String, Object>> byTarget =
                new IdentityHashMap<Object, Map<String, Object>>();

        MapFields put(Object target, String fieldName, Object value) {
            Map<String, Object> fields = byTarget.get(target);
            if (fields == null) {
                fields = new HashMap<String, Object>();
                byTarget.put(target, fields);
            }
            fields.put(fieldName, value);
            return this;
        }

        @Override
        public Object get(Object target, String fieldName) {
            if (target == null || fieldName == null) return null;
            Map<String, Object> fields = byTarget.get(target);
            return fields != null ? fields.get(fieldName) : null;
        }
    }

    private final Object dialog = new Object();
    private final Object controller = new Object();

    @Test
    public void classifiesPlainMessageDialog() {
        MapFields fields = new MapFields()
                .put(dialog, "mAlert", controller)
                .put(controller, "mMessage", "Hello")
                .put(controller, "mButtonPositiveText", "OK");
        AlertDialogExtract.Snapshot snap = AlertDialogExtract.fromDialog(dialog, fields);
        assertEquals(AlertDialogExtract.Kind.PLAIN, snap.kind);
        assertEquals("Hello", snap.message);
        assertArrayEquals(new String[] {"OK"}, snap.buttonLabels);
    }

    @Test
    public void classifiesSimpleItemList() {
        MapFields fields = new MapFields()
                .put(controller, "mItems", new CharSequence[] {"One", "Two"})
                .put(controller, "mIsSingleChoice", Boolean.FALSE);
        assertEquals(AlertDialogExtract.Kind.LIST,
                AlertDialogExtract.classifyKind(dialog, controller, fields));
    }

    @Test
    public void classifiesSingleChoiceList() {
        MapFields fields = new MapFields()
                .put(dialog, "mAlert", controller)
                .put(controller, "mItems", new CharSequence[] {"A", "B"})
                .put(controller, "mIsSingleChoice", Boolean.TRUE)
                .put(controller, "mCheckedItem", 1);
        AlertDialogExtract.Snapshot snap = AlertDialogExtract.fromDialog(dialog, fields);
        assertEquals(AlertDialogExtract.Kind.LIST, snap.kind);
        assertTrue(snap.singleChoice);
        assertEquals(1, snap.checkedItem);
        assertArrayEquals(new String[] {"A", "B"}, snap.listItems);
    }

    @Test
    public void failsOpenForMultiChoice() {
        MapFields fields = new MapFields()
                .put(controller, "mIsMultiChoice", Boolean.TRUE);
        assertEquals(AlertDialogExtract.Kind.MULTI_CHOICE,
                AlertDialogExtract.classifyKind(dialog, controller, fields));
    }

    @Test
    public void failsOpenForProgressDialog() {
        MapFields fields = new MapFields()
                .put(controller, "mProgress", new Object());
        assertTrue(AlertDialogExtract.isProgressDialog(dialog, controller, fields));
    }

    @Test
    public void failsOpenForCustomViewOnly() {
        MapFields fields = new MapFields()
                .put(controller, "mView", new Object());
        assertEquals(AlertDialogExtract.Kind.CUSTOM_VIEW,
                AlertDialogExtract.classifyKind(dialog, controller, fields));
    }

    @Test
    public void failsOpenForAdapterList() {
        MapFields fields = new MapFields()
                .put(controller, "mAdapter", new Object());
        assertEquals(AlertDialogExtract.Kind.EMPTY,
                AlertDialogExtract.classifyKind(dialog, controller, fields));
    }

    @Test
    public void listKindIncludesSingleChoiceFlag() {
        MapFields fields = new MapFields()
                .put(dialog, "mAlert", controller)
                .put(controller, "mItems", new CharSequence[] {"Red", "Blue"})
                .put(controller, "mIsSingleChoice", Boolean.TRUE);
        AlertDialogExtract.Snapshot snap = AlertDialogExtract.fromDialog(dialog, fields);
        assertEquals(AlertDialogExtract.Kind.LIST, snap.kind);
        assertTrue(snap.singleChoice);
    }
}
