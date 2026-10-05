// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.jenkins.plugins.gerritchecksapi.rest;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.GerritChecksConfiguration;
import io.jenkins.plugins.gerritchecksapi.PatchSetId;
import io.jenkins.plugins.gerritchecksapi.StageReporting;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun.RunStatus;
import io.jenkins.plugins.gerritchecksapi.rest.Link.LinkIcon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

public abstract class AbstractCheckRunFactory {
  /** Plugin providing the flow graph that the stage results are computed from. */
  static final String WORKFLOW_JOB_PLUGIN = "workflow-job";

  private final Jenkins jenkins = Jenkins.get();

  public abstract CheckRun create(PatchSetId ps, Job<?, ?> job, Run<?, ?> run, int attempt);

  protected abstract List<Action> computeActions(Run<?, ?> run);

  // TODO(Thomas): Add RUNNABLE status
  public static RunStatus computeStatus(Run<?, ?> run) {
    if (run.hasntStartedYet()) {
      return RunStatus.SCHEDULED;
    }
    if (run.isBuilding()) {
      return RunStatus.RUNNING;
    }
    return RunStatus.COMPLETED;
  }

  public static String computeFinishedTimeStamp(Run<?, ?> run) {
    if (run.hasntStartedYet()) {
      return null;
    }
    long duration;
    if (run.isBuilding()) {
      duration = run.getEstimatedDuration();
    } else {
      duration = run.getDuration();
    }
    return Instant.ofEpochMilli(run.getStartTimeInMillis())
        .plusMillis(duration)
        .toString();
  }

  protected List<CheckResult> computeCheckResults(Run<?, ?> run) {
    return computeCheckResults(jenkins, run, run.getExternalizableId(), getAbsoluteRunUrl(run));
  }

  /**
   * The results of a run: the result of the run itself, followed by the results of the stages of a
   * Pipeline build.
   *
   * @param resultIdPrefix prefix of the result ID, which has to be unique within the CheckRun. The
   *     ID the CheckRun was created with is used for it.
   */
  public static List<CheckResult> computeCheckResults(
      Jenkins jenkins, Run<?, ?> run, String resultIdPrefix, String runUrl) {
    List<CheckResult> results = new ArrayList<>(computeCheckResults(run, resultIdPrefix, runUrl));
    results.addAll(computeStageResults(jenkins, run, runUrl));
    return results;
  }

  /**
   * The result of the run itself. The stages of a Pipeline build are results of their own, which
   * {@link #computeCheckResults(Jenkins, Run, String, String)} adds to it. A run that is still
   * building has no result of its own yet.
   *
   * @param resultIdPrefix prefix of the result ID, which has to be unique within the CheckRun. The
   *     ID the CheckRun was created with is used for it.
   */
  public static List<CheckResult> computeCheckResults(
      Run<?, ?> run, String resultIdPrefix, String runUrl) {
    if (run.hasntStartedYet() || run.isBuilding()) {
      return List.of();
    }
    return List.of(computeRunResult(run, resultIdPrefix, runUrl));
  }

  /**
   * Computes the results of the stages of a Pipeline build, which are to be added to the results of
   * the run itself. Stages that are reported as check runs of their own are not results of the run,
   * see {@link #computeStageCheckRuns}.
   *
   * @return the results of the stages, or an empty list for runs that are not Pipeline builds, for
   *     Jenkins instances without Pipeline installed, for the stages that are reported as check
   *     runs, and for the builds whose stages are reported once they have finished only
   */
  public static List<CheckResult> computeStageResults(Jenkins jenkins, Run<?, ?> run, String runUrl) {
    // Guarding the reference to the (optional) Pipeline API.
    if (jenkins.getPlugin(WORKFLOW_JOB_PLUGIN) == null) {
      return List.of();
    }
    StageReporting reporting = GerritChecksConfiguration.resolve(run.getParent());
    if (!reporting.isReportStagesEnabled() || reporting.reportsStagesAsCheckRuns()) {
      return List.of();
    }
    return PipelineStages.results(run, runUrl, reporting);
  }

  /**
   * Computes the check runs of the stages of a Pipeline build, which are to be added next to the
   * check run of the run itself.
   *
   * @param parent the check run of the run itself
   * @return the check runs of the stages, or an empty list for runs that are not Pipeline builds,
   *     for Jenkins instances without Pipeline installed, for the stages that are reported as
   *     results of their run, and for the builds whose stages are reported once they have finished
   *     only
   */
  public static List<CheckRun> computeStageCheckRuns(
      Jenkins jenkins, PatchSetId ps, Run<?, ?> run, CheckRun parent, String runUrl) {
    // Guarding the reference to the (optional) Pipeline API.
    if (jenkins.getPlugin(WORKFLOW_JOB_PLUGIN) == null) {
      return List.of();
    }
    StageReporting reporting = GerritChecksConfiguration.resolve(run.getParent());
    if (!reporting.isReportStagesEnabled() || !reporting.reportsStagesAsCheckRuns()) {
      return List.of();
    }
    return PipelineStages.checkRuns(ps, run, parent, runUrl, reporting);
  }

  /**
   * The ID of a check run that is a child of another one, e.g. a downstream build. The parent and
   * the run parts are the runs' externalizable IDs.
   */
  public static String childId(String parentKey, String childKey) {
    return String.format("{\"parent\":\"%s\",\"run\":\"%s\"}", parentKey, childKey);
  }

  /** The result of the whole run. While a run is still building, there is no result yet. */
  private static CheckResult computeRunResult(Run<?, ?> run, String resultIdPrefix, String runUrl) {
    CheckResult result = new CheckResult();
    result.setExternalId(resultIdPrefix);
    Result res = run.getResult();
    result.setCategory(res != null ? Category.fromResult(res) : Category.INFO);
    result.setLinks(computeResultLinks(runUrl));
    return result;
  }

  private static List<Link> computeResultLinks(String runUrl) {
    List<Link> links = new ArrayList<>();
    Link consoleLogLink = new Link();
    consoleLogLink.setUrl(String.format("%sconsole", runUrl));
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
