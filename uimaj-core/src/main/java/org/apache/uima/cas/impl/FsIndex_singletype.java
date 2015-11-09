/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.uima.cas.impl;

import java.util.Comparator;
import java.util.List;

import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.admin.FSIndexComparator;
import org.apache.uima.cas.admin.LinearTypeOrder;
import org.apache.uima.jcas.cas.TOP;

/**
 * The common (among all index kinds - set, sorted, bag) info for an index over 1 type (excluding subtypes)
 *   The name "Leaf" is a misnomer - it's just over one type excluding any subtypes if they exist
 * Subtypes define the actual index repository (integers indexing the CAS) for each kind.
 * 
 * @param <T> the Java cover class type for this index, passed along to (wrapped) iterators producing Java cover classes
 */
public abstract class FsIndex_singletype<T extends TOP> implements Comparator<TOP>, LowLevelIndex<T> {

  private final static String[] indexTypes = new String[] {"Sorted", "Set", "Bag", "DefaultBag"}; 

  private final int indexType;  // Sorted, Set, Bag, Default-bag, etc.

  // A reference to the low-level CAS.
  final protected CASImpl casImpl;
  
  private FSIndexComparatorImpl comparatorForIndexSpecs;

  private boolean isInitialized = false;

  /***********  Info about Index Comparator (not used for bag ***********
   * Index into these arrays is the key number (indexes can have multiple keys)
   */
  // For each key, the int code of the type of that key.
  private Object[] keys;   // either a FeatImpl or a LinearTypeOrder;

  private int[] keyTypeCodes;
  // For each key, the comparison to use.
  private boolean[] isReverse;    // true = reverse, false = standard

  final private TypeImpl type; // The type of this
  
  final private int typeCode;
  
  
  @Override
  public String toString() {
    String kind = (indexType >= 0 && indexType < 4) ? indexTypes[indexType] : "Invalid";     
    return this.getClass().getSimpleName() + " [type=" + type.getName() + ", kind=" + kind + "(" + indexType + ")]";
  }

  // never called
  // declared private to block external calls
  @SuppressWarnings("unused")
  private FsIndex_singletype() {
    this.indexType = 0; // must do because it's final
    this.casImpl = null;
    this.type = null;
    this.typeCode = 0;
  }

  /**
   * Constructor for FsIndex_singletype.
   * @param cas -
   * @param type -
   * @param indexType -
   */
  protected FsIndex_singletype(CASImpl cas, Type type, int indexType) {
    super();
    this.indexType = indexType;
    this.casImpl = cas;
    this.type = (TypeImpl) type;
    this.typeCode = ((TypeImpl)type).getCode();
  }
  
  /**
   * Adding FS to an index.
   *   not in upper interfaces because it's internal use only - called via addToIndexes etc.
   * @param fs the fs to be added
   * @return true if the fs was added, (an identical one wasn't already there)
   */
  abstract boolean insert(T fs);  // not in upper interfaces because it's internal use only

  /**
   * @param fs - the Feature Structure to be removed.
   *             Only this exact Feature Structure is removed (this is a stronger test than, for example,
   *             what moveTo(fs) does, where the fs in that case is used as a template).  
   *             It is not an error if this exact Feature Structure is not in an index.
   * @return true if something was removed, false if not found
   */
  boolean remove(int fs) {
    return deleteFS((T) getCasImpl().getFsFromId_checked(fs));
  }
   
  /**
   * @param fs - the Feature Structure to be removed.
   *             Only this exact Feature Structure is removed (this is a stronger test than, for example,
   *             what moveTo(fs) does, where the fs in that case is used as a template).  
   *             It is not an error if this exact Feature Structure is not in an index.
   * @return true if something was removed, false if not found
   */
  abstract boolean deleteFS(T fs);
  
  @Override
  public FSIterator<T> iterator(FeatureStructure initialPositionFs) {
    FSIterator<T> fsIt = iterator();
    fsIt.moveTo(initialPositionFs);
    return fsIt;
  }
    
  @Override
  public FSIndexComparator getComparatorForIndexSpecs() {
    return this.comparatorForIndexSpecs;
  }

  @Override
  public int getIndexingStrategy() {
    return this.indexType;
  }

  public int[] getDetectIllegalIndexUpdates() {
    return this.casImpl.indexRepository.detectIllegalIndexUpdates;
  }
  /**
   * Comparators - 
   *   These are converted to use fs instances
   * @param comp
   * @return
   */
  boolean init(FSIndexComparator comp) {
    if (this.isInitialized) {
      return false;
    }
    FSIndexComparatorImpl compForIndexSpecs = (FSIndexComparatorImpl) comp;
    this.comparatorForIndexSpecs = compForIndexSpecs.copy();
    if (!this.comparatorForIndexSpecs.isValid()) {
      return false;
    }
    
    // Initialize the comparator info.
    final int nKeys = this.comparatorForIndexSpecs.getNumberOfKeys();
    this.keys = new Object[nKeys];
    this.keyTypeCodes = new int[nKeys];
    this.isReverse = new boolean[nKeys];

    for (int i = 0; i < nKeys; i++) {
      
      final Object k =  (comp.getKeyType(i) == FSIndexComparator.FEATURE_KEY)
                     ? (FeatureImpl) this.comparatorForIndexSpecs.getKeyFeature(i)
                     : this.comparatorForIndexSpecs.getKeyTypeOrder(i);
      keys[i] = k; 
      if (k instanceof FeatureImpl) {
        keyTypeCodes[i] = ((TypeImpl)((FeatureImpl)k).getRange()).getCode();
      }
      isReverse[i] = this.comparatorForIndexSpecs.getKeyComparator(i) == FSIndexComparator.REVERSE_STANDARD_COMPARE;
    }
    this.isInitialized = true;
    return true;
  }

  /**
   * @param fs1 -
   * @param fs2 -
   * @return 0 if equal, &lt; 0 if fs1 &lt; fs2, &gt; 0 if fs1 &gt; fs2
   */
  @Override
  public int ll_compare(int fs1, int fs2) {
    return this.compare(fs1, fs2);
  }

  /**
   * @param fs1 -
   * @param fs2 -
   * @return 0 if equal, &lt; 0 if fs1 &lt; fs2, &gt; 0 if fs1 &gt; fs2
   */
  public int compare(int fs1, int fs2) {
    return compare(casImpl.getFsFromId_checked(fs1), casImpl.getFsFromId_checked(fs2));
  }
  
  @Override
  public int compare(FeatureStructure fs1, FeatureStructure fs2) {
    return compare((TOP) fs1, (TOP) fs2);
  }
  
  /**
   * @see org.apache.uima.cas.FSIndex#compare(T, T)
   */    
  @Override
  public int compare(TOP fs1, TOP fs2) {
  
    if (fs1 == fs2) {
      return 0;
    }
    
    /**
     * for each key:
     *   if Feature:
     *     Switch by type:  float, 
     *       get the value:  fs1.getXXX, compare
     */
    int i = -1;
    for (Object key : keys) {
      int result = 0;
      i++;
      if (key instanceof FeatureImpl) {
        FeatureImpl fi = (FeatureImpl) key;
        switch (keyTypeCodes[i]) {
        case TypeSystemImpl.booleanTypeCode:
          result = Integer.compare(fs1.getBooleanValue(fi) ? 1 : 0,
                                   fs2.getBooleanValue(fi) ? 1 : 0);
          break;
        case TypeSystemImpl.byteTypeCode:
          result = Integer.compare(fs1.getByteValue(fi), fs2.getByteValue(fi));
          break;
        case TypeSystemImpl.shortTypeCode:
          result = Integer.compare(fs1.getShortValue(fi), fs2.getShortValue(fi));
          break;
        case TypeSystemImpl.intTypeCode:
          result = Integer.compare(fs1.getIntValue(fi), fs2.getIntValue(fi));
          break;
        case TypeSystemImpl.longTypeCode:
          result = Long.compare(fs1.getLongValue(fi), fs2.getLongValue(fi));
          break;
        case TypeSystemImpl.floatTypeCode:
          result = Float.compare(fs1.getFloatValue(fi), fs2.getFloatValue(fi));
          break;
        case TypeSystemImpl.doubleTypeCode:
          result = Double.compare(fs1.getDoubleValue(fi), fs2.getDoubleValue(fi));
          break;
        case TypeSystemImpl.stringTypeCode:
          String s1 = fs1.getStringValue(fi);
          String s2 = fs1.getStringValue(fi);
          result = (s1 == null)
                   ? ((s2 == null) ? 0 : -1)   // s1 null is lessthan a non-null s2
                   : ( (s2 == null)            
                       ? 1                     // s1 not null is greaterthan a null s2
                       : s1.compareTo(s2) );   // if both not null, do compare
          break;         
        }
      } else {  // is type order compare    
        result = ((LinearTypeOrder) key).compare(fs1, fs2);
      }
        
      if (result == 0) {
        continue;  
      }
      
      return (isReverse[i]) ? ( (result < 0) ? 1 : -1) 
                            : ( (result > 0) ? 1 : -1);
    } // of for loop iterating over all compare keys    
    return 0;  // all keys compare equal      
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((comparatorForIndexSpecs == null) ? 0 : comparatorForIndexSpecs.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FsIndex_singletype<?> other = (FsIndex_singletype<?>) obj;
    if (comparatorForIndexSpecs == null) {
      if (other.comparatorForIndexSpecs != null)
        return false;
    } else if (!comparatorForIndexSpecs.equals(other.comparatorForIndexSpecs))
      return false;
    return true;
  }

  /**
   * @see org.apache.uima.cas.FSIndex#getType()
   * @return The type of feature structures in this index.
   */
  @Override
  public Type getType() {
    return this.type;
  }
  
  public TypeImpl getTypeImpl() {
    return this.type;
  }
  
  int getTypeCode() {
    return this.typeCode;
  }

  /**
   * For serialization: get all the items in this index and bulk add to an List<T>
   * @param v the set of items to add
   */
  protected abstract void bulkAddTo(List<TOP> v);
  
  @Override
  public LowLevelIterator<T> ll_iterator(boolean ambiguous) {
    if (ambiguous) {
      return this.ll_iterator();
    }

    return null;
  }
  
  @Override
  public CASImpl getCasImpl() {
    return this.casImpl;
  }
  
  @Override
  public FSIndex<T> withSnapshotIterators() {
    // Is a no-op because this is a single type index.
    // should never be called
    // this is an artifact of the fact that FsIndex_singletype implements FSIndex interface
    return this;
  }
  
  /**
   * used to see if a particular fs is in the index  
   * @param fs
   * @return
   */
  public abstract boolean containsEq(FeatureStructureImplC fs);

  boolean isSetOrSorted() {
    return indexType == FSIndex.SET_INDEX || indexType == FSIndex.SORTED_INDEX;
  }
  
  boolean isSorted() {
    return indexType == FSIndex.SORTED_INDEX;
  }
}
