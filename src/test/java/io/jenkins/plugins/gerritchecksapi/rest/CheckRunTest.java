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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.gerritchecksapi.rest.CheckRun.RunStatus;
import org.junit.jupiter.api.Test;

class CheckRunTest {

  @Test
  void isBuilding_whenJenkinsIsBuildingTheRunOrStillHasTo() {
    assertTrue(checkRun(RunStatus.RUNNING).isBuilding());
    assertTrue(checkRun(RunStatus.SCHEDULED).isBuilding());
  }

  @Test
  void isBuilding_isFalseForARunThatFinished() {
    assertFalse(checkRun(RunStatus.COMPLETED).isBuilding());
  }

  @Test
  void isBuilding_isFalseForARunThatWasNotTriggered() {
    assertFalse(checkRun(RunStatus.RUNNABLE).isBuilding());
  }

  private static CheckRun checkRun(RunStatus status) {
    CheckRun run = new CheckRun();
    run.setStatus(status);
    return run;
  }
}
