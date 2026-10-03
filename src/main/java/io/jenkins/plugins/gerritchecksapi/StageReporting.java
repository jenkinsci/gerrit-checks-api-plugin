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

import java.io.Serializable;

/**
 * The settings controlling which stages of a Pipeline build are reported to Gerrit as check runs.
 *
 * <p>A value that is left as {@code INHERIT} is taken from the enclosing folder, then from the
 * global configuration and finally from {@link #defaults()}, which reports every stage.
 */
public class StageReporting implements Serializable {
  private static final long serialVersionUID = 1L;

  private Inheritable reportStages = Inheritable.INHERIT;
  private StageDepth stageDepth = StageDepth.INHERIT;
  private int maxDepth;
  private Inheritable skipDeclarativeStages = Inheritable.INHERIT;

  public StageReporting() {}

  public StageReporting(
      Inheritable reportStages, StageDepth stageDepth, int maxDepth, Inheritable skipDeclarativeStages) {
    setReportStages(reportStages);
    setStageDepth(stageDepth);
    setMaxDepth(maxDepth);
    setSkipDeclarativeStages(skipDeclarativeStages);
  }

  /** The settings used where nothing is configured at all. */
  public static StageReporting defaults() {
    StageReporting defaults = new StageReporting();
    defaults.reportStages = Inheritable.ENABLED;
    defaults.stageDepth = StageDepth.ALL;
    defaults.skipDeclarativeStages = Inheritable.DISABLED;
    return defaults;
  }

  /**
  * Copies the values that the other settings set on top of these. Settings that are left to be
  * inherited keep the value they had, so that earlier settings act as the fallback.
  */
  public void overlay(StageReporting other) {
    if (other == null) {
      return;
    }
    if (other.reportStages.isSet()) {
      reportStages = other.reportStages;
    }
    if (other.stageDepth.isSet()) {
      stageDepth = other.stageDepth;
    }
    if (other.maxDepth > 0) {
      maxDepth = other.maxDepth;
    }
    if (other.skipDeclarativeStages.isSet()) {
      skipDeclarativeStages = other.skipDeclarativeStages;
    }
  }

  /** Whether the stages of a build are reported at all. */
  public boolean isReportStagesEnabled() {
    return reportStages.isEnabled();
  }

  /**
  * The deepest nesting of stages that is reported, where 1 is a stage that is not nested inside
  * another stage. A value of 0 means that every stage is reported, however deeply nested.
  */
  public int maxReportedDepth() {
    switch (stageDepth) {
      case TOP_LEVEL:
        return 1;
      case MAX_DEPTH:
        // A maximum without a value is no maximum.
        return Math.max(maxDepth, 0);
      default:
        return 0;
    }
  }

  /** Whether the stages that Jenkins itself adds to a declarative pipeline are left out. */
  public boolean isSkipDeclarativeStagesEnabled() {
    return skipDeclarativeStages.isEnabled();
  }

  public Inheritable getReportStages() {
    return reportStages;
  }

  public void setReportStages(Inheritable reportStages) {
    this.reportStages = reportStages == null ? Inheritable.INHERIT : reportStages;
  }

  public StageDepth getStageDepth() {
    return stageDepth;
  }

  public void setStageDepth(StageDepth stageDepth) {
    this.stageDepth = stageDepth == null ? StageDepth.INHERIT : stageDepth;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public Inheritable getSkipDeclarativeStages() {
    return skipDeclarativeStages;
  }

  public void setSkipDeclarativeStages(Inheritable skipDeclarativeStages) {
    this.skipDeclarativeStages = skipDeclarativeStages == null ? Inheritable.INHERIT : skipDeclarativeStages;
  }
}
