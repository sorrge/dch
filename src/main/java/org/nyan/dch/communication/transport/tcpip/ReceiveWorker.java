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
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IConnections;

/**
 *
 * @author sorrge
 */
public class ReceiveWorker implements Runnable
{
  static abstract class WorkItem 
  {
    final SelectionKey connection;

    public WorkItem(SelectionKey acceptedConnection)
    {
      this.connection = acceptedConnection;
    }
  }
  
  static class ConnectionChanged extends WorkItem
  {
    final boolean connected, iAmInitiator;

    public ConnectionChanged(SelectionKey acceptedConnection, boolean connected, boolean iAmInitiator)
    {
      super(acceptedConnection);
      this.connected = connected;
      this.iAmInitiator = iAmInitiator;
    }
  }
  
  static class DataReceived extends WorkItem
  {
    final byte[] data;

    public DataReceived(SelectionKey connection, byte[] data)
    {
      super(connection);
      this.data = data;
    }
  }
  
  static class ConnectionFailed extends WorkItem
  {
    final IAddress address;
    
    public ConnectionFailed(IAddress address)
    {
      super(null);
      this.address = address;
    }
  }
  
  private TCPServer server;
  final BlockingQueue<WorkItem> receivedItems = new LinkedBlockingQueue<>();
  private IConnections connections;
  private final Random rand;

  public ReceiveWorker(Random rand)
  {
    this.rand = rand;
  }

  public void SetServer(TCPServer server)
  {
    this.server = server;
  }

  public void SetConnections(IConnections connections)
  {
    this.connections = connections;
  }
  
  @Override
  public void run()
  {
    while(true)
    {
      try 
      {      
        WorkItem it = receivedItems.take();
        ProcessItem(it);
      }
      catch (InterruptedException ex)
      {
        break;
      }
      catch (Exception e)
      {
        System.err.printf("Unknown worker error: %s\n", e);
        e.printStackTrace(System.err);        
      }
    }
    
    while(!receivedItems.isEmpty())
    {
      try 
      {      
        WorkItem it = receivedItems.take();
        ProcessItem(it);
      }
      catch (InterruptedException ex)
      {
        break;
      }
      catch (Exception e)
      {
        System.err.printf("Unknown worker error: %s\n", e);
        e.printStackTrace(System.err);        
      }
    }    
  }

  private void ProcessItem(WorkItem it)
  {
    if (it instanceof ConnectionChanged)
    {
      ConnectionChanged ce = (ConnectionChanged)it;
      if (ce.connected)
      {
        if (!ce.connection.channel().isOpen())
        {
//          System.err.printf("Connection closed immediately: %s\n", ce.connection.channel());
          return;
        }
        
        SocketChannel channel = (SocketChannel)ce.connection.channel();
        TCPConnection conn = new TCPConnection((IPAddress)ce.connection.attachment(), server, ce.connection, rand.nextLong());
        ce.connection.attach(conn);
        connections.Connected(conn, ce.iAmInitiator);
//        System.out.printf("Server %s connected to %s\n", this, ce.connection.attachment());
//        server.DumpConnections();
      }
      else
      {
        if(ce.connection.attachment() instanceof TCPConnection)
        {
          TCPConnection conn = (TCPConnection)ce.connection.attachment();
          if(conn != null)
          {
            conn.ReportDisconnect(ce.iAmInitiator);
//            System.out.printf("Server %s disconnected from %s (cookie: %s)\n", this, conn.GetAddress(), conn.GetCookie());
          }
        }
//        else
//          System.out.printf("Connection closed: %s\n", ce.connection.attachment());
      }
    }
    else if (it instanceof DataReceived)
    {
      DataReceived de = (DataReceived)it;
      if (!(de.connection.attachment() instanceof TCPConnection))
      {
//        System.err.printf("Data received on already closed connection: %s\n", de.connection.channel());
        return;
      }
      
      TCPConnection conn = (TCPConnection)de.connection.attachment();
      conn.ReceivedData(de.data);
    }
    else if(it instanceof ConnectionFailed)
    {
//      System.out.printf("Connection to %s failed\n", ((ConnectionFailed)it).address);
      connections.ConnectionFailed(((ConnectionFailed)it).address);
    }
    else
      System.err.printf("Unknown item in the processing queue: %s\n", it);
  }
}
