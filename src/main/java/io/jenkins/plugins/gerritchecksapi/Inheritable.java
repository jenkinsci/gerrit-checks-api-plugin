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

/**
 * A setting that a job or a folder can either set itself or leave to the enclosing folder and
 * finally to the global configuration.
 */
public enum Inheritable {
  INHERIT,
  ENABLED,
  DISABLED;

  public boolean isSet() {
    return this != INHERIT;
  }

  public boolean isEnabled() {
    return this == ENABLED;
  }

  public boolean isDisabled() {
    return this == DISABLED;
  }

  /** Label of the option in the configuration forms. */
  public String getDisplayName() {
    switch (this) {
      case ENABLED:
        return Messages.Inheritable_ENABLED();
      case DISABLED:
        return Messages.Inheritable_DISABLED();
      default:
        return Messages.Inheritable_INHERIT();
    }
  }
}
