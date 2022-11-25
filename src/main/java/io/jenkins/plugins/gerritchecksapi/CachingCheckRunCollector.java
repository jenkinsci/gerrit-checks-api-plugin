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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import hudson.model.Job;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class CachingCheckRunCollector implements CheckRunCollector {
  private final Cache<PatchSetId, Map<Job<?, ?>, List<CheckRun>>> cache =
      Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();

  private final CheckRunCollector directCollector;

  @Inject
  CachingCheckRunCollector(@Direct CheckRunCollector directCollector) {
    this.directCollector = directCollector;
  }

  public Map<Job<?, ?>, List<CheckRun>> collectFor(int change, int patchset) {
    return cache.get(
        PatchSetId.create(change, patchset),
        ps -> directCollector.collectFor(ps.changeId(), ps.patchSetNumber()));
  }
}
