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
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Configures which stages of a Pipeline build are reported to Gerrit for every job in a folder.
 *
 * <p>A multibranch Pipeline configures its branches from the project it is defined in, and its
 * branch jobs cannot be configured individually, so this is where such a pipeline is configured.
 * Jobs in the folder inherit the settings unless they set their own.
 */
public class StageReportingFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {
  private final StageReporting stageReporting;

  @DataBoundConstructor
  public StageReportingFolderProperty(
      Inheritable reportStages,
      Inheritable reportStagesWhileBuilding,
      StageForm stageForm,
      StageDepth stageDepth,
      int maxDepth,
      Inheritable skipDeclarativeStages) {
    this.stageReporting =
        new StageReporting(
            reportStages,
            reportStagesWhileBuilding,
            stageForm,
            stageDepth,
            maxDepth,
            skipDeclarativeStages);
  }

  public StageReporting getStageReporting() {
    return stageReporting;
  }

  // For the configuration form, which binds to the fields of the property.
  public Inheritable getReportStages() {
    return stageReporting.getReportStages();
  }

  public Inheritable getReportStagesWhileBuilding() {
    return stageReporting.getReportStagesWhileBuilding();
  }

  public StageForm getStageForm() {
    return stageReporting.getStageForm();
  }

  public StageDepth getStageDepth() {
    return stageReporting.getStageDepth();
  }

  public int getMaxDepth() {
    return stageReporting.getMaxDepth();
  }

  public Inheritable getSkipDeclarativeStages() {
    return stageReporting.getSkipDeclarativeStages();
  }

  @Extension
  public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
    @Override
    public String getDisplayName() {
      return Messages.StageReporting_displayName();
    }
  }
}
