// Copyright (C) 2026 Amarula Solutions
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
import hudson.model.Action;
import hudson.model.Run;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;

@SuppressWarnings("rawtypes")
@Extension
public class DownstreamRetriggerActionFactory
    extends TransientActionFactory<Run> {

  @Override
  public Class<Run> type() {
    return Run.class;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Collection<? extends Action> createFor(Run target) {
    if (hasExistingRetriggerAction(target)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(
        new DownstreamRetriggerAction(target));
  }

  private static boolean hasExistingRetriggerAction(Run<?, ?> run) {
    for (Action action : run.getActions()) {
      if ("gerrit-trigger-retrigger-this".equals(action.getUrlName())) {
        return true;
      }
    }
    return false;
  }
}
