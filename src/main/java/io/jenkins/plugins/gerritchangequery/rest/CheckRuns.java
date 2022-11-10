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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CheckRuns {
  private List<CheckRun> runs = new ArrayList<>();

  public CheckRuns() {}

  public CheckRuns(List<CheckRun> runs) {
    this.runs = runs;
  }

  public boolean addRun(CheckRun run) {
    return runs.add(run);
  }

  public boolean addRuns(Collection<CheckRun> runs) {
    return this.runs.addAll(runs);
  }

  public List<CheckRun> getRuns() {
    return runs;
  }

  public void setRuns(List<CheckRun> runs) {
    this.runs = runs;
  }
}
