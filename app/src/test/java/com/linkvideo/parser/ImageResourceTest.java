package com.linkvideo.parser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ImageResourceTest {
    @Test public void keepsOrderedUniqueHttpsCandidates() {
        ImageResource image = new ImageResource(Arrays.asList(
                "https://img.example/a.jpg", "", "http://img.example/no.jpg",
                "https://img.example/a.jpg", "https://img.example/b.webp"),
                Collections.singletonMap("Referer", "https://www.douyin.com/note/1"), 1080, 1440);

        assertEquals(Arrays.asList("https://img.example/a.jpg", "https://img.example/b.webp"), image.urls);
        assertEquals("https://img.example/a.jpg", image.primaryUrl());
        assertEquals(1080, image.width);
        assertEquals(1440, image.height);
    }

    @Test public void rejectsResourceWithoutHttpsCandidate() {
        assertThrows(IllegalArgumentException.class, () -> new ImageResource(
                Arrays.asList("", "http://img.example/no.jpg"), Collections.emptyMap(), 0, 0));
    }

    @Test public void imagePostIsImmutableAndIdentifiesItsKind() {
        List<ImageResource> source = new ArrayList<>();
        source.add(new ImageResource(Collections.singletonList("https://img.example/1.jpg"),
                Collections.emptyMap(), 0, 0));

        ImagePostResult result = new ImagePostResult("抖音", "文案", "作者",
                "https://www.douyin.com/note/1", "", source);
        source.clear();

        assertEquals(ParsedResult.Kind.IMAGE_POST, result.kind());
        assertEquals("抖音", result.platform());
        assertEquals(1, result.images.size());
        assertThrows(UnsupportedOperationException.class, () -> result.images.clear());
    }
}
