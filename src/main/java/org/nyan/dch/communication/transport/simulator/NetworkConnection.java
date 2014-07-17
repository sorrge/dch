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

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import org.nyan.dch.communication.AlwaysAccepting;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.IAcceptance;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IConnections;
import org.nyan.dch.communication.IDiscovery;
import org.nyan.dch.communication.IDiscoveryListener;
import org.nyan.dch.communication.INetworkTransport;
import org.nyan.dch.communication.IRemoteHost;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.communication.RemoteNodeMessages;

/**
 *
 * @author sorrge
 */
public class NetworkConnection implements INetworkTransport, IDiscovery
{
  private IConnections connections;
  private final Network network;
  private boolean needDiscovery = false;
  private final IAddress myAddress;
  private final HashSet<HostConnection> hostConnections = new HashSet<>();
  private final Random rand;
  private final Acceptance acceptance = new Acceptance();
  private final boolean canAccept;

  public NetworkConnection(Network network, IAddress myAddress, boolean canAccept, Random rand)
  {
    this.network = network;
    this.myAddress = myAddress;
    this.rand = rand;
    this.canAccept = canAccept;
  }
  
  @Override
  public void BeginDiscovery()
  {
    needDiscovery = true;
  }

  @Override
  public void EndDiscovery()
  {
    needDiscovery = false;
  }

  @Override
  public void SetConnectionsListener(IConnections listener)
  {
    this.connections = listener;
  }

  @Override
  public IAddress ReadAddress(DataInputStream stream) throws IOException
  {
    return new Address(stream);
  }

  @Override
  public int GetCurrentConnectionAttempts()
  {
    return 0;
  }

  @Override
  public void Connect(IAddress toWhom, boolean test)
  {
    if(network.GetConnection(myAddress) == null)
      connections.ConnectionFailed(toWhom);
    else
    {
      NetworkConnection otherConn = network.GetConnection(toWhom);
      if(otherConn == null || otherConn == this || !otherConn.canAccept)
        connections.ConnectionFailed(toWhom);
      else
      {
//        System.out.printf("Connection between %s and %s established\n", myAddress, toWhom);
        long cookie = rand.nextLong();
        HostConnection myHost = new HostConnection(myAddress, otherConn, cookie);      
        HostConnection otherHost = new HostConnection(toWhom, this, cookie);
        myHost.ConnectTo(otherHost);
        otherHost.ConnectTo(myHost);
        hostConnections.add(otherHost);
        otherConn.hostConnections.add(myHost);
        connections.Connected(otherHost, true);
        otherConn.connections.Connected(myHost, false);
        otherConn.acceptance.AcceptanceConfirmed();
        if(test)
          myHost.Disconnect();
      }
    }
  }
  
  public boolean Step() throws ProtocolException
  {
    boolean updated = false;
    if(needDiscovery)
    {
      IAddress discovered = network.Discover();
      if(discovered != null && network.GetConnection(discovered) != this)
        updated = connections.AddAddress(discovered);
    }
    
    for(HostConnection hc : hostConnections)
      updated |= hc.Step();
    
    updated |= acceptance.Step();
    
    return updated;
  }
  
  public Collection<HostConnection> Connections()
  {
    return hostConnections;
  }
  
  public IConnections GetConnections()
  {
    return connections;
  }
  
  public void Disconnect(HostConnection whom)
  {
    hostConnections.remove(whom);
  }

  @Override
  public IDiscovery GetDiscovery()
  {
    return this;
  }

  @Override
  public void SetDiscoveryListener(IDiscoveryListener listener)
  {
    assert(listener == connections);
  }

  @Override
  public void Close()
  {
  }

  @Override
  public IAddress GetMyAddress()
  {
    return myAddress;
  }

  @Override
  public int GetConnectionsCount()
  {
    return hostConnections.size();
  }

  @Override
  public IRemoteHost GetConnectedHost(IAddress whom)
  {
    for(HostConnection c : hostConnections)
      if(whom.equals(c.GetAddress()))
        return c;
    
    return null;    
  }

  @Override
  public void Restart()
  {
  }

  @Override
  public IAcceptance GetAcceptance()
  {
    return acceptance;
  }

  public boolean CanAccept()
  {
    return canAccept;
  }
}
