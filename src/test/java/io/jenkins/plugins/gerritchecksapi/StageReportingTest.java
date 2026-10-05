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

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageReportingTest {

  @Test
  void defaults_reportEveryStageAndSkipNothing() {
    StageReporting defaults = StageReporting.defaults();

    assertTrue(defaults.isReportStagesEnabled());
    assertTrue(defaults.isReportStagesWhileBuildingEnabled(), "A running build shows its stages");
    assertFalse(defaults.reportsStagesAsCheckRuns(), "The stages are results of their run");
    assertEquals(0, defaults.maxReportedDepth(), "0 means every stage");
    assertFalse(defaults.isSkipDeclarativeStagesEnabled());
  }

  @Test
  void overlay_keepsTheValuesThatAreInherited() {
    StageReporting inherited = new StageReporting();
    inherited.setReportStages(Inheritable.ENABLED);
    inherited.setReportStagesWhileBuilding(Inheritable.ENABLED);
    inherited.setMaxDepth(3);
    StageReporting overriding =
        new StageReporting(
            Inheritable.DISABLED,
            Inheritable.DISABLED,
            StageForm.CHECK_RUNS,
            StageDepth.TOP_LEVEL,
            0,
            Inheritable.INHERIT);

    inherited.overlay(overriding);

    assertFalse(inherited.isReportStagesEnabled(), "A value that is set wins");
    assertFalse(
        inherited.isReportStagesWhileBuildingEnabled(), "A value that is set wins");
    assertTrue(inherited.reportsStagesAsCheckRuns(), "A value that is set wins");
    assertEquals(1, inherited.maxReportedDepth(), "A value that is set wins");
    assertEquals(3, inherited.getMaxDepth(), "A value that is not set is inherited");
    assertFalse(inherited.isSkipDeclarativeStagesEnabled());
  }

  @Test
  void overlay_ofAJobConfiguredBeforeTheSettingExisted_keepsTheStagesWhileBuilding()
      throws Exception {
    // A job that was configured before the setting existed has no value for it:
    // the XML it was saved as has none, and the field is left empty rather than
    // set by the constructor, which is what the reflection here reproduces.
    StageReporting inherited = StageReporting.defaults();
    StageReporting savedBeforeTheSettingExisted = new StageReporting();
    savedBeforeTheSettingExisted.setReportStages(Inheritable.DISABLED);
    for (String field : List.of("reportStagesWhileBuilding", "stageForm")) {
      Field emptyField = StageReporting.class.getDeclaredField(field);
      emptyField.setAccessible(true);
      emptyField.set(savedBeforeTheSettingExisted, null);
    }

    inherited.overlay(savedBeforeTheSettingExisted);

    assertFalse(inherited.isReportStagesEnabled(), "A value that is set wins");
    assertTrue(
        inherited.isReportStagesWhileBuildingEnabled(),
        "An empty value reads as inherited, not as disabled");
    assertFalse(
        inherited.reportsStagesAsCheckRuns(),
        "An empty value reads as inherited, not as check runs");
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
    StageReporting settings =
        new StageReporting(
            Inheritable.ENABLED,
            Inheritable.ENABLED,
            StageForm.RESULTS,
            StageDepth.TOP_LEVEL,
            5,
            Inheritable.INHERIT);

    assertEquals(1, settings.maxReportedDepth());
  }

  @Test
  void maxReportedDepth_withoutAValueMeansEveryStage() {
    StageReporting settings =
        new StageReporting(
            Inheritable.ENABLED,
            Inheritable.ENABLED,
            StageForm.RESULTS,
            StageDepth.MAX_DEPTH,
            0,
            Inheritable.INHERIT);

    assertEquals(0, settings.maxReportedDepth());
  }

  @Test
  void enumValues_areInheritedWhenNotSet() {
    StageReporting settings = new StageReporting(null, null, null, null, 0, null);

    assertEquals(Inheritable.INHERIT, settings.getReportStages());
    assertEquals(Inheritable.INHERIT, settings.getReportStagesWhileBuilding());
    assertEquals(StageForm.INHERIT, settings.getStageForm());
    assertEquals(StageDepth.INHERIT, settings.getStageDepth());
    assertEquals(Inheritable.INHERIT, settings.getSkipDeclarativeStages());
    assertTrue(StageReporting.defaults().maxReportedDepth() == 0);
  }
}
