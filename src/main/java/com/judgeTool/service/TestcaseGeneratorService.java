package com.judgeTool.service;

import com.judgeTool.model.Problem;
import com.judgeTool.model.Testcase;

import java.io.IOException;
import java.util.List;

public class TestcaseGeneratorService {
    private final GeminiService gemini;

    public TestcaseGeneratorService(GeminiService gemini) {
        this.gemini = gemini;
    }

    public List<Testcase> generate(Problem problem, int count, boolean includeEdge, boolean includeStress,
                                    String extraRequirements) throws IOException {
        return gemini.generateTestcases(problem, count, includeEdge, includeStress, extraRequirements);
    }
}
