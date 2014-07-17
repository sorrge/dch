/*
 * The MIT License
 *
 * Copyright 2014 sorrge.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.nyan.dch.communication.transport.tcpip;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.nyan.dch.communication.IAddress;

/**
 *
 * @author sorrge
 */
public class IPAddress implements IAddress
{
  public final InetSocketAddress address;

  public IPAddress(InetAddress address, int port)
  {
    this.address = new InetSocketAddress(address, port);
  }

  public IPAddress(InetSocketAddress address)
  {
    this.address = address;
  }
  
  public IPAddress(DataInputStream input) throws IOException
  {
    boolean ipv4 = input.readBoolean();
    byte[] bytes = new byte[ipv4 ? 4 : 6];
    input.readFully(bytes);
    int port = input.readInt();
    address = new InetSocketAddress(InetAddress.getByAddress(bytes), port);
  }
  
  @Override
  public void Write(DataOutputStream stream) throws IOException
  {
    stream.writeBoolean(address.getAddress() instanceof Inet4Address);
    stream.write(address.getAddress().getAddress());
    stream.writeInt(address.getPort());
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 13 * hash + Objects.hashCode(this.address);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
      return false;
    
    if (getClass() != obj.getClass())
      return false;
    
    final IPAddress other = (IPAddress) obj;
    return Objects.equals(this.address, other.address);
  }

  @Override
  public String toString()
  {
    return address.toString();
  }
}
