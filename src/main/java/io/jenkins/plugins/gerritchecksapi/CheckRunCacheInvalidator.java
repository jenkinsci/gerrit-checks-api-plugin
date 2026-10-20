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

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.Objects;

/**
 * Drops what was collected for the patchset of a build when the build starts and when it finishes.
 *
 * <p>A rerun is triggered at Jenkins and not through this plugin, so nothing else tells the plugin
 * that the runs of a patchset have changed: what was collected before it would be reported until it
 * expires, without the build that was just triggered and, the stages being reported as results of
 * their build, without its stages. The build that finished changed the stages it reports as well.
 *
 * <p>Which patchset a build reports for is what gerrit-trigger passes to the builds it triggers,
 * the change and the patchset number. A build that has none of them is reported as a downstream
 * build of one that has, so the build that triggered it is asked; a build that is not from Gerrit
 * at all changes nothing.
 */
@Extension
public class CheckRunCacheInvalidator extends RunListener<Run<?, ?>> {
  /** Parameters gerrit-trigger passes to the builds it triggers. */
  static final String GERRIT_CHANGE_NUMBER = "GERRIT_CHANGE_NUMBER";

  static final String GERRIT_PATCHSET_NUMBER = "GERRIT_PATCHSET_NUMBER";

  @Override
  public void onStarted(Run<?, ?> run, TaskListener listener) {
    invalidate(run);
  }

  @Override
  public void onCompleted(Run<?, ?> run, TaskListener listener) {
    invalidate(run);
  }

  static void invalidate(Run<?, ?> run) {
    PatchSetId ps = patchSetOf(run);
    if (ps != null) {
      CachingCheckRunCollector.invalidate(ps);
    }
  }

  /** The patchset the build reports for, or null when it is not a build of a Gerrit event. */
  private static PatchSetId patchSetOf(Run<?, ?> run) {
    // The build that triggered this one, and the one that triggered that, up to the
    // build gerrit-trigger started for the event.
    for (Run<?, ?> reported = run; reported != null; reported = upstreamOf(reported)) {
      PatchSetId ps = patchSetOfParameters(reported.getAction(ParametersAction.class));
      if (ps != null) {
        return ps;
      }
    }
    return null;
  }

  private static PatchSetId patchSetOfParameters(ParametersAction parameters) {
    if (parameters == null) {
      return null;
    }
    Integer changeNumber = number(parameters, GERRIT_CHANGE_NUMBER);
    Integer patchSetNumber = number(parameters, GERRIT_PATCHSET_NUMBER);
    if (changeNumber == null || patchSetNumber == null) {
      return null;
    }
    return PatchSetId.create(changeNumber, patchSetNumber);
  }

  private static Run<?, ?> upstreamOf(Run<?, ?> run) {
    for (Cause cause : run.getCauses()) {
      if (cause instanceof Cause.UpstreamCause) {
        return ((Cause.UpstreamCause) cause).getUpstreamRun();
      }
    }
    return null;
  }

  /** The parameter as a number, or null when it is absent or is not one. */
  private static Integer number(ParametersAction parameters, String name) {
    ParameterValue parameter = parameters.getParameter(name);
    if (parameter == null) {
      return null;
    }
    try {
      return Integer.valueOf(Objects.toString(parameter.getValue(), "").trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
