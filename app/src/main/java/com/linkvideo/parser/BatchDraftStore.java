package com.linkvideo.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** App-private batch draft; write then replace so an interrupted write preserves the previous draft. */
final class BatchDraftStore {
    private BatchDraftStore() { }

    static BatchState load(File file) {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(file))) {
            Object value = input.readObject();
            if (value instanceof BatchState) return (BatchState) value;
        } catch (IOException | ClassNotFoundException ignored) { }
        return new BatchState();
    }

    static void save(File file, BatchState state) throws IOException {
        File pending = new File(file.getParentFile(), file.getName() + ".pending");
        try (FileOutputStream bytes = new FileOutputStream(pending);
             ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
            output.flush();
        }
        Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    static void clear(File file) throws IOException {
        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(new File(file.getParentFile(), file.getName() + ".pending").toPath());
    }
}
