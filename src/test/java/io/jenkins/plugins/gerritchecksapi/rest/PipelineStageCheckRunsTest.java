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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.PatchSetId;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun.RunStatus;
import io.jenkins.plugins.gerritchecksapi.rest.Link.LinkIcon;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipelineStageCheckRunsTest {

  private static final PatchSetId PS = PatchSetId.create(101, 4);
  private static final String RUN_KEY = "my-job#7";
  private static final String RUN_URL = "https://jenkins/job/my-job/7/";
  private static final String STAGE_STATUS_TAG = "STAGE_STATUS";

  private FlowExecution execution;
  private CheckRun parent;

  @BeforeEach
  void setUp() {
    execution = mock(FlowExecution.class);
    parent = new CheckRun();
    parent.setChange(PS.changeId());
    parent.setPatchSet(PS.patchSetNumber());
    parent.setAttempt(2);
    parent.setCheckDescription("description of my-job");
    parent.setStatusDescription("stable");
    parent.setStatusLink(RUN_URL);
    parent.setScheduledTimestamp("2024-01-01T00:00:00Z");
    parent.setStartedTimestamp("2024-01-01T00:00:01Z");
    parent.setFinishedTimestamp("2024-01-01T00:00:02Z");
  }

  // --- run-level handling ---

  @Test
  void compute_nonPipelineRun_returnsEmpty() {
    Run<?, ?> run = mock(Run.class);

    assertTrue(PipelineStageCheckRuns.compute(PS, run, parent, RUN_URL).isEmpty());
  }

  @Test
  void compute_pipelineWithoutExecution_returnsEmpty() {
    WorkflowRun run = mock(WorkflowRun.class);
    when(run.getExecution()).thenReturn(null);

    assertTrue(PipelineStageCheckRuns.compute(PS, run, parent, RUN_URL).isEmpty());
  }

  @Test
  void compute_workflowWithoutStages_returnsEmpty() {
    FlowNode start = TestFlowNodes.block(execution, "1");

    assertTrue(compute(start).isEmpty());
  }

  // --- the check run of a stage ---

  @Test
  void compute_finishedStage_isNestedUnderItsRun() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckRun checkRun = checkRunNamed(compute(stage, end), "Build");

    assertNotNull(checkRun);
    // The parent/run ID nests the stage below the run in Gerrit.
    assertEquals(
        "{\"parent\":\"my-job#7\",\"run\":\"my-job#7#2\"}", checkRun.getExternalId());
    assertEquals(PS.changeId(), checkRun.getChange());
    assertEquals(PS.patchSetNumber(), checkRun.getPatchSet());
    assertEquals(parent.getAttempt(), checkRun.getAttempt());
    assertEquals(RunStatus.COMPLETED, checkRun.getStatus());
    assertEquals(parent.getCheckDescription(), checkRun.getCheckDescription());
    assertEquals(parent.getStatusLink(), checkRun.getStatusLink());
    assertEquals(parent.getStartedTimestamp(), checkRun.getStartedTimestamp());
    assertEquals(RUN_URL, checkRun.getCheckLink(), "Without a stage view, link to the run");
    assertTrue(checkRun.getActions().isEmpty(), "The run carries the actions");
  }

  @Test
  void compute_finishedStage_hasSuccessResult() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckRun checkRun = checkRunNamed(compute(stage, end), "Build");

    List<CheckResult> results = checkRun.getResults();
    assertEquals(1, results.size(), "A completed run without results counts as passing");
    assertEquals("my-job#7#2", results.get(0).getExternalId());
    assertEquals(Category.SUCCESS, results.get(0).getCategory());
    assertNull(results.get(0).getMessage());
  }

  @Test
  void compute_failedStage_mapsToError() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);
    end.addAction(new ErrorAction(new RuntimeException("compilation failed")));
    enclosing(end, stage);

    CheckRun checkRun = checkRunNamed(compute(stage, end), "Build");

    assertEquals(Category.ERROR, checkRun.getResults().get(0).getCategory());
    assertEquals("compilation failed", checkRun.getResults().get(0).getMessage());
  }

  @Test
  void compute_unstableStage_mapsToWarning() {
    BlockStartNode stage = stage("2", "Test");
    BlockEndNode<?> end = end("3", stage);
    end.addAction(new WarningAction(Result.UNSTABLE).withMessage("2 tests failed"));
    enclosing(end, stage);

    CheckRun checkRun = checkRunNamed(compute(stage, end), "Test");

    assertEquals(Category.WARNING, checkRun.getResults().get(0).getCategory());
    assertEquals("2 tests failed", checkRun.getResults().get(0).getMessage());
  }

  @Test
  void compute_errorWinsOverWarning() {
    BlockStartNode stage = stage("2", "Test");
    BlockEndNode<?> end = end("3", stage);
    end.addAction(new WarningAction(Result.UNSTABLE).withMessage("flaky"));
    end.addAction(new ErrorAction(new RuntimeException("failed")));
    enclosing(end, stage);

    CheckRun checkRun = checkRunNamed(compute(stage, end), "Test");

    assertEquals(Category.ERROR, checkRun.getResults().get(0).getCategory());
  }

  @Test
  void compute_runningStage_isRunningAndInformational() {
    BlockStartNode stage = stage("2", "Deploy");

    CheckRun checkRun = checkRunNamed(compute(true, stage), "Deploy");

    assertEquals(RunStatus.RUNNING, checkRun.getStatus());
    assertEquals(Category.INFO, checkRun.getResults().get(0).getCategory());
  }

  @Test
  void compute_stageWithoutEndInFinishedRun_isCompleted() {
    // A build that was killed can leave blocks without an end node behind.
    BlockStartNode stage = stage("2", "Deploy");

    CheckRun checkRun = checkRunNamed(compute(false, stage), "Deploy");

    assertEquals(RunStatus.COMPLETED, checkRun.getStatus());
  }

  // --- which nodes become stages ---

  @Test
  void compute_skippedStage_isOmitted() {
    BlockStartNode skipped = stage("2", "Deploy");
    TagsAction tags = new TagsAction();
    tags.addTag(STAGE_STATUS_TAG, "SKIPPED_FOR_CONDITIONAL");
    skipped.addAction(tags);
    BlockEndNode<?> end = end("3", skipped);

    assertTrue(compute(skipped, end).isEmpty());
  }

  @Test
  void compute_stageSkippedForFailure_isOmitted() {
    BlockStartNode skipped = stage("2", "Deploy");
    TagsAction tags = new TagsAction();
    tags.addTag(STAGE_STATUS_TAG, "SKIPPED_FOR_FAILURE");
    skipped.addAction(tags);
    BlockEndNode<?> end = end("3", skipped);

    assertTrue(compute(skipped, end).isEmpty());
  }

  @Test
  void compute_parallelContainer_isOmitted() {
    // The synthetic block wrapping the branches of a parallel step.
    BlockStartNode container = stage("2", "Parallel");
    BlockEndNode<?> end = end("3", container);

    assertTrue(compute(container, end).isEmpty());
  }

  @Test
  void compute_parallelBranch_isReported() {
    BlockStartNode branch = stage("4", "linux");
    branch.addAction(TestFlowNodes.threadName("linux"));
    BlockEndNode<?> end = end("5", branch);

    List<CheckRun> checkRuns = compute(branch, end);

    assertEquals(1, checkRuns.size());
    assertEquals("linux", checkRuns.get(0).getCheckName());
  }

  @Test
  void compute_blockWithoutLabel_isOmitted() {
    BlockStartNode block = TestFlowNodes.block(execution, "2");
    BlockEndNode<?> end = end("3", block);

    assertTrue(compute(block, end).isEmpty());
  }

  // --- nesting ---

  @Test
  void compute_errorIsAttributedToInnermostStage() {
    BlockStartNode outer = stage("2", "Test");
    BlockStartNode inner = stage("4", "Unit");
    BlockEndNode<?> outerEnd = end("3", outer);
    BlockEndNode<?> innerEnd = end("5", inner);
    innerEnd.addAction(new ErrorAction(new RuntimeException("assertion failed")));
    // The failing node is enclosed by the inner stage, which is enclosed by the outer one.
    enclosing(innerEnd, inner, outer);
    enclosing(outerEnd, outer);

    List<CheckRun> checkRuns = compute(outer, inner, outerEnd, innerEnd);

    assertEquals(2, checkRuns.size());
    assertEquals(Category.SUCCESS, categoryOf(checkRuns, "Test"));
    assertEquals(Category.ERROR, categoryOf(checkRuns, "Unit"));
  }

  @Test
  void compute_errorOutsideAnyStage_isNotReported() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);
    FlowNode postBuild = TestFlowNodes.block(execution, "9");
    postBuild.addAction(new ErrorAction(new RuntimeException("post build failed")));
    enclosing(end, stage);
    enclosing(postBuild);

    List<CheckRun> checkRuns = compute(stage, end, postBuild);

    assertEquals(1, checkRuns.size());
    assertEquals(Category.SUCCESS, checkRuns.get(0).getResults().get(0).getCategory());
  }

  // --- names ---

  @Test
  void compute_stagesOfTheSameName_areNumbered() {
    BlockStartNode first = stage("2", "Test");
    BlockStartNode second = stage("5", "Test");
    BlockEndNode<?> firstEnd = end("3", first);
    BlockEndNode<?> secondEnd = end("6", second);

    List<String> names = names(compute(first, second, firstEnd, secondEnd));

    assertEquals(List.of("Test", "Test (2)"), names);
  }

  @Test
  void compute_stageInParallelBranch_isQualifiedWithBranchName() {
    BlockStartNode branch = stage("4", "linux");
    branch.addAction(TestFlowNodes.threadName("linux"));
    BlockStartNode stage = stage("5", "Test");
    BlockEndNode<?> end = end("6", stage);
    enclosing(stage, branch);

    List<CheckRun> checkRuns = compute(branch, stage, end);

    assertNotNull(checkRunNamed(checkRuns, "linux / Test"));
  }

  @Test
  void compute_stagesOfDifferentBranches_areNotNumbered() {
    BlockStartNode linux = stage("2", "linux");
    linux.addAction(TestFlowNodes.threadName("linux"));
    BlockStartNode windows = stage("6", "windows");
    windows.addAction(TestFlowNodes.threadName("windows"));
    BlockStartNode linuxTest = stage("3", "Test");
    BlockStartNode windowsTest = stage("7", "Test");
    BlockEndNode<?> linuxEnd = end("4", linuxTest);
    BlockEndNode<?> windowsEnd = end("8", windowsTest);
    enclosing(linuxTest, linux);
    enclosing(windowsTest, windows);
    enclosing(linuxEnd, linuxTest, linux);
    enclosing(windowsEnd, windowsTest, windows);

    List<String> names = names(compute(linux, linuxTest, windows, windowsTest, linuxEnd, windowsEnd));

    assertTrue(names.contains("linux / Test"), names.toString());
    assertTrue(names.contains("windows / Test"), names.toString());
  }

  @Test
  void compute_stagesAreInCreationOrder() {
    // The walk returns them out of order, the node IDs order them.
    BlockStartNode later = stage("9", "Second");
    BlockStartNode earlier = stage("3", "First");
    BlockEndNode<?> laterEnd = end("10", later);
    BlockEndNode<?> earlierEnd = end("4", earlier);

    List<String> names = names(compute(later, earlier, laterEnd, earlierEnd));

    assertEquals(List.of("First", "Second"), names);
  }

  // --- links ---

  @Test
  void compute_resultLinksToBuildLog() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckRun checkRun = checkRunNamed(compute(stage, end), "Build");

    Link console = linkWithUrl(checkRun.getResults().get(0).getLinks(), RUN_URL + "console");
    assertNotNull(console, "Stage results link to the build log");
    assertEquals(LinkIcon.CODE, console.getIcon());
    assertTrue(console.isPrimary());
  }

  // --- helpers ---

  private List<CheckRun> compute(FlowNode... nodes) {
    return compute(false, nodes);
  }

  private List<CheckRun> compute(boolean building, FlowNode... nodes) {
    when(execution.getCurrentHeads()).thenReturn(Arrays.asList(nodes));
    return PipelineStageCheckRuns.compute(PS, execution, RUN_KEY, building, parent, RUN_URL);
  }

  private BlockStartNode stage(String id, String name) {
    BlockStartNode stage = TestFlowNodes.stage(execution, id, name);
    // Stages without a declared enclosing block are top level.
    when(execution.iterateEnclosingBlocks(stage)).thenReturn(Collections.emptyList());
    return stage;
  }

  private BlockEndNode<?> end(String id, BlockStartNode start) {
    return TestFlowNodes.end(execution, id, start);
  }

  /** Declares which blocks enclose the node, as the flow graph does. */
  private void enclosing(FlowNode node, BlockStartNode... blocks) {
    when(execution.iterateEnclosingBlocks(node)).thenReturn(Arrays.asList(blocks));
  }

  private static CheckRun checkRunNamed(List<CheckRun> checkRuns, String name) {
    return checkRuns.stream()
        .filter(checkRun -> name.equals(checkRun.getCheckName()))
        .findFirst()
        .orElse(null);
  }

  private static Category categoryOf(List<CheckRun> checkRuns, String name) {
    CheckRun checkRun = checkRunNamed(checkRuns, name);
    return checkRun == null ? null : checkRun.getResults().get(0).getCategory();
  }

  private static List<String> names(List<CheckRun> checkRuns) {
    return checkRuns.stream().map(CheckRun::getCheckName).collect(Collectors.toList());
  }

  private static Link linkWithUrl(List<Link> links, String url) {
    return links.stream()
        .filter(link -> url.equals(link.getUrl()))
        .findFirst()
        .orElse(null);
  }
}
