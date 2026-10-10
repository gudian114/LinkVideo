package com.linkvideo.parser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnnouncementContractTest {
    @Test public void announcementExplainsFreshInstallVerification() throws Exception {
        String strings = read("app/src/main/res/values/strings.xml");
        assertTrue(strings.contains("卸载并重新安装会清除抖音访客身份"));
        assertTrue(strings.contains("首次解析可能需要完成验证"));
        assertTrue(strings.contains("后续作品通常可直接解析"));
    }

    @Test public void repositoryReadmeUsesVideoParserName() throws Exception {
        String readme = read("README.md");
        assertTrue(readme.startsWith("# 视频解析（Android）"));
        assertFalse(readme.startsWith("# 拾链（Android）"));
    }

    private static String read(String relative) throws Exception {
        Path current = Paths.get(System.getProperty("user.dir"));
        Path root = Files.isDirectory(current.resolve("outputs/LinkVideo"))
                ? current.resolve("outputs/LinkVideo") : current;
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }
}
