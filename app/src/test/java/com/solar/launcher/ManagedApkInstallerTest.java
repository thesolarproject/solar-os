package com.solar.launcher;

import org.junit.Test;

import java.io.File;

public class ManagedApkInstallerTest {

    @Test
    public void describeApkMissingFileReturnsEmpty() {
        if (!"".equals(ManagedApkInstaller.describeApk(null, new File("/no/such.apk")))) {
            throw new AssertionError("missing apk should yield empty label");
        }
    }

    @Test
    public void installMissingFileFails() {
        ManagedApkInstaller.Result r = ManagedApkInstaller.install(null, null);
        if (r.success || !"missing".equals(r.code)) {
            throw new AssertionError("null apk must fail with missing code");
        }
    }

    @Test
    public void readPackageNameMissingReturnsNull() {
        if (ManagedApkInstaller.readPackageName(null, new File("/no/such.apk")) != null) {
            throw new AssertionError("unreadable apk should return null package");
        }
    }
}
