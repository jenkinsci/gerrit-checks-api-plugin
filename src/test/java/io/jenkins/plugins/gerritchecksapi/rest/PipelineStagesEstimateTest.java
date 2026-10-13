package io.jenkins.plugins.gerritchecksapi.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Result;
import io.jenkins.plugins.gerritchecksapi.PatchSetId;
import io.jenkins.plugins.gerritchecksapi.StageReporting;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The times a stage reports, against the flow graph of a real Pipeline: a stage has a start and an
 * end of its own, and a stage that is still running is estimated from what the same stage took in
 * the build before it, so that Gerrit shows an ETA for the stage and not for the whole build.
 */
@WithJenkins
class PipelineStagesEstimateTest {

  private static final PatchSetId PS = PatchSetId.create(101, 4);
  private static final String RUN_URL = "https://jenkins/job/my-job/7/";
  private static final AtomicInteger JOB_COUNTER = new AtomicInteger();

  @Test
  void finishedStage_reportsItsOwnStartAndEnd(JenkinsRule j) throws Exception {
    WorkflowRun run =
        build(
            j,
            "stage('First') {\n"
                + "  sleep 2\n"
                + "}\n"
                + "stage('Second') {\n"
                + "  sleep 1\n"
                + "}\n");
    j.assertBuildStatus(Result.SUCCESS, j.waitForCompletion(run));

    CheckRun first = stageNamed(stagesOf(run), "First");
    Instant started = Instant.parse(first.getStartedTimestamp());
    Instant ended = Instant.parse(first.getFinishedTimestamp());
    assertTrue(ended.isAfter(started), "The stage took time of its own");
    assertTrue(
        Instant.parse(stageNamed(stagesOf(run), "Second").getStartedTimestamp()).isAfter(ended),
        "The second stage started when the first had ended, not when the build had");
  }

  @Test
  void runningStage_isEstimatedFromTheStageOfTheBuildBefore(JenkinsRule j) throws Exception {
    WorkflowJob job = newJob(j);
    build(job, verifyStage("sleep 2"));
    WorkflowRun before = build(job, verifyStage("sleep 2"));
    j.assertBuildStatus(Result.SUCCESS, j.waitForCompletion(before));
    Duration beforeDuration = durationOf(stageNamed(stagesOf(before), "Verify"));

    WorkflowRun running = build(job, verifyStage("sleep 20"));
    try {
      CheckRun stage = awaitRunningStage(running, "Verify");

      assertEquals(
          Instant.parse(stage.getStartedTimestamp()).plus(beforeDuration).toString(),
          stage.getFinishedTimestamp(),
          "The estimate is what the stage took the last time the job built");
    } finally {
      running.doKill();
      j.waitForCompletion(running);
    }
  }

  @Test
  void runningStageWithoutABuildBefore_reportsNoEstimate(JenkinsRule j) throws Exception {
    WorkflowRun running = build(newJob(j), verifyStage("sleep 20"));
    try {
      CheckRun stage = awaitRunningStage(running, "Verify");

      assertNull(
          stage.getFinishedTimestamp(),
          "A guess that is known to be wrong is worse than no estimate at all");
    } finally {
      running.doKill();
      j.waitForCompletion(running);
    }
  }

  private static String verifyStage(String body) {
    return "stage('Verify') {\n" + "  " + body + "\n" + "}\n";
  }

  private static WorkflowJob newJob(JenkinsRule j) throws Exception {
    return j.createProject(WorkflowJob.class, "estimate-" + JOB_COUNTER.incrementAndGet());
  }

  private static WorkflowRun build(JenkinsRule j, String script) throws Exception {
    return build(newJob(j), script);
  }

  private static WorkflowRun build(WorkflowJob job, String script) throws Exception {
    job.setDefinition(new CpsFlowDefinition(script, true));
    // waitForStart, not get(): a build whose stages are still running is what is tested here.
    return job.scheduleBuild2(0).waitForStart();
  }

  /** Waits until the running build reports the stage, which it does while it is still running. */
  private static CheckRun awaitRunningStage(WorkflowRun run, String name) throws Exception {
    long deadline = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < deadline) {
      for (CheckRun stage : stagesOf(run)) {
        if (name.equals(stage.getCheckName()) && stage.getStatus() == CheckRun.RunStatus.RUNNING) {
          return stage;
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError(name + " is not reported while the build runs");
  }

  private static List<CheckRun> stagesOf(WorkflowRun run) {
    return PipelineStages.checkRuns(PS, run, parentOf(run), RUN_URL, StageReporting.defaults());
  }

  private static CheckRun parentOf(WorkflowRun run) {
    CheckRun parent = new CheckRun();
    parent.setChange(PS.changeId());
    parent.setPatchSet(PS.patchSetNumber());
    parent.setAttempt(1);
    parent.setCheckDescription("description of my-job");
    parent.setStatusDescription("stable");
    parent.setStatusLink(RUN_URL);
    parent.setStartedTimestamp(Instant.ofEpochMilli(run.getStartTimeInMillis()).toString());
    parent.setScheduledTimestamp(parent.getStartedTimestamp());
    parent.setFinishedTimestamp(AbstractCheckRunFactory.computeFinishedTimeStamp(run));
    return parent;
  }

  private static CheckRun stageNamed(List<CheckRun> stages, String name) {
    return stages.stream()
        .filter(stage -> name.equals(stage.getCheckName()))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    name
                        + " is not reported, got "
                        + stages.stream().map(CheckRun::getCheckName).toList()));
  }

  private static Duration durationOf(CheckRun stage) {
    assertNotNull(stage.getFinishedTimestamp());
    return Duration.between(
        Instant.parse(stage.getStartedTimestamp()), Instant.parse(stage.getFinishedTimestamp()));
  }
}
