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

public class Fix {
  private String description;
  private List<Replacement> replacements;

  public Fix() {}

  public Fix(String description, List<Replacement> replacements) {
    this.description = description;
    this.replacements = replacements;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<Replacement> getReplacements() {
    return replacements;
  }

  public void setReplacements(List<Replacement> replacements) {
    this.replacements = replacements;
  }
}
