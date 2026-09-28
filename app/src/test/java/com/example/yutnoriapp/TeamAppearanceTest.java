package com.example.yutnoriapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class TeamAppearanceTest {
    @Test public void randomSuggestionsNeverDuplicateColorsOrShapesOrRepeatTheWholeSet() {
        java.util.Random random = new java.util.Random(150);
        int[] colors = TeamAppearance.recommendedColors();
        int[] shapes = TeamAppearance.recommendedShapes();
        for (int attempt = 0; attempt < 1000; attempt++) {
            int[] previousColors = colors.clone(), previousShapes = shapes.clone();
            TeamAppearance.randomize(colors, shapes, random);
            assertTrue(TeamAppearance.distinctColors(colors, 4));
            for (int i = 0; i < 4; i++) {
                assertTrue(TeamAppearance.isShape(shapes[i]));
                for (int j = 0; j < i; j++) assertNotEquals(shapes[i], shapes[j]);
            }
            assertFalse(java.util.Arrays.equals(colors, previousColors) && java.util.Arrays.equals(shapes, previousShapes));
        }
    }
    @Test public void recommendedSetsIncludeForsythiaAndDistinctColorsForEveryTeamCount() {
        for (int count = 2; count <= 4; count++) {
            int[] colors = TeamAppearance.recommendedColors();
            assertEquals(TeamAppearance.FORSYTHIA, colors[0]);
            assertTrue(TeamAppearance.distinctColors(colors, count));
        }
    }

    @Test public void whiteBlackAndForsythiaHaveReadableNumeralsAndOutlines() {
        assertEquals(0xFFFFD43B, TeamAppearance.fill(TeamAppearance.FORSYTHIA));
        assertEquals(0xFFFFFFFF, TeamAppearance.fill(5));
        assertEquals(0xFF202124, TeamAppearance.fill(6));
        assertNotEquals(TeamAppearance.fill(5), TeamAppearance.ink(5));
        assertNotEquals(TeamAppearance.fill(5), TeamAppearance.outline(5));
        assertNotEquals(TeamAppearance.fill(6), TeamAppearance.ink(6));
        assertNotEquals(TeamAppearance.fill(4), TeamAppearance.ink(4));
    }

    @Test public void duplicateAndIncompleteTeamSelectionsAreRejected() {
        assertFalse(TeamAppearance.distinctColors(new int[]{4, 4, 0, 3}, 2));
        assertFalse(TeamAppearance.distinctColors(new int[]{4}, 2));
        assertFalse(TeamAppearance.distinctColors(null, 2));
        assertFalse(TeamAppearance.distinctColors(new int[]{4, -1, 0, 3}, 2));
    }

    @Test public void legacyGamesKeepTheirOriginalColorsAndCircleShapes() {
        assertArrayEquals(new int[]{0, 1, 2, 3}, TeamAppearance.restoreColors(new int[0], 4));
        assertArrayEquals(new int[]{0, 0, 0, 0}, TeamAppearance.restoreShapes(null));
    }

    @Test public void customSelectionsSurviveRoundTripWithoutAliasing() {
        int[] colors = {5, 6, 4, 8};
        int[] shapes = {4, 5, 1, 2};
        assertArrayEquals(colors, TeamAppearance.restoreColors(colors, 4));
        assertArrayEquals(shapes, TeamAppearance.restoreShapes(shapes));
        assertNotSame(colors, TeamAppearance.restoreColors(colors, 4));
        assertNotSame(shapes, TeamAppearance.restoreShapes(shapes));
    }

    @Test public void corruptedOrShortSavesAreRepairedIntoValidDistinctColors() {
        for (int count = 2; count <= 4; count++) {
            assertTrue(TeamAppearance.distinctColors(TeamAppearance.restoreColors(new int[]{5, 5, -9, 900}, count), count));
            assertTrue(TeamAppearance.distinctColors(TeamAppearance.restoreColors(new int[]{1}, count), count));
        }
        assertArrayEquals(new int[]{5, 0, 0, 0}, TeamAppearance.restoreShapes(new int[]{5, -1, 99}));
    }

    @Test public void paletteAndShapesOfferNineColorsWithoutOrangeAndSixShapes() {
        assertEquals(9, TeamAppearance.colorCount());
        assertEquals(6, TeamAppearance.SHAPE_COUNT);
        for (int color = 0; color < TeamAppearance.colorCount(); color++) {
            assertTrue(TeamAppearance.isColor(color));
            assertNotEquals(0xFFF28C28, TeamAppearance.fill(color));
            for (int other = 0; other < color; other++) {
                assertNotEquals(TeamAppearance.fill(other), TeamAppearance.fill(color));
            }
        }
    }
}
