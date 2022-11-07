package io.jenkins.plugins.gerritchangequery.rest;

import hudson.model.Job;
import hudson.model.Run;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GerritTriggerCheckRunFactory extends AbstractCheckRunFactory {

  @Override
  public CheckRun create(int change, int patchset, Job<?, ?> job, Run<?, ?> run, int attempt) {
    CheckRun checkRun = new CheckRun();

    checkRun.setChange(change);
    checkRun.setPatchSet(patchset);

    checkRun.setAttempt(attempt);
    checkRun.setExternalId(run.getExternalizableId());
    checkRun.setCheckName(job.getDisplayName());
    checkRun.setCheckDescription(job.getDescription());
    checkRun.setCheckLink(getAbsoluteRunUrl(run));
    checkRun.setStatus(computeStatus(run));
    checkRun.setStatusDescription(run.getBuildStatusSummary().message);
    checkRun.setStatusLink(getAbsoluteRunUrl(run));
    // TODO(Thomas): labelName. Info might be present in the gerrit plugins
    checkRun.setLabelName(null);
    checkRun.setActions(computeActions(job));
    checkRun.setScheduledTimestamp(run.getTime().toInstant().toString());
    checkRun.setStartedTimestamp(Instant.ofEpochMilli(run.getStartTimeInMillis()).toString());
    checkRun.setFinishedTimestamp(computeFinishedTimeStamp(run));
    checkRun.setResults(computeCheckResults(run));
    return checkRun;
  }

  @Override
  protected List<Action> computeActions(Job<?, ?> job) {
    List<Action> actions = new ArrayList<>();
    actions.add(new GerritTriggerRerunAction(job.getAbsoluteUrl()));
    return actions;
  }
}
