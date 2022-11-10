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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gerrit.extensions.restapi.Url;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRuns;
import io.jenkins.plugins.gerritchecksapi.rest.GerritMultiBranchCheckRunFactory;
import io.jenkins.plugins.gerritchecksapi.rest.GerritTriggerCheckRunFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.lucene.search.databackend.SearchBackendManager;

public class CheckRunCollector {
  private final GerritTriggerCheckRunFactory gerritTriggerCheckRunFactory =
      new GerritTriggerCheckRunFactory();
  private final GerritMultiBranchCheckRunFactory gerritMultiBranchCheckRunFactory =
      new GerritMultiBranchCheckRunFactory();
  private final Jenkins jenkins = Jenkins.get();
  private final SearchBackendManager manager =
      jenkins.getExtensionList(SearchBackendManager.class).get(0);
  private final LoadingCache<String, CheckRuns> cache =
      Caffeine.newBuilder()
          .expireAfterWrite(30, TimeUnit.SECONDS)
          .build(
              new CacheLoader<String, CheckRuns>() {
                @Override
                public CheckRuns load(String ref) {
                  return collectAll(ref);
                }
              });

  public CheckRuns collectFor(int change, int patchset) {
    return cache.get(convertToRef(change, patchset));
  }

  private CheckRuns collectAll(String ref) {
    String[] refParts = ref.split("/");
    return collectAll(
        Integer.valueOf(refParts[refParts.length - 2]),
        Integer.valueOf(refParts[refParts.length - 1]));
  }

  private CheckRuns collectAll(int change, int patchset) {
    CheckRuns checkRuns = new CheckRuns();
    checkRuns.addRuns(collectGerritTriggerRuns(change, patchset));
    checkRuns.addRuns(collectGerritMultiBranchRuns(change, patchset));
    return checkRuns;
  }

  @SuppressWarnings("rawtypes")
  private List<CheckRun> collectGerritTriggerRuns(int change, int patchset) {
    List<CheckRun> checkRuns = new ArrayList<>();
    List<Run> hits =
        manager
            .getHits(String.format("p:\"refs/changes/%s\"", convertToRef(change, patchset)), false)
            .stream()
            .map(
                hit ->
                    jenkins
                        .getItemByFullName(hit.getProjectName(), Job.class)
                        .getBuild(hit.getSearchName().substring(1)))
            .sorted(Comparator.comparing(Run::getNumber))
            .collect(Collectors.toList());
    for (int i = 0; i < hits.size(); i++) {
      checkRuns.add(
          gerritTriggerCheckRunFactory.create(
              change, patchset, hits.get(i).getParent(), hits.get(i), i + 1));
    }
    return checkRuns;
  }

  @SuppressWarnings("rawtypes")
  private List<CheckRun> collectGerritMultiBranchRuns(int change, int patchset) {
    List<CheckRun> checkRuns = new ArrayList<>();
    List<Run> hits =
        manager.getHits(String.format("j:\"%s\"", convertToUrlEncodedRef(change, patchset)), false)
            .stream()
            .map(
                hit ->
                    jenkins
                        .getItemByFullName(hit.getProjectName(), Job.class)
                        .getBuild(hit.getSearchName().substring(1)))
            .collect(Collectors.toList());
    for (Run hit : hits) {
      checkRuns.add(
          gerritMultiBranchCheckRunFactory.create(
              change, patchset, hit.getParent(), hit, hit.getNumber()));
    }
    return checkRuns;
  }

  private static String convertToRef(int change, int patchset) {
    return String.format("%02d/%d/%d", change % 100, change, patchset);
  }

  private static String convertToUrlEncodedRef(int change, int patchset) {
    return Url.encode(convertToRef(change, patchset));
  }
}
