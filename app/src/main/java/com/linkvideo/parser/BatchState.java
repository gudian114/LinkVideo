package com.linkvideo.parser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class BatchState implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int LIMIT = 10;
    static final int EMPTY_INPUT = -2;

    static final class Entry implements Serializable {
        private static final long serialVersionUID = 1L;
        String input = "";
        ParsedResult result;
        String downloadPath = "";
        boolean selected;
        String editedCaption;
        String coverDownloadPath = "";
        boolean parseFailed;

        String caption() { return editedCaption != null ? editedCaption : result == null ? "" : result.title(); }

        void acceptVideo(VideoResult video) {
            clearResult();
            result = video;
        }

        void clearResult() {
            result = null;
            downloadPath = "";
            coverDownloadPath = "";
            editedCaption = null;
            parseFailed = false;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private boolean selecting;
    private boolean selectVideos;

    BatchState() { entries.add(new Entry()); }

    static boolean opensNextInput(String inserted) { return "\n".equals(inserted); }

    static boolean emptyInput(String text) {
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (!Character.isWhitespace(value) && !Character.isSpaceChar(value)) return false;
        }
        return true;
    }

    int size() { return entries.size(); }
    Entry entry(int index) { return entries.get(index); }

    int next(int index) {
        return next(index, false);
    }

    int next(int index, boolean skipPreviews) {
        if (emptyInput(entries.get(index).input)) return EMPTY_INPUT;
        for (int candidate = index + 1; candidate < entries.size(); candidate++) {
            Entry entry = entries.get(candidate);
            if (!skipPreviews || !(entry.result instanceof VideoResult) || entry.parseFailed) return candidate;
        }
        if (entries.size() == LIMIT) return -1;
        entries.add(new Entry());
        return entries.size() - 1;
    }

    List<Integer> parseTargets() {
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!emptyInput(entries.get(i).input)) targets.add(i);
        }
        return targets;
    }

    List<List<Integer>> similarLinks(List<Integer> targets) {
        Map<String, List<Integer>> matching = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            String link;
            try { link = LinkInput.parse(entries.get(i).input).url; }
            catch (Exception ignored) { continue; }
            List<Integer> group = matching.get(link);
            if (group == null) {
                group = new ArrayList<>();
                matching.put(link, group);
            }
            group.add(i);
        }
        List<List<Integer>> groups = new ArrayList<>();
        for (List<Integer> group : matching.values()) {
            if (group.size() < 2) continue;
            for (int index : group) {
                if (targets.contains(index)) { groups.add(group); break; }
            }
        }
        return groups;
    }

    List<Integer> videoTargets() {
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).result instanceof VideoResult) targets.add(i);
        }
        return targets;
    }

    boolean isSelecting() { return selecting; }
    boolean selectingVideos() { return selecting && selectVideos; }

    void beginSelection(boolean videos) {
        cancelSelection();
        selecting = true;
        selectVideos = videos;
    }

    boolean canSelect(int index) {
        return selecting && (!selectVideos || entries.get(index).result instanceof VideoResult);
    }

    void setSelected(int index, boolean value) {
        if (canSelect(index)) entries.get(index).selected = value;
    }

    List<Integer> selectedTargets() {
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (canSelect(i) && entries.get(i).selected) targets.add(i);
        }
        return targets;
    }

    boolean allSelected() {
        int eligible = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (!canSelect(i)) continue;
            eligible++;
            if (!entries.get(i).selected) return false;
        }
        return eligible > 0;
    }

    void toggleSelectAll() {
        boolean value = !allSelected();
        for (int i = 0; i < entries.size(); i++) setSelected(i, value);
    }

    List<Integer> downloadTargets() {
        if (!selecting) return videoTargets();
        return selectVideos ? selectedTargets() : new ArrayList<>();
    }

    List<Integer> deleteSelected(boolean removeVideoInputs) {
        List<Integer> targets = selectedTargets();
        for (int i = targets.size() - 1; i >= 0; i--) {
            int index = targets.get(i);
            if (selectVideos && !removeVideoInputs) {
                entries.get(index).clearResult();
            } else entries.remove(index);
        }
        if (entries.isEmpty()) entries.add(new Entry());
        cancelSelection();
        return targets;
    }

    void cancelSelection() {
        selecting = false;
        selectVideos = false;
        for (Entry entry : entries) entry.selected = false;
    }

    void clearInputs() {
        cancelSelection();
        entries.clear();
        entries.add(new Entry());
    }

    boolean removeEmpty(int index) {
        if (entries.size() == 1 || !entries.get(index).input.isEmpty()) return false;
        entries.remove(index);
        return true;
    }
}
