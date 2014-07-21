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

package org.nyan.dch.apps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.transport.tcpip.IPAddress;
import org.nyan.dch.communication.transport.tcpip.LANBroadcastDiscovery;
import org.nyan.dch.communication.transport.tcpip.TCPServer;
import org.nyan.dch.misc.Utils;
import org.nyan.dch.node.Node;
import org.nyan.dch.posts.IPostAddedListener;
import org.nyan.dch.posts.Post;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.posts.Storage;

/**
 *
 * @author sorrge
 */
public class LANChat
{
  static class ReceiveReporter implements IPostAddedListener
  {
    private final DateFormat format = new SimpleDateFormat("HH:mm:ss");    

    @Override
    public void PostAdded(Post post)
    {
      PrintWriter writer = null;
      if(System.console() != null && System.console().writer() != null)
        writer = System.console().writer();
      
      if(writer == null)
        System.out.printf("[%s]\t%s\n", format.format(post.GetSentAt()), post.GetBody());
      else
        writer.printf("[%s]\t%s\n", format.format(post.GetSentAt()), post.GetBody());
    }
  }
  
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) throws IOException, InterruptedException
  {
    Node node = new Node(new Storage("b"));
    node.storage.AddPostAddedListener(new ReceiveReporter());
    Random rand = new Random();
    int port = rand.nextInt(10000) + 10000;
    IPAddress addr = new IPAddress(InetAddress.getLocalHost(), port);
    LANBroadcastDiscovery discovery = new LANBroadcastDiscovery(addr);
    TCPServer server = null;
    while(server == null)
      try
      {
        server = new TCPServer(port, discovery, rand);
      }
      catch(BindException be)
      {
        System.err.printf("Could not bind to port %d, trying another\n", port);
        port = rand.nextInt(10000) + 10000;
        addr = new IPAddress(InetAddress.getLocalHost(), port);
        discovery.Close();
        discovery = new LANBroadcastDiscovery(addr);
      }
    
//    System.out.println("My address: " + addr);
    
    Connections connections = new Connections(server, node, rand);
    Thread serverThread = new Thread(server);
    serverThread.start();
    Utils.StopOnShutdown(server);

    try
    {
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      while(true)
      {
        String input = br.readLine();
        if(input == null)
          break;
        
        node.storage.Add(new Post(new PostData("b", null, "", input, new Date())));
      }
    }
    catch(Exception ex)
    {
      ex.printStackTrace(System.err);
    }
  }  
}
