package com.gihan.contextioq.ContextIQ.document;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
    // Code-point boundaries avoid splitting Unicode surrogate pairs. Small chunks bound prompt size.
    public List<String> split(String text) {
        int[] points = text.replace("\r\n", "\n").codePoints().toArray();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < points.length; start += 1000) {
            int length = Math.min(1200, points.length - start);
            String chunk = new String(points, start, length).strip();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (start + length == points.length) break;
        }
        return List.copyOf(chunks);
    }
}
