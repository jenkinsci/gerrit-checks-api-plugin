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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.Plugin;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.Inheritable;
import io.jenkins.plugins.gerritchecksapi.PatchSetId;
import io.jenkins.plugins.gerritchecksapi.StageDepth;
import io.jenkins.plugins.gerritchecksapi.StageReportingJobProperty;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractCheckRunFactoryTest {

  private static final PatchSetId PS = PatchSetId.create(101, 4);
  private static final String RUN_ID = "my-job#7";
  private static final String RUN_URL = "https://jenkins/job/my-job/7/";

  private Jenkins jenkins;
  private FlowExecution execution;

  @BeforeEach
  void setUp() {
    jenkins = mock(Jenkins.class);
    execution = mock(FlowExecution.class);
  }

  // --- the result of the run ---

  @Test
  void computeCheckResults_notStartedYet_returnsEmpty() {
    Run<?, ?> run = mock(Run.class);
    when(run.hasntStartedYet()).thenReturn(true);

    assertTrue(computeCheckResults(run).isEmpty());
  }

  @Test
  void computeCheckResults_buildingRun_returnsEmpty() {
    Run<?, ?> run = run(null, true);

    // The stages of a running build are reported as check runs of their own, the
    // result of the run itself is only known once it has finished.
    assertTrue(computeCheckResults(run).isEmpty());
  }

  @Test
  void computeCheckResults_finishedRun_returnsRunResult() {
    Run<?, ?> run = run(Result.SUCCESS, false);

    List<CheckResult> results = computeCheckResults(run);

    assertEquals(1, results.size());
    CheckResult result = results.get(0);
    assertEquals(RUN_ID, result.getExternalId());
    assertEquals(Category.SUCCESS, result.getCategory());
    assertEquals(1, result.getLinks().size());
    assertEquals(RUN_URL + "console", result.getLinks().get(0).getUrl());
    assertTrue(result.getLinks().get(0).isPrimary());
  }

  @Test
  void computeCheckResults_finishedFailedRun_mapsToError() {
    Run<?, ?> run = run(Result.FAILURE, false);

    assertEquals(Category.ERROR, computeCheckResults(run).get(0).getCategory());
  }

  // --- the check runs of the stages ---

  @Test
  void computeStageCheckRuns_pipelinePluginMissing_returnsEmpty() {
    // The Pipeline API must not be touched, so that the plugin also works without it.
    WorkflowRun run = pipeline();
    BlockStartNode stage = TestFlowNodes.stage(execution, "2", "Build");
    when(execution.getCurrentHeads()).thenReturn(List.of(stage));

    assertTrue(computeStageCheckRuns(run).isEmpty());
  }

  @Test
  void computeStageCheckRuns_pipelinePluginInstalled_returnsStageCheckRun() {
    when(jenkins.getPlugin(AbstractCheckRunFactory.WORKFLOW_JOB_PLUGIN))
        .thenReturn(mock(Plugin.class));
    WorkflowRun run = pipeline();
    BlockStartNode stage = TestFlowNodes.stage(execution, "2", "Build");
    BlockEndNode<?> end = TestFlowNodes.end(execution, "3", stage);
    when(execution.getCurrentHeads()).thenReturn(List.of(stage, end));
    when(execution.iterateEnclosingBlocks(stage)).thenReturn(List.of());

    List<CheckRun> stages = computeStageCheckRuns(run);

    assertEquals(1, stages.size());
    assertEquals("Build", stages.get(0).getCheckName());
    assertEquals(
        "{\"parent\":\"my-job#7\",\"run\":\"my-job#7#2\"}", stages.get(0).getExternalId());
  }

  @Test
  void computeStageCheckRuns_stagesDisabledForTheJob_returnsEmpty() {
    when(jenkins.getPlugin(AbstractCheckRunFactory.WORKFLOW_JOB_PLUGIN))
        .thenReturn(mock(Plugin.class));
    WorkflowRun run = pipeline();
    // Built before the when() chain: Mockito does not allow nested stubbing.
    WorkflowJob job = jobReporting(Inheritable.DISABLED);
    when(run.getParent()).thenReturn(job);
    BlockStartNode stage = TestFlowNodes.stage(execution, "2", "Build");
    when(execution.getCurrentHeads()).thenReturn(List.of(stage));

    assertTrue(computeStageCheckRuns(run).isEmpty());
  }

  @Test
  void computeStageCheckRuns_stagesEnabledForTheJob_returnsStageCheckRun() {
    when(jenkins.getPlugin(AbstractCheckRunFactory.WORKFLOW_JOB_PLUGIN))
        .thenReturn(mock(Plugin.class));
    WorkflowRun run = pipeline();
    // Built before the when() chain: Mockito does not allow nested stubbing.
    WorkflowJob job = jobReporting(Inheritable.ENABLED);
    when(run.getParent()).thenReturn(job);
    BlockStartNode stage = TestFlowNodes.stage(execution, "2", "Build");
    when(execution.getCurrentHeads()).thenReturn(List.of(stage));
    when(execution.iterateEnclosingBlocks(stage)).thenReturn(List.of());

    assertEquals(1, computeStageCheckRuns(run).size());
  }

  @Test
  void computeStageCheckRuns_nonPipelineRun_returnsEmpty() {
    when(jenkins.getPlugin(AbstractCheckRunFactory.WORKFLOW_JOB_PLUGIN))
        .thenReturn(mock(Plugin.class));

    assertTrue(computeStageCheckRuns(run(Result.SUCCESS, false)).isEmpty());
  }

  // --- helpers ---

  private List<CheckResult> computeCheckResults(Run<?, ?> run) {
    return AbstractCheckRunFactory.computeCheckResults(run, RUN_ID, RUN_URL);
  }

  private List<CheckRun> computeStageCheckRuns(Run<?, ?> run) {
    CheckRun parent = new CheckRun();
    parent.setAttempt(1);
    return AbstractCheckRunFactory.computeStageCheckRuns(jenkins, PS, run, parent, RUN_URL);
  }

  private WorkflowJob jobReporting(Inheritable reportStages) {
    WorkflowJob job = mock(WorkflowJob.class);
    when(job.getProperty(StageReportingJobProperty.class))
        .thenReturn(
            new StageReportingJobProperty(reportStages, StageDepth.INHERIT, 0, Inheritable.INHERIT));
    return job;
  }

  private WorkflowRun pipeline() {
    WorkflowRun run = mock(WorkflowRun.class);
    stubRun(run, Result.SUCCESS, false);
    when(run.getExecution()).thenReturn(execution);
    return run;
  }

  private Run<?, ?> run(Result result, boolean building) {
    Run<?, ?> run = mock(Run.class);
    stubRun(run, result, building);
    return run;
  }

  private void stubRun(Run<?, ?> run, Result result, boolean building) {
    when(run.getExternalizableId()).thenReturn(RUN_ID);
    when(run.hasntStartedYet()).thenReturn(false);
    when(run.isBuilding()).thenReturn(building);
    when(run.getResult()).thenReturn(result);
    when(run.getUrl()).thenReturn("job/my-job/7/");
  }
}
