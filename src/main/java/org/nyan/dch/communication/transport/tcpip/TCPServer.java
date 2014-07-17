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

import com.google.common.collect.Sets;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IConnections;
import org.nyan.dch.communication.IDiscovery;
import org.nyan.dch.communication.INetworkTransport;
import org.nyan.dch.communication.IRemoteHost;
import org.nyan.dch.misc.IStoppable;

/**
 *
 * @author sorrge
 */
public class TCPServer implements Runnable, INetworkTransport, IStoppable
{
  private static final Logger log = Logger.getLogger(TCPServer.class.getName());
  private Selector selector;
  private ServerSocketChannel serverChannel;
  private final ReceiveWorker processor;
  // The buffer into which we'll read data when it's available
  private final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
  private final ConcurrentLinkedQueue<IPAddress> connectionRequests = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<IPAddress> testConnectionRequests = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<SelectionKey> disconnectionRequests = new ConcurrentLinkedQueue<>();  
  private final Set<IPAddress> connecting = new HashSet<>();
  private final Set<SelectionKey> connected = Sets.newConcurrentHashSet();
  private final AtomicInteger connectionAttempts = new AtomicInteger(0);
  private boolean shouldStop;
  private final IDiscovery discovery;
  private final int port;
  
  public TCPServer(int port, IDiscovery discovery, Random rand) throws IOException
  {
    this.discovery = discovery;
    this.port = port;
    processor = new ReceiveWorker(rand);
    
    OpenChannel();
  }

  public final void Restart() throws IOException
  {
    OpenChannel();
    discovery.Restart();
  }

  private void OpenChannel() throws ClosedChannelException, IOException
  {
    shouldStop = false;
    selector = Selector.open();
    serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    InetAddress wildcard = null;
    serverChannel.socket().bind(new InetSocketAddress(wildcard, port));
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);
  }

  @Override
  public void run()
  {
    processor.SetServer(this);    
    Thread procThread = new Thread(processor);
    procThread.start();
    
    while (!shouldStop) 
    {
      try 
      {
        // Wait for an event one of the registered channels
        selector.select();

        // Iterate over the set of keys for which events are available
        Iterator selectedKeys = selector.selectedKeys().iterator();
        while (selectedKeys.hasNext()) 
        {
          SelectionKey key = (SelectionKey)selectedKeys.next();
          selectedKeys.remove();

          if (!key.isValid())
          {
            System.err.printf("Key for connection %s is not valid\n", key.attachment());
            continue;
          }

          // Check what event is available and deal with it
          if (key.isAcceptable()) 
            Accept(key);
          else if(key.isReadable())
            Read(key);
          else if(key.isConnectable())
          {
            if(key.attachment() == null)
              FinishTestConnect(key);
            else
              FinishConnect(key);
          }
        }
        
        for(SelectionKey key : selector.keys())
          Write(key);
        
        while(true)
        {
          IPAddress addr = connectionRequests.poll();
          if(addr == null)
            break;
          
          InitConnection(addr);
        }
        
        while(true)
        {
          IPAddress addr = testConnectionRequests.poll();
          if(addr == null)
            break;
          
          InitTestConnection(addr);
        }        
        
        while(true)
        {
          SelectionKey key = disconnectionRequests.poll();
          if(key == null)
            break;
          
          DropConnection(key);
        }        
      } 
      catch (IOException e) 
      {
        System.err.printf("TCP transport error: %s\n", e.getMessage());
        e.printStackTrace(System.err);
      }
      catch(Exception e)
      {
        System.err.printf("Unknown server error: %s\n", e);
        e.printStackTrace(System.err);
      }
    }
    
    procThread.interrupt();
    try
    {
      procThread.join();
    }
    catch (InterruptedException ex)
    {
    }    
    
    for(SelectionKey key : selector.keys())
      try
      {
        key.channel().close();
        if(connected.remove(key))
        {
          TCPConnection conn = (TCPConnection)key.attachment();
          if(conn != null)
            conn.ReportDisconnect(true);
        }
      }
      catch (IOException ex)
      {
        System.err.printf("Error closing connection: %s\n", ex.getMessage());        
      }
    
    try
    {
      selector.close();
    }
    catch (IOException ex)
    {
      System.err.printf("Error closing selector: %s\n", ex.getMessage());        
    }
    
    connecting.clear();
    connectionAttempts.set(0);
    connectionRequests.clear();
    disconnectionRequests.clear();
  }
  
  private void DropConnection(SelectionKey key) throws IOException
  {
//    System.out.printf("Server %s asked to disconnect from %s\n", processor, key.attachment());
//    DumpConnections();
    
    key.cancel();
    key.channel().close();
    connected.remove(key);
    processor.receivedItems.add(new ReceiveWorker.ConnectionChanged(key, false, true));
    
//    DumpConnections();    
  }  

  private void InitConnection(IPAddress addr) throws IOException
  {
//    System.out.printf("Server %s is requested to connect to %s\n", processor, addr);
//    DumpConnections();
    
    if (connecting.contains(addr) || GetConnectedHost(addr) != null || discovery.GetMyAddress().equals(addr))
    {
//      System.out.printf("Server %s has refused the request\n", processor);
//      System.out.printf("Connections %s's server has refused the connection request\n", processor.connections);
      connectionAttempts.decrementAndGet();
      processor.receivedItems.add(new ReceiveWorker.ConnectionFailed(addr));      
      return;
    }
    
    // Create a non-blocking socket channel
    SocketChannel socketChannel = SocketChannel.open();
    socketChannel.configureBlocking(false);
    
    // Kick off connection establishment
    socketChannel.connect(addr.address);
    SelectionKey key = socketChannel.register(selector, SelectionKey.OP_CONNECT);
    key.attach(addr);
    connecting.add(addr);
  }
  
  private void FinishConnect(SelectionKey key)
  {
    IPAddress addr = (IPAddress)key.attachment();
    connecting.remove(addr);
    connectionAttempts.decrementAndGet();
    SocketChannel socketChannel = (SocketChannel)key.channel();
    
    if(GetConnectedHost(addr) != null)
    {
      try      
      {
        socketChannel.close();
      }
      catch (IOException ex)
      {
      }
      
      key.cancel(); 
      processor.receivedItems.add(new ReceiveWorker.ConnectionFailed(addr));      
      return;
    }
  
    // Finish the connection. If the connection operation failed
    // this will raise an IOException.
    try 
    {
      socketChannel.finishConnect();
    } 
    catch (IOException e) 
    {
      // Cancel the channel's registration with our selector
      key.cancel();
      processor.receivedItems.add(new ReceiveWorker.ConnectionFailed(addr));
      return;
    }

    // Register an interest in reading on this channel
    key.interestOps(SelectionKey.OP_READ);
    connected.add(key);
//    System.out.printf("Server %s has added a connection: %s\n", processor, key.attachment());
//    DumpConnections();    
    processor.receivedItems.add(new ReceiveWorker.ConnectionChanged(key, true, true));
  }

  private void Write(SelectionKey key) throws IOException
  {
    if(!(key.attachment() instanceof TCPConnection))
      return;
    
    TCPConnection conn = (TCPConnection)key.attachment();
    
    ByteBuffer data = conn.toSend.peek();
    if(data == null)
      return;
    
    if(conn.sendBlocked && !key.isWritable())
      return;
    
    SocketChannel channel = (SocketChannel)key.channel();
    
    try
    {
      channel.write(data);
    }
    catch(IOException e)
    {  
      // The remote forcibly closed the connection, cancel
      // the selection key and close the channel.
      key.cancel();
      channel.close();
      connected.remove(key);
      processor.receivedItems.add(new ReceiveWorker.ConnectionChanged(key, false, false));
      return;      
    }
    
    if(data.remaining() > 0)
    {
      conn.sendBlocked = true;
      if((key.interestOps() & SelectionKey.OP_WRITE) == 0)
        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
      
      return;
    }
    
    conn.toSend.remove();
    conn.sendBlocked = false;
    if((key.interestOps() & SelectionKey.OP_WRITE) != 0)
      key.interestOps(SelectionKey.OP_READ);
    
    if(!conn.toSend.isEmpty())
      selector.wakeup();
  }

  private void Accept(SelectionKey key) throws IOException
  {
    // For an accept to be pending the channel must be a server socket channel.
    ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

    // Accept the connection and make it non-blocking
    SocketChannel socketChannel = serverSocketChannel.accept();
    socketChannel.configureBlocking(false);

    // Register the new SocketChannel with our Selector, indicating
    // we'd like to be notified when there's data waiting to be read
    SelectionKey acceptedKey = socketChannel.register(this.selector, SelectionKey.OP_READ);
    connected.add(acceptedKey);
//    System.out.printf("Server %s has added a connection: %s\n", processor, socketChannel.getRemoteAddress());   
//    DumpConnections();    
    processor.receivedItems.add(new ReceiveWorker.ConnectionChanged(acceptedKey, true, false));
    discovery.GetAcceptance().AcceptanceConfirmed();    
  }

  void DumpConnections()
  {
    for(SelectionKey k : connected)
      System.out.printf("\tServer %s is currently connected to %s\n", processor, k.attachment());
  }
  
  private void Read(SelectionKey key) throws IOException
  {
    SocketChannel socketChannel = (SocketChannel) key.channel();

    // Clear out our read buffer so it's ready for new data
    readBuffer.clear();
    
    // Attempt to read off the channel
    int numRead;
    try 
    {
      numRead = socketChannel.read(this.readBuffer);
    }
    catch (IOException e) 
    {
      // The remote forcibly closed the connection, cancel
      // the selection key and close the channel.
      key.cancel();
      socketChannel.close();
      connected.remove(key);
      processor.receivedItems.add(new ReceiveWorker.ConnectionChanged(key, false, false));
      return;
    }

    if (numRead == -1) 
    {
      // Remote entity shut the socket down cleanly. Do the
      // same from our end and close the channel.
      key.cancel();
      socketChannel.close();
      connected.remove(key);
      processor.receivedItems.add(new ReceiveWorker.ConnectionChanged(key, false, false));      
      return;
    }
    
    if(numRead > 0)
    {
      byte[] data = new byte[numRead];
      readBuffer.rewind();
      readBuffer.get(data);
      processor.receivedItems.add(new ReceiveWorker.DataReceived(key, data));
    }
    else
      System.err.printf("Read 0 bytes from channel %s\n", socketChannel.getRemoteAddress());
  }
  
  void WakeUp()
  {
    selector.wakeup();
  }
  
  void Disconnect(SelectionKey key)
  {
    disconnectionRequests.add(key);
    selector.wakeup();
  }
  
  @Override
  public void Close()
  {
    shouldStop = true;
    selector.wakeup();
    discovery.Close();
  }

  @Override
  public void SetConnectionsListener(IConnections listener)
  {
    processor.SetConnections(listener);
  }

  @Override
  public IAddress ReadAddress(DataInputStream stream) throws IOException
  {
    return new IPAddress(stream);
  }

  @Override
  public int GetCurrentConnectionAttempts()
  {
    return connectionAttempts.get();
  }

  @Override
  public void Connect(IAddress toWhom, boolean test)
  {
    if(!(toWhom instanceof IPAddress))
      throw new IllegalArgumentException("Wrong type of address supplied");
    
    IPAddress addr = (IPAddress)toWhom;
    
    log.log(Level.INFO, "{0}onnection to {1} requested.", new Object[]{test ? "Test c" : "C", toWhom});
    
    if(test)
    {
      if(!testConnectionRequests.contains(addr))
        testConnectionRequests.add(addr);
    }
    else
    {
      connectionRequests.add((IPAddress)toWhom);
      connectionAttempts.incrementAndGet();
    }
    
    selector.wakeup();
  }

  @Override
  public IDiscovery GetDiscovery()
  {
    return discovery;
  }

  @Override
  public int GetConnectionsCount()
  {
    return connected.size();
  }

  @Override
  public IRemoteHost GetConnectedHost(IAddress whom)
  {
    for(SelectionKey k : connected)
      if(k.attachment() instanceof TCPConnection)
      {
        TCPConnection conn = (TCPConnection)k.attachment();
        if(whom.equals(conn.GetAddress()))
          return conn;
      }
    
    return null;
  }

  private void InitTestConnection(IPAddress addr) throws IOException
  {
    if (discovery.GetMyAddress().equals(addr))
      return;
    
//    System.out.println("Testing connection to " + addr);
    
    // Create a non-blocking socket channel
    SocketChannel socketChannel = SocketChannel.open();
    socketChannel.configureBlocking(false);
    
    // Kick off connection establishment
    socketChannel.connect(addr.address);
    socketChannel.register(selector, SelectionKey.OP_CONNECT);
  }

  private void FinishTestConnect(SelectionKey key) throws IOException
  {
    SocketChannel socketChannel = (SocketChannel)key.channel();
    InetSocketAddress addr = (InetSocketAddress)socketChannel.getRemoteAddress();
//    System.out.println("Connection test for " + socketChannel.getRemoteAddress() + " succeeded");

    // Finish the connection. If the connection operation failed
    // this will raise an IOException.
    try 
    {
      socketChannel.finishConnect();
    } 
    catch (IOException e) 
    {
      processor.receivedItems.add(new ReceiveWorker.ConnectionFailed(new IPAddress(addr)));      
    }

    socketChannel.close();
    key.cancel();
  }
}
