package com.judgeTool.model;

public class Testcase {
    private long id;
    private long problemId;
    private String inputData;
    private String expectedOutput;
    private String caseType;
    private boolean edgeCase;
    private String generatorPrompt;

    public Testcase() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getProblemId() {
        return problemId;
    }

    public void setProblemId(long problemId) {
        this.problemId = problemId;
    }

    public String getInputData() {
        return inputData;
    }

    public void setInputData(String inputData) {
        this.inputData = inputData;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public boolean isEdgeCase() {
        return edgeCase;
    }

    public void setEdgeCase(boolean edgeCase) {
        this.edgeCase = edgeCase;
    }

    public String getGeneratorPrompt() {
        return generatorPrompt;
    }

    public void setGeneratorPrompt(String generatorPrompt) {
        this.generatorPrompt = generatorPrompt;
    }
}
