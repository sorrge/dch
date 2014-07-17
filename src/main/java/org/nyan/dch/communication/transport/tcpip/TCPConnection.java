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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IRemoteHost;
import org.nyan.dch.communication.IRemoteHostListener;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.communication.RemoteNodeMessages;
import org.nyan.dch.misc.Utils;

/**
 *
 * @author sorrge
 */
public class TCPConnection implements IRemoteHost
{
  private IPAddress remoteAddress;
  private IRemoteHostListener myNode;
  private final TCPServer server;
  private final ByteArrayOutputStream incomingMessage = new ByteArrayOutputStream(RemoteNodeMessages.MaxMessageSize);
  private int expectedSize = Integer.SIZE / 8;
  private boolean readingSize = true;
  private final SelectionKey channelKey;
  final ConcurrentLinkedQueue<ByteBuffer> toSend = new ConcurrentLinkedQueue<>();
  boolean sendBlocked = false;
  private long cookie;

  public TCPConnection(IPAddress remoteAddress, TCPServer server, SelectionKey channelKey, long cookie)
  {
    this.remoteAddress = remoteAddress;
    this.server = server;
    this.channelKey = channelKey;
    this.cookie = cookie;
  }

  @Override
  public void SendData(byte[] data) throws ProtocolException
  {
    if(data.length == 0 || data.length > RemoteNodeMessages.MaxMessageSize)
      throw new ProtocolException(String.format("Tried to send a message with a wrong size: %d", data.length));
    
    ByteBuffer buf = ByteBuffer.allocate(data.length + Integer.SIZE / 8);
    buf.put(Utils.IntToByteArray(data.length));
    buf.put(data);
    buf.rewind();
    toSend.add(buf);
    server.WakeUp();
  }

  @Override
  public void SetReceiveListener(IRemoteHostListener node)
  {
    myNode = node;
  }

  @Override
  public void Disconnect()
  {
    server.Disconnect(channelKey);
  }

  public void ReportDisconnect(boolean iAmInitiator)
  {
    myNode.Disconnected(iAmInitiator);
  }

  @Override
  public IAddress GetAddress()
  {
    return remoteAddress;
  }
  
  public void ReceivedData(byte[] data)
  {
    for(int offset = 0; offset < data.length; )
    {
      int toRead = Math.min(data.length - offset, expectedSize - incomingMessage.size());
      incomingMessage.write(data, offset, toRead);
      offset += toRead;      
      if(expectedSize == incomingMessage.size())
      {
        if(readingSize)
        {
          expectedSize = Utils.ByteArrayToInt(incomingMessage.toByteArray());
          incomingMessage.reset();
          if(expectedSize <= 0 || expectedSize > RemoteNodeMessages.MaxMessageSize)
          {
            System.err.printf("Message of wrong size (%d) recieived from %s\n", expectedSize, remoteAddress);
            Disconnect();
            expectedSize = Integer.SIZE / 8;
            return;            
          }            
          
          readingSize = false;
        }
        else
          try
          {
            myNode.ReceiveData(incomingMessage.toByteArray());
          }
          catch (ProtocolException ex)
          {
            System.err.printf("Error while processing a message from %s: %s\n", remoteAddress, ex.getMessage());
            Disconnect();
            return;
          }
          finally
          {       
            expectedSize = Integer.SIZE / 8; 
            readingSize = true;
            incomingMessage.reset();
          }
      }
    }
  }

  @Override
  public void SetAddress(IAddress address, long cookie)
  {
    if(!(address instanceof IPAddress))
      throw new IllegalArgumentException("Wrong type of address given");
    
    remoteAddress = (IPAddress)address;
    this.cookie = cookie;
  }

  @Override
  public long GetCookie()
  {
    return cookie;
  }

  @Override
  public String toString()
  {
    return String.format("%s (cookie: %d)", remoteAddress, cookie);
  }
}
