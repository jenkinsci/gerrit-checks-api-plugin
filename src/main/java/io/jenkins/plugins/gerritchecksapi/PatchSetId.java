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

import java.util.Objects;

// TODO: convert this to @AutoValue
public class PatchSetId {

  public static PatchSetId create(int changeId, int patchSetNumber) {
    return new PatchSetId(changeId, patchSetNumber);
  }

  private final int changeId;
  private final int patchSetNumber;

  private PatchSetId(int changeId, int patchSetNumber) {
    this.changeId = changeId;
    this.patchSetNumber = patchSetNumber;
  }

  public int changeId() {
    return changeId;
  }

  public int patchSetNumber() {
    return patchSetNumber;
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeId, patchSetNumber);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof PatchSetId) {
      PatchSetId o = (PatchSetId) obj;
      return o.changeId == changeId && o.patchSetNumber == patchSetNumber;
    }

    return false;
  }

  public String toRef() {
    return String.format("%02d/%d/%d", changeId % 100, changeId, patchSetNumber);
  }
}
