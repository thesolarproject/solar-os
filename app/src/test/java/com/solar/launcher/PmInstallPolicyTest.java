package com.solar.launcher;

import org.junit.Test;

public class PmInstallPolicyTest {

    @Test
    public void isPmSuccessRecognizesSuccess() {
        if (!PmInstallPolicy.isPmSuccess("Success")) {
            throw new AssertionError("Success");
        }
    }

    @Test
    public void isPmSuccessRejectsFailure() {
        if (PmInstallPolicy.isPmSuccess("Failure [INSTALL_FAILED]")) {
            throw new AssertionError("failure");
        }
    }
}
