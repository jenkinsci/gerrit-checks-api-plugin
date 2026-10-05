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
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Configures which stages of a Pipeline build are reported to Gerrit, for a single job. Values that
 * are left to be inherited are taken from the enclosing folder and finally from the global
 * configuration.
 */
public class StageReportingJobProperty extends JobProperty<Job<?, ?>> {
  private final StageReporting stageReporting;

  @DataBoundConstructor
  public StageReportingJobProperty(
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
  public static class DescriptorImpl extends JobPropertyDescriptor {
    @Override
    public String getDisplayName() {
      return Messages.StageReporting_displayName();
    }
  }
}
