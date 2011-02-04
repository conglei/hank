/**
 *  Copyright 2011 Rapleaf
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
package com.rapleaf.tiamat.config;

import java.util.Map;

import com.rapleaf.tiamat.exception.DataNotFoundException;

public interface RingGroupConfig {
  public String getName();
  public Map<Integer, RingConfig> getRingConfigs();
  public RingConfig getRingConfig(int ringNumber) throws DataNotFoundException;
  public DomainGroupConfig getDomainGroupConfig();
  public RingConfig getRingConfigForHost(String hostName) throws DataNotFoundException;
}
