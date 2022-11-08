package io.jenkins.plugins.gerritchangequery.rest;

import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.gerritchangequery.rest.CheckResult.Category;
import io.jenkins.plugins.gerritchangequery.rest.CheckRun.RunStatus;
import io.jenkins.plugins.gerritchangequery.rest.Link.LinkIcon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

public abstract class AbstractCheckRunFactory {
  private final Jenkins jenkins = Jenkins.get();

  public abstract CheckRun create(
      int change, int patchset, Job<?, ?> job, Run<?, ?> run, int attempt);

  protected abstract List<Action> computeActions(Run<?, ?> run);

  // TODO(Thomas): Add RUNNABLE status
  protected static RunStatus computeStatus(Run<?, ?> run) {
    if (run.hasntStartedYet()) {
      return RunStatus.SCHEDULED;
    }
    if (run.isBuilding()) {
      return RunStatus.RUNNING;
    }
    return RunStatus.COMPLETED;
  }

  protected static String computeFinishedTimeStamp(Run<?, ?> run) {
    if (run.hasntStartedYet() || run.isBuilding()) {
      return null;
    }
    return Instant.ofEpochMilli(run.getStartTimeInMillis())
        .plusMillis(run.getDuration())
        .toString();
  }

  // Currently only a single result can be returned. Having multiple results per build might
  // require introducing additional functionality to Jenkins
  protected List<CheckResult> computeCheckResults(Run<?, ?> run) {
    List<CheckResult> results = new ArrayList<CheckResult>();
    if (run.hasntStartedYet() || run.isBuilding()) {
      return results;
    }
    CheckResult result = new CheckResult();
    result.setExternalId(run.getExternalizableId());
    result.setCategory(Category.fromResult(run.getResult()));
    result.setLinks(computeResultLinks(run));
    results.add(result);
    return results;
  }

  private List<Link> computeResultLinks(Run<?, ?> run) {
    List<Link> links = new ArrayList<>();
    Link consoleLogLink = new Link();
    consoleLogLink.setUrl(String.format("%sconsole", getAbsoluteRunUrl(run)));
    consoleLogLink.setTooltip("Build log.");
    consoleLogLink.setIcon(LinkIcon.CODE);
    consoleLogLink.setPrimary(true);
    links.add(consoleLogLink);
    return links;
  }

  protected String getAbsoluteRunUrl(Run<?, ?> run) {
    return String.format("%s%s", jenkins.getRootUrl(), run.getUrl());
  }
}
