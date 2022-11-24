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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hudson.model.Job;
import io.jenkins.plugins.gerritchecksapi.rest.CheckRun;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;

@Singleton
public class PermissionAwareCheckRunCollector implements CheckRunCollector {

  private final CheckRunCollector cachingCollector;
  private final Jenkins jenkins;

  @Inject
  PermissionAwareCheckRunCollector(@Caching CheckRunCollector cachingCollector, Jenkins jenkins) {
    this.cachingCollector = cachingCollector;
    this.jenkins = jenkins;
  }

  @Override
  public Map<Job<?, ?>, List<CheckRun>> collectFor(int change, int patchset) {
    Map<Job<?, ?>, List<CheckRun>> result = new HashMap<>();
    Map<Job<?, ?>, List<CheckRun>> cached = cachingCollector.collectFor(change, patchset);
    for (Map.Entry<Job<?, ?>, List<CheckRun>> entry : cached.entrySet()) {
      if (jenkins.getAuthorizationStrategy().getACL(entry.getKey()).hasPermission(Job.READ)) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }
}
