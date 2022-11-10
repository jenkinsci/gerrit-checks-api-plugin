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

import hudson.model.Result;
import java.util.List;

public class CheckResult {
  private String ExternalId;
  private Category category;
  private String summary;
  private String message;
  private List<Tag> tags;
  private List<Link> links;
  private List<CodePointer> codePointers;
  private List<Action> actions;
  private List<Fix> fixes;

  public CheckResult() {}

  public CheckResult(
      String externalId,
      Category category,
      String summary,
      String message,
      List<Tag> tags,
      List<Link> links,
      List<CodePointer> codePointers,
      List<Action> actions,
      List<Fix> fixes) {
    ExternalId = externalId;
    this.category = category;
    this.summary = summary;
    this.message = message;
    this.tags = tags;
    this.links = links;
    this.codePointers = codePointers;
    this.actions = actions;
    this.fixes = fixes;
  }

  public String getExternalId() {
    return ExternalId;
  }

  public void setExternalId(String externalId) {
    ExternalId = externalId;
  }

  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<Tag> getTags() {
    return tags;
  }

  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  public List<Link> getLinks() {
    return links;
  }

  public void setLinks(List<Link> links) {
    this.links = links;
  }

  public List<CodePointer> getCodePointers() {
    return codePointers;
  }

  public void setCodePointers(List<CodePointer> codePointers) {
    this.codePointers = codePointers;
  }

  public List<Action> getActions() {
    return actions;
  }

  public void setActions(List<Action> actions) {
    this.actions = actions;
  }

  public List<Fix> getFixes() {
    return fixes;
  }

  public void setFixes(List<Fix> fixes) {
    this.fixes = fixes;
  }

  enum Category {
    SUCCESS,
    INFO,
    WARNING,
    ERROR;

    public static Category fromResult(Result res) {
      switch (res.toString()) {
        case "SUCCESS":
          return SUCCESS;
        case "UNSTABLE":
          return WARNING;
        case "FAILURE":
          return ERROR;
        default:
          return INFO;
      }
    }
  }
}
