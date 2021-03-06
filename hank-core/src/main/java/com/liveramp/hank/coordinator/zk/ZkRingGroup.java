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
package com.liveramp.hank.coordinator.zk;

import com.liveramp.commons.util.BytesUtils;
import com.liveramp.hank.coordinator.AbstractRingGroup;
import com.liveramp.hank.coordinator.Coordinator;
import com.liveramp.hank.coordinator.DataLocationChangeListener;
import com.liveramp.hank.coordinator.DomainGroup;
import com.liveramp.hank.coordinator.PartitionServerAddress;
import com.liveramp.hank.coordinator.Ring;
import com.liveramp.hank.coordinator.RingGroup;
import com.liveramp.hank.coordinator.RingGroupDataLocationChangeListener;
import com.liveramp.hank.generated.ClientMetadata;
import com.liveramp.hank.ring_group_conductor.RingGroupConductorMode;
import com.liveramp.hank.zookeeper.WatchedEnum;
import com.liveramp.hank.zookeeper.WatchedMap;
import com.liveramp.hank.zookeeper.WatchedMap.ElementLoader;
import com.liveramp.hank.zookeeper.WatchedMapListener;
import com.liveramp.hank.zookeeper.WatchedNodeListener;
import com.liveramp.hank.zookeeper.WatchedThriftNode;
import com.liveramp.hank.zookeeper.ZkPath;
import com.liveramp.hank.zookeeper.ZooKeeperPlus;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.*;

public class ZkRingGroup extends AbstractRingGroup implements RingGroup {

  protected static final String RING_GROUP_CONDUCTOR_ONLINE_PATH = "ring_group_conductor_online";
  private static final String CLIENTS_PATH = "c";
  private static final String CLIENT_NODE = "c";
  private static final ClientMetadata emptyClientMetadata = new ClientMetadata();

  private DomainGroup domainGroup;
  private final WatchedMap<ZkRing> rings;
  private final String ringGroupPath;
  private final String ringGroupConductorOnlinePath;
  private final ZooKeeperPlus zk;
  private final Coordinator coordinator;
  private WatchedMap<WatchedThriftNode<ClientMetadata>> clients;

  private final WatchedEnum<RingGroupConductorMode> ringGroupConductorMode;
  private final List<RingGroupDataLocationChangeListener> dataLocationChangeListeners = new ArrayList<RingGroupDataLocationChangeListener>();
  private final DataLocationChangeListener dataLocationChangeListener = new LocalDataLocationChangeListener();

  public static ZkRingGroup create(ZooKeeperPlus zk, String path, ZkDomainGroup domainGroup, Coordinator coordinator) throws KeeperException, InterruptedException, IOException {
    zk.create(path, domainGroup.getName().getBytes());
    zk.create(ZkPath.append(path, CLIENTS_PATH), null);
    zk.create(ZkPath.append(path, DotComplete.NODE_NAME), null);
    return new ZkRingGroup(zk, path, domainGroup, coordinator);
  }

  public ZkRingGroup(ZooKeeperPlus zk, String ringGroupPath, DomainGroup domainGroup, final Coordinator coordinator)
      throws InterruptedException, KeeperException {
    super(ZkPath.getFilename(ringGroupPath));
    this.zk = zk;
    this.ringGroupPath = ringGroupPath;
    this.domainGroup = domainGroup;
    this.coordinator = coordinator;

    if (coordinator == null) {
      throw new IllegalArgumentException("Cannot initialize a ZkRingGroup with a null Coordinator.");
    }

    rings = new WatchedMap<ZkRing>(zk, ringGroupPath, new ElementLoader<ZkRing>() {
      @Override
      public ZkRing load(ZooKeeperPlus zk, String basePath, String relPath) throws KeeperException, InterruptedException {
        if (relPath.matches("ring-\\d+")) {
          return new ZkRing(zk, ZkPath.append(basePath, relPath), ZkRingGroup.this, coordinator, dataLocationChangeListener);
        }
        return null;
      }
    });
    rings.addListener(new ZkRingGroup.RingsWatchedMapListener());

    // Note: clients is lazy loaded in getClients()

    ringGroupConductorOnlinePath = ZkPath.append(ringGroupPath, RING_GROUP_CONDUCTOR_ONLINE_PATH);

    ringGroupConductorMode = new WatchedEnum<RingGroupConductorMode>(RingGroupConductorMode.class,
        zk, ringGroupConductorOnlinePath, false);
  }

  private class LocalDataLocationChangeListener implements DataLocationChangeListener {

    @Override
    public void onDataLocationChange() {
      fireDataLocationChangeListeners();
    }
  }

  private class RingsWatchedMapListener implements WatchedMapListener<ZkRingGroup> {

    @Override
    public void onWatchedMapChange(WatchedMap<ZkRingGroup> watchedMap) {
      fireDataLocationChangeListeners();
    }

  }

  @Override
  public DomainGroup getDomainGroup() {
    return domainGroup;
  }

  @Override
  public Ring getRing(int ringNumber) {
    return rings.get("ring-" + ringNumber);
  }

  @Override
  public Ring getRingForHost(PartitionServerAddress hostAddress) {
    for (Ring ring : rings.values()) {
      if (ring.getHostByAddress(hostAddress) != null) {
        return ring;
      }
    }
    return null;
  }

  @Override
  public Set<Ring> getRings() {
    return new HashSet<Ring>(rings.values());
  }

  @Override
  public boolean claimRingGroupConductor(RingGroupConductorMode mode) throws IOException {
    try {
      if (zk.exists(ringGroupConductorOnlinePath, false) == null) {
        zk.create(ringGroupConductorOnlinePath, BytesUtils.stringToBytes(mode.toString()), CreateMode.EPHEMERAL);
        return true;
      }
      return false;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public void releaseRingGroupConductor() throws IOException {
    try {
      if (zk.exists(ringGroupConductorOnlinePath, false) != null) {
        zk.delete(ringGroupConductorOnlinePath, -1);
        return;
      }
      throw new IllegalStateException(
          "Can't release the ring group conductor lock when it's not currently set!");
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public RingGroupConductorMode getRingGroupConductorMode() throws IOException {
    return ringGroupConductorMode.get();
  }

  @Override
  public void setRingGroupConductorMode(RingGroupConductorMode mode) throws IOException {
    try {
      ringGroupConductorMode.set(mode);
    } catch (KeeperException e) {
      throw new IOException(e);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public Ring addRing(int ringNum) throws IOException {
    try {
      ZkRing ring = ZkRing.create(zk, coordinator, ringGroupPath, ringNum, this, dataLocationChangeListener);
      rings.put("ring-" + Integer.toString(ring.getRingNumber()), ring);
      fireDataLocationChangeListeners();
      return ring;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public boolean removeRing(int ringNum) throws IOException {
    ZkRing ring = rings.remove("ring-" + Integer.toString(ringNum));
    if (ring == null) {
      return false;
    } else {
      ring.delete();
      fireDataLocationChangeListeners();
      return true;
    }
  }

  @Override
  public void addRingGroupConductorModeListener(WatchedNodeListener<RingGroupConductorMode> listener) {
    ringGroupConductorMode.addListener(listener);
  }

  @Override
  public void removeRingGroupConductorModeListener(WatchedNodeListener<RingGroupConductorMode> listener) {
    ringGroupConductorMode.removeListener(listener);
  }

  @Override
  public List<ClientMetadata> getClients() {
    if (clients == null) {
      clients = new WatchedMap<WatchedThriftNode<ClientMetadata>>(zk, ZkPath.append(ringGroupPath, CLIENTS_PATH), new ElementLoader<WatchedThriftNode<ClientMetadata>>() {
        @Override
        public WatchedThriftNode<ClientMetadata> load(ZooKeeperPlus zk, String basePath, String relPath) throws KeeperException, InterruptedException, IOException {
          return new WatchedThriftNode<ClientMetadata>(zk, ZkPath.append(basePath, relPath), true, null, null, emptyClientMetadata);
        }
      });
    }
    List<ClientMetadata> result = new ArrayList<ClientMetadata>();
    for (WatchedThriftNode<ClientMetadata> client : clients.values()) {
      result.add(client.get());
    }
    return result;
  }

  @Override
  public void registerClient(ClientMetadata client) throws IOException {
    try {
      new WatchedThriftNode<ClientMetadata>(zk,
          ZkPath.append(ringGroupPath, CLIENTS_PATH, CLIENT_NODE),
          false,
          CreateMode.EPHEMERAL_SEQUENTIAL,
          client,
          emptyClientMetadata);
    } catch (KeeperException e) {
      throw new IOException(e);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public boolean isRingGroupConductorOnline() throws IOException {
    try {
      return zk.exists(ringGroupConductorOnlinePath, false) != null;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public boolean delete() throws IOException {
    try {
      zk.deleteNodeRecursively(ringGroupPath);
      return true;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public void addDataLocationChangeListener(RingGroupDataLocationChangeListener listener) {
    synchronized (dataLocationChangeListeners) {
      dataLocationChangeListeners.add(listener);
    }
  }

  @Override
  public void removeDataLocationChangeListener(RingGroupDataLocationChangeListener listener) {
    synchronized (dataLocationChangeListeners) {
      dataLocationChangeListeners.remove(listener);
    }
  }

  private void fireDataLocationChangeListeners() {
    synchronized (dataLocationChangeListeners) {
      for (RingGroupDataLocationChangeListener listener : dataLocationChangeListeners) {
        listener.onDataLocationChange(this);
      }
    }
  }
}
