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
package com.rapleaf.tiamat.hasher;

import java.nio.ByteBuffer;

import junit.framework.TestCase;

public class TestMurmur64Hasher extends TestCase {
  public void testIt() {
    Murmur64Hasher hsh = new Murmur64Hasher();
    byte[] bufA = new byte[9];
    hsh.hash(ByteBuffer.wrap(new byte[]{1, 2, 3}), bufA);
    byte[] bufB = new byte[9];
    hsh.hash(ByteBuffer.wrap(new byte[]{1, 2, 3}), bufB);

    assertTrue(bufA[8] != 0);
    assertEquals(ByteBuffer.wrap(bufA), ByteBuffer.wrap(bufB));
  }
}
