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
 * How many of the stages of a Pipeline build are reported. A pipeline with tens of nested stages is
 * difficult to read in Gerrit, so the nested ones can be left out.
 */
public enum StageDepth {
  /** Leave the setting to the enclosing folder and finally to the global configuration. */
  INHERIT,
  /** Report every stage, however deeply nested. */
  ALL,
  /** Report only the stages that are not nested inside another stage. */
  TOP_LEVEL,
  /** Report stages up to {@link StageReporting#getMaxDepth()} levels deep. */
  MAX_DEPTH;

  public boolean isSet() {
    return this != INHERIT;
  }

  /** Label of the option in the configuration forms. */
  public String getDisplayName() {
    switch (this) {
      case ALL:
        return Messages.StageDepth_ALL();
      case TOP_LEVEL:
        return Messages.StageDepth_TOP_LEVEL();
      case MAX_DEPTH:
        return Messages.StageDepth_MAX_DEPTH();
      default:
        return Messages.StageDepth_INHERIT();
    }
  }
}
