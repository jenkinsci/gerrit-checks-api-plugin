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
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * The global defaults for which stages of a Pipeline build are reported to Gerrit. Jobs and folders
 * can override them, see {@link StageReportingJobProperty} and {@link StageReportingFolderProperty}.
 */
@Extension
public class GerritChecksConfiguration extends GlobalConfiguration {
  /** Plugin providing the folders whose settings a job can inherit. */
  private static final String FOLDER_PLUGIN = "cloudbees-folder";

  private Inheritable reportStages = Inheritable.ENABLED;
  private Inheritable reportStagesWhileBuilding = Inheritable.ENABLED;
  private StageForm stageForm = StageForm.RESULTS;
  private StageDepth stageDepth = StageDepth.ALL;
  private int maxDepth;
  private Inheritable skipDeclarativeStages = Inheritable.DISABLED;

  public GerritChecksConfiguration() {
    load();
  }

  public static GerritChecksConfiguration get() {
    return GlobalConfiguration.all().get(GerritChecksConfiguration.class);
  }

  /**
  * The settings that apply to a job, with everything it inherits resolved: what the job sets
  * itself, then what the folders it lives in set, then the global configuration and finally the
  * built-in defaults.
  */
  public static StageReporting resolve(Job<?, ?> job) {
    StageReporting resolved = StageReporting.defaults();
    Jenkins jenkins = Jenkins.getInstanceOrNull();
    if (jenkins != null) {
      GerritChecksConfiguration configuration = get();
      if (configuration != null) {
        resolved.overlay(configuration.getStageReporting());
      }
      if (job != null && jenkins.getPlugin(FOLDER_PLUGIN) != null) {
        resolved.overlay(FolderStageReporting.of(job));
      }
    }
    if (job != null) {
      StageReportingJobProperty property = job.getProperty(StageReportingJobProperty.class);
      if (property != null) {
        resolved.overlay(property.getStageReporting());
      }
    }
    return resolved;
  }

  @Override
  public boolean configure(StaplerRequest2 req, JSONObject json) throws Descriptor.FormException {
    req.bindJSON(this, json);
    save();
    return true;
  }

  /** Only used with {@link StageDepth#MAX_DEPTH}. */
  public FormValidation doCheckMaxDepth(@QueryParameter String value) {
    if (value == null || value.trim().isEmpty()) {
      return FormValidation.ok();
    }
    try {
      if (Integer.parseInt(value.trim()) > 0) {
        return FormValidation.ok();
      }
    } catch (NumberFormatException e) {
      return FormValidation.error(Messages.StageReporting_maxDepthNotANumber());
    }
    return FormValidation.error(Messages.StageReporting_maxDepthTooSmall());
  }

  @Override
  public String getDisplayName() {
    return Messages.StageReporting_displayName();
  }

  public StageReporting getStageReporting() {
    return new StageReporting(
        reportStages,
        reportStagesWhileBuilding,
        stageForm,
        stageDepth,
        maxDepth,
        skipDeclarativeStages);
  }

  public Inheritable getReportStages() {
    return reportStages;
  }

  public void setReportStages(Inheritable reportStages) {
    this.reportStages = reportStages;
  }

  public Inheritable getReportStagesWhileBuilding() {
    return reportStagesWhileBuilding;
  }

  public void setReportStagesWhileBuilding(Inheritable reportStagesWhileBuilding) {
    this.reportStagesWhileBuilding = reportStagesWhileBuilding;
  }

  public StageForm getStageForm() {
    return stageForm;
  }

  public void setStageForm(StageForm stageForm) {
    this.stageForm = stageForm;
  }

  public StageDepth getStageDepth() {
    return stageDepth;
  }

  public void setStageDepth(StageDepth stageDepth) {
    this.stageDepth = stageDepth;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public Inheritable getSkipDeclarativeStages() {
    return skipDeclarativeStages;
  }

  public void setSkipDeclarativeStages(Inheritable skipDeclarativeStages) {
    this.skipDeclarativeStages = skipDeclarativeStages;
  }
}
