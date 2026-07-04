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

import com.google.gerrit.extensions.restapi.Url;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import io.jenkins.plugins.gerritchecksapi.rest.AbstractCheckRunFactory;
import io.jenkins.plugins.gerritchecksapi.rest.GerritMultiBranchCheckRunFactory;
import io.jenkins.plugins.gerritchecksapi.rest.GerritTriggerCheckRunFactory;
import io.jenkins.plugins.gerritchecksapi.rest.Link;
import io.jenkins.plugins.gerritchecksapi.rest.Link.LinkIcon;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;

import org.apache.log4j.Logger;
import org.jenkinsci.plugins.lucene.search.FreeTextSearchItemImplementation;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;

@Singleton
public class DirectCheckRunCollector implements CheckRunCollector {
    private static final Logger LOG = Logger.getLogger(DirectCheckRunCollector.class);
    private static final int MAX_DOWNSTREAM_DEPTH = 10;
    private static final int MAX_RECENT_BUILDS_PER_JOB = 100;

  private final Jenkins jenkins;
  private final Provider<SearchBackendManager> managerProvider;
  private final GerritTriggerCheckRunFactory gerritTriggerCheckRunFactory;
  private final GerritMultiBranchCheckRunFactory gerritMultiBranchCheckRunFactory;

  @Inject
  DirectCheckRunCollector(
      Jenkins jenkins,
      Provider<SearchBackendManager> managerProvider,
      GerritTriggerCheckRunFactory gerritTriggerCheckRunFactory,
      GerritMultiBranchCheckRunFactory gerritMultiBranchCheckRunFactory) {
    this.jenkins = jenkins;
    this.managerProvider = managerProvider;
    this.gerritTriggerCheckRunFactory = gerritTriggerCheckRunFactory;
    this.gerritMultiBranchCheckRunFactory = gerritMultiBranchCheckRunFactory;
  }

  @Override
  public Map<Job<?, ?>, List<CheckRun>> collectFor(PatchSetId ps) {
    Map<Job<?, ?>, List<CheckRun>> checkRuns = new HashMap<>();
    if (jenkins.getPlugin("gerrit-trigger") != null) {
      checkRuns.putAll(collectGerritTriggerRuns(ps));
    }
    if (jenkins.getPlugin("gerrit-code-review") != null) {
      checkRuns.putAll(collectGerritMultiBranchRuns(ps));
    }
    collectDownstreamCheckRuns(ps, checkRuns);
    return checkRuns;
  }

  @SuppressWarnings("rawtypes")
  private Map<Job<?, ?>, List<CheckRun>> collectGerritTriggerRuns(PatchSetId ps) {
    SearchBackendManager manager = getSearchBackendManager();
    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
      Map<Job<?, ?>, List<Run>> hits = queryRuns(String.format("p:\"refs/changes/%s\"", ps.toRef()), manager).stream()
              .sorted(Comparator.comparing(Run::getNumber))
              .collect(Collectors.groupingBy(Run::getParent));

      Map<Job<?, ?>, List<CheckRun>> checkRuns = new HashMap<>();
      for (Map.Entry<Job<?, ?>, List<Run>> entry : hits.entrySet()) {
        List<Run> runs = entry.getValue();
        List<CheckRun> checks = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
          checks.add(
              gerritTriggerCheckRunFactory.create(ps, runs.get(i).getParent(), runs.get(i), i + 1));
        }
        checkRuns.put(entry.getKey(), checks);
      }
      return checkRuns;
    }
  }

  @SuppressWarnings("rawtypes")
  private Map<Job<?, ?>, List<CheckRun>> collectGerritMultiBranchRuns(PatchSetId ps) {
    SearchBackendManager manager = getSearchBackendManager();
    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
      Map<Job<?, ?>, List<Run>> runs =
          queryRuns(String.format("j:\"%s\"", convertToUrlEncodedRef(ps)), manager).stream()
              .collect(Collectors.groupingBy(Run::getParent));

      Map<Job<?, ?>, List<CheckRun>> checkRuns = new HashMap<>();
      for (Map.Entry<Job<?, ?>, List<Run>> entry : runs.entrySet()) {
        checkRuns.put(
            entry.getKey(),
            entry.getValue().stream()
                .map(
                    run ->
                        gerritMultiBranchCheckRunFactory.create(
                            ps, run.getParent(), run, run.getNumber()))
                .collect(Collectors.toList()));
      }
      return checkRuns;
    }
  }

  @SuppressWarnings("rawtypes")
  private List<Run> queryRuns(String query, SearchBackendManager manager) {
      List<Run> foundRuns = new ArrayList<>();
      for (FreeTextSearchItemImplementation hit : manager.getHits(query, false)) {
        Job job = jenkins
                .getItemByFullName(hit.getProjectName(), Job.class);
        if (job == null) {
          throw new IllegalStateException("Couldn't find project returned by index query: " + hit.getProjectName());
        }
        Run run = job.getBuild(hit.getSearchName().split("#")[1]);
        if (run == null) {
          throw new IllegalStateException(String.format("Couldn't find build %s for job %s returned by index query: ", hit.getSearchName(), job.getFullName()));
        }

        LOG.debug(String.format("Found job %s build %s with parent %s", job.getFullName(), run.getNumber(), run.getParent()));
        foundRuns.add(run);
      }
      return foundRuns;
  }

  private SearchBackendManager getSearchBackendManager() {
    SearchBackendManager manager = managerProvider.get();
    if (manager == null) {
      throw new MissingDependencyException(SearchBackendManager.class.getName());
    }
    return manager;
  }

  private static String convertToUrlEncodedRef(PatchSetId ps) {
    return Url.encode(ps.toRef());
  }

  @SuppressWarnings("rawtypes")
  private void collectDownstreamCheckRuns(
      PatchSetId ps, Map<Job<?, ?>, List<CheckRun>> checkRuns) {
    Set<String> directRunIds = new HashSet<>();
    Map<String, CheckRun> directRunByExtId = new HashMap<>();
    for (List<CheckRun> runs : checkRuns.values()) {
      for (CheckRun cr : runs) {
        directRunIds.add(cr.getExternalId());
        directRunByExtId.put(cr.getExternalId(), cr);
      }
    }
    if (directRunIds.isEmpty()) {
      return;
    }

    Map<String, List<Run<?, ?>>> downstreamMap = buildDownstreamMap();
    Set<String> traversed = new HashSet<>(directRunIds);
    for (String rootKey : directRunIds) {
      List<Run<?, ?>> children = downstreamMap.get(rootKey);
      if (children != null) {
        for (Run<?, ?> child : children) {
          String childKey = child.getExternalizableId();
          if (directRunByExtId.containsKey(childKey)) {
            CheckRun directRun = directRunByExtId.remove(childKey);
            directRun.setExternalId(
                buildDownstreamExternalId(rootKey, childKey));
          } else {
            Job<?, ?> childJob = child.getParent();
            checkRuns.computeIfAbsent(childJob, k -> new ArrayList<>()).add(
                createDownstreamCheckRun(ps, rootKey, child, childJob));
          }
          if (traversed.add(childKey)) {
            traverseDownstream(ps, childKey, child, checkRuns,
                downstreamMap, traversed, directRunIds, directRunByExtId, 1);
          }
        }
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private Map<String, List<Run<?, ?>>> buildDownstreamMap() {
    Map<String, List<Run<?, ?>>> downstreamMap = new HashMap<>();
    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
      for (Job<?, ?> job : jenkins.getAllItems(Job.class)) {
        int count = 0;
        for (Run<?, ?> run : job.getBuilds()) {
          if (count++ >= MAX_RECENT_BUILDS_PER_JOB) {
            break;
          }
          if (run == null) {
            continue;
          }
          for (Cause cause : run.getCauses()) {
            if (cause instanceof Cause.UpstreamCause) {
              Cause.UpstreamCause uc = (Cause.UpstreamCause) cause;
              String upstreamKey =
                  uc.getUpstreamProject() + "#" + uc.getUpstreamBuild();
              downstreamMap
                  .computeIfAbsent(upstreamKey, k -> new ArrayList<>())
                  .add(run);
            }
          }
        }
      }
    }
    return downstreamMap;
  }

  @SuppressWarnings("rawtypes")
  private void traverseDownstream(
      PatchSetId ps,
      String upstreamKey,
      Run<?, ?> upstreamRun,
      Map<Job<?, ?>, List<CheckRun>> checkRuns,
      Map<String, List<Run<?, ?>>> downstreamMap,
      Set<String> traversed,
      Set<String> directRunIds,
      Map<String, CheckRun> directRunByExtId,
      int depth) {

    if (depth >= MAX_DOWNSTREAM_DEPTH) {
      LOG.warn(String.format(
          "Reached max downstream depth %d at run %s",
          MAX_DOWNSTREAM_DEPTH, upstreamKey));
      return;
    }

    List<Run<?, ?>> children = downstreamMap.get(upstreamKey);
    if (children == null) {
      return;
    }

    for (Run<?, ?> child : children) {
      String childKey = child.getExternalizableId();
      if (directRunByExtId.containsKey(childKey)) {
        CheckRun directRun = directRunByExtId.remove(childKey);
        directRun.setExternalId(
            buildDownstreamExternalId(upstreamKey, childKey));
      } else {
        Job<?, ?> childJob = child.getParent();
        checkRuns.computeIfAbsent(childJob, k -> new ArrayList<>()).add(
            createDownstreamCheckRun(ps, upstreamKey, child, childJob));
      }
      if (traversed.add(childKey)) {
        traverseDownstream(ps, childKey, child, checkRuns,
            downstreamMap, traversed, directRunIds,
            directRunByExtId, depth + 1);
      }
    }
  }

  private static String buildDownstreamExternalId(
      String parentKey, String runKey) {
    return String.format(
        "{\"parent\":\"%s\",\"run\":\"%s\"}", parentKey, runKey);
  }

  @SuppressWarnings("rawtypes")
  private CheckRun createDownstreamCheckRun(
      PatchSetId ps,
      String upstreamKey,
      Run<?, ?> run,
      Job<?, ?> job) {

    String runId = run.getExternalizableId();
    String externalId = buildDownstreamExternalId(upstreamKey, runId);

    String runUrl = String.format("%s%s", jenkins.getRootUrl(), run.getUrl());

    CheckRun checkRun = new CheckRun();
    checkRun.setChange(ps.changeId());
    checkRun.setPatchSet(ps.patchSetNumber());
    checkRun.setAttempt(1);
    checkRun.setExternalId(externalId);
    checkRun.setCheckName(job.getDisplayName());
    checkRun.setCheckDescription(job.getDescription());
    checkRun.setCheckLink(runUrl);
    checkRun.setStatus(AbstractCheckRunFactory.computeStatus(run));
    checkRun.setStatusDescription(run.getBuildStatusSummary().message);
    checkRun.setStatusLink(runUrl);
    checkRun.setLabelName(null);
    checkRun.setActions(new ArrayList<>());
    checkRun.setScheduledTimestamp(run.getTime().toInstant().toString());
    checkRun.setStartedTimestamp(
        Instant.ofEpochMilli(run.getStartTimeInMillis()).toString());
    checkRun.setFinishedTimestamp(
        AbstractCheckRunFactory.computeFinishedTimeStamp(run));

    List<CheckResult> results = new ArrayList<>();
    if (!run.hasntStartedYet() && !run.isBuilding()) {
      CheckResult result = new CheckResult();
      result.setExternalId(externalId);
      Result jenkinsResult = run.getResult();
      if (jenkinsResult != null) {
        result.setCategory(Category.fromResult(jenkinsResult));
      }
      Link consoleLink = new Link();
      consoleLink.setUrl(String.format("%sconsole", runUrl));
      consoleLink.setTooltip("Build log.");
      consoleLink.setIcon(LinkIcon.CODE);
      consoleLink.setPrimary(true);
      result.setLinks(List.of(consoleLink));
      results.add(result);
    }
    checkRun.setResults(results);

    return checkRun;
  }
}
