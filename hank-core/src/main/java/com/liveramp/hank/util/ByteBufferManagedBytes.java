/**
 *  Copyright 2013 LiveRamp
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

package com.liveramp.hank.util;

import java.nio.ByteBuffer;

public class ByteBufferManagedBytes implements ManagedBytes {

  private final ByteBuffer buffer;

  public ByteBufferManagedBytes(ByteBuffer buffer) {
    this.buffer = buffer;
  }

  public ByteBuffer getBuffer() {
    return buffer;
  }

  @Override
  public long getNumManagedBytes() {
    // 8 bytes for ByteBufferManagedBytes itself
    // 40 bytes for the corresponding ByteBuffer
    // Plus the capacity of the underlying byte array
    return 8 + 40 + buffer.capacity();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ByteBufferManagedBytes)) {
      return false;
    }

    ByteBufferManagedBytes that = (ByteBufferManagedBytes)o;

    if (buffer != null ? !buffer.equals(that.buffer) : that.buffer != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return buffer != null ? buffer.hashCode() : 0;
  }
}
