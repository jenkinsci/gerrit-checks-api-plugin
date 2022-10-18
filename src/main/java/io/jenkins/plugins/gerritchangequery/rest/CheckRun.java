package io.jenkins.plugins.gerritchangequery.rest;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.gerritchangequery.rest.CheckResult.Category;
import io.jenkins.plugins.gerritchangequery.rest.Link.LinkIcon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CheckRun {
  private int change;
  private int patchSet;
  private int attempt;
  private String externalId;
  private String checkName;
  private String checkDescription;
  private String checkLink;
  private RunStatus status;
  private String statusDescription;
  private String statusLink;
  private String labelName;
  private List<Action> actions;
  private String scheduledTimestamp;
  private String startedTimestamp;
  private String finishedTimestamp;
  private List<CheckResult> results;

  public static class Factory {
    public enum JobType {
      GERRIT_TRIGGER,
      GERRIT_MULTI_BRANCH
    }

    @SuppressWarnings("deprecation")
    public static CheckRun create(int change, int patchset, JobType jobType, Job job, Run run) {
      CheckRun checkRun = new CheckRun();

      checkRun.setChange(change);
      checkRun.setPatchSet(patchset);

      // Only true for gerritcodereview plugin
      // TODO(Thomas): Fix for gerrit-trigger plugin
      checkRun.setAttempt(run.getNumber());
      checkRun.setExternalId(run.getExternalizableId());
      checkRun.setCheckName(job.getDisplayName());
      checkRun.setCheckDescription(job.getDescription());
      checkRun.setCheckLink(run.getAbsoluteUrl());
      checkRun.setStatus(computeStatus(run));
      checkRun.setStatusDescription(run.getBuildStatusSummary().message);
      checkRun.setStatusLink(run.getAbsoluteUrl());
      // TODO(Thomas): labelName. Info might be present in the gerrit plugins
      checkRun.setLabelName(null);
      checkRun.setActions(computeActions(jobType, job));
      checkRun.setScheduledTimestamp(run.getTime().toInstant().toString());
      checkRun.setStartedTimestamp(Instant.ofEpochMilli(run.getStartTimeInMillis()).toString());
      checkRun.setFinishedTimestamp(computeFinishedTimeStamp(run));
      checkRun.setResults(computeCheckResults(run));
      return checkRun;
    }

    // TODO(Thomas): Add RUNNABLE status
    private static RunStatus computeStatus(Run run) {
      if (run.hasntStartedYet()) {
        return RunStatus.SCHEDULED;
      }
      if (run.isBuilding()) {
        return RunStatus.RUNNING;
      }
      return RunStatus.COMPLETED;
    }

    private static String computeFinishedTimeStamp(Run run) {
      if (run.hasntStartedYet() || run.isBuilding()) {
        return null;
      }
      return Instant.ofEpochMilli(run.getStartTimeInMillis())
          .plusMillis(run.getDuration())
          .toString();
    }

    // Currently only a single result can be returned. Having multiple results per build might
    // require introducing additional functionality to Jenkins
    private static List<CheckResult> computeCheckResults(Run run) {
      List<CheckResult> results = new ArrayList<CheckResult>();
      if (run.hasntStartedYet() || run.isBuilding()) {
        return results;
      }
      CheckResult result = new CheckResult();
      result.setExternalId(run.getExternalizableId());
      result.setCategory(computeCategory(run));
      result.setLinks(computeResultLinks(run));
      results.add(result);
      return results;
    }

    private static Category computeCategory(Run run) {
      Result res = run.getResult();
      switch (res.toString()) {
        case ("SUCCESS"):
          return Category.SUCCESS;
        case ("UNSTABLE"):
          return Category.WARNING;
        case ("FAILURE"):
          return Category.ERROR;
        default:
          return Category.INFO;
      }
    }

    @SuppressWarnings("deprecation")
    private static List<Link> computeResultLinks(Run run) {
      List<Link> links = new ArrayList<>();
      Link consoleLogLink = new Link();
      consoleLogLink.setUrl(String.format("%sconsole", run.getAbsoluteUrl()));
      consoleLogLink.setTooltip("Build log.");
      consoleLogLink.setIcon(LinkIcon.CODE);
      consoleLogLink.setPrimary(true);
      links.add(consoleLogLink);
      return links;
    }

    private static List<Action> computeActions(JobType jobType, Job job) {
      List<Action> actions = new ArrayList<>();
      switch (jobType) {
        case GERRIT_TRIGGER:
          actions.add(new GerritTriggerRerunAction(job.getAbsoluteUrl()));
          break;
        case GERRIT_MULTI_BRANCH:
          actions.add(new GerritMultiBranchRerunAction(job.getAbsoluteUrl()));
          break;
        default:
          throw new IllegalStateException(String.format("Unknown job type: %s", jobType));
      }
      return actions;
    }
  }

  public CheckRun() {}

  public CheckRun(
      int change,
      int patchSet,
      int attempt,
      String externalId,
      String checkName,
      String checkDescription,
      String checkLink,
      RunStatus status,
      String statusDescription,
      String statusLink,
      String labelName,
      List<Action> actions,
      String scheduledTimestamp,
      String startedTimestamp,
      String finishedTimestamp,
      List<CheckResult> results) {
    this.change = change;
    this.patchSet = patchSet;
    this.attempt = attempt;
    this.externalId = externalId;
    this.checkName = checkName;
    this.checkDescription = checkDescription;
    this.checkLink = checkLink;
    this.status = status;
    this.statusDescription = statusDescription;
    this.statusLink = statusLink;
    this.labelName = labelName;
    this.actions = actions;
    this.scheduledTimestamp = scheduledTimestamp;
    this.startedTimestamp = startedTimestamp;
    this.finishedTimestamp = finishedTimestamp;
    this.results = results;
  }

  public int getChange() {
    return change;
  }

  public void setChange(int change) {
    this.change = change;
  }

  public int getPatchSet() {
    return patchSet;
  }

  public void setPatchSet(int patchSet) {
    this.patchSet = patchSet;
  }

  public int getAttempt() {
    return attempt;
  }

  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public String getCheckName() {
    return checkName;
  }

  public void setCheckName(String checkName) {
    this.checkName = checkName;
  }

  public String getCheckDescription() {
    return checkDescription;
  }

  public void setCheckDescription(String checkDescription) {
    this.checkDescription = checkDescription;
  }

  public String getCheckLink() {
    return checkLink;
  }

  public void setCheckLink(String checkLink) {
    this.checkLink = checkLink;
  }

  public RunStatus getStatus() {
    return status;
  }

  public void setStatus(RunStatus status) {
    this.status = status;
  }

  public String getStatusDescription() {
    return statusDescription;
  }

  public void setStatusDescription(String statusDescription) {
    this.statusDescription = statusDescription;
  }

  public String getStatusLink() {
    return statusLink;
  }

  public void setStatusLink(String statusLink) {
    this.statusLink = statusLink;
  }

  public String getLabelName() {
    return labelName;
  }

  public void setLabelName(String labelName) {
    this.labelName = labelName;
  }

  public List<Action> getActions() {
    return actions;
  }

  public void setActions(List<Action> actions) {
    this.actions = actions;
  }

  public String getScheduledTimestamp() {
    return scheduledTimestamp;
  }

  public void setScheduledTimestamp(String scheduledTimestamp) {
    this.scheduledTimestamp = scheduledTimestamp;
  }

  public String getStartedTimestamp() {
    return startedTimestamp;
  }

  public void setStartedTimestamp(String startedTimestamp) {
    this.startedTimestamp = startedTimestamp;
  }

  public String getFinishedTimestamp() {
    return finishedTimestamp;
  }

  public void setFinishedTimestamp(String finishedTimestamp) {
    this.finishedTimestamp = finishedTimestamp;
  }

  public List<CheckResult> getResults() {
    return results;
  }

  public void setResults(List<CheckResult> results) {
    this.results = results;
  }

  enum RunStatus {
    RUNNABLE,
    RUNNING,
    SCHEDULED,
    COMPLETED
  }
}
