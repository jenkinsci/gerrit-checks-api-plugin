package io.jenkins.plugins.gerritchangequery.rest;

public class GerritTriggerRerunAction extends RerunAction {
  public static final String PATH = "gerrit-trigger-retrigger-this";

  protected GerritTriggerRerunAction(String runUrl) {
    super(false, String.format("%s%s", runUrl, PATH));
  }
}
