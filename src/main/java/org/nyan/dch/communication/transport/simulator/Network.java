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

import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.misc.RandomSet;
import org.nyan.dch.node.IRemoteResponseListener;

/**
 *
 * @author sorrge
 */
public class Network
{
  private final HashMap<IAddress, NetworkConnection> hosts = new HashMap<>();
  private final RandomSet<IAddress> addresses = new RandomSet<>();
  private final Random rand;

  public Network(Random rand)
  {
    this.rand = rand;
  }
  
  public IAddress AddNode(IRemoteResponseListener node)
  {
    return AddNode(node, true);
  }
  
  public IAddress AddNode(IRemoteResponseListener node, boolean accepting)
  {
    Address addr = new Address(rand);    
    NetworkConnection net = new NetworkConnection(this, addr, accepting, rand);
    Connections conns = new Connections(net, node, rand);
    hosts.put(addr, net);
    addresses.add(addr);
    return addr;
  }
  
  public IAddress Discover()
  {
    if(addresses.isEmpty())
      return null;
    
    return addresses.get(rand.nextInt(addresses.size()));
  }
  
  public NetworkConnection GetConnection(IAddress address)
  {
    return hosts.get(address);
  }
  
  public boolean Step() throws ProtocolException
  {
    boolean updated = false;
    for(NetworkConnection nc : hosts.values())
      updated |= nc.Step();
    
    return updated;
  }
  
  public boolean StepN(int maxSteps) throws ProtocolException
  {
    for(int i = 0, empty = 0; i < maxSteps;)
    {
      if(!Step())
        ++empty;
      else
        ++i;
      
      if(empty > 50)
        return true;
    }
    
    return false;
  }
  
  public Collection<NetworkConnection> GetHosts()
  {
    return hosts.values();
  }
  
  public void RemoveHost(IAddress toRemove)
  {
    NetworkConnection nc = hosts.get(toRemove);
    hosts.remove(toRemove);
    addresses.remove(toRemove);
    
    HostConnection[] hcs = new HostConnection[nc.Connections().size()];
    for(HostConnection hc : nc.Connections().toArray(hcs))
      hc.Disconnect();
  }
  
  public Collection<IAddress> GetAddresses()
  {
    return addresses;
  }
}
