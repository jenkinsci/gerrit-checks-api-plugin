package io.jenkins.plugins.gerritchangequery.rest;


public class RerunAction extends Action {
  public static final String NAME = "Rerun";
  public static final String TOOLTIP = "Run the build for the patchset again.";
  public static final boolean PRIMARY = true;
  public static final boolean SUMMARY = false;
  public static final String METHOD = "POST";

  protected RerunAction() {
    super(NAME, TOOLTIP, PRIMARY, SUMMARY, METHOD);
  }

  protected RerunAction(boolean disabled, String url) {
    super(NAME, TOOLTIP, PRIMARY, SUMMARY, METHOD, disabled, url, null);
  }
}
