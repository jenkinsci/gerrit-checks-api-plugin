package io.jenkins.plugins.gerritchangequery.rest;

public class GerritTriggerRerunAction extends RerunAction {
  public static final String PATH = "gerrit-trigger-retrigger-this";

  protected GerritTriggerRerunAction(String jobUrl) {
    super(false, String.format("%s%s", jobUrl, PATH));
  }
}
