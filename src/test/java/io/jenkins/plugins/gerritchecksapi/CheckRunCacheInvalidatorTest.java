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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.Cause;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckRunCacheInvalidatorTest {

  private final CheckRunCollector direct = mock(CheckRunCollector.class);
  private final CachingCheckRunCollector caching = new CachingCheckRunCollector(direct);
  private final CheckRunCacheInvalidator listener = new CheckRunCacheInvalidator();

  @BeforeEach
  void setUp() {
    // A collection that is not empty, or there would be nothing to serve from the cache.
    when(direct.collectFor(any())).thenReturn(Map.of());
  }

  @Test
  void collectFor_isServedFromTheCacheUntilItExpires() {
    PatchSetId ps = PatchSetId.create(101, 4);

    caching.collectFor(ps);
    caching.collectFor(ps);

    verify(direct, times(1)).collectFor(ps);
  }

  @Test
  void onStarted_dropsWhatWasCollectedForThePatchsetOfTheBuild() {
    PatchSetId ps = PatchSetId.create(102, 4);
    caching.collectFor(ps);

    listener.onStarted(buildOf(ps), null);

    caching.collectFor(ps);
    verify(direct, times(2)).collectFor(ps);
  }

  @Test
  void onCompleted_dropsWhatWasCollectedForThePatchsetOfTheBuild() {
    PatchSetId ps = PatchSetId.create(103, 4);
    caching.collectFor(ps);

    listener.onCompleted(buildOf(ps), null);

    caching.collectFor(ps);
    verify(direct, times(2)).collectFor(ps);
  }

  @Test
  void onCompleted_ofADownstreamBuild_dropsWhatWasCollectedForItsPatchset() {
    PatchSetId ps = PatchSetId.create(104, 4);
    caching.collectFor(ps);

    listener.onCompleted(downstreamOf(buildOf(ps)), null);

    caching.collectFor(ps);
    verify(direct, times(2)).collectFor(ps);
  }

  @Test
  void onStarted_keepsWhatWasCollectedForAnotherPatchset() {
    PatchSetId ps = PatchSetId.create(105, 4);
    caching.collectFor(ps);

    listener.onStarted(buildOf(PatchSetId.create(106, 4)), null);

    caching.collectFor(ps);
    verify(direct, times(1)).collectFor(ps);
  }

  @Test
  void onStarted_ofABuildThatIsNotFromGerrit_keepsTheCache() {
    PatchSetId ps = PatchSetId.create(107, 4);
    caching.collectFor(ps);

    listener.onStarted(run(Map.of(), null), null);

    caching.collectFor(ps);
    verify(direct, times(1)).collectFor(ps);
  }

  @Test
  void onStarted_ofABuildWhoseParametersAreNotNumbers_keepsTheCache() {
    PatchSetId ps = PatchSetId.create(108, 4);
    caching.collectFor(ps);

    listener.onStarted(
        run(
            Map.of(
                CheckRunCacheInvalidator.GERRIT_CHANGE_NUMBER, "not a number",
                CheckRunCacheInvalidator.GERRIT_PATCHSET_NUMBER, "4"),
            null),
        null);

    caching.collectFor(ps);
    verify(direct, times(1)).collectFor(ps);
  }

  /** A build of a patchset, as gerrit-trigger starts it. */
  private static Run<?, ?> buildOf(PatchSetId ps) {
    return run(
        Map.of(
            CheckRunCacheInvalidator.GERRIT_CHANGE_NUMBER, "" + ps.changeId(),
            CheckRunCacheInvalidator.GERRIT_PATCHSET_NUMBER, "" + ps.patchSetNumber()),
        null);
  }

  /** A build that the given build triggered, as the collector discovers downstream builds. */
  private static Run<?, ?> downstreamOf(Run<?, ?> upstream) {
    return run(Map.of(), upstream);
  }

  private static Run<?, ?> run(Map<String, String> parameters, Run<?, ?> upstream) {
    List<ParameterValue> values = new ArrayList<>();
    for (Map.Entry<String, String> parameter : parameters.entrySet()) {
      values.add(new StringParameterValue(parameter.getKey(), parameter.getValue()));
    }
    Run<?, ?> run = mock(Run.class);
    when(run.getAction(ParametersAction.class))
        .thenReturn(values.isEmpty() ? null : new ParametersAction(values));
    if (upstream == null) {
      when(run.getCauses()).thenReturn(List.of());
    } else {
      Cause.UpstreamCause cause = mock(Cause.UpstreamCause.class);
      // doReturn, which does not check the type of what the stub returns: the
      // run of the cause is a wildcard of the one that is stubbed.
      doReturn(upstream).when(cause).getUpstreamRun();
      when(run.getCauses()).thenReturn(List.of(cause));
    }
    return run;
  }
}
