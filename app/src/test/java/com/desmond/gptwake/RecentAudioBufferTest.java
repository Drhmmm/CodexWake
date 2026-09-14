package com.desmond.gptwake;

import static org.junit.Assert.*;
import org.junit.Test;

public class RecentAudioBufferTest {
    @Test public void collectsOnlyWhileTheDiagnosticScreenIsVisible() {
        RecentAudioBuffer buffer = new RecentAudioBuffer(4);
        buffer.append(new float[]{1, 2});
        assertEquals(0, buffer.snapshot().length);
        buffer.enable();
        buffer.append(new float[]{1, 2});
        assertArrayEquals(new float[]{1, 2}, buffer.snapshot(), 0);
        buffer.disable();
        buffer.append(new float[]{3});
        assertEquals(0, buffer.snapshot().length);
    }

    @Test public void keepsOnlyTheNewestAudioInChronologicalOrder() {
        RecentAudioBuffer buffer = new RecentAudioBuffer(4);
        buffer.enable();
        buffer.append(new float[]{1, 2, 3});
        buffer.append(new float[]{4, 5, 6});
        assertArrayEquals(new float[]{3, 4, 5, 6}, buffer.snapshot(), 0);
        float[] snapshot = buffer.snapshot();
        buffer.append(new float[]{7});
        assertArrayEquals(new float[]{3, 4, 5, 6}, snapshot, 0);
        assertArrayEquals(new float[]{4, 5, 6, 7}, buffer.snapshot(), 0);
    }

    @Test public void reopeningDiagnosticsStartsWithAnEmptyBuffer() {
        RecentAudioBuffer buffer = new RecentAudioBuffer(2);
        buffer.enable();
        buffer.append(new float[]{1, 2, 3});
        assertArrayEquals(new float[]{2, 3}, buffer.snapshot(), 0);
        buffer.enable();
        assertEquals(0, buffer.snapshot().length);
    }
}
