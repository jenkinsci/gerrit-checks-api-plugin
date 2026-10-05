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

import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.PatchSetId;
import io.jenkins.plugins.gerritchecksapi.StageForm;
import io.jenkins.plugins.gerritchecksapi.StageReporting;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun.RunStatus;
import io.jenkins.plugins.gerritchecksapi.rest.Link.LinkIcon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Computes what to report for each stage of a Pipeline build, either as a {@link CheckResult} of
 * the run of the build or as a {@link CheckRun} of its own.
 *
 * <p>A stage is reported as a result of the check run of the build it belongs to. Gerrit lists
 * check runs as a flat list, so one run per stage would push a row per stage next to the build;
 * as a result the stage stays inside the run it belongs to. Gerrit derives the badge of a run from
 * the most severe of its results, so a single stage failing still turns the build red without
 * opening Jenkins. Where a stage deserves a row and a status of its own, the check run form is
 * used instead, see {@link StageForm}.
 *
 * <p>The key of a stage is the ID of the run followed by '#' and the ID of the stage's flow node,
 * e.g. 'my-pipeline#7#12'. It identifies the stage in the results of its run, and nests the check
 * run of a stage below the check run of the run.
 *
 * <p>Every class referencing the Pipeline API is confined to this class. Callers have to check that
 * the {@code workflow-job} plugin is installed before calling {@link #results} or {@link
 * #checkRuns}, so that the plugin keeps working on instances without Pipeline.
 */
public final class PipelineStages {
  /** Plugin providing the stage view that the stage links point at. */
  static final String STAGE_VIEW_PLUGIN = "pipeline-graph-view";

  /** Tag holding the status of a stage. */
  private static final String STAGE_STATUS_TAG = "STAGE_STATUS";

  /** Prefix of the status tag values marking stages that were skipped and never ran. */
  private static final String SKIPPED_TAG_PREFIX = "SKIPPED_FOR";

  /** Label of the synthetic block wrapping the branches of a parallel step. */
  private static final String PARALLEL_LABEL = "Parallel";

  /** Prefix of the names of the stages Jenkins itself adds to a declarative pipeline. */
  private static final String DECLARATIVE_PREFIX = "Declarative: ";

  private PipelineStages() {}

  /**
   * Returns one result per stage of the given run, which are to be added to the results of the run
   * itself.
   *
   * @return the results of the stages, or an empty list if the run is not a Pipeline build, does
   *     not expose any stage, or is still building and not allowed to report its stages yet
   */
  public static List<CheckResult> results(Run<?, ?> run, String runUrl, StageReporting reporting) {
    if (!(run instanceof WorkflowRun)) {
      return List.of();
    }
    return results(
        ((WorkflowRun) run).getExecution(),
        run.getExternalizableId(),
        run.isBuilding(),
        runUrl,
        reporting);
  }

  static List<CheckResult> results(
      FlowExecution execution,
      String runKey,
      boolean building,
      String runUrl,
      StageReporting reporting) {
    List<CheckResult> results = new ArrayList<>();
    for (Stage stage : reportedStages(execution, building, reporting)) {
      results.add(stage.toResult(runKey, runUrl));
    }
    return results;
  }

  /**
   * Returns one check run per stage of the given run, which are to be added next to the check run
   * of the run itself.
   *
   * @param parent the check run of the run itself, which the stages are nested below and whose
   *     attempt, description and timestamps they share
   * @return the check runs of the stages, or an empty list if the run is not a Pipeline build, does
   *     not expose any stage, or is still building and not allowed to report its stages yet
   */
  public static List<CheckRun> checkRuns(
      PatchSetId ps, Run<?, ?> run, CheckRun parent, String runUrl, StageReporting reporting) {
    if (!(run instanceof WorkflowRun)) {
      return List.of();
    }
    return checkRuns(
        ps,
        ((WorkflowRun) run).getExecution(),
        run.getExternalizableId(),
        run.isBuilding(),
        parent,
        runUrl,
        reporting);
  }

  static List<CheckRun> checkRuns(
      PatchSetId ps,
      FlowExecution execution,
      String runKey,
      boolean building,
      CheckRun parent,
      String runUrl,
      StageReporting reporting) {
    List<CheckRun> checkRuns = new ArrayList<>();
    for (Stage stage : reportedStages(execution, building, reporting)) {
      checkRuns.add(stage.toCheckRun(ps, parent, runKey, runUrl, building));
    }
    return checkRuns;
  }

  /**
   * The stages of a run that are reported, in the order in which they were created, each carrying
   * whether it has finished.
   *
   * @return the stages, or an empty list if the run has no execution, exposes no stage that the
   *     settings report, or is still building and not allowed to report its stages while it runs
   */
  private static List<Stage> reportedStages(
      FlowExecution execution, boolean building, StageReporting reporting) {
    if (execution == null) {
      return List.of();
    }
    if (building && !reporting.isReportStagesWhileBuildingEnabled()) {
      // A build that is still building reports its stages only when they are wanted while
      // it runs: they change on every poll, so leaving them out until the build has
      // finished keeps what the run reports quiet in the meantime.
      return List.of();
    }
    List<FlowNode> nodes = walk(execution);
    List<Stage> stages = findStages(nodes);
    if (stages.isEmpty()) {
      return List.of();
    }
    stages = applyReportingSettings(stages, reporting);
    if (stages.isEmpty()) {
      return List.of();
    }
    // Errors of stages that are not reported are attributed to the stage enclosing
    // them, if there is one.
    collectFailures(nodes, stages);
    Set<String> finishedStages = findFinishedStages(nodes);
    for (Stage stage : stages) {
      stage.finished = finishedStages.contains(stage.id());
    }
    nameStages(stages);
    return stages;
  }

  private static List<Stage> applyReportingSettings(List<Stage> stages, StageReporting reporting) {
    Map<String, Stage> allStages = indexById(stages);
    int maxDepth = reporting.maxReportedDepth();
    boolean skipDeclarative = reporting.isSkipDeclarativeStagesEnabled();
    if (maxDepth <= 0 && !skipDeclarative) {
      return stages;
    }
    List<Stage> reported = new ArrayList<>();
    for (Stage stage : stages) {
      if (skipDeclarative && stage.isDeclarative()) {
        continue;
      }
      if (maxDepth > 0 && stage.depth(allStages) > maxDepth) {
        continue;
      }
      reported.add(stage);
    }
    return reported;
  }

  private static Map<String, Stage> indexById(List<Stage> stages) {
    Map<String, Stage> stagesById = new HashMap<>();
    for (Stage stage : stages) {
      stagesById.put(stage.id(), stage);
    }
    return stagesById;
  }

  private static List<FlowNode> walk(FlowExecution execution) {
    List<FlowNode> nodes = new ArrayList<>();
    for (FlowNode node : new FlowGraphWalker(execution)) {
      nodes.add(node);
    }
    return nodes;
  }

  /**
   * Collects the stages in the order in which they were created. Node IDs are handed out by a
   * counter that only ever increases, so this order does not change while a build is running and
   * the stages keep their position in the Gerrit UI.
   */
  private static List<Stage> findStages(List<FlowNode> nodes) {
    List<Stage> stages = new ArrayList<>();
    for (FlowNode node : nodes) {
      if (isStage(node)) {
        stages.add(new Stage((BlockStartNode) node));
      }
    }
    stages.sort(Comparator.comparingLong(Stage::creationOrder).thenComparing(Stage::id));
    return stages;
  }

  private static boolean isStage(FlowNode node) {
    if (!(node instanceof BlockStartNode)) {
      return false;
    }
    LabelAction label = node.getPersistentAction(LabelAction.class);
    if (label == null) {
      return false;
    }
    if (isSkipped(node)) {
      // A skipped stage never ran, so it has no result of its own.
      return false;
    }
    // A parallel step wraps its branches in a synthetic block that carries the label
    // "Parallel" but no thread name. It has no result of its own, the branches have.
    return !(PARALLEL_LABEL.equals(label.getDisplayName())
        && node.getPersistentAction(ThreadNameAction.class) == null);
  }

  private static boolean isSkipped(FlowNode node) {
    String status = TagsAction.getTagValue(node, STAGE_STATUS_TAG);
    return status != null && status.startsWith(SKIPPED_TAG_PREFIX);
  }

  private static Set<String> findFinishedStages(List<FlowNode> nodes) {
    Set<String> finished = new HashSet<>();
    for (FlowNode node : nodes) {
      if (node instanceof BlockEndNode) {
        BlockStartNode start = ((BlockEndNode<?>) node).getStartNode();
        if (start != null) {
          finished.add(start.getId());
        }
      }
    }
    return finished;
  }

  private static void collectFailures(List<FlowNode> nodes, List<Stage> stages) {
    Map<String, Stage> stagesById = indexById(stages);
    for (FlowNode node : nodes) {
      ErrorAction error = node.getPersistentAction(ErrorAction.class);
      WarningAction warning = node.getPersistentAction(WarningAction.class);
      if (error == null && warning == null) {
        continue;
      }
      Stage stage = enclosingStage(node, stagesById);
      if (stage == null) {
        continue;
      }
      if (error != null) {
        stage.error = error;
      } else if (stage.warning == null) {
        stage.warning = warning;
      }
    }
  }

  /**
   * Finds the innermost stage containing the node. Errors and warnings are attached to the node
   * where they occurred, which can be anywhere inside the stage, and not to the stage itself.
   */
  private static Stage enclosingStage(FlowNode node, Map<String, Stage> stages) {
    Stage stage = stages.get(node.getId());
    if (stage != null) {
      return stage;
    }
    // The enclosing blocks are returned innermost first.
    for (BlockStartNode block : node.iterateEnclosingBlocks()) {
      stage = stages.get(block.getId());
      if (stage != null) {
        return stage;
      }
    }
    return null;
  }

  /**
   * Gives the stages their names. Stages can have the same name, e.g. when a stage runs in a loop
   * or once per branch of a parallel step. Gerrit shows the name as the summary of the result, so
   * stages sharing a name would be indistinguishable, and they are numbered.
   *
   * <p>Only the second and later occurrences are numbered, in creation order. The name of a stage
   * therefore never changes once it has been reported, not even when further stages are added
   * while the build is running.
   */
  private static void nameStages(List<Stage> stages) {
    Map<String, Integer> occurrences = new HashMap<>();
    for (Stage stage : stages) {
      int occurrence = occurrences.merge(stage.baseName, 1, Integer::sum);
      stage.checkName =
          occurrence == 1 ? stage.baseName : String.format("%s (%d)", stage.baseName, occurrence);
    }
  }

  private static boolean isPluginInstalled(String pluginName) {
    // Not Jenkins.get(), which throws when Jenkins is not running.
    Jenkins jenkins = Jenkins.getInstanceOrNull();
    return jenkins != null && jenkins.getPlugin(pluginName) != null;
  }

  private static final class Stage {
    private final BlockStartNode node;
    private final String baseName;
    private String checkName;
    private boolean finished;
    private ErrorAction error;
    private WarningAction warning;

    private Stage(BlockStartNode node) {
      this.node = node;
      this.baseName = qualify(nameOf(node), branchOf(node));
    }

    /** Names the stage after the parallel branch it runs in, if it runs in one. */
    private static String qualify(String name, String branch) {
      return branch == null || name.startsWith(branch)
          ? name
          : String.format("%s / %s", branch, name);
    }

    private static String nameOf(FlowNode node) {
      ThreadNameAction threadName = node.getPersistentAction(ThreadNameAction.class);
      if (threadName != null) {
        return threadName.getThreadName();
      }
      LabelAction label = node.getPersistentAction(LabelAction.class);
      return label == null ? node.getDisplayName() : label.getDisplayName();
    }

    /**
     * Finds the parallel branch the stage runs in, if any. Stages of the same name in different
     * branches of a parallel step are told apart by the name of their branch.
     */
    private static String branchOf(BlockStartNode node) {
      for (BlockStartNode block : node.iterateEnclosingBlocks()) {
        ThreadNameAction threadName = block.getPersistentAction(ThreadNameAction.class);
        if (threadName != null) {
          return threadName.getThreadName();
        }
      }
      return null;
    }

    private String id() {
      return node.getId();
    }

    /** 1 for a stage that is not nested inside another stage. */
    private int depth(Map<String, Stage> allStages) {
      int depth = 1;
      for (BlockStartNode block : node.iterateEnclosingBlocks()) {
        if (allStages.containsKey(block.getId())) {
          depth++;
        }
      }
      return depth;
    }

    /** The stages that Jenkins itself adds to a declarative pipeline. */
    private boolean isDeclarative() {
      return nameOf(node).startsWith(DECLARATIVE_PREFIX);
    }

    /** Node IDs are handed out by a counter, so they order the stages by creation. */
    private long creationOrder() {
      try {
        return Long.parseLong(node.getId());
      } catch (NumberFormatException e) {
        return Long.MAX_VALUE;
      }
    }

    private Category category() {
      if (error != null) {
        return Category.ERROR;
      }
      if (warning != null) {
        return Category.WARNING;
      }
      // A stage whose block has not ended yet is still running.
      return finished ? Category.SUCCESS : Category.INFO;
    }

    private String message() {
      if (error != null && error.getError() != null) {
        return error.getError().getMessage();
      }
      return warning == null ? null : warning.getMessage();
    }

    private CheckResult toResult(String runKey, String runUrl) {
      return computeResult(stageKey(runKey), checkName, runUrl, id(), category(), message());
    }

    private CheckRun toCheckRun(
        PatchSetId ps, CheckRun parent, String runKey, String runUrl, boolean building) {
      CheckRun checkRun = new CheckRun();
      checkRun.setChange(ps.changeId());
      checkRun.setPatchSet(ps.patchSetNumber());
      checkRun.setAttempt(parent.getAttempt());
      checkRun.setExternalId(AbstractCheckRunFactory.childId(runKey, stageKey(runKey)));
      checkRun.setCheckName(checkName);
      checkRun.setCheckDescription(parent.getCheckDescription());
      checkRun.setCheckLink(stageUrl(runUrl, id()));
      checkRun.setStatus(!finished && building ? RunStatus.RUNNING : RunStatus.COMPLETED);
      checkRun.setStatusDescription(parent.getStatusDescription());
      // Not the status link of the run: it has to lead to the stage itself.
      checkRun.setStatusLink(stageUrl(runUrl, id()));
      checkRun.setLabelName(parent.getLabelName());
      // The check run of the build carries the actions, e.g. the one to rerun it.
      checkRun.setActions(List.of());
      checkRun.setScheduledTimestamp(parent.getScheduledTimestamp());
      checkRun.setStartedTimestamp(parent.getStartedTimestamp());
      checkRun.setFinishedTimestamp(parent.getFinishedTimestamp());
      checkRun.setResults(
          List.of(computeResult(stageKey(runKey), checkName, runUrl, id(), category(), message())));
      return checkRun;
    }

    /**
     * The key of the stage, which is the key of its run followed by '#' and the ID of the stage's
     * flow node. It identifies the stage in the results of the run, and nests its check run below
     * the check run of the run.
     */
    private String stageKey(String runKey) {
      return String.format("%s#%s", runKey, id());
    }
  }

  /**
   * The result of one stage. Gerrit shows the summary on the row of the result and the message in
   * its details, so the summary carries the name of the stage and the message the error or warning
   * it produced, if any. A check run of a stage carries the same result: a completed run without
   * results would count as passing.
   *
   * @param resultId the {@link Stage#stageKey(String) key of the stage}, which has to be unique
   *     within the results of the run so that the stage can be told apart from the others
   */
  private static CheckResult computeResult(
      String resultId,
      String summary,
      String runUrl,
      String nodeId,
      Category category,
      String message) {
    CheckResult result = new CheckResult();
    result.setExternalId(resultId);
    result.setSummary(summary);
    result.setCategory(category);
    result.setMessage(message);
    result.setLinks(computeResultLinks(runUrl, nodeId));
    return result;
  }

  private static List<Link> computeResultLinks(String runUrl, String nodeId) {
    List<Link> links = new ArrayList<>();
    Link stageLink = new Link();
    stageLink.setUrl(stageUrl(runUrl, nodeId));
    stageLink.setTooltip("Stage log.");
    stageLink.setIcon(LinkIcon.CODE);
    stageLink.setPrimary(true);
    links.add(stageLink);

    Link consoleLogLink = new Link();
    consoleLogLink.setUrl(String.format("%sconsole", runUrl));
    consoleLogLink.setTooltip("Build log.");
    consoleLogLink.setIcon(LinkIcon.CODE);
    consoleLogLink.setPrimary(false);
    links.add(consoleLogLink);
    return links;
  }

  /**
   * Links to the stage itself rather than to the run it belongs to. The stage view shows the stage
   * in the context of the pipeline, so it is preferred; it is only available when the plugin
   * providing it is installed. The flow node of the stage is part of the Pipeline API itself and
   * shows the log of the stage.
   */
  private static String stageUrl(String runUrl, String nodeId) {
    return isPluginInstalled(STAGE_VIEW_PLUGIN)
        ? String.format("%sstages/?selected-node=%s", runUrl, nodeId)
        : String.format("%sexecution/node/%s/", runUrl, nodeId);
  }
}
