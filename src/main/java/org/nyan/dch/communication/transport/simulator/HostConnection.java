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

package org.nyan.dch.communication.transport.simulator;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IRemoteHost;
import org.nyan.dch.communication.IRemoteHostListener;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.communication.RemoteNodeMessages;

/**
 *
 * @author sorrge
 */
public class HostConnection implements IRemoteHost
{
  private IAddress remoteAddress;
  private IRemoteHostListener myNode;
  private HostConnection remoteHost;
  private final Queue<byte[]> messages = new ArrayDeque<>();
  private long cookie;
  private final NetworkConnection networkConnection;

  public HostConnection(IAddress remoteAddress, NetworkConnection networkConnection, long cookie)
  {
    this.remoteAddress = remoteAddress;
    this.cookie = cookie;
    this.networkConnection = networkConnection;
  }
  
  public void ConnectTo(HostConnection remoteHost)
  {
    this.remoteHost = remoteHost;
  }

  @Override
  public void SendData(byte[] data) throws ProtocolException
  {
    messages.add(data);
  }

  @Override
  public void SetReceiveListener(IRemoteHostListener node)
  {
    myNode = node;
  }

  @Override
  public IAddress GetAddress()
  {
    return remoteAddress;
  }
  
  public boolean Step() throws ProtocolException
  {
    if(messages.isEmpty())
      return false;
    
    if(remoteHost != null && remoteHost.myNode != null)
      remoteHost.myNode.ReceiveData(messages.remove());
    
    return true;
  }

  public IRemoteHostListener GetMyNode()
  {
    return myNode;
  }

  public HostConnection GetRemoteHost()
  {
    return remoteHost;
  }

  @Override
  public void Disconnect()
  {
    networkConnection.Disconnect(this);
    remoteHost.networkConnection.Disconnect(remoteHost);
    myNode.Disconnected(true);
    remoteHost.myNode.Disconnected(false);
    remoteHost.remoteHost = null;
    remoteHost = null;
  }

  @Override
  public void SetAddress(IAddress address, long cookie)
  {
    remoteAddress = address;
    this.cookie = cookie;
  }

  @Override
  public long GetCookie()
  {
    return cookie;
  }
}
