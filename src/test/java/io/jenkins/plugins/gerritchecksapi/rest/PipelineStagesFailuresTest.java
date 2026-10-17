package io.jenkins.plugins.gerritchecksapi.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.gerritchecksapi.StageReporting;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The failures the stages report, against the flow graph of a real Pipeline.
 *
 * <p>An error only fails the stage it ended: a step whose failure the pipeline caught and carried
 * on from is a failure of that step alone, and Jenkins reports the stage as successful. The
 * reviewer watched a stage of a running build that showed an error for exactly that reason — a
 * probe wrapped in a try/catch — while Jenkins showed the branch green.
 */
@WithJenkins
class PipelineStagesFailuresTest {

  private static final String RUN_URL = "https://jenkins/job/my-job/7/";
  private static final AtomicInteger JOB_COUNTER = new AtomicInteger();

  @Test
  void caughtFailure_doesNotFailTheStage(JenkinsRule j) throws Exception {
    List<CheckResult> results =
        resultsOf(
            j,
            "stage('Verify') {\n"
                + "  try {\n"
                + "    error('boom')\n"
                + "  } catch (e) {\n"
                + "    echo 'handled'\n"
                + "  }\n"
                + "}\n"
                + "stage('After') {\n"
                + "  echo 'ok'\n"
                + "}\n");

    assertEquals(Category.SUCCESS, resultNamed(results, "Verify").getCategory());
    assertEquals(Category.SUCCESS, resultNamed(results, "After").getCategory());
  }

  @Test
  void uncaughtFailure_failsTheStage(JenkinsRule j) throws Exception {
    List<CheckResult> results =
        resultsOf(j, "stage('Verify') {\n" + "  error('boom')\n" + "}\n");

    CheckResult verify = resultNamed(results, "Verify");
    assertEquals(Category.ERROR, verify.getCategory());
    assertEquals("boom", verify.getMessage());
  }

  @Test
  void caughtFailureInAParallelBranch_doesNotFailTheBranch(JenkinsRule j) throws Exception {
    List<CheckResult> results =
        resultsOf(
            j,
            "parallel(\n"
                + "  'Android': {\n"
                + "    try {\n"
                + "      error('boom')\n"
                + "    } catch (e) {\n"
                + "      echo 'handled'\n"
                + "    }\n"
                + "  },\n"
                + "  'iOS': {\n"
                + "    echo 'ok'\n"
                + "  }\n"
                + ")\n");

    assertEquals(Category.SUCCESS, resultNamed(results, "Android").getCategory());
    assertEquals(Category.SUCCESS, resultNamed(results, "iOS").getCategory());
  }

  @Test
  void uncaughtFailureInAParallelBranch_failsThatBranchOnly(JenkinsRule j) throws Exception {
    List<CheckResult> results =
        resultsOf(
            j,
            "parallel(\n"
                + "  'Android': {\n"
                + "    error('boom')\n"
                + "  },\n"
                + "  'iOS': {\n"
                + "    echo 'ok'\n"
                + "  }\n"
                + ")\n");

    CheckResult android = resultNamed(results, "Android");
    assertEquals(Category.ERROR, android.getCategory());
    assertEquals("boom", android.getMessage());
    assertEquals(Category.SUCCESS, resultNamed(results, "iOS").getCategory());
  }

  @Test
  void unstableStage_isAWarning(JenkinsRule j) throws Exception {
    List<CheckResult> results =
        resultsOf(j, "stage('Test') {\n" + "  unstable('2 tests failed')\n" + "}\n");

    CheckResult test = resultNamed(results, "Test");
    assertEquals(Category.WARNING, test.getCategory());
    assertEquals("2 tests failed", test.getMessage());
  }

  @Test
  void failureCaughtByCatchError_isAWarning(JenkinsRule j) throws Exception {
    // catchError turns the failure into the result of the stage: Jenkins reports the stage as
    // unstable, and the error it caught is not what the stage failed with.
    List<CheckResult> results =
        resultsOf(
            j,
            "stage('Test') {\n"
                + "  catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {\n"
                + "    error('boom')\n"
                + "  }\n"
                + "}\n");

    assertEquals(Category.WARNING, resultNamed(results, "Test").getCategory());
  }

  @Test
  void caughtFailureOfARunningBuild_isNotReported(JenkinsRule j) throws Exception {
    WorkflowRun run =
        runOf(
            j,
            "parallel(\n"
                + "  'Android': {\n"
                + "    try {\n"
                + "      error('boom')\n"
                + "    } catch (e) {\n"
                + "      echo 'handled'\n"
                + "    }\n"
                + "    sleep 3\n"
                + "  },\n"
                + "  'iOS': {\n"
                + "    sleep 3\n"
                + "  }\n"
                + ")\n");
    awaitFailure(run);
    assertTrue(run.isBuilding(), "The pipeline is still running");

    List<CheckResult> results =
        PipelineStages.results(
            run.getExecution(),
            run.getExternalizableId(),
            run.isBuilding(),
            RUN_URL,
            StageReporting.defaults());

    assertEquals(
        Category.INFO,
        resultNamed(results, "Android").getCategory(),
        "The failure was caught, the branch is still running and has not failed");
    assertEquals(Category.INFO, resultNamed(results, "iOS").getCategory());
    j.waitForCompletion(run);
  }

  private static List<CheckResult> resultsOf(JenkinsRule j, String script) throws Exception {
    WorkflowRun run = runOf(j, script);
    j.waitForCompletion(run);
    return PipelineStages.results(
        run.getExecution(),
        run.getExternalizableId(),
        false,
        RUN_URL,
        StageReporting.defaults());
  }

  private static WorkflowRun runOf(JenkinsRule j, String script) throws Exception {
    WorkflowJob job =
        j.createProject(WorkflowJob.class, "pipeline-" + JOB_COUNTER.incrementAndGet());
    job.setDefinition(new CpsFlowDefinition(script, true));
    // waitForStart, not get(): a build whose stages are still running is what these tests are
    // about, and get() waits for the build to have finished.
    return job.scheduleBuild2(0).waitForStart();
  }

  /** Waits until the pipeline has caught the failure, i.e. the error is in the graph. */
  private static void awaitFailure(WorkflowRun run) throws Exception {
    long deadline = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < deadline && !hasFailure(run)) {
      Thread.sleep(100);
    }
    assertTrue(hasFailure(run), "The pipeline caught its failure");
  }

  private static boolean hasFailure(WorkflowRun run) {
    // A build that has just started has no execution yet.
    if (run.getExecution() == null) {
      return false;
    }
    for (FlowNode node : PipelineStages.walk(run.getExecution())) {
      if (node.getPersistentAction(ErrorAction.class) != null) {
        return true;
      }
    }
    return false;
  }

  private static CheckResult resultNamed(List<CheckResult> results, String name) {
    return results.stream()
        .filter(result -> name.equals(result.getSummary()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " is not reported, got " + summaries(results)));
  }

  private static List<String> summaries(List<CheckResult> results) {
    return results.stream().map(CheckResult::getSummary).toList();
  }
}
