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

import java.io.FileNotFoundException;

import junit.framework.TestCase;

public class TestYamlConfigurator extends TestCase {
  private YamlConfigurator config;

  protected void setUp() {
    config = new YamlConfigurator(
        "/home/intern/workspace/tiamat/trunk/test/java/com/rapleaf/tiamat/config/config.yaml");
    try {
      config.loadConfig();
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
  
  public void testLoad() {
    assertEquals(config.getRingGroupName(), "rapleaf-1");
    assertEquals(config.getRingNumber(), 15);
    assertEquals(config.getServicePort(), 4545);
    assertEquals(config.getNumThreads(), 3);
    assertEquals(config.getNumConcurrentUpdates(), 5);
    assertEquals(config.getLocalDataDirectories().toString(), "[/usr/bin/, /home/bliu/]");
  }
}
