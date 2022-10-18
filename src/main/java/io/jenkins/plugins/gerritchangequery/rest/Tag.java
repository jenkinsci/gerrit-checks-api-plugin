package io.jenkins.plugins.gerritchangequery.rest;

public class Tag {
  private String name;
  private String tooltip;
  private TagColor color;

  public Tag() {}

  public Tag(String name, String tooltip, TagColor color) {
    this.name = name;
    this.tooltip = tooltip;
    this.color = color;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTooltip() {
    return tooltip;
  }

  public void setTooltip(String tooltip) {
    this.tooltip = tooltip;
  }

  public TagColor getColor() {
    return color;
  }

  public void setColor(TagColor color) {
    this.color = color;
  }

  enum TagColor {
    GRAY,
    YELLOW,
    PINK,
    PURPLE,
    CYAN,
    BROWN
  }
}
