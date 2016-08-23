/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zmlx.hg4idea.log;

import com.intellij.ui.JBColor;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.SingletonRefGroup;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class HgRefManager implements VcsLogRefManager {
  private static final Color CLOSED_BRANCH_COLOR = new JBColor(new Color(0x823139), new Color(0xff5f6f));
  private static final Color LOCAL_TAG_COLOR = new JBColor(new Color(0x009090), new Color(0x00f3f3));
  private static final Color MQ_TAG_COLOR = new JBColor(new Color(0x002f90), new Color(0x0055ff));

  public static final VcsRefType TIP = new SimpleRefType(true, VcsLogStandardColors.Refs.TIP);
  public static final VcsRefType HEAD = new SimpleRefType(true, VcsLogStandardColors.Refs.LEAF);
  public static final VcsRefType BRANCH = new SimpleRefType(true, VcsLogStandardColors.Refs.BRANCH);
  public static final VcsRefType CLOSED_BRANCH = new SimpleRefType(false, CLOSED_BRANCH_COLOR);
  public static final VcsRefType BOOKMARK = new SimpleRefType(true, VcsLogStandardColors.Refs.BRANCH_REF);
  public static final VcsRefType TAG = new SimpleRefType(false, VcsLogStandardColors.Refs.TAG);
  public static final VcsRefType LOCAL_TAG = new SimpleRefType(false, LOCAL_TAG_COLOR);
  public static final VcsRefType MQ_APPLIED_TAG = new SimpleRefType(false, MQ_TAG_COLOR);

  // first has the highest priority
  private static final List<VcsRefType> REF_TYPE_PRIORITIES = Arrays.asList(TIP, HEAD, BRANCH, BOOKMARK, TAG);
  private static final List<VcsRefType> REF_TYPE_INDEX =
    Arrays.asList(TIP, HEAD, BRANCH, CLOSED_BRANCH, BOOKMARK, TAG, LOCAL_TAG, MQ_APPLIED_TAG);

  // -1 => higher priority
  public static final Comparator<VcsRefType> REF_TYPE_COMPARATOR = new Comparator<VcsRefType>() {
    @Override
    public int compare(VcsRefType type1, VcsRefType type2) {
      int p1 = REF_TYPE_PRIORITIES.indexOf(type1);
      int p2 = REF_TYPE_PRIORITIES.indexOf(type2);
      return p1 - p2;
    }
  };

  private static final String DEFAULT = "default";

  // @NotNull private final RepositoryManager<HgRepository> myRepositoryManager;

  // -1 => higher priority, i. e. the ref will be displayed at the left
  private final Comparator<VcsRef> REF_COMPARATOR = new Comparator<VcsRef>() {
    public int compare(VcsRef ref1, VcsRef ref2) {
      VcsRefType type1 = ref1.getType();
      VcsRefType type2 = ref2.getType();

      int typeComparison = REF_TYPE_COMPARATOR.compare(type1, type2);
      if (typeComparison != 0) {
        return typeComparison;
      }

      int nameComparison = ref1.getName().compareTo(ref2.getName());
      if (nameComparison != 0) {
        if (type1 == BRANCH) {
          if (ref1.getName().equals(DEFAULT)) {
            return -1;
          }
          if (ref2.getName().equals(DEFAULT)) {
            return 1;
          }
        }
        return nameComparison;
      }

      return VcsLogUtil.compareRoots(ref1.getRoot(), ref2.getRoot());
    }
  };

  @NotNull
  @Override
  public Comparator<VcsRef> getLabelsOrderComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  @Override
  public List<RefGroup> groupForBranchPopup(@NotNull Collection<VcsRef> refs) {
    return ContainerUtil.map(sort(refs), new Function<VcsRef, RefGroup>() {
      @Override
      public RefGroup fun(final VcsRef ref) {
        return new SingletonRefGroup(ref);
      }
    });
  }

  @NotNull
  @Override
  public List<RefGroup> groupForTable(@NotNull Collection<VcsRef> references) {
    List<VcsRef> sortedReferences = sort(references);
    Set<Map.Entry<VcsRefType, Collection<VcsRef>>> groupedRefs = ContainerUtil.groupBy(sortedReferences, VcsRef::getType).entrySet();
    Map.Entry<VcsRefType, Collection<VcsRef>> firstGroup =
      ContainerUtil.find(groupedRefs, entry -> !entry.getKey().equals(TIP) && !entry.getKey().equals(HEAD));
    if (firstGroup == null) {
      firstGroup = ContainerUtil.getFirstItem(groupedRefs);
    }

    List<RefGroup> groups = ContainerUtil.newArrayList();

    if (firstGroup == null) return groups;

    VcsRef firstRef = ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(firstGroup.getValue()));
    VcsRefType firstRefType = firstGroup.getKey();

    groups.add(
      new SimpleRefGroup(firstRefType.isBranch() && !firstRefType.equals(TIP) && !firstRefType.equals(HEAD) ? firstRef.getName() : "",
                         sortedReferences));

    return groups;
  }

  @Override
  public void serialize(@NotNull DataOutput out, @NotNull VcsRefType type) throws IOException {
    out.writeInt(REF_TYPE_INDEX.indexOf(type));
  }

  @NotNull
  @Override
  public VcsRefType deserialize(@NotNull DataInput in) throws IOException {
    int id = in.readInt();
    if (id < 0 || id > REF_TYPE_INDEX.size() - 1) throw new IOException("Reference type by id " + id + " does not exist");
    return REF_TYPE_INDEX.get(id);
  }

  @NotNull
  @Override
  public Comparator<VcsRef> getBranchLayoutComparator() {
    return REF_COMPARATOR;
  }

  @NotNull
  private List<VcsRef> sort(@NotNull Collection<VcsRef> refs) {
    return ContainerUtil.sorted(refs, getLabelsOrderComparator());
  }

  private static class SimpleRefType implements VcsRefType {
    private final boolean myIsBranch;
    @NotNull private final Color myColor;

    public SimpleRefType(boolean isBranch, @NotNull Color color) {
      myIsBranch = isBranch;
      myColor = color;
    }

    @Override
    public boolean isBranch() {
      return myIsBranch;
    }

    @NotNull
    @Override
    public Color getBackgroundColor() {
      return myColor;
    }
  }

  private static class SimpleRefGroup implements RefGroup {
    @NotNull private final String myName;
    @NotNull private final List<VcsRef> myRefs;

    private SimpleRefGroup(@NotNull String name, @NotNull List<VcsRef> refs) {
      myName = name;
      myRefs = refs;
    }

    @Override
    public boolean isExpanded() {
      return false;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public List<VcsRef> getRefs() {
      return myRefs;
    }

    @NotNull
    @Override
    public Color getBgColor() {
      return myRefs.get(0).getType().getBackgroundColor();
    }
  }
}
