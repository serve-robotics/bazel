// Copyright 2021 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.testutil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.actions.ActionExecutionContext;
import com.google.devtools.build.lib.actions.ActionExecutionException;
import com.google.devtools.build.lib.actions.ActionExecutionMetadata;
import com.google.devtools.build.lib.actions.ActionKeyContext;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Artifact.ArtifactExpander;
import com.google.devtools.build.lib.actions.EmptyRunfilesSupplier;
import com.google.devtools.build.lib.actions.MiddlemanType;
import com.google.devtools.build.lib.actions.RunfilesSupplier;
import com.google.devtools.build.lib.analysis.platform.ConstraintCollection.DuplicateConstraintException;
import com.google.devtools.build.lib.analysis.platform.PlatformInfo;
import com.google.devtools.build.lib.analysis.platform.PlatformInfo.ExecPropertiesException;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import java.util.Collection;
import javax.annotation.Nullable;
import net.starlark.java.syntax.Location;

/** A fake implementation of ResourceOwner that does nothing except give output strings. */
public class FakeResourceOwner implements ActionExecutionMetadata {

  private final String mnemonic;

  public FakeResourceOwner(String mnemonic) {
    this.mnemonic = mnemonic;
  }

  @Nullable
  @Override
  public String getProgressMessage() {
    return "Progress on " + mnemonic;
  }

  @Nullable
  @Override
  public String describeKey() {
    return "fake key";
  }

  @Override
  public RunfilesSupplier getRunfilesSupplier() {
    return EmptyRunfilesSupplier.INSTANCE;
  }

  @Override
  public boolean inputsDiscovered() {
    return false;
  }

  @Override
  public boolean discoversInputs() {
    return false;
  }

  @Override
  public ActionOwner getOwner() {
    return ActionOwner.create(
        null,
        ImmutableList.of(),
        Location.BUILTIN,
        "fake",
        "fake target kind",
        "fake",
        null,
        null,
        ImmutableMap.of(),
        null);
  }

  @Override
  public boolean isShareable() {
    return false;
  }

  @Override
  public String getMnemonic() {
    return mnemonic;
  }

  @Override
  public String getKey(
      ActionKeyContext actionKeyContext, @Nullable ArtifactExpander artifactExpander)
      throws InterruptedException {
    return "fake key";
  }

  @Override
  public String prettyPrint() {
    return mnemonic;
  }

  @Override
  public String describe() {
    return "Executing " + mnemonic;
  }

  @Override
  public NestedSet<Artifact> getTools() {
    return NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  }

  @Override
  public NestedSet<Artifact> getInputs() {
    return NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  }

  @Override
  public Collection<String> getClientEnvironmentVariables() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableSet<Artifact> getOutputs() {
    return ImmutableSet.of();
  }

  @Override
  public NestedSet<Artifact> getInputFilesForExtraAction(
      ActionExecutionContext actionExecutionContext)
      throws ActionExecutionException, InterruptedException {
    return NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  }

  @Override
  public ImmutableSet<Artifact> getMandatoryOutputs() {
    return ImmutableSet.of();
  }

  @Override
  public Artifact getPrimaryInput() {
    return null;
  }

  @Override
  public Artifact getPrimaryOutput() {
    return null;
  }

  @Override
  public NestedSet<Artifact> getMandatoryInputs() {
    return NestedSetBuilder.emptySet(Order.STABLE_ORDER);
  }

  @Override
  public boolean shouldReportPathPrefixConflict(ActionAnalysisMetadata action) {
    return false;
  }

  @Override
  public MiddlemanType getActionType() {
    return MiddlemanType.NORMAL;
  }

  @Override
  public ImmutableMap<String, String> getExecProperties() {
    return ImmutableMap.of();
  }

  @Nullable
  @Override
  public PlatformInfo getExecutionPlatform() {
    try {
      return PlatformInfo.builder().build();
    } catch (DuplicateConstraintException | ExecPropertiesException e) {
      return null;
    }
  }
}
