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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IDiscoveryListener;

/**
 *
 * @author sorrge
 */
public class LANBroadcastDiscoveryTest
{
  static class DiscoveryListenerStub implements IDiscoveryListener
  {
    final HashSet<IAddress> discovered = new HashSet<>();
    
    @Override
    public boolean AddAddress(IAddress address)
    {
//      System.out.printf("Discovered address: %s\n", address);
      return discovered.add(address);
    }
  }
  
  /**
   * Test of class LANBroadcastDiscovery.
   */
  @Test
  public void testGetAddress() throws SocketException
  {
    ArrayList<InetAddress> addrs = LANBroadcastDiscovery.GetBroadcastAddresses();
    for(InetAddress ad : addrs)
      System.out.println(ad);
  }
  
  /**
   * Test of class LANBroadcastDiscovery.
   */
  @Test
  public void testReceive() throws SocketException, InterruptedException, IOException
  {
    IPAddress addr1 = new IPAddress(InetAddress.getLocalHost(), 1234);
    LANBroadcastDiscovery disc1 = new LANBroadcastDiscovery(addr1);
    DiscoveryListenerStub ds1 = new DiscoveryListenerStub();
    disc1.SetDiscoveryListener(ds1);
    disc1.BeginDiscovery();
    
    IPAddress addr2 = new IPAddress(InetAddress.getLocalHost(), 2234);
    LANBroadcastDiscovery disc2 = new LANBroadcastDiscovery(addr2);
    DiscoveryListenerStub ds2 = new DiscoveryListenerStub();
    disc2.SetDiscoveryListener(ds2);
    disc2.BeginDiscovery();
    
    Thread.sleep(5000);
    
    disc1.EndDiscovery();
    disc1.Close();
    
    disc2.EndDiscovery();
    disc2.Close();
    
    assert(ds1.discovered.equals(ds2.discovered));
    assert(ds1.discovered.size() == 2);
    assert(ds1.discovered.contains(addr1));
    assert(ds1.discovered.contains(addr2));
  }
  
  /**
   * Test of class LANBroadcastDiscovery.
   */
  @Test
  public void testShutdown() throws SocketException, InterruptedException, IOException
  {
    IPAddress addr1 = new IPAddress(InetAddress.getLocalHost(), 1234);
    LANBroadcastDiscovery disc1 = new LANBroadcastDiscovery(addr1);
    DiscoveryListenerStub ds1 = new DiscoveryListenerStub();
    disc1.SetDiscoveryListener(ds1);
    disc1.BeginDiscovery();
    
    Thread.sleep(5000);
    
    disc1.Close();
    Thread.sleep(1000);
    assert(!disc1.IsDiscovering());
  }  
}
