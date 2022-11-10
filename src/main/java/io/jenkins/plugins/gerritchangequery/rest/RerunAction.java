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

package io.jenkins.plugins.gerritchangequery.rest;

public class RerunAction extends Action {
  public static final String NAME = "Rerun";
  public static final String TOOLTIP = "Run the build for the patchset again.";
  public static final boolean PRIMARY = true;
  public static final boolean SUMMARY = false;
  public static final String METHOD = "POST";

  protected RerunAction() {
    super(NAME, TOOLTIP, PRIMARY, SUMMARY, METHOD);
  }

  protected RerunAction(boolean disabled, String url) {
    super(NAME, TOOLTIP, PRIMARY, SUMMARY, METHOD, disabled, url, null);
  }
}
