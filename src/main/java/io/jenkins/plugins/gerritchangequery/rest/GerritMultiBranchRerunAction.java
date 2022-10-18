package io.jenkins.plugins.gerritchangequery.rest;

public class GerritMultiBranchRerunAction extends RerunAction {
  public static final String PATH = "build";

  protected GerritMultiBranchRerunAction(String jobUrl) {
    super(false, String.format("%s%s", jobUrl, PATH));
  }
}
