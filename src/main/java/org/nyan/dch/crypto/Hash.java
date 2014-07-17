/**
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2014 sorrge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nyan.dch.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.nyan.dch.misc.BitcoinUtils;

import static com.google.common.base.Preconditions.checkArgument;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * A Hash just wraps a byte[] so that equals and hashcode work correctly, allowing it to be used as keys in a
 map. It also checks that the length is correct and provides a bit more type safety.
 */
public abstract class Hash implements Serializable, Comparable<Hash> 
{
  private byte[] bytes;

  protected abstract int BytesLength();

  /**
   * Creates a Hash by wrapping the given byte array.
   */
  public Hash(byte[] rawHashBytes) {
      checkArgument(rawHashBytes.length == BytesLength());
      this.bytes = rawHashBytes;
  }

  /**
   * Creates a Hash by decoding the given hex string. It must be 64 characters long.
   */
  public Hash(String hexString) {
      checkArgument(hexString.length() == BytesLength() * 2);
      this.bytes = BitcoinUtils.HEX.decode(hexString);
  }

  @Override
  public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Hash other = (Hash) o;
      return Arrays.equals(bytes, other.bytes);
  }

  /**
   * Hash code of the byte array as calculated by {@link Arrays#hashCode()}. Note the difference between a SHA256
   * secure bytes and the type of quick/dirty bytes used by the Java hashCode method which is designed for use in
   * bytes tables.
   */
  @Override
  public int hashCode() {
      return (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8) | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
  }

  @Override
  public String toString() {
      return BitcoinUtils.HEX.encode(bytes);
  }

  /**
   * Returns the bytes interpreted as a positive integer.
   */
  public BigInteger toBigInteger() {
      return new BigInteger(1, bytes);
  }

  public byte[] getBytes() {
      return bytes;
  }

  @Override
  public int compareTo(Hash o) {
      int thisCode = this.hashCode();
      int oCode = ((Hash)o).hashCode();
      return thisCode > oCode ? 1 : (thisCode == oCode ? 0 : -1);
  }
    
  public void Write(DataOutputStream stream) throws IOException
  {
    stream.write(bytes, 0, bytes.length);
  }
  
  protected Hash(DataInputStream stream) throws IOException
  {
    bytes = new byte[BytesLength()];
    stream.readFully(bytes);
  }
}
