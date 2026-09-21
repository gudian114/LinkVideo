package com.linkvideo.parser;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
public class DouyinBrowserScriptProbe {
    public static void main(String[] args) throws Exception {
        String id = args[0];
        DouyinBrowserPage page = new DouyinBrowserPage("https://www.douyin.com/video/" + id,
                "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=" + id + "&aid=1128&app_name=aweme");
        Files.write(Paths.get(args[1]), page.script().getBytes(StandardCharsets.UTF_8));
    }
}
