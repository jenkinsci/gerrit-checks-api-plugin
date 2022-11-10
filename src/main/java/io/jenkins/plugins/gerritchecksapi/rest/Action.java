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

import com.google.gson.JsonObject;

public abstract class Action {
  private final String name;
  private final String tooltip;
  private final boolean primary;
  private final boolean summary;
  private final String method;

  private boolean disabled;
  private String url;
  private JsonObject data;

  protected Action(String name, String tooltip, boolean primary, boolean summary, String method) {
    this.name = name;
    this.tooltip = tooltip;
    this.primary = primary;
    this.summary = summary;
    this.method = method;
  }

  protected Action(
      String name,
      String tooltip,
      boolean primary,
      boolean summary,
      String method,
      boolean disabled,
      String url,
      JsonObject data) {
    this.name = name;
    this.tooltip = tooltip;
    this.primary = primary;
    this.summary = summary;
    this.method = method;
    this.disabled = disabled;
    this.url = url;
    this.data = data;
  }

  public String getName() {
    return name;
  }

  public String getTooltip() {
    return tooltip;
  }

  public boolean isPrimary() {
    return primary;
  }

  public boolean isSummary() {
    return summary;
  }

  public String getMethod() {
    return method;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public void setDisabled(boolean disabled) {
    this.disabled = disabled;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public JsonObject getData() {
    return data;
  }

  public void setData(JsonObject data) {
    this.data = data;
  }
}
