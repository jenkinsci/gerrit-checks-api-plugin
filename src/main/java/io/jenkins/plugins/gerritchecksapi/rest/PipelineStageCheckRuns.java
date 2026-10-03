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
 * Computes one {@link CheckRun} per stage of a Pipeline build.
 *
 * <p>The stages of a build are reported as check runs of their own, nested below the check run of
 * the build through the parent/run external ID that downstream builds already use. That way Gerrit
 * shows them as leaves of the build in its list of check runs, and a single stage failing is
 * visible without opening Jenkins. Without this, a Pipeline build is reported as a single run,
 * which gives no indication of which stage failed, was unstable or was skipped.
 *
 * <p>Every class referencing the Pipeline API is confined to this class. Callers have to check that
 * the {@code workflow-job} plugin is installed before calling {@link #compute}, so that the plugin
 * keeps working on instances without Pipeline.
 */
public final class PipelineStageCheckRuns {
  /** Plugin providing the stage view that the stage links point at. */
  static final String STAGE_VIEW_PLUGIN = "pipeline-graph-view";

  /** Tag holding the status of a stage. */
  private static final String STAGE_STATUS_TAG = "STAGE_STATUS";

  /** Prefix of the status tag values marking stages that were skipped and never ran. */
  private static final String SKIPPED_TAG_PREFIX = "SKIPPED_FOR";

  /** Label of the synthetic block wrapping the branches of a parallel step. */
  private static final String PARALLEL_LABEL = "Parallel";

  private PipelineStageCheckRuns() {}

  /**
   * Returns one check run per stage of the given run.
   *
   * @param parent the check run of the run itself, which the stages are nested below and whose
   *     attempt, description and timestamps they share
   * @return the check runs of the stages, or an empty list if the run is not a Pipeline build or
   *     does not expose any stage
   */
  public static List<CheckRun> compute(PatchSetId ps, Run<?, ?> run, CheckRun parent, String runUrl) {
    if (!(run instanceof WorkflowRun)) {
      return List.of();
    }
    return compute(
        ps,
        ((WorkflowRun) run).getExecution(),
        run.getExternalizableId(),
        run.isBuilding(),
        parent,
        runUrl);
  }

  static List<CheckRun> compute(
      PatchSetId ps,
      FlowExecution execution,
      String runKey,
      boolean building,
      CheckRun parent,
      String runUrl) {
    if (execution == null) {
      return List.of();
    }
    List<FlowNode> nodes = walk(execution);
    List<Stage> stages = findStages(nodes);
    if (stages.isEmpty()) {
      return List.of();
    }
    collectFailures(nodes, stages);
    Set<String> finishedStages = findFinishedStages(nodes);
    nameStages(stages);

    List<CheckRun> checkRuns = new ArrayList<>();
    for (Stage stage : stages) {
      checkRuns.add(
          stage.toCheckRun(
              ps, parent, runKey, runUrl, finishedStages.contains(stage.id()), building));
    }
    return checkRuns;
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
    Map<String, Stage> stagesById = new HashMap<>();
    for (Stage stage : stages) {
      stagesById.put(stage.id(), stage);
    }
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
   * or once per branch of a parallel step, but Gerrit identifies a check run by its name: two runs
   * with the same name, change, patchset and attempt are the same run to Gerrit. Stages sharing a
   * name are therefore numbered.
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

    /** Node IDs are handed out by a counter, so they order the stages by creation. */
    private long creationOrder() {
      try {
        return Long.parseLong(node.getId());
      } catch (NumberFormatException e) {
        return Long.MAX_VALUE;
      }
    }

    private Category category(boolean finished) {
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

    private CheckRun toCheckRun(
        PatchSetId ps,
        CheckRun parent,
        String runKey,
        String runUrl,
        boolean finished,
        boolean building) {
      String stageKey = String.format("%s#%s", runKey, id());

      CheckRun checkRun = new CheckRun();
      checkRun.setChange(ps.changeId());
      checkRun.setPatchSet(ps.patchSetNumber());
      checkRun.setAttempt(parent.getAttempt());
      checkRun.setExternalId(AbstractCheckRunFactory.childId(runKey, stageKey));
      checkRun.setCheckName(checkName);
      checkRun.setCheckDescription(parent.getCheckDescription());
      checkRun.setCheckLink(checkLink(runUrl, id()));
      checkRun.setStatus(!finished && building ? RunStatus.RUNNING : RunStatus.COMPLETED);
      checkRun.setStatusDescription(parent.getStatusDescription());
      checkRun.setStatusLink(parent.getStatusLink());
      checkRun.setLabelName(parent.getLabelName());
      // The check run of the build carries the actions, e.g. the one to rerun it.
      checkRun.setActions(List.of());
      checkRun.setScheduledTimestamp(parent.getScheduledTimestamp());
      checkRun.setStartedTimestamp(parent.getStartedTimestamp());
      checkRun.setFinishedTimestamp(parent.getFinishedTimestamp());
      checkRun.setResults(
          List.of(computeResult(stageKey, runUrl, category(finished), message())));
      return checkRun;
    }
  }

  /**
   * A check run needs a result: Gerrit treats a completed run without results as passing, so a
   * failed stage would be shown as successful without one.
   */
  private static CheckResult computeResult(
      String resultId, String runUrl, Category category, String message) {
    CheckResult result = new CheckResult();
    result.setExternalId(resultId);
    result.setCategory(category);
    result.setMessage(message);
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

  /** Links to the stage in the Pipeline stage view, if that view is installed. */
  private static String checkLink(String runUrl, String nodeId) {
    return isPluginInstalled(STAGE_VIEW_PLUGIN)
        ? String.format("%sstages/?selected-node=%s", runUrl, nodeId)
        : runUrl;
  }
}
