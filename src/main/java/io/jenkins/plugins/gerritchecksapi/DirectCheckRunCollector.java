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
import hudson.model.Job;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.ACLContext;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun;
import io.jenkins.plugins.gerritchecksapi.rest.GerritMultiBranchCheckRunFactory;
import io.jenkins.plugins.gerritchecksapi.rest.GerritTriggerCheckRunFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;

@Singleton
public class DirectCheckRunCollector implements CheckRunCollector {

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
    return checkRuns;
  }

  @SuppressWarnings("rawtypes")
  private Map<Job<?, ?>, List<CheckRun>> collectGerritTriggerRuns(PatchSetId ps) {
    SearchBackendManager manager = getSearchBackendManager();
    try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
      Map<Job<?, ?>, List<Run>> hits =
          manager.getHits(String.format("p:\"refs/changes/%s\"", convertToRef(ps)), false).stream()
              .map(
                  hit ->
                      jenkins
                          .getItemByFullName(hit.getProjectName(), Job.class)
                          .getBuild(hit.getSearchName().substring(1)))
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
          manager.getHits(String.format("j:\"%s\"", convertToUrlEncodedRef(ps)), false).stream()
              .map(
                  hit ->
                      jenkins
                          .getItemByFullName(hit.getProjectName(), Job.class)
                          .getBuild(hit.getSearchName().substring(1)))
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

  private SearchBackendManager getSearchBackendManager() {
    SearchBackendManager manager = managerProvider.get();
    if (manager == null) {
      throw new MissingDependencyException(SearchBackendManager.class.getName());
    }
    return manager;
  }

  private static String convertToRef(PatchSetId ps) {
    return String.format("%02d/%d/%d", ps.changeId() % 100, ps.changeId(), ps.patchSetNumber());
  }

  private static String convertToUrlEncodedRef(PatchSetId ps) {
    return Url.encode(convertToRef(ps));
  }
}
