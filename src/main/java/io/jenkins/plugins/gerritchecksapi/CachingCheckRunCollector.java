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
  /**
   * What was collected for a patchset, so that the polls of the Gerrit UI do not run the search
   * for every one of them.
   *
   * <p>Static, so that a build that starts or finishes can drop the entry of its patchset through
   * {@link #invalidate(PatchSetId)}: the collector is created by the injector of the REST endpoint,
   * which a listener of the builds, registered by Jenkins on its own, is not part of.
   */
  private static final Cache<PatchSetId, Map<Job<?, ?>, List<CheckRun>>> cache =
      Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();

  private final CheckRunCollector directCollector;

  @Inject
  CachingCheckRunCollector(@Direct CheckRunCollector directCollector) {
    this.directCollector = directCollector;
  }

  @Override
  public Map<Job<?, ?>, List<CheckRun>> collectFor(PatchSetId ps) {
    Map<Job<?, ?>, List<CheckRun>> collected = cache.get(ps, p -> directCollector.collectFor(p));
    if (isBuilding(collected)) {
      // What a running build reports changes with every stage it goes through, so
      // what was collected of it is kept only for this poll: the next one is
      // served the stages the build had reached by then otherwise.
      cache.invalidate(ps);
    }
    return collected;
  }

  /** Whether any of the collected runs is still building. */
  private static boolean isBuilding(Map<Job<?, ?>, List<CheckRun>> collected) {
    return collected.values().stream()
        .flatMap(List::stream)
        .anyMatch(CheckRun::isBuilding);
  }

  /**
   * Drops what was collected for a patchset, so that the next poll collects it again. A build that
   * starts is what a patchset reports from then on — the rerun the user is waiting for — and one
   * that finishes reports the stages it ran last; both would otherwise be served from the
   * collection made before them until it expires.
   */
  public static void invalidate(PatchSetId ps) {
    cache.invalidate(ps);
  }
}
