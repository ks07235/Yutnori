package com.example.yutnoriapp;

/** Stable palette and shape IDs; independent of Android for save migration and validation. */
final class TeamAppearance {
    static final int TEAM_COUNT = 4;
    static final int FORSYTHIA = 4;
    static final int CIRCLE = 0;
    static final int ROUNDED_SQUARE = 1;
    static final int DIAMOND = 2;
    static final int TRIANGLE = 3;
    static final int STAR = 4;
    static final int HEXAGON = 5;
    static final int SHAPE_COUNT = 6;

    // Saved games store the current palette index.
    private static final int[] FILLS = {
            0xFFD94841, 0xFF2364D2, 0xFF8357D6, 0xFF2F9E62, 0xFFFFD43B,
            0xFFFFFFFF, 0xFF202124, 0xFFE75491, 0xFF008C95
    };
    private static final int[] HIGHLIGHTS = {
            0xFFFF7A72, 0xFF4D96FF, 0xFFAC86EE, 0xFF66C98B, 0xFFFFE878,
            0xFFFFFFFF, 0xFF50545B, 0xFFFF91BC, 0xFF49C3C5
    };
    private static final int[] INKS = {
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF172B21, 0xFF493800,
            0xFF25323B, 0xFFFFFFFF, 0xFF391024, 0xFF092E32
    };
    // Readable on the app's light panels, including yellow and white teams.
    private static final int[] LABELS = {
            0xFFA92F2A, 0xFF1C50AA, 0xFF6740AA, 0xFF1E7044, 0xFF795B00,
            0xFF455A64, 0xFF202124, 0xFFA32960, 0xFF00666E
    };

    private TeamAppearance() { }

    static int colorCount() { return FILLS.length; }
    static boolean isColor(int id) { return id >= 0 && id < colorCount(); }
    static boolean isShape(int id) { return id >= 0 && id < SHAPE_COUNT; }
    static int fill(int id) { return FILLS[isColor(id) ? id : 0]; }
    static int highlight(int id) { return HIGHLIGHTS[isColor(id) ? id : 0]; }
    static int ink(int id) { return INKS[isColor(id) ? id : 0]; }
    static int label(int id) { return LABELS[isColor(id) ? id : 0]; }
    static int outline(int id) { return id == FORSYTHIA || id == 5 ? 0xFF455A64 : 0xFFFFFFFF; }

    static int[] recommendedColors() { return new int[]{FORSYTHIA, 1, 0, 3}; }
    static int[] recommendedShapes() { return new int[]{CIRCLE, ROUNDED_SQUARE, DIAMOND, TRIANGLE}; }
    static void randomize(int[] colors, int[] shapes, java.util.Random random) {
        randomize(colors, shapes, TEAM_COUNT, random);
    }

    static void randomize(int[] colors, int[] shapes, int teamCount, java.util.Random random) {
        int[] oldColors = colors.clone();
        int[] oldShapes = shapes.clone();
        int[] palette = shuffled(colorCount(), random);
        int[] forms = shuffled(SHAPE_COUNT, random);
        System.arraycopy(palette, 0, colors, 0, TEAM_COUNT);
        System.arraycopy(forms, 0, shapes, 0, TEAM_COUNT);
        if (java.util.Arrays.equals(java.util.Arrays.copyOf(oldColors, teamCount), java.util.Arrays.copyOf(colors, teamCount))
                && java.util.Arrays.equals(java.util.Arrays.copyOf(oldShapes, teamCount), java.util.Arrays.copyOf(shapes, teamCount))) {
            int first = colors[0]; colors[0] = colors[1]; colors[1] = first;
        }
    }

    private static int[] shuffled(int count, java.util.Random random) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = i;
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int value = values[i]; values[i] = values[j]; values[j] = value;
        }
        return values;
    }
    static int[] legacyColors() { return new int[]{0, 1, 2, 3}; }
    static int[] legacyShapes() { return new int[]{CIRCLE, CIRCLE, CIRCLE, CIRCLE}; }

    static boolean distinctColors(int[] colors, int teamCount) {
        if (teamCount < 2 || teamCount > TEAM_COUNT || colors == null || colors.length < teamCount) {
            return false;
        }
        for (int team = 0; team < teamCount; team++) {
            if (!isColor(colors[team])) return false;
            for (int other = 0; other < team; other++) {
                if (colors[team] == colors[other]) return false;
            }
        }
        return true;
    }

    static int[] restoreColors(int[] saved, int teamCount) {
        int[] result = legacyColors();
        boolean[] used = new boolean[colorCount()];
        int count = Math.max(2, Math.min(TEAM_COUNT, teamCount));
        for (int team = 0; team < count; team++) {
            int candidate = saved != null && team < saved.length && isColor(saved[team])
                    ? saved[team] : team;
            if (used[candidate]) {
                candidate = team;
                while (used[candidate]) candidate = (candidate + 1) % colorCount();
            }
            result[team] = candidate;
            used[candidate] = true;
        }
        return result;
    }

    static int[] restoreShapes(int[] saved) {
        int[] result = legacyShapes();
        for (int team = 0; team < TEAM_COUNT; team++) {
            if (saved != null && team < saved.length && isShape(saved[team])) result[team] = saved[team];
        }
        return result;
    }
}
