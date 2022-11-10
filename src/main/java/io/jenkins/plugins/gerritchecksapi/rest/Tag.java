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

public class Tag {
  private String name;
  private String tooltip;
  private TagColor color;

  public Tag() {}

  public Tag(String name, String tooltip, TagColor color) {
    this.name = name;
    this.tooltip = tooltip;
    this.color = color;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTooltip() {
    return tooltip;
  }

  public void setTooltip(String tooltip) {
    this.tooltip = tooltip;
  }

  public TagColor getColor() {
    return color;
  }

  public void setColor(TagColor color) {
    this.color = color;
  }

  enum TagColor {
    GRAY,
    YELLOW,
    PINK,
    PURPLE,
    CYAN,
    BROWN
  }
}
