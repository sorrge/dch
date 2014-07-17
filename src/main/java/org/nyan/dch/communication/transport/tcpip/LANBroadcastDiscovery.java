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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.communication.AlwaysAccepting;
import org.nyan.dch.communication.IAcceptance;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IDiscovery;
import org.nyan.dch.communication.IDiscoveryListener;

/**
 *
 * @author sorrge
 */
public class LANBroadcastDiscovery implements IDiscovery, Runnable
{
  public static final int Port = 48930;
  public static final int AnnouncePeriodSec = 1;
  private static final int DatagramSize = 30;
  private final AlwaysAccepting acceptance = new AlwaysAccepting();
  
  private class Announcer extends TimerTask
  {
    private final ByteBuffer writeBuffer;
    private final DatagramChannel channel;
    private final ArrayList<InetSocketAddress> broadcastAddresses = new ArrayList<>();
    
    public Announcer() throws IOException
    {
      channel = DatagramChannel.open();
      for(InetAddress destination : GetBroadcastAddresses())
        broadcastAddresses.add(new InetSocketAddress(destination, Port));
      
      ByteArrayOutputStream baos = new ByteArrayOutputStream(DatagramSize);
      DataOutputStream str = new DataOutputStream(baos);
      myAddress.Write(str);
      byte[] bytes = baos.toByteArray();
      writeBuffer = ByteBuffer.allocate(bytes.length);
      writeBuffer.put(bytes);      
    }

    @Override
    public void run()
    {
      for(InetSocketAddress destination : broadcastAddresses)
        try
        {
//          System.out.printf("Announcing to %s\n", destination);
          writeBuffer.rewind();
          channel.send(writeBuffer, destination);
        }
        catch(IOException ex)
        {
          System.err.println("Error while announcing IP: " + ex.getMessage());
        }
    }
    
    public void Close()
    {
      try
      {
        channel.close();
      }
      catch (IOException ex)
      {
      }
    }
  }
  
  private IDiscoveryListener listener;
  private final IPAddress myAddress;
  private Thread thread;
  private final ByteBuffer readBuffer = ByteBuffer.allocate(DatagramSize);
  private Announcer announcer;
  private Timer timer = new Timer("AnnounceTimer");

  public LANBroadcastDiscovery(IPAddress myAddress) throws IOException
  {
    this.myAddress = myAddress;    
    this.announcer = new Announcer();
    timer.schedule(announcer, 0, AnnouncePeriodSec * 1000);
  }
  
  @Override
  public void BeginDiscovery()
  {
    if(!IsDiscovering())
    {
      thread = new Thread(this, "LANBroadcastDiscovery");
      thread.start();
    }
  }

  @Override
  public void EndDiscovery()
  {
    if(IsDiscovering())
      thread.interrupt();
  }

  @Override
  public void SetDiscoveryListener(IDiscoveryListener listener)
  {
    this.listener = listener;
  }

  @Override
  public void Close()
  {
    EndDiscovery();
    announcer.cancel();
    announcer.Close();
    announcer = null;
    timer.cancel();
  }

  @Override
  public void Restart() throws IOException
  {
    announcer = new Announcer();
    timer.cancel();
    timer = new Timer("AnnounceTimer");
    timer.schedule(announcer, 0, AnnouncePeriodSec * 1000);    
  }

  @Override
  public IAddress GetMyAddress()
  {
    return myAddress;
  }

  @Override
  public void run()
  {
    DatagramChannel channel;
    try
    {
      channel = DatagramChannel.open();
      channel.socket().setReuseAddress(true);
      channel.socket().bind(new InetSocketAddress(Port));
    }
    catch(IOException ex)
    {
      System.err.printf("Broadcast discovery failed: %s\n", ex.getMessage());
      return;
    }

    while(!Thread.interrupted())
      try
      {
        readBuffer.clear();        
        channel.receive(readBuffer);
        readBuffer.flip();
        byte[] read = new byte[readBuffer.limit()];
        readBuffer.get(read);
        DataInputStream str = new DataInputStream(new ByteArrayInputStream(read));
        IPAddress discovered = new IPAddress(str);
        if(str.available() != 0)
          System.err.println("Unexpected data in the received datagram");
        else if(listener != null)
          listener.AddAddress(discovered);
      }
      catch (IOException ex)
      {
        if(!(ex instanceof ClosedByInterruptException))
          System.err.println("Error when receiving datagram: " + ex.getMessage());
        
        if(!channel.isOpen())
          break;
      }
    
    try
    {
      channel.close();
    }
    catch (IOException ex)
    {
    }
  } 
  
  public static ArrayList<InetAddress> GetBroadcastAddresses() 
  {
    ArrayList<InetAddress> listOfBroadcasts = new ArrayList();
    try 
    {
      Enumeration list = NetworkInterface.getNetworkInterfaces();

      while(list.hasMoreElements()) 
      {
        NetworkInterface iface = (NetworkInterface) list.nextElement();

        if(iface == null)
          continue;

        if(!iface.isLoopback() && iface.isUp()) 
          for (InterfaceAddress address : iface.getInterfaceAddresses())
          {
            //System.out.println("Found address: " + address);
            
            if(address == null) 
              continue;
            
            InetAddress broadcast = address.getBroadcast();
            if(broadcast != null) 
              listOfBroadcasts.add(broadcast);
          }
      }
    } 
    catch (SocketException ex) 
    {
      System.err.println("Error enumerating broadcast addresses: " + ex.getMessage());
    }
    
    if(listOfBroadcasts.isEmpty())
      listOfBroadcasts.add(InetAddress.getLoopbackAddress());

    return listOfBroadcasts;
  }
  
  public boolean IsDiscovering()
  {
    return thread != null && thread.isAlive();
  }
  
  @Override
  public IAcceptance GetAcceptance()
  {
    return acceptance;
  }  
}
