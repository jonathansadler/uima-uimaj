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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.internal.util.XmlAttribute;
import org.apache.uima.internal.util.XmlElementName;
import org.apache.uima.internal.util.XmlElementNameAndContents;
import org.apache.uima.internal.util.rb_trees.RedBlackTree;

/**
 * A container for data that is shared between the {@link XmiCasSerializer} and the {@link XmiCasDeserializer}.
 * This has a number of uses:
 * <ul>
 *   <li>Consistency of xmi:id values across serializations.  If you pass an <code>XmiSerializationSharedData</code>
 *       instance to the deserializer, the deserializer will store information about the incoming xmi:id values.  If you then pass
 *       the same <code>XmiSerializationSharedData</code> object to the serializer when you attempt to serialize the same CAS,
 *       all of the incoming FS will be serialized using the same xmi:id value that they had when they were deserialized.</li>
 *   <li>Support for "out-of-typesystem data".  If you call the XMI deserializer with the <code>lenient</code> parameter set to true,
 *       whenever it encounters an XMI element that doesn't correspond to a type in the type system, it will populate the
 *       <code>XmiSerializationSharedData</code> with information about these elements.  If you then pass the same 
 *       <code>XmiSerializationSharedData</code> object to the serializer when you attempt to serialize the same CAS, these
 *       out-of-typesystem FS will be reserialized without loss of information.  References between in-typesystem and out-of-typesystem
 *       FS (in either direction) are maintained as well.</li>
 *   <li>After calling the XmiCasSerializer and passing an <code>XmiSerializationSharedData</code>, you can call the
 *       {@link #getMaxXmiId()} method to get the maximum xmi:id value in the serialized CAS.  This feature, along with the consistency of
 *       xmi:id values, allows merging multiple XMI documents into a single CAS.  See TODO.</li>
 *       
 * 
 */
public class XmiSerializationSharedData {
  /**
   * A map from FeatureStructure address to xmi:id. This is populated whenever
   * an XMI element is serialized or deserialized.  It is used by the
   * getXmiId() method, which is done to ensure a consistent ID for each FS 
   * address across multiple serializations.
   */
  private RedBlackTree fsAddrToXmiIdMap = new RedBlackTree();
  
  /** 
   * A map from xmi:id to FeatureStructure address.  This is populated whenever
   * an XMI element is serialized or deserialized.  It is used by the
   * getFsAddrForXmiId() method, necessary to support merging multiple XMI
   * CASes into the same CAS object.
   **/
  private RedBlackTree xmiIdToFsAddrMap = new RedBlackTree();
  
  /**
   * List of OotsElementData objects, each of which captures information about
   * incoming XMI elements that did not correspond to any type in the type system.
   */
  private List ootsFs = new ArrayList();
  
  /**
   * Map that from the xmi:id (String) of a Sofa to a List of xmi:id's (Strings) for
   * the out-of-typesystem FSs that are members of that Sofa's view.
   */
  private Map ootsViewMembers = new HashMap();

  /** Map from Feature Structure address (Integer) to OotsElementData object, capturing information 
   * about out-of-typesystem features that were part of an in-typesystem FS.  These include both
   * features not defined in the typesystem and features that are references to out-of-typesystem
   * elements.  This information needs to be included when the FS is subsequently serialized.
   */
  private Map ootsFeatures = new HashMap();
  
  /** Map from Feature Structure address (Integer) of an FSArray to a list of 
   * {@link XmiArrayElement} objects, each of which holds an index and an xmi:id
   * for an out-of-typesystem array element.
   */
  private Map ootsArrayElements = new HashMap();
  
  /**
   * The maximum XMI ID used in the serialization. Used to generate unique IDs if needed.
   */
  private int maxXmiId = 0;

  void addIdMapping(int fsAddr, int xmiId) {
    fsAddrToXmiIdMap.put(fsAddr, Integer.toString(xmiId));
    xmiIdToFsAddrMap.put(xmiId, new Integer(fsAddr));
    if (xmiId > maxXmiId)
      maxXmiId = xmiId;
  }

  String getXmiId(int fsAddr) {
    // see if we already have a mapping
    String xmiId = (String) fsAddrToXmiIdMap.get(fsAddr);
    if (xmiId != null) {
      return xmiId;
    } else // no mapping for this FS. Generate a unique ID
    {
      // to be sure we get a unique Id, increment maxXmiId and use that
      String idStr = Integer.toString(++maxXmiId);
      fsAddrToXmiIdMap.put(fsAddr, idStr);
      xmiIdToFsAddrMap.put(maxXmiId, new Integer(fsAddr));
      return idStr;
    }
  }

  
  /**
   * Gets the maximum xmi:id that has been generated or read so far.
   * @return the maximum xmi:id
   */
  public int getMaxXmiId() {
    return maxXmiId;
  }
  
  /**
   * Gets the FS address that corresponds to the given xmi:id, in the most
   * recent serialization or deserialization.
   *   
   * @param xmiId an xmi:id from the most recent XMI CAS that was serialized
   *   or deserialized.
   * @return the FS address of the FeatureStructure corresponding to that
   *   xmi:id, -1 if none.
   */
  public int getFsAddrForXmiId(int xmiId) {
    Integer addr = (Integer)xmiIdToFsAddrMap.get(xmiId);
    return addr == null ? -1 : addr.intValue();
  }
  
  /** 
   * Clears the ID mapping information that was populated in
   * previous serializations or deserializations.
   * TODO: maybe a more general reset that resets other things?
   */
  public void clearIdMap() {
    fsAddrToXmiIdMap.clear();
    xmiIdToFsAddrMap.clear();
    maxXmiId = 0;
  }
  
  /**
   * Records information about an XMI element that was not an instance of any type in the type system.
   * @param elemData information about the out-of-typesystem XMI element
   */
  public void addOutOfTypeSystemElement(OotsElementData elemData) {
    this.ootsFs.add(elemData);
  }

  /**
   * Gets a List of {@link OotsElementData} objects, each of which describes an
   * incoming XMI element that did not correspond to a Type in the TypeSystem.
   * @return List of {@link OotsElementData} objects
   */
  public List getOutOfTypeSystemElements() {
    return Collections.unmodifiableList(this.ootsFs);
  }
  
  /**
   * Records that an out-of-typesystem XMI element should be a member of the
   * specified view.
   * @param sofaXmiId xmi:id of a Sofa
   * @param memberXmiId xmi:id of an out-of-typesystem element that should be
   *   a member of the view for the given Sofa
   */
  public void addOutOfTypeSystemViewMember(String sofaXmiId, String memberXmiId) {
    List membersList = (List)this.ootsViewMembers.get(sofaXmiId);
    if (membersList == null) {
      membersList = new ArrayList();
      this.ootsViewMembers.put(sofaXmiId, membersList);
    }
    membersList.add(memberXmiId);
  }
  
  /**
   * Gets a List of xmi:id's (Strings) of all out-of-typesystem XMI elements
   * that are members of the view with the given id.
   * @param sofaXmiId xmi:id of a Sofa
   * @return List of xmi:id's of members of the view for the given Sofa.
   */
  public List getOutOfTypeSystemViewMembers(String sofaXmiId) {
    List members = (List)this.ootsViewMembers.get(sofaXmiId);
    return members == null ? null : Collections.unmodifiableList(members);
  }
  
  /**
   * Records an out-of-typesystem attribute that belongs to an in-typesystem FS.
   * This will be added to the attributes when that FS is reserialized.
   * @param addr CAS address of the FS 
   * @param featName name of the feature
   * @param featVal value of the feature, as a string
   */
  public void addOutOfTypeSystemAttribute(int addr, String featName, String featVal) {
    Integer key = new Integer(addr);
    OotsElementData oed = (OotsElementData)this.ootsFeatures.get(key);
    if (oed == null) {
      oed = new OotsElementData();
      this.ootsFeatures.put(key, oed);
    }
    oed.attributes.add(new XmlAttribute(featName, featVal));
  }  
  
  /**
   * Records out-of-typesystem child elements that belong to an in-typesystem FS.
   * These will be added to the child elements when that FS is reserialized.
   * @param addr CAS address of the FS 
   * @param featName name of the feature (element tag name)
   * @param featVal values of the feature, as a List of strings
   */
  public void addOutOfTypeSystemChildElements(int addr, String featName, List featVals) {
    Integer key = new Integer(addr);
    OotsElementData oed = (OotsElementData)this.ootsFeatures.get(key);
    if (oed == null) {
      oed = new OotsElementData();
      this.ootsFeatures.put(key, oed);
    }
    Iterator iter = featVals.iterator();
    XmlElementName elemName = new XmlElementName(null,featName,featName);
    while (iter.hasNext()) {
      oed.childElements.add(new XmlElementNameAndContents(elemName, (String)iter.next()));
    }
  }  
  
  /**
   * Gets information about out-of-typesystem features that belong to an
   * in-typesystem FS.
   * @param addr CAS address of the FS
   * @return object containing information about out-of-typesystem features
   *   (both attributes and child elements)
   */
  public OotsElementData getOutOfTypeSystemFeatures(int addr) {
    Integer key = new Integer(addr);
    return (OotsElementData)this.ootsFeatures.get(key);
  }
  
  /**
   * Get all FS Addresses that have been added to the id map.
   * @return an array containing all the FS addresses
   */
  public int[] getAllFsAddressesInIdMap() {
    return fsAddrToXmiIdMap.keySet();
  }  
  
  /**
   * Gets information about out-of-typesystem array elements.
   * @param addr the CAS address of an FSArray
   * @return a List of {@link XmiArrayElement} objects, each of which
   *   holds the index and xmi:id of an array element that is a
   *   reference to an out-of-typesystem FS.
   */
  public List getOutOfTypeSystemArrayElements(int addr) {
    return (List)this.ootsArrayElements.get(new Integer(addr));
  }
  

  /**
   * Records an out-of-typesystem array element in the XmiSerializationSharedData.
   * @param addr CAS address of FSArray
   * @param index index into array 
   * @param xmiId xmi:id of the out-of-typesystem element that is the value at the given index
   */
  public void addOutOfTypeSystemArrayElement(int addr, int index, int xmiId) {
    Integer key = new Integer(addr);
    List list = (List)this.ootsArrayElements.get(key);
    if (list == null) {
      list = new ArrayList();
      this.ootsArrayElements.put(key, list);
    }
    list.add(new XmiArrayElement(index, Integer.toString(xmiId)));
  }
  
  /**
   * For debugging purposes only.
   */
  void checkForDups() {
    Set ids = new HashSet();
    Iterator iter = fsAddrToXmiIdMap.iterator();
    while (iter.hasNext()) {
      String xmiId = (String) iter.next();
      if (!ids.add(xmiId)) {
        throw new RuntimeException("Duplicate ID " + xmiId + "!");
      }
    }
  }

  /**
   * For debugging purposes only.
   */
  public String toString() {
    StringBuffer buf = new StringBuffer();
    int[] keys = fsAddrToXmiIdMap.keySet();
    for (int i = 0; i < keys.length; i++) {
      buf.append(keys[i]).append(": ").append(fsAddrToXmiIdMap.get(keys[i])).append('\n');
    }
    return buf.toString();
  }

  /**
   * Data structure holding all information about an XMI element
   * containing an out-of-typesystem FS.
   */
  static class OotsElementData {
    /**
     * xmi:id of the element
     */
    String xmiId;

    /**
     * Name of the element, including XML namespace.
     */
    XmlElementName elementName;

    /**
     * List of XmlAttribute objects each holding name and value of an attribute.
     */
    List attributes = new ArrayList();
    
    /**
     * List of XmlElementNameAndContents objects each describing one of the
     * child elements representing features of this out-of-typesystem element.
     */
    List childElements = new ArrayList();
  }
  
  /** 
   * Data structure holding the index and the xmi:id of an array or list element that
   * is a reference to an out-of-typesystem FS.
   */
  static class XmiArrayElement {
    int index;

    String xmiId;

    XmiArrayElement(int index, String xmiId) {
      this.index = index;
      this.xmiId = xmiId;
    }
  }
}
