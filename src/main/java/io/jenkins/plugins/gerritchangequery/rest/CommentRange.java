package io.jenkins.plugins.gerritchangequery.rest;

public class CommentRange {
  private int startLine;
  private int startCharacter;
  private int endLine;
  private int endCharacter;

  public CommentRange() {}

  public CommentRange(int startLine, int startCharacter, int endLine, int endCharacter) {
    this.startLine = startLine;
    this.startCharacter = startCharacter;
    this.endLine = endLine;
    this.endCharacter = endCharacter;
  }

  public int getStartLine() {
    return startLine;
  }

  public void setStartLine(int startLine) {
    this.startLine = startLine;
  }

  public int getStartCharacter() {
    return startCharacter;
  }

  public void setStartCharacter(int startCharacter) {
    this.startCharacter = startCharacter;
  }

  public int getEndLine() {
    return endLine;
  }

  public void setEndLine(int endLine) {
    this.endLine = endLine;
  }

  public int getEndCharacter() {
    return endCharacter;
  }

  public void setEndCharacter(int endCharacter) {
    this.endCharacter = endCharacter;
  }
}
