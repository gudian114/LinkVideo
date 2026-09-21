package com.linkvideo.parser;

import java.io.Serializable;

public interface ParsedResult extends Serializable {
    enum Kind { VIDEO, IMAGE_POST }

    Kind kind();
    String platform();
    String title();
    String author();
    String sourceUrl();
}
