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

/**
 * How the stages of a Pipeline build are reported to Gerrit.
 *
 * <p>Gerrit lists check runs as a flat list, so the stages of a build are reported as results of
 * the run of the build: one row per build, and the stages inside it. A stage can also be reported
 * as a check run of its own, which gives it a row and a status of its own, as it was before the
 * results were introduced.
 */
public enum StageForm {
  /** Leave the setting to the enclosing folder and finally to the global configuration. */
  INHERIT,
  /** Report a stage as a result of the run of its build. */
  RESULTS,
  /** Report a stage as a check run of its own, nested below the run of its build. */
  CHECK_RUNS;

  public boolean isSet() {
    return this != INHERIT;
  }

  /** Label of the option in the configuration forms. */
  public String getDisplayName() {
    switch (this) {
      case RESULTS:
        return Messages.StageForm_RESULTS();
      case CHECK_RUNS:
        return Messages.StageForm_CHECK_RUNS();
      default:
        return Messages.StageForm_INHERIT();
    }
  }
}
