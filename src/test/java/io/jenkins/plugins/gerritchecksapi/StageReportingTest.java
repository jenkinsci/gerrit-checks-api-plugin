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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StageReportingTest {

  @Test
  void defaults_reportEveryStageAndSkipNothing() {
    StageReporting defaults = StageReporting.defaults();

    assertTrue(defaults.isReportStagesEnabled());
    assertEquals(0, defaults.maxReportedDepth(), "0 means every stage");
    assertFalse(defaults.isSkipDeclarativeStagesEnabled());
  }

  @Test
  void overlay_keepsTheValuesThatAreInherited() {
    StageReporting inherited = new StageReporting();
    inherited.setReportStages(Inheritable.ENABLED);
    inherited.setMaxDepth(3);
    StageReporting overriding =
        new StageReporting(Inheritable.DISABLED, StageDepth.TOP_LEVEL, 0, Inheritable.INHERIT);

    inherited.overlay(overriding);

    assertFalse(inherited.isReportStagesEnabled(), "A value that is set wins");
    assertEquals(1, inherited.maxReportedDepth(), "A value that is set wins");
    assertEquals(3, inherited.getMaxDepth(), "A value that is not set is inherited");
    assertFalse(inherited.isSkipDeclarativeStagesEnabled());
  }

  @Test
  void overlay_ofNothing_changesNothing() {
    StageReporting settings = new StageReporting();
    settings.setReportStages(Inheritable.DISABLED);

    settings.overlay(null);

    assertFalse(settings.isReportStagesEnabled());
  }

  @Test
  void maxReportedDepth_topLevelIsOne() {
    StageReporting settings = new StageReporting(Inheritable.ENABLED, StageDepth.TOP_LEVEL, 5, Inheritable.INHERIT);

    assertEquals(1, settings.maxReportedDepth());
  }

  @Test
  void maxReportedDepth_withoutAValueMeansEveryStage() {
    StageReporting settings = new StageReporting(Inheritable.ENABLED, StageDepth.MAX_DEPTH, 0, Inheritable.INHERIT);

    assertEquals(0, settings.maxReportedDepth());
  }

  @Test
  void enumValues_areInheritedWhenNotSet() {
    StageReporting settings = new StageReporting(null, null, 0, null);

    assertEquals(Inheritable.INHERIT, settings.getReportStages());
    assertEquals(StageDepth.INHERIT, settings.getStageDepth());
    assertEquals(Inheritable.INHERIT, settings.getSkipDeclarativeStages());
    assertTrue(StageReporting.defaults().maxReportedDepth() == 0);
  }
}
