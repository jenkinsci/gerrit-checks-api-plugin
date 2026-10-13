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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.Job;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CachingCheckRunCollectorTest {

  private final CheckRunCollector direct = mock(CheckRunCollector.class);
  private final CachingCheckRunCollector caching = new CachingCheckRunCollector(direct);

  @Test
  void collectFor_isServedFromTheCacheUntilItExpires() {
    PatchSetId ps = PatchSetId.create(201, 4);
    // Built before the when() chain: Mockito does not allow nested stubbing.
    Map<Job<?, ?>, List<CheckRun>> collected = collectionOf(runBuilding(false));
    when(direct.collectFor(ps)).thenReturn(collected);

    caching.collectFor(ps);
    caching.collectFor(ps);

    verify(direct, times(1)).collectFor(ps);
  }

  @Test
  void collectFor_ofABuildThatIsStillBuilding_isCollectedAgainByTheNextPoll() {
    PatchSetId ps = PatchSetId.create(202, 4);
    Map<Job<?, ?>, List<CheckRun>> collected = collectionOf(runBuilding(true));
    when(direct.collectFor(ps)).thenReturn(collected);

    caching.collectFor(ps);
    caching.collectFor(ps);

    verify(direct, times(2)).collectFor(ps);
  }

  @Test
  void collectFor_ofOneBuildingRunAmongOthers_isCollectedAgainByTheNextPoll() {
    PatchSetId ps = PatchSetId.create(203, 4);
    Map<Job<?, ?>, List<CheckRun>> collected =
        collectionOf(runBuilding(false), runBuilding(true));
    when(direct.collectFor(ps)).thenReturn(collected);

    caching.collectFor(ps);
    caching.collectFor(ps);

    verify(direct, times(2)).collectFor(ps);
  }

  private static Map<Job<?, ?>, List<CheckRun>> collectionOf(CheckRun... runs) {
    Job<?, ?> job = mock(Job.class);
    return Map.of(job, List.of(runs));
  }

  private static CheckRun runBuilding(boolean building) {
    CheckRun run = mock(CheckRun.class);
    when(run.isBuilding()).thenReturn(building);
    return run;
  }
}
