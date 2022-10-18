package io.jenkins.plugins.gerritchangequery.rest;

import java.util.List;

public class CheckResult {
  private String ExternalId;
  private Category category;
  private String summary;
  private String message;
  private List<Tag> tags;
  private List<Link> links;
  private List<CodePointer> codePointers;
  private List<Action> actions;
  private List<Fix> fixes;

  public CheckResult() {}

  public CheckResult(
      String externalId,
      Category category,
      String summary,
      String message,
      List<Tag> tags,
      List<Link> links,
      List<CodePointer> codePointers,
      List<Action> actions,
      List<Fix> fixes) {
    ExternalId = externalId;
    this.category = category;
    this.summary = summary;
    this.message = message;
    this.tags = tags;
    this.links = links;
    this.codePointers = codePointers;
    this.actions = actions;
    this.fixes = fixes;
  }

  public String getExternalId() {
    return ExternalId;
  }

  public void setExternalId(String externalId) {
    ExternalId = externalId;
  }

  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<Tag> getTags() {
    return tags;
  }

  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  public List<Link> getLinks() {
    return links;
  }

  public void setLinks(List<Link> links) {
    this.links = links;
  }

  public List<CodePointer> getCodePointers() {
    return codePointers;
  }

  public void setCodePointers(List<CodePointer> codePointers) {
    this.codePointers = codePointers;
  }

  public List<Action> getActions() {
    return actions;
  }

  public void setActions(List<Action> actions) {
    this.actions = actions;
  }

  public List<Fix> getFixes() {
    return fixes;
  }

  public void setFixes(List<Fix> fixes) {
    this.fixes = fixes;
  }

  enum Category {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
  }
}
