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

import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.gerritchecksapi.rest.CheckResult.Category;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun.RunStatus;
import io.jenkins.plugins.gerritchecksapi.rest.Link.LinkIcon;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

public abstract class AbstractCheckRunFactory {
  private final Jenkins jenkins = Jenkins.get();

  public abstract CheckRun create(
      int change, int patchset, Job<?, ?> job, Run<?, ?> run, int attempt);

  protected abstract List<Action> computeActions(Run<?, ?> run);

  // TODO(Thomas): Add RUNNABLE status
  protected static RunStatus computeStatus(Run<?, ?> run) {
    if (run.hasntStartedYet()) {
      return RunStatus.SCHEDULED;
    }
    if (run.isBuilding()) {
      return RunStatus.RUNNING;
    }
    return RunStatus.COMPLETED;
  }

  protected static String computeFinishedTimeStamp(Run<?, ?> run) {
    if (run.hasntStartedYet() || run.isBuilding()) {
      return null;
    }
    return Instant.ofEpochMilli(run.getStartTimeInMillis())
        .plusMillis(run.getDuration())
        .toString();
  }

  // Currently only a single result can be returned. Having multiple results per build might
  // require introducing additional functionality to Jenkins
  protected List<CheckResult> computeCheckResults(Run<?, ?> run) {
    List<CheckResult> results = new ArrayList<CheckResult>();
    if (run.hasntStartedYet() || run.isBuilding()) {
      return results;
    }
    CheckResult result = new CheckResult();
    result.setExternalId(run.getExternalizableId());
    result.setCategory(Category.fromResult(run.getResult()));
    result.setLinks(computeResultLinks(run));
    results.add(result);
    return results;
  }

  private List<Link> computeResultLinks(Run<?, ?> run) {
    List<Link> links = new ArrayList<>();
    Link consoleLogLink = new Link();
    consoleLogLink.setUrl(String.format("%sconsole", getAbsoluteRunUrl(run)));
    consoleLogLink.setTooltip("Build log.");
    consoleLogLink.setIcon(LinkIcon.CODE);
    consoleLogLink.setPrimary(true);
    links.add(consoleLogLink);
    return links;
  }

  protected String getAbsoluteRunUrl(Run<?, ?> run) {
    return String.format("%s%s", jenkins.getRootUrl(), run.getUrl());
  }
}
