package com.solar.launcher.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import com.solar.launcher.SolarLog;
import com.solar.launcher.db.android.AndroidSolarDatabase;

import java.io.File;

/**
 * Base helper for Solar SQLite caches. Enables WAL so reads can run in parallel
 * via Android's connection pool, and applies sane pragmas for durability vs speed.
 *
 * <p>Subclasses get a modernized SQLite setup without changing call sites.
 * The helper intentionally stays compatible with {@code minSdk 17}; a future
 * libSQL engine can be swapped in behind {@link SolarDatabase} pilots.</p>
 */
public abstract class SolarDbHelper extends SQLiteOpenHelper {

    private final boolean walEnabled;
    private final Context appContext;

    protected SolarDbHelper(Context context, String name, int version) {
        this(context, name, version, true);
    }

    protected SolarDbHelper(Context context, String name, int version, boolean walEnabled) {
        super(context.getApplicationContext(), name, null, version);
        this.appContext = context.getApplicationContext();
        this.walEnabled = walEnabled;
        // API 18+ only — on 4.x setWriteAheadLoggingEnabled hits execSQL paths that reject PRAGMA.
        if (walEnabled && Build.VERSION.SDK_INT >= 18) {
            setWriteAheadLoggingEnabled(true);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!walEnabled) return;
        // rawQuery accepts PRAGMA result rows; execSQL on API 17 throws SQLiteException.
        if (Build.VERSION.SDK_INT < 18) {
            applyPragma(db, "PRAGMA journal_mode=WAL");
        }
        applyPragma(db, "PRAGMA synchronous=NORMAL");
        applyPragma(db, "PRAGMA journal_size_limit=1048576");
        applyPragma(db, "PRAGMA wal_autocheckpoint=100");
    }

    private static void applyPragma(SQLiteDatabase db, String sql) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            if (cursor != null) cursor.moveToFirst();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /** Borrow a readable database handle wrapped for portability. */
    public SolarDatabase openReadable() {
        return new AndroidSolarDatabase(openDatabase(false));
    }

    /** Borrow a writable database handle wrapped for portability. */
    public SolarDatabase openWritable() {
        return new AndroidSolarDatabase(openDatabase(true));
    }

    /** 2026-07-05 — Rename corrupt DB and recreate schema on SQLITE_CORRUPT. */
    private SQLiteDatabase openDatabase(boolean writable) {
        try {
            return writable ? getWritableDatabase() : getReadableDatabase();
        } catch (SQLiteDatabaseCorruptException e) {
            SolarLog.w("SolarDbHelper", "corrupt db " + getDatabaseName() + " — recovering");
            recoverCorruptDatabaseFile();
            return writable ? getWritableDatabase() : getReadableDatabase();
        }
    }

    private void recoverCorruptDatabaseFile() {
        Context ctx = appContext;
        if (ctx == null) return;
        File db = ctx.getDatabasePath(getDatabaseName());
        if (db.isFile()) {
            File corrupt = new File(db.getParentFile(),
                    db.getName() + ".corrupt." + System.currentTimeMillis());
            db.renameTo(corrupt);
        }
        deleteIfExists(db.getPath() + "-wal");
        deleteIfExists(db.getPath() + "-shm");
        deleteIfExists(db.getPath() + "-journal");
    }

    private static void deleteIfExists(String path) {
        File f = new File(path);
        if (f.isFile()) f.delete();
    }

    @Override
    public final void onCreate(SQLiteDatabase db) {
        onCreate(new AndroidSolarDatabase(db));
    }

    /** Create tables using the portable {@link SolarDatabase} API. */
    public abstract void onCreate(SolarDatabase db);

    @Override
    public final void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(new AndroidSolarDatabase(db), oldVersion, newVersion);
    }

    /** Upgrade tables using the portable {@link SolarDatabase} API. */
    public abstract void onUpgrade(SolarDatabase db, int oldVersion, int newVersion);
}
