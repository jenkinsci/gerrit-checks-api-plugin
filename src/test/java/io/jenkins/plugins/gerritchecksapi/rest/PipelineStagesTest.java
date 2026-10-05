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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.Inheritable;
import io.jenkins.plugins.gerritchecksapi.PatchSetId;
import io.jenkins.plugins.gerritchecksapi.StageDepth;
import io.jenkins.plugins.gerritchecksapi.StageForm;
import io.jenkins.plugins.gerritchecksapi.StageReporting;
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

class PipelineStagesTest {

  private static final PatchSetId PS = PatchSetId.create(101, 4);
  private static final String RUN_KEY = "my-job#7";
  private static final String RUN_URL = "https://jenkins/job/my-job/7/";
  /** Without the stage view plugin, a stage links to its flow node. */
  private static final String STAGE_URL = RUN_URL + "execution/node/2/";
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
  void results_nonPipelineRun_returnsEmpty() {
    Run<?, ?> run = mock(Run.class);

    assertTrue(PipelineStages.results(run, RUN_URL, StageReporting.defaults()).isEmpty());
  }

  @Test
  void results_pipelineWithoutExecution_returnsEmpty() {
    WorkflowRun run = mock(WorkflowRun.class);
    when(run.getExecution()).thenReturn(null);

    assertTrue(PipelineStages.results(run, RUN_URL, StageReporting.defaults()).isEmpty());
  }

  @Test
  void results_workflowWithoutStages_returnsEmpty() {
    FlowNode start = TestFlowNodes.block(execution, "1");

    assertTrue(results(start).isEmpty());
  }

  // --- the result of a stage ---

  @Test
  void results_finishedStage_isKeyedByItsRun() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckResult result = resultNamed(results(stage, end), "Build");

    assertNotNull(result);
    // The key of the run and the node of the stage identify the stage.
    assertEquals("my-job#7#2", result.getExternalId());
    assertEquals("Build", result.getSummary(), "The name of the stage labels the result");
  }

  @Test
  void results_finishedStage_hasSuccessResult() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckResult result = resultNamed(results(stage, end), "Build");

    assertEquals(Category.SUCCESS, result.getCategory());
    assertNull(result.getMessage());
  }

  @Test
  void results_failedStage_mapsToError() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);
    end.addAction(new ErrorAction(new RuntimeException("compilation failed")));
    enclosing(end, stage);

    CheckResult result = resultNamed(results(stage, end), "Build");

    assertEquals(Category.ERROR, result.getCategory());
    assertEquals("compilation failed", result.getMessage());
  }

  @Test
  void results_unstableStage_mapsToWarning() {
    BlockStartNode stage = stage("2", "Test");
    BlockEndNode<?> end = end("3", stage);
    end.addAction(new WarningAction(Result.UNSTABLE).withMessage("2 tests failed"));
    enclosing(end, stage);

    CheckResult result = resultNamed(results(stage, end), "Test");

    assertEquals(Category.WARNING, result.getCategory());
    assertEquals("2 tests failed", result.getMessage());
  }

  @Test
  void results_errorWinsOverWarning() {
    BlockStartNode stage = stage("2", "Test");
    BlockEndNode<?> end = end("3", stage);
    end.addAction(new WarningAction(Result.UNSTABLE).withMessage("flaky"));
    end.addAction(new ErrorAction(new RuntimeException("failed")));
    enclosing(end, stage);

    CheckResult result = resultNamed(results(stage, end), "Test");

    assertEquals(Category.ERROR, result.getCategory());
  }

  @Test
  void results_runningStage_isInformational() {
    BlockStartNode stage = stage("2", "Deploy");

    CheckResult result = resultNamed(results(settingsOfEveryStage(), true, stage), "Deploy");

    assertEquals(Category.INFO, result.getCategory());
  }

  @Test
  void results_stageWithoutEndInFinishedRun_isInformational() {
    // A build that was killed can leave blocks without an end node behind.
    BlockStartNode stage = stage("2", "Deploy");

    CheckResult result = resultNamed(results(settingsOfEveryStage(), false, stage), "Deploy");

    assertEquals(Category.INFO, result.getCategory(), "The stage never finished");
  }

  // --- which nodes become stages ---

  @Test
  void results_skippedStage_isOmitted() {
    BlockStartNode skipped = stage("2", "Deploy");
    TagsAction tags = new TagsAction();
    tags.addTag(STAGE_STATUS_TAG, "SKIPPED_FOR_CONDITIONAL");
    skipped.addAction(tags);
    BlockEndNode<?> end = end("3", skipped);

    assertTrue(results(skipped, end).isEmpty());
  }

  @Test
  void results_stageSkippedForFailure_isOmitted() {
    BlockStartNode skipped = stage("2", "Deploy");
    TagsAction tags = new TagsAction();
    tags.addTag(STAGE_STATUS_TAG, "SKIPPED_FOR_FAILURE");
    skipped.addAction(tags);
    BlockEndNode<?> end = end("3", skipped);

    assertTrue(results(skipped, end).isEmpty());
  }

  @Test
  void results_parallelContainer_isOmitted() {
    // The synthetic block wrapping the branches of a parallel step.
    BlockStartNode container = stage("2", "Parallel");
    BlockEndNode<?> end = end("3", container);

    assertTrue(results(container, end).isEmpty());
  }

  @Test
  void results_parallelBranch_isReported() {
    BlockStartNode branch = stage("4", "linux");
    branch.addAction(TestFlowNodes.threadName("linux"));
    BlockEndNode<?> end = end("5", branch);

    List<CheckResult> results = results(branch, end);

    assertEquals(List.of("linux"), names(results));
  }

  @Test
  void results_blockWithoutLabel_isOmitted() {
    BlockStartNode block = TestFlowNodes.block(execution, "2");
    BlockEndNode<?> end = end("3", block);

    assertTrue(results(block, end).isEmpty());
  }

  // --- nesting ---

  @Test
  void results_errorIsAttributedToInnermostStage() {
    BlockStartNode outer = stage("2", "Test");
    BlockStartNode inner = stage("4", "Unit");
    BlockEndNode<?> outerEnd = end("3", outer);
    BlockEndNode<?> innerEnd = end("5", inner);
    innerEnd.addAction(new ErrorAction(new RuntimeException("assertion failed")));
    // The failing node is enclosed by the inner stage, which is enclosed by the outer one.
    enclosing(innerEnd, inner, outer);
    enclosing(outerEnd, outer);

    List<CheckResult> results = results(outer, inner, outerEnd, innerEnd);

    assertEquals(2, results.size());
    assertEquals(Category.SUCCESS, categoryNamed(results, "Test"));
    assertEquals(Category.ERROR, categoryNamed(results, "Unit"));
  }

  @Test
  void results_errorOutsideAnyStage_isNotReported() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);
    FlowNode postBuild = TestFlowNodes.block(execution, "9");
    postBuild.addAction(new ErrorAction(new RuntimeException("post build failed")));
    enclosing(end, stage);
    enclosing(postBuild);

    List<CheckResult> results = results(stage, end, postBuild);

    assertEquals(1, results.size());
    assertEquals(Category.SUCCESS, results.get(0).getCategory());
  }

  // --- names ---

  @Test
  void results_stagesOfTheSameName_areNumbered() {
    BlockStartNode first = stage("2", "Test");
    BlockStartNode second = stage("5", "Test");
    BlockEndNode<?> firstEnd = end("3", first);
    BlockEndNode<?> secondEnd = end("6", second);

    List<String> names = names(results(first, second, firstEnd, secondEnd));

    assertEquals(List.of("Test", "Test (2)"), names);
  }

  @Test
  void results_stageInParallelBranch_isQualifiedWithBranchName() {
    BlockStartNode branch = stage("4", "linux");
    branch.addAction(TestFlowNodes.threadName("linux"));
    BlockStartNode stage = stage("5", "Test");
    BlockEndNode<?> end = end("6", stage);
    enclosing(stage, branch);

    List<CheckResult> results = results(branch, stage, end);

    assertNotNull(resultNamed(results, "linux / Test"));
  }

  @Test
  void results_stagesOfDifferentBranches_areNotNumbered() {
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

    List<String> names = names(results(linux, linuxTest, windows, windowsTest, linuxEnd, windowsEnd));

    assertTrue(names.contains("linux / Test"), names.toString());
    assertTrue(names.contains("windows / Test"), names.toString());
  }

  @Test
  void results_stagesAreInCreationOrder() {
    // The walk returns them out of order, the node IDs order them.
    BlockStartNode later = stage("9", "Second");
    BlockStartNode earlier = stage("3", "First");
    BlockEndNode<?> laterEnd = end("10", later);
    BlockEndNode<?> earlierEnd = end("4", earlier);

    List<String> names = names(results(later, earlier, laterEnd, earlierEnd));

    assertEquals(List.of("First", "Second"), names);
  }

  // --- reporting settings ---

  @Test
  void results_stagesOfABuildingRun_areOmittedWhenTheyAreNotWantedWhileBuilding() {
    BlockStartNode stage = stage("2", "Build");

    List<CheckResult> results = results(settingsOfFinishedBuildsOnly(), true, stage);

    assertTrue(results.isEmpty(), "The stages are reported once the build has finished");
  }

  @Test
  void results_stagesOfAFinishedRun_areReportedWhenOnlyTheyAreWanted() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    List<CheckResult> results = results(settingsOfFinishedBuildsOnly(), false, stage, end);

    assertEquals(List.of("Build"), names(results));
  }

  @Test
  void results_topLevelOnly_omitsNestedStages() {
    BlockStartNode outer = stage("2", "Test");
    BlockStartNode inner = stage("4", "Unit");
    BlockEndNode<?> outerEnd = end("3", outer);
    enclosing(inner, outer);
    enclosing(outerEnd, outer);
    StageReporting reporting = settingsOfDepth(StageDepth.TOP_LEVEL, 0);

    List<String> names = names(results(reporting, false, outer, inner, outerEnd));

    assertEquals(List.of("Test"), names);
  }

  @Test
  void results_maxDepth_omitsStagesBelowIt() {
    BlockStartNode first = stage("2", "First");
    BlockStartNode second = stage("4", "Second");
    BlockStartNode third = stage("6", "Third");
    BlockEndNode<?> end = end("3", first);
    enclosing(second, first);
    enclosing(third, second, first);
    enclosing(end, first);
    StageReporting reporting = settingsOfDepth(StageDepth.MAX_DEPTH, 2);

    List<String> names = names(results(reporting, false, first, second, third, end));

    assertEquals(List.of("First", "Second"), names);
  }

  @Test
  void results_omittedNestedStage_reportsItsErrorOnTheParent() {
    BlockStartNode outer = stage("2", "Test");
    BlockStartNode inner = stage("4", "Unit");
    BlockEndNode<?> innerEnd = end("5", inner);
    innerEnd.addAction(new ErrorAction(new RuntimeException("assertion failed")));
    enclosing(innerEnd, inner, outer);
    enclosing(inner, outer);
    StageReporting reporting = settingsOfDepth(StageDepth.TOP_LEVEL, 0);

    CheckResult result = resultNamed(results(reporting, false, outer, inner, innerEnd), "Test");

    assertEquals(Category.ERROR, result.getCategory());
  }

  @Test
  void results_skipDeclarativeStages_omitsThem() {
    BlockStartNode checkout = stage("2", "Declarative: Checkout SCM");
    BlockStartNode build = stage("4", "Build");
    BlockEndNode<?> checkoutEnd = end("3", checkout);
    BlockEndNode<?> buildEnd = end("5", build);
    StageReporting reporting = settingsWithoutDeclarativeStages();

    List<String> names = names(results(reporting, false, checkout, build, checkoutEnd, buildEnd));

    assertEquals(List.of("Build"), names);
  }

  // --- links ---

  @Test
  void results_resultLinksToTheStage() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckResult result = resultNamed(results(stage, end), "Build");

    List<Link> links = result.getLinks();
    Link stageLink = linkWithUrl(links, STAGE_URL);
    assertNotNull(stageLink, "The stage is linked first");
    assertEquals(LinkIcon.CODE, stageLink.getIcon());
    assertTrue(stageLink.isPrimary());

    Link console = linkWithUrl(links, RUN_URL + "console");
    assertNotNull(console, "The build log is still reachable");
    assertFalse(console.isPrimary());
  }

  // --- the check runs of the stages ---

  @Test
  void checkRuns_finishedStage_isNestedUnderItsRun() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckRun checkRun = checkRunNamed(checkRuns(stage, end), "Build");

    assertNotNull(checkRun);
    // The parent/run ID nests the stage below the run in Gerrit.
    assertEquals("{\"parent\":\"my-job#7\",\"run\":\"my-job#7#2\"}", checkRun.getExternalId());
    assertEquals(PS.changeId(), checkRun.getChange());
    assertEquals(PS.patchSetNumber(), checkRun.getPatchSet());
    assertEquals(parent.getAttempt(), checkRun.getAttempt());
    assertEquals(RunStatus.COMPLETED, checkRun.getStatus());
    assertEquals(parent.getCheckDescription(), checkRun.getCheckDescription());
    assertEquals(STAGE_URL, checkRun.getStatusLink(), "The stage, not the run");
    assertEquals(parent.getStartedTimestamp(), checkRun.getStartedTimestamp());
    assertEquals(STAGE_URL, checkRun.getCheckLink());
    assertTrue(checkRun.getActions().isEmpty(), "The run carries the actions");
  }

  @Test
  void checkRuns_finishedStage_carriesItsOwnResult() {
    BlockStartNode stage = stage("2", "Build");
    BlockEndNode<?> end = end("3", stage);

    CheckRun checkRun = checkRunNamed(checkRuns(stage, end), "Build");

    List<CheckResult> results = checkRun.getResults();
    assertEquals(1, results.size(), "A completed run without results counts as passing");
    assertEquals("my-job#7#2", results.get(0).getExternalId());
    assertEquals("Build", results.get(0).getSummary());
    assertEquals(Category.SUCCESS, results.get(0).getCategory());
    assertNotNull(linkWithUrl(results.get(0).getLinks(), STAGE_URL));
  }

  @Test
  void checkRuns_runningStage_isRunning() {
    BlockStartNode stage = stage("2", "Deploy");

    CheckRun checkRun = checkRunNamed(checkRuns(settingsOfEveryStage(), true, stage), "Deploy");

    assertEquals(RunStatus.RUNNING, checkRun.getStatus());
    assertEquals(Category.INFO, checkRun.getResults().get(0).getCategory());
  }

  @Test
  void checkRuns_stageWithoutEndInFinishedRun_isCompleted() {
    // A build that was killed can leave blocks without an end node behind.
    BlockStartNode stage = stage("2", "Deploy");

    CheckRun checkRun = checkRunNamed(checkRuns(settingsOfEveryStage(), false, stage), "Deploy");

    assertEquals(RunStatus.COMPLETED, checkRun.getStatus());
  }

  @Test
  void checkRuns_stagesOfABuildingRun_areOmittedWhenTheyAreNotWantedWhileBuilding() {
    BlockStartNode stage = stage("2", "Build");

    List<CheckRun> checkRuns = checkRuns(settingsOfFinishedBuildsOnly(), true, stage);

    assertTrue(checkRuns.isEmpty(), "The stages are reported once the build has finished");
  }

  // --- helpers ---

  /** The settings of a job that reports every stage, also while a build is running. */
  private static StageReporting settingsOfEveryStage() {
    return StageReporting.defaults();
  }

  /** The settings of a job that reports the stages as check runs of their own. */
  private static StageReporting settingsOfCheckRuns() {
    return settings(StageDepth.ALL, 0, Inheritable.DISABLED, StageForm.CHECK_RUNS);
  }

  /** The settings of a job that reports every stage up to the given depth. */
  private static StageReporting settingsOfDepth(StageDepth depth, int maxDepth) {
    return settings(depth, maxDepth, Inheritable.DISABLED, StageForm.RESULTS);
  }

  /** The settings of a job that reports every stage but the declarative ones. */
  private static StageReporting settingsWithoutDeclarativeStages() {
    return settings(StageDepth.ALL, 0, Inheritable.ENABLED, StageForm.RESULTS);
  }

  /** The settings of a job that reports the stages of a build once it has finished. */
  private static StageReporting settingsOfFinishedBuildsOnly() {
    return new StageReporting(
        Inheritable.ENABLED,
        Inheritable.DISABLED,
        StageForm.RESULTS,
        StageDepth.ALL,
        0,
        Inheritable.DISABLED);
  }

  private static StageReporting settings(
      StageDepth depth, int maxDepth, Inheritable skipDeclarative, StageForm form) {
    return new StageReporting(
        Inheritable.ENABLED, Inheritable.ENABLED, form, depth, maxDepth, skipDeclarative);
  }

  private List<CheckResult> results(FlowNode... nodes) {
    return results(settingsOfEveryStage(), false, nodes);
  }

  private List<CheckResult> results(StageReporting reporting, boolean building, FlowNode... nodes) {
    walk(nodes);
    return PipelineStages.results(execution, RUN_KEY, building, RUN_URL, reporting);
  }

  private List<CheckRun> checkRuns(FlowNode... nodes) {
    return checkRuns(settingsOfCheckRuns(), false, nodes);
  }

  private List<CheckRun> checkRuns(StageReporting reporting, boolean building, FlowNode... nodes) {
    walk(nodes);
    return PipelineStages.checkRuns(PS, execution, RUN_KEY, building, parent, RUN_URL, reporting);
  }

  /** The flow graph of the given nodes, as the walk of the execution returns them. */
  private void walk(FlowNode... nodes) {
    when(execution.getCurrentHeads()).thenReturn(Arrays.asList(nodes));
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

  private static CheckResult resultNamed(List<CheckResult> results, String summary) {
    return results.stream()
        .filter(result -> summary.equals(result.getSummary()))
        .findFirst()
        .orElse(null);
  }

  private static Category categoryNamed(List<CheckResult> results, String summary) {
    CheckResult result = resultNamed(results, summary);
    return result == null ? null : result.getCategory();
  }

  private static CheckRun checkRunNamed(List<CheckRun> checkRuns, String name) {
    return checkRuns.stream()
        .filter(checkRun -> name.equals(checkRun.getCheckName()))
        .findFirst()
        .orElse(null);
  }

  private static List<String> names(List<CheckResult> results) {
    return results.stream().map(CheckResult::getSummary).collect(Collectors.toList());
  }

  private static Link linkWithUrl(List<Link> links, String url) {
    return links.stream()
        .filter(link -> url.equals(link.getUrl()))
        .findFirst()
        .orElse(null);
  }
}
