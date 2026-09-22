package com.linkvideo.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstallOutcomeTest {
    @Test public void mapsSuccess() {
        InstallOutcome.Result result = InstallOutcome.fromStatus(0, null);
        assertEquals(InstallOutcome.Kind.SUCCESS, result.kind());
        assertEquals("更新安装成功", result.message());
    }

    @Test public void mapsUserCancellationAndPreservesCurrentVersionMessage() {
        InstallOutcome.Result result = InstallOutcome.fromStatus(3, null);
        assertEquals(InstallOutcome.Kind.CANCELED, result.kind());
        assertTrue(result.message().contains("安装已取消"));
        assertTrue(result.message().contains("当前版本未改变"));
    }

    @Test public void mapsFailureWithSafeSystemDetail() {
        InstallOutcome.Result result = InstallOutcome.fromStatus(1, "证书冲突");
        assertEquals(InstallOutcome.Kind.FAILED, result.kind());
        assertTrue(result.message().contains("证书冲突"));
        assertTrue(result.message().contains("当前版本未改变"));
    }

    @Test public void omitsBlankSystemDetail() {
        InstallOutcome.Result result = InstallOutcome.fromStatus(6, "  ");
        assertEquals("安装失败，当前版本未改变", result.message());
    }
}
