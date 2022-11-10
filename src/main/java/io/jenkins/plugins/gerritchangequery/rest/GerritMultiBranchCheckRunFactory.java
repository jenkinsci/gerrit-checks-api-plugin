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

package io.jenkins.plugins.gerritchangequery.rest;

import hudson.model.Job;
import hudson.model.Run;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GerritMultiBranchCheckRunFactory extends AbstractCheckRunFactory {

  @Override
  public CheckRun create(int change, int patchset, Job<?, ?> job, Run<?, ?> run, int attempt) {
    CheckRun checkRun = new CheckRun();

    checkRun.setChange(change);
    checkRun.setPatchSet(patchset);

    checkRun.setAttempt(attempt);
    checkRun.setExternalId(run.getExternalizableId());
    checkRun.setCheckName(job.getParent().getDisplayName());
    checkRun.setCheckDescription(job.getDescription());
    checkRun.setCheckLink(getAbsoluteRunUrl(run));
    checkRun.setStatus(computeStatus(run));
    checkRun.setStatusDescription(run.getBuildStatusSummary().message);
    checkRun.setStatusLink(getAbsoluteRunUrl(run));
    // TODO(Thomas): labelName. Info might be present in the gerrit plugins
    checkRun.setLabelName(null);
    checkRun.setActions(computeActions(run));
    checkRun.setScheduledTimestamp(run.getTime().toInstant().toString());
    checkRun.setStartedTimestamp(Instant.ofEpochMilli(run.getStartTimeInMillis()).toString());
    checkRun.setFinishedTimestamp(computeFinishedTimeStamp(run));
    checkRun.setResults(computeCheckResults(run));
    return checkRun;
  }

  @Override
  protected List<Action> computeActions(Run<?, ?> run) {
    List<Action> actions = new ArrayList<>();
    actions.add(new GerritMultiBranchRerunAction(run.getParent().getAbsoluteUrl()));
    return actions;
  }
}
