/**
 *  Copyright 2011 LiveRamp
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.liveramp.hank.coordinator.mock;

import com.liveramp.hank.coordinator.*;

import java.io.IOException;
import java.util.*;

public class MockDomainGroup extends AbstractDomainGroup implements DomainGroup {

  private final String name;
  private Set<DomainGroupDomainVersion> domainVersions = new HashSet<DomainGroupDomainVersion>();
  private Collection<DomainGroupListener> listeners
      = new TreeSet<DomainGroupListener>();

  public MockDomainGroup(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Set<DomainGroupDomainVersion> getDomainVersions() throws IOException {
    return domainVersions;
  }

  @Override
  public void setDomainVersions(Map<Domain, Integer> domainVersions) throws IOException {
    this.domainVersions.clear();
    for (Map.Entry<Domain, Integer> entry : domainVersions.entrySet()) {
      this.domainVersions.add(new DomainGroupDomainVersion(entry.getKey(), entry.getValue()));
    }
    notifyListeners();
  }

  @Override
  public void setDomainVersion(Domain domain, int versionNumber) {
    domainVersions.add(new DomainGroupDomainVersion(domain, versionNumber));
    notifyListeners();
  }

  @Override
  public void mergeDomainVersions(Map<Domain, Integer> domainVersions) throws IOException {
    for (Map.Entry<Domain, Integer> entry : domainVersions.entrySet()) {
      this.domainVersions.add(new DomainGroupDomainVersion(entry.getKey(), entry.getValue()));
    }
    notifyListeners();
  }

  @Override
  public void removeDomain(Domain domain) throws IOException {
    domainVersions.remove(new DomainGroupDomainVersion(domain, -1));
  }

  @Override
  public void addListener(DomainGroupListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(DomainGroupListener listener) {
    listeners.remove(listener);
  }

  private void notifyListeners() {
    for (DomainGroupListener listener : listeners) {
      listener.onDomainGroupChange(this);
    }
  }

  @Override
  public String toString() {
    return "MockDomainGroup [name=" + name + "]";
  }
}
