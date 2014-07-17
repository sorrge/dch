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

package org.nyan.dch.communication;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.misc.RandomSet;
import org.nyan.dch.node.IRemoteResponseListener;

/**
 *
 * @author sorrge
 */
public class Connections implements IConnections
{
  public static final int WantConnections = 10, SendAcceptanceConfirmationRequests = 1;
  
  private static final Logger log = Logger.getLogger(Connections.class.getName());
  
  private final RandomSet<IAddress> knownAddresses = new RandomSet<>();
  private final INetworkTransport transport;
  public final IRemoteResponseListener node;
  private final Random rand;
  private final IDiscovery discovery;
  private int acceptanceConfirmationsAsked = 0;

  public Connections(INetworkTransport transport, IRemoteResponseListener node, Random rand)
  {
    this.transport = transport;
    this.node = node;
    this.rand = rand;
    discovery = transport.GetDiscovery();
    
    transport.SetConnectionsListener(this);
    discovery.SetDiscoveryListener(this);
    discovery.BeginDiscovery();
  }
  
  @Override
  public boolean AddAddress(IAddress address)
  {
    if(discovery.GetMyAddress() != null && discovery.GetMyAddress().equals(address))
      return false;
    
    synchronized(knownAddresses)
    {
      if(!knownAddresses.add(address))
        return false;
      
      log.log(Level.INFO, "New address discovered: {0}", address);

      if(knownAddresses.size() > WantConnections * 3)
        discovery.EndDiscovery();
    }
    
    EnsureConnected();    
    return true;
  }
  
  @Override
  public void Connected(IRemoteHost host, boolean iAmTheInitiator)
  {
    System.out.printf("%s connection established, %d connections in total\n", iAmTheInitiator ? "Outgoing" : "Incoming", transport.GetConnectionsCount());
    RemoteNodeMessages msg = new RemoteNodeMessages(host, this);
    msg.SetResponseListener(node, iAmTheInitiator);
    host.SetReceiveListener(msg);
    IAddress remoteAddress = host.GetAddress();
    if(remoteAddress != null)
      AddAddress(remoteAddress);
    
    synchronized(knownAddresses)
    {
      try 
      { 
        if(!iAmTheInitiator)
        {
          if(knownAddresses.size() > 1)
          {
            HashSet<IAddress> toGive = new HashSet<>();
            if(knownAddresses.size() <= RemoteNodeMessages.GiveAddressesCount)
              toGive.addAll(knownAddresses);
            else
              while(toGive.size() < RemoteNodeMessages.GiveAddressesCount)
                toGive.add(knownAddresses.get(rand.nextInt(knownAddresses.size())));

            msg.GiveAddresses(toGive);
          }
        }

        if(iAmTheInitiator && discovery.GetAcceptance().CanAcceptConnections())
        {
//          System.out.printf("Connection to %s established, sending my address %s\n", host.GetAddress(), discovery.GetMyAddress());
          msg.GiveMyAddress(discovery.GetMyAddress(), discovery.GetAcceptance().NeedToConfirmAcceptance() && acceptanceConfirmationsAsked < SendAcceptanceConfirmationRequests);
          if(discovery.GetAcceptance().NeedToConfirmAcceptance())
          {
            ++acceptanceConfirmationsAsked;
            if(acceptanceConfirmationsAsked == SendAcceptanceConfirmationRequests)
              discovery.GetAcceptance().AcceptanceConfirmationRequested();
          }
        }
      }
      catch (ProtocolException ex) 
      {
        System.err.println(ex.getMessage());
      }
    }
  }
  
  @Override
  public void Disconnected(IRemoteHost host)
  {
    System.out.printf("Connection dropped, %d connections in total\n", transport.GetConnectionsCount());    
    EnsureConnected();    
  }

  @Override
  public void ConnectionFailed(IAddress host)
  {
    if(transport.GetConnectedHost(host) == null)
      synchronized(knownAddresses)
      {
        knownAddresses.remove(host);
      }
    
    if(knownAddresses.size() <= WantConnections * 3)
      discovery.BeginDiscovery();
    
    EnsureConnected();
  }

  public void EnsureConnected()
  {
//    System.out.printf("Connections.EnsureConnected in %s called: %d connections + %d connecting\n", this, transport.GetConnectionsCount(), 
//                transport.GetCurrentConnectionAttempts());
    
    synchronized(knownAddresses)
    {
      while(transport.GetConnectionsCount() + transport.GetCurrentConnectionAttempts() < Math.min(WantConnections, knownAddresses.size()))
      {
//        System.out.printf("\tConnections.EnsureConnected in %s: %d connections + %d connecting: trying to connect\n", this, transport.GetConnectionsCount(), 
//                transport.GetCurrentConnectionAttempts());        
        IAddress addr = knownAddresses.get(rand.nextInt(knownAddresses.size()));
        if(transport.GetConnectedHost(addr) == null && !discovery.GetMyAddress().equals(addr))
          transport.Connect(addr, false);
      }
    }
    
//    System.out.printf("Connections.EnsureConnected in %s finished: %d connections + %d connecting\n", this, transport.GetConnectionsCount(), 
//                transport.GetCurrentConnectionAttempts());    
  }

  @Override
  public INetworkTransport GetTransport()
  {
    return transport;
  }

  @Override
  public void AddressSupplied(IRemoteHost host, IAddress address, long cookie, boolean wandAcceptanceConfirmation)
  {
    IRemoteHost alreadyConnected = transport.GetConnectedHost(address);    
    host.SetAddress(address, cookie);  
//          System.out.printf("Received cookie from %s\n", host);          
    if(alreadyConnected != null && alreadyConnected != host)
    {
//            assert(alreadyConnected != host);
//            System.out.printf("Oops, already connected to %s\n", addr);
//            System.out.printf("This connection's cookie: %d, the other's: %d\n", host.GetCookie(), alreadyConnected.GetCookie());
      long c1 = alreadyConnected.GetCookie(), c2 = host.GetCookie();

      try
      {
        if(c1 > c2)
          host.Disconnect();
        else
          alreadyConnected.Disconnect();
      }
      catch(IOException ex)
      {
      }
    }
    else
    {
      AddAddress(address);
      if(wandAcceptanceConfirmation)
        transport.Connect(address, true);
    }
  }
}
