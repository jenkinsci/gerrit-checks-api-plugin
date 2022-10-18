package io.jenkins.plugins.gerritchangequery.rest;

public class Replacement {
  private String path;
  private CommentRange range;
  private String replacement;

  public Replacement() {}

  public Replacement(String path, CommentRange range, String replacement) {
    this.path = path;
    this.range = range;
    this.replacement = replacement;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public CommentRange getRange() {
    return range;
  }

  public void setRange(CommentRange range) {
    this.range = range;
  }

  public String getReplacement() {
    return replacement;
  }

  public void setReplacement(String replacement) {
    this.replacement = replacement;
  }
}
