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

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads the stage reporting settings from the folders a job lives in.
 *
 * <p>Every class referencing the folder plugin is confined to this class, so that the plugin also
 * works without it. Callers have to check that the plugin is installed first, see
 * {@link GerritChecksConfiguration#resolve}.
 */
final class FolderStageReporting {

  private FolderStageReporting() {}

  /**
  * The settings of the folders enclosing the job, with the settings of the innermost folder
  * winning.
  *
  * @return the settings of the enclosing folders, or null when none of them sets anything
  */
  static StageReporting of(Job<?, ?> job) {
    // Innermost folder first: the outermost one is applied last, so that the
    // innermost one wins.
    List<StageReporting> settings = new ArrayList<>();
    ItemGroup<?> parent = job.getParent();
    while (parent instanceof AbstractFolder) {
      AbstractFolder<?> folder = (AbstractFolder<?>) parent;
      StageReportingFolderProperty property = folder.getProperties().get(StageReportingFolderProperty.class);
      if (property != null) {
        settings.add(property.getStageReporting());
      }
      parent = folder.getParent();
    }
    if (settings.isEmpty()) {
      return null;
    }
    Collections.reverse(settings);
    StageReporting resolved = new StageReporting();
    for (StageReporting setting : settings) {
      resolved.overlay(setting);
    }
    return resolved;
  }
}
