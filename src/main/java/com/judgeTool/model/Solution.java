package com.judgeTool.model;

import java.time.LocalDateTime;

public class Solution {
    private long id;
    private long problemId;
    private String code;
    private String language;
    private String verdict;
    private String note;
    private LocalDateTime createdAt;

    public Solution() {
    }

    public Solution(long id, long problemId, String code, String language, String verdict, String note,
                    LocalDateTime createdAt) {
        this.id = id;
        this.problemId = problemId;
        this.code = code;
        this.language = language;
        this.verdict = verdict;
        this.note = note;
        this.createdAt = createdAt;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
