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
import io.jenkins.plugins.gerritchecksapi.StageForm;
import io.jenkins.plugins.gerritchecksapi.StageReportingJobProperty;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import java.util.List;
import java.util.stream.Collectors;
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

    // The result of the run itself is only known once it has finished. Its stages
    // have results of their own, which are added to them.
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

  @Test
  void computeCheckResults_runWithStages_returnsTheRunResultFirst() {
    withPipelineInstalled();
    WorkflowRun run = pipeline();
    BlockStartNode stage = stage("Build");

    List<CheckResult> results =
        AbstractCheckRunFactory.computeCheckResults(jenkins, run, RUN_ID, RUN_URL);

    assertEquals(List.of(RUN_ID, "my-job#7#2"), resultIds(results));
  }

  // --- the results of the stages ---

  @Test
  void computeStageResults_pipelinePluginMissing_returnsEmpty() {
    // The Pipeline API must not be touched, so that the plugin also works without it.
    WorkflowRun run = pipeline();
    BlockStartNode stage = TestFlowNodes.stage(execution, "2", "Build");
    when(execution.getCurrentHeads()).thenReturn(List.of(stage));

    assertTrue(computeStageResults(run).isEmpty());
  }

  @Test
  void computeStageResults_pipelinePluginInstalled_returnsStageResult() {
    withPipelineInstalled();
    WorkflowRun run = pipeline();
    BlockStartNode stage = stage("Build");

    List<CheckResult> results = computeStageResults(run);

    assertEquals(1, results.size());
    assertEquals("Build", results.get(0).getSummary());
    // The key of the run and the node of the stage identify the stage.
    assertEquals("my-job#7#2", results.get(0).getExternalId());
  }

  @Test
  void computeStageResults_stagesDisabledForTheJob_returnsEmpty() {
    withPipelineInstalled();
    WorkflowRun run = pipeline();
    // Built before the when() chain: Mockito does not allow nested stubbing.
    WorkflowJob job = jobReporting(Inheritable.DISABLED);
    when(run.getParent()).thenReturn(job);
    stage("Build");

    assertTrue(computeStageResults(run).isEmpty());
  }

  @Test
  void computeStageResults_stagesEnabledForTheJob_returnsStageResult() {
    withPipelineInstalled();
    WorkflowRun run = pipeline();
    // Built before the when() chain: Mockito does not allow nested stubbing.
    WorkflowJob job = jobReporting(Inheritable.ENABLED);
    when(run.getParent()).thenReturn(job);
    stage("Build");

    assertEquals(1, computeStageResults(run).size());
  }

  @Test
  void computeStageResults_buildingRunWithoutStagesWhileBuilding_returnsEmpty() {
    withPipelineInstalled();
    WorkflowRun run = pipeline(Result.SUCCESS, true);
    WorkflowJob job = jobReporting(Inheritable.ENABLED, Inheritable.DISABLED);
    when(run.getParent()).thenReturn(job);
    stage("Build");

    assertTrue(computeStageResults(run).isEmpty());
  }

  @Test
  void computeStageResults_buildingRunWithStagesWhileBuilding_returnsStageResult() {
    withPipelineInstalled();
    WorkflowRun run = pipeline(Result.SUCCESS, true);
    WorkflowJob job = jobReporting(Inheritable.ENABLED, Inheritable.ENABLED);
    when(run.getParent()).thenReturn(job);
    stage("Build");

    assertEquals(1, computeStageResults(run).size());
  }

  @Test
  void computeStageResults_stagesAsCheckRuns_returnsEmpty() {
    withPipelineInstalled();
    WorkflowRun run = pipeline();
    WorkflowJob job = jobReporting(Inheritable.ENABLED, StageForm.CHECK_RUNS);
    when(run.getParent()).thenReturn(job);
    stage("Build");

    assertTrue(
        computeStageResults(run).isEmpty(), "The stages are reported as check runs of their own");
  }

  @Test
  void computeStageResults_nonPipelineRun_returnsEmpty() {
    withPipelineInstalled();

    assertTrue(computeStageResults(run(Result.SUCCESS, false)).isEmpty());
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
  void computeStageCheckRuns_stagesAsCheckRuns_returnsStageCheckRun() {
    withPipelineInstalled();
    WorkflowRun run = pipeline();
    WorkflowJob job = jobReporting(Inheritable.ENABLED, StageForm.CHECK_RUNS);
    when(run.getParent()).thenReturn(job);
    stage("Build");

    List<CheckRun> stages = computeStageCheckRuns(run);

    assertEquals(1, stages.size());
    assertEquals("Build", stages.get(0).getCheckName());
    // The stage is nested below the run of the build.
    assertEquals(
        "{\"parent\":\"my-job#7\",\"run\":\"my-job#7#2\"}", stages.get(0).getExternalId());
  }

  @Test
  void computeStageCheckRuns_stagesAsResults_returnsEmpty() {
    withPipelineInstalled();
    WorkflowRun run = pipeline();
    WorkflowJob job = jobReporting(Inheritable.ENABLED, StageForm.RESULTS);
    when(run.getParent()).thenReturn(job);
    stage("Build");

    assertTrue(computeStageCheckRuns(run).isEmpty(), "The stages are results of the run");
  }

  // --- helpers ---

  private List<CheckResult> computeCheckResults(Run<?, ?> run) {
    return AbstractCheckRunFactory.computeCheckResults(run, RUN_ID, RUN_URL);
  }

  private List<CheckResult> computeStageResults(Run<?, ?> run) {
    return AbstractCheckRunFactory.computeStageResults(jenkins, run, RUN_URL);
  }

  private List<CheckRun> computeStageCheckRuns(Run<?, ?> run) {
    CheckRun parent = new CheckRun();
    parent.setAttempt(1);
    return AbstractCheckRunFactory.computeStageCheckRuns(jenkins, PS, run, parent, RUN_URL);
  }

  /** A finished stage of the mocked execution, named and numbered as the tests expect. */
  private BlockStartNode stage(String name) {
    BlockStartNode stage = TestFlowNodes.stage(execution, "2", name);
    BlockEndNode<?> end = TestFlowNodes.end(execution, "3", stage);
    when(execution.getCurrentHeads()).thenReturn(List.of(stage, end));
    when(execution.iterateEnclosingBlocks(stage)).thenReturn(List.of());
    return stage;
  }

  private void withPipelineInstalled() {
    when(jenkins.getPlugin(AbstractCheckRunFactory.WORKFLOW_JOB_PLUGIN))
        .thenReturn(mock(Plugin.class));
  }

  private WorkflowJob jobReporting(Inheritable reportStages) {
    return jobReporting(reportStages, Inheritable.INHERIT);
  }

  private WorkflowJob jobReporting(Inheritable reportStages, StageForm stageForm) {
    return jobReporting(reportStages, Inheritable.INHERIT, stageForm);
  }

  private WorkflowJob jobReporting(
      Inheritable reportStages, Inheritable reportStagesWhileBuilding) {
    return jobReporting(reportStages, reportStagesWhileBuilding, StageForm.INHERIT);
  }

  private WorkflowJob jobReporting(
      Inheritable reportStages, Inheritable reportStagesWhileBuilding, StageForm stageForm) {
    WorkflowJob job = mock(WorkflowJob.class);
    when(job.getProperty(StageReportingJobProperty.class))
        .thenReturn(
            new StageReportingJobProperty(
                reportStages,
                reportStagesWhileBuilding,
                stageForm,
                StageDepth.INHERIT,
                0,
                Inheritable.INHERIT));
    return job;
  }

  private WorkflowRun pipeline() {
    return pipeline(Result.SUCCESS, false);
  }

  private WorkflowRun pipeline(Result result, boolean building) {
    WorkflowRun run = mock(WorkflowRun.class);
    stubRun(run, result, building);
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

  private static List<String> resultIds(List<CheckResult> results) {
    return results.stream().map(CheckResult::getExternalId).collect(Collectors.toList());
  }
}
