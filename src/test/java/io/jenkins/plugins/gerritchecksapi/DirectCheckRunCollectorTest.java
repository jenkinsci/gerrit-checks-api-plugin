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

package io.jenkins.plugins.gerritchecksapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun;
import io.jenkins.plugins.gerritchecksapi.rest.GerritMultiBranchCheckRunFactory;
import io.jenkins.plugins.gerritchecksapi.rest.GerritTriggerCheckRunFactory;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.lucene.search.FreeTextSearchItemImplementation;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({"rawtypes", "unchecked"})
class DirectCheckRunCollectorTest {

  @Mock private Jenkins jenkins;
  @Mock private Provider<SearchBackendManager> managerProvider;
  @Mock private SearchBackendManager manager;
  @Mock private GerritTriggerCheckRunFactory triggerFactory;
  @Mock private GerritMultiBranchCheckRunFactory multiBranchFactory;

  private DirectCheckRunCollector collector;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(managerProvider.get()).thenReturn(manager);
    when(jenkins.getRootUrl()).thenReturn("http://jenkins/");
    collector = new DirectCheckRunCollector(
        jenkins, managerProvider, triggerFactory, multiBranchFactory);
  }

  // --- helpers ---

  private Run mockRun(String fullJobName, int buildNumber, Job parentJob) {
    Run run = mock(Run.class);
    when(run.getExternalizableId()).thenReturn(fullJobName + "#" + buildNumber);
    when(run.getNumber()).thenReturn(buildNumber);
    when(run.getParent()).thenReturn(parentJob);
    when(run.getUrl()).thenReturn("job/" + fullJobName + "/" + buildNumber + "/");
    when(run.getTime()).thenReturn(new Date(1_000_000L));
    when(run.getStartTimeInMillis()).thenReturn(1_000_500L);
    when(run.getDuration()).thenReturn(500L);
    when(run.getEstimatedDuration()).thenReturn(600L);
    when(run.hasntStartedYet()).thenReturn(false);
    when(run.isBuilding()).thenReturn(false);
    when(run.getResult()).thenReturn(Result.SUCCESS);
    when(run.getBuildStatusSummary()).thenReturn(new Run.Summary(false, "stable"));
    return run;
  }

  private Job mockJob(String fullName, String displayName) {
    Job job = mock(Job.class);
    when(job.getFullName()).thenReturn(fullName);
    when(job.getDisplayName()).thenReturn(displayName);
    when(job.getDescription()).thenReturn("description of " + displayName);
    return job;
  }

  private FreeTextSearchItemImplementation mockHit(
      String projectName, String searchName) {
    return new FreeTextSearchItemImplementation(
        searchName, projectName, new String[0], "", false);
  }

  private CheckRun createPlainCheckRun(int change, int patchset, String externalId) {
    CheckRun cr = new CheckRun();
    cr.setChange(change);
    cr.setPatchSet(patchset);
    cr.setExternalId(externalId);
    cr.setAttempt(1);
    return cr;
  }

  // --- verification of existing behavior ---

  @Test
  void collectFor_noPluginsInstalled_returnsEmpty() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(null);
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(null);

    Map<Job<?, ?>, List<CheckRun>> result = collector.collectFor(PatchSetId.create(1, 1));
    assertTrue(result.isEmpty());
  }

  @Test
  void collectFor_oneDirectRun_noDownstream_returnsOneRun() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(null);

    Job job = mockJob("trigger-job", "trigger-job");
    Run run = mockRun("trigger-job", 5, job);

    when(manager.getHits(anyString(), anyBoolean()))
        .thenReturn(Collections.singletonList(mockHit("trigger-job", "trigger-job#5")));
    when(jenkins.getItemByFullName("trigger-job", Job.class)).thenReturn(job);
    when(job.getBuild("5")).thenReturn(run);

    when(triggerFactory.create(any(), any(), any(), anyInt()))
        .thenReturn(createPlainCheckRun(1, 1, "trigger-job#5"));

    Map<Job<?, ?>, List<CheckRun>> result = collector.collectFor(PatchSetId.create(1, 1));

    assertEquals(1, result.size());
    assertEquals("trigger-job#5", result.get(job).get(0).getExternalId());
  }

  @Test
  void collectFor_noLuceneHits_returnsEmpty() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(null);
    when(manager.getHits(anyString(), anyBoolean()))
        .thenReturn(Collections.emptyList());

    Map<Job<?, ?>, List<CheckRun>> result = collector.collectFor(PatchSetId.create(1, 1));
    assertTrue(result.isEmpty());
  }

  @Test
  void collectFor_bothPluginsInstalled_mergesResults() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(mock(hudson.Plugin.class));

    Job jobA = mockJob("trigger-job", "trigger-job");
    Run runA = mockRun("trigger-job", 1, jobA);

    Job jobB = mockJob("multibranch-job", "multibranch-job");
    Run runB = mockRun("multibranch-job/main", 1, jobB);

    when(manager.getHits(anyString(), anyBoolean()))
        .thenReturn(
            Collections.singletonList(mockHit("trigger-job", "trigger-job#1")),
            Collections.singletonList(mockHit("multibranch-job", "multibranch-job/main#1")));
    when(jenkins.getItemByFullName("trigger-job", Job.class)).thenReturn(jobA);
    when(jenkins.getItemByFullName("multibranch-job", Job.class)).thenReturn(jobB);
    when(jobA.getBuild("1")).thenReturn(runA);
    when(jobB.getBuild("1")).thenReturn(runB);

    when(triggerFactory.create(any(), any(), any(), anyInt()))
        .thenReturn(createPlainCheckRun(1, 1, "trigger-job#1"));
    when(multiBranchFactory.create(any(), any(), any(), anyInt()))
        .thenReturn(createPlainCheckRun(1, 1, "multibranch-job/main#1"));

    Map<Job<?, ?>, List<CheckRun>> result = collector.collectFor(PatchSetId.create(1, 1));
    assertEquals(2, result.size());
  }

  @Test
  void collectFor_gerritTriggerMultipleAttempts_sequentialNumbers() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(null);

    Job job = mockJob("trigger-job", "trigger-job");
    Run run5 = mockRun("trigger-job", 5, job);
    Run run12 = mockRun("trigger-job", 12, job);

    when(manager.getHits(anyString(), anyBoolean())).thenReturn(List.of(
        mockHit("trigger-job", "trigger-job#12"),
        mockHit("trigger-job", "trigger-job#5")));
    when(jenkins.getItemByFullName("trigger-job", Job.class)).thenReturn(job);
    when(job.getBuild("5")).thenReturn(run5);
    when(job.getBuild("12")).thenReturn(run12);

    when(triggerFactory.create(any(), eq(job), eq(run5), eq(1)))
        .thenReturn(createPlainCheckRun(1, 1, "trigger-job#5"));
    when(triggerFactory.create(any(), eq(job), eq(run12), eq(2)))
        .thenReturn(createPlainCheckRun(1, 1, "trigger-job#12"));

    Map<Job<?, ?>, List<CheckRun>> result = collector.collectFor(PatchSetId.create(1, 1));

    assertEquals(1, result.size());
    assertEquals(2, result.get(job).size());
  }

  @Test
  void collectFor_gerritTriggerMultipleJobs_perJobGrouping() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(null);

    Job jobA = mockJob("job-a", "job-a");
    Run runA = mockRun("job-a", 1, jobA);
    Job jobB = mockJob("job-b", "job-b");
    Run runB = mockRun("job-b", 3, jobB);

    when(manager.getHits(anyString(), anyBoolean())).thenReturn(List.of(
        mockHit("job-a", "job-a#1"),
        mockHit("job-b", "job-b#3")));
    when(jenkins.getItemByFullName("job-a", Job.class)).thenReturn(jobA);
    when(jenkins.getItemByFullName("job-b", Job.class)).thenReturn(jobB);
    when(jobA.getBuild("1")).thenReturn(runA);
    when(jobB.getBuild("3")).thenReturn(runB);

    when(triggerFactory.create(any(), eq(jobA), eq(runA), anyInt()))
        .thenReturn(createPlainCheckRun(1, 1, "job-a#1"));
    when(triggerFactory.create(any(), eq(jobB), eq(runB), anyInt()))
        .thenReturn(createPlainCheckRun(1, 1, "job-b#3"));

    Map<Job<?, ?>, List<CheckRun>> result = collector.collectFor(PatchSetId.create(1, 1));

    assertEquals(2, result.size());
    assertTrue(result.containsKey(jobA));
    assertTrue(result.containsKey(jobB));
    assertEquals(1, result.get(jobA).size());
    assertEquals(1, result.get(jobB).size());
  }

  @Test
  void collectFor_gerritMultiBranch_passesRunNumberAsAttempt() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(null);
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(mock(hudson.Plugin.class));

    Job job = mockJob("mbp/branch", "mbp");
    Run run = mockRun("mbp/branch", 7, job);

    when(manager.getHits(anyString(), anyBoolean()))
        .thenReturn(Collections.singletonList(mockHit("mbp/branch", "mbp/branch#7")));
    when(jenkins.getItemByFullName("mbp/branch", Job.class)).thenReturn(job);
    when(job.getBuild("7")).thenReturn(run);

    when(multiBranchFactory.create(any(), any(), any(), anyInt()))
        .thenReturn(createPlainCheckRun(1, 1, "mbp/branch#7"));

    collector.collectFor(PatchSetId.create(42, 3));

    verify(multiBranchFactory).create(any(), any(), eq(run), eq(7));
  }

  @Test
  void collectFor_searchBackendManagerNull_throwsMissingDependencyException() {
    when(managerProvider.get()).thenReturn(null);
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));

    assertThrows(MissingDependencyException.class,
        () -> collector.collectFor(PatchSetId.create(1, 1)));
  }

  @Test
  void patchSetId_toRef_correctFormat() {
    assertEquals("23/123/5", PatchSetId.create(123, 5).toRef());
    assertEquals("07/7/1", PatchSetId.create(7, 1).toRef());
    assertEquals("45/12345/3", PatchSetId.create(12345, 3).toRef());
  }

  @Test
  void patchSetId_equalsAndHashCode() {
    PatchSetId a1 = PatchSetId.create(1, 1);
    PatchSetId a2 = PatchSetId.create(1, 1);
    PatchSetId b = PatchSetId.create(1, 2);
    PatchSetId c = PatchSetId.create(2, 1);

    assertEquals(a1, a2);
    assertEquals(a1.hashCode(), a2.hashCode());
    assertEquals(a1, a1);
    org.junit.jupiter.api.Assertions.assertNotEquals(a1, null);
    org.junit.jupiter.api.Assertions.assertNotEquals(a1, "string");
    org.junit.jupiter.api.Assertions.assertNotEquals(a1, b);
    org.junit.jupiter.api.Assertions.assertNotEquals(a1, c);
  }

  @Test
  void queryRuns_jobNotFoundByLucene_throwsIllegalStateException() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(null);

    when(manager.getHits(anyString(), anyBoolean()))
        .thenReturn(Collections.singletonList(mockHit("ghost-job", "ghost-job#1")));
    when(jenkins.getItemByFullName("ghost-job", Job.class)).thenReturn(null);

    assertThrows(IllegalStateException.class,
        () -> collector.collectFor(PatchSetId.create(1, 1)));
  }

  @Test
  void queryRuns_buildNotFoundByLucene_throwsIllegalStateException() {
    when(jenkins.getPlugin("gerrit-trigger")).thenReturn(mock(hudson.Plugin.class));
    when(jenkins.getPlugin("gerrit-code-review")).thenReturn(null);

    Job job = mockJob("real-job", "real-job");
    when(manager.getHits(anyString(), anyBoolean()))
        .thenReturn(Collections.singletonList(mockHit("real-job", "real-job#99")));
    when(jenkins.getItemByFullName("real-job", Job.class)).thenReturn(job);
    when(job.getBuild("99")).thenReturn(null);

    assertThrows(IllegalStateException.class,
        () -> collector.collectFor(PatchSetId.create(1, 1)));
  }
}
