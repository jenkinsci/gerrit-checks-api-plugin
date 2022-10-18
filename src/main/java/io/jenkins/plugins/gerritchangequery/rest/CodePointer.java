package io.jenkins.plugins.gerritchangequery.rest;

public class CodePointer {
  private String path;
  private CommentRange range;

  public CodePointer() {}

  public CodePointer(String path, CommentRange range) {
    this.path = path;
    this.range = range;
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
}
