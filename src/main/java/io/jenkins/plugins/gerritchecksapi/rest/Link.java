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

public class Link {
  private String url;
  private String tooltip;
  private boolean primary;
  private LinkIcon icon;

  public Link() {}

  public Link(String url, String tooltip, boolean primary, LinkIcon icon) {
    this.url = url;
    this.tooltip = tooltip;
    this.primary = primary;
    this.icon = icon;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getTooltip() {
    return tooltip;
  }

  public void setTooltip(String tooltip) {
    this.tooltip = tooltip;
  }

  public boolean isPrimary() {
    return primary;
  }

  public void setPrimary(boolean primary) {
    this.primary = primary;
  }

  public LinkIcon getIcon() {
    return icon;
  }

  public void setIcon(LinkIcon icon) {
    this.icon = icon;
  }

  enum LinkIcon {
    EXTERNAL,
    IMAGE,
    HISTORY,
    DOWNLOAD,
    DOWNLOAD_MOBILE,
    HELP_PAGE,
    REPORT_BUG,
    CODE,
    FILE_PRESENT
  }
}
