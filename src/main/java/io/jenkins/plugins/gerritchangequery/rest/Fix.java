package io.jenkins.plugins.gerritchangequery.rest;

import java.util.List;

public class Fix {
  private String description;
  private List<Replacement> replacements;

  public Fix() {}

  public Fix(String description, List<Replacement> replacements) {
    this.description = description;
    this.replacements = replacements;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<Replacement> getReplacements() {
    return replacements;
  }

  public void setReplacements(List<Replacement> replacements) {
    this.replacements = replacements;
  }
}
