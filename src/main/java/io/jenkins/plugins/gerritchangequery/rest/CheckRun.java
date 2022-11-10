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

import java.util.List;

public class CheckRun {
  private int change;
  private int patchSet;
  private int attempt;
  private String externalId;
  private String checkName;
  private String checkDescription;
  private String checkLink;
  private RunStatus status;
  private String statusDescription;
  private String statusLink;
  private String labelName;
  private List<Action> actions;
  private String scheduledTimestamp;
  private String startedTimestamp;
  private String finishedTimestamp;
  private List<CheckResult> results;

  public CheckRun() {}

  public CheckRun(
      int change,
      int patchSet,
      int attempt,
      String externalId,
      String checkName,
      String checkDescription,
      String checkLink,
      RunStatus status,
      String statusDescription,
      String statusLink,
      String labelName,
      List<Action> actions,
      String scheduledTimestamp,
      String startedTimestamp,
      String finishedTimestamp,
      List<CheckResult> results) {
    this.change = change;
    this.patchSet = patchSet;
    this.attempt = attempt;
    this.externalId = externalId;
    this.checkName = checkName;
    this.checkDescription = checkDescription;
    this.checkLink = checkLink;
    this.status = status;
    this.statusDescription = statusDescription;
    this.statusLink = statusLink;
    this.labelName = labelName;
    this.actions = actions;
    this.scheduledTimestamp = scheduledTimestamp;
    this.startedTimestamp = startedTimestamp;
    this.finishedTimestamp = finishedTimestamp;
    this.results = results;
  }

  public int getChange() {
    return change;
  }

  public void setChange(int change) {
    this.change = change;
  }

  public int getPatchSet() {
    return patchSet;
  }

  public void setPatchSet(int patchSet) {
    this.patchSet = patchSet;
  }

  public int getAttempt() {
    return attempt;
  }

  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }

  public String getCheckName() {
    return checkName;
  }

  public void setCheckName(String checkName) {
    this.checkName = checkName;
  }

  public String getCheckDescription() {
    return checkDescription;
  }

  public void setCheckDescription(String checkDescription) {
    this.checkDescription = checkDescription;
  }

  public String getCheckLink() {
    return checkLink;
  }

  public void setCheckLink(String checkLink) {
    this.checkLink = checkLink;
  }

  public RunStatus getStatus() {
    return status;
  }

  public void setStatus(RunStatus status) {
    this.status = status;
  }

  public String getStatusDescription() {
    return statusDescription;
  }

  public void setStatusDescription(String statusDescription) {
    this.statusDescription = statusDescription;
  }

  public String getStatusLink() {
    return statusLink;
  }

  public void setStatusLink(String statusLink) {
    this.statusLink = statusLink;
  }

  public String getLabelName() {
    return labelName;
  }

  public void setLabelName(String labelName) {
    this.labelName = labelName;
  }

  public List<Action> getActions() {
    return actions;
  }

  public void setActions(List<Action> actions) {
    this.actions = actions;
  }

  public String getScheduledTimestamp() {
    return scheduledTimestamp;
  }

  public void setScheduledTimestamp(String scheduledTimestamp) {
    this.scheduledTimestamp = scheduledTimestamp;
  }

  public String getStartedTimestamp() {
    return startedTimestamp;
  }

  public void setStartedTimestamp(String startedTimestamp) {
    this.startedTimestamp = startedTimestamp;
  }

  public String getFinishedTimestamp() {
    return finishedTimestamp;
  }

  public void setFinishedTimestamp(String finishedTimestamp) {
    this.finishedTimestamp = finishedTimestamp;
  }

  public List<CheckResult> getResults() {
    return results;
  }

  public void setResults(List<CheckResult> results) {
    this.results = results;
  }

  enum RunStatus {
    RUNNABLE,
    RUNNING,
    SCHEDULED,
    COMPLETED
  }
}
