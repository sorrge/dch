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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.communication.Connections;
import org.nyan.dch.communication.transport.tcpip.IPAddress;
import org.nyan.dch.communication.transport.tcpip.IRCDiscovery;
import org.nyan.dch.communication.transport.tcpip.NAT.Utils;
import org.nyan.dch.communication.transport.tcpip.TCPServer;
import org.nyan.dch.node.Node;
import org.nyan.dch.posts.Post;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.posts.Storage;

/**
 *
 * @author sorrge
 */
public class GlobalChat
{
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) throws IOException, InterruptedException
  {
    Level logLevel;
    if(args.length > 0)
      logLevel = Level.parse(args[0]);
    else
      logLevel = Level.OFF;    
    
    //Handler systemOutHandler = new StreamHandler(System.out, new SimpleFormatter());
    //systemOutHandler.setLevel(logLevel);
    Logger rootLogger = Logger.getLogger("");
    //rootLogger.addHandler(systemOutHandler);
    rootLogger.setLevel(logLevel);

    Random rand = new Random();
    List<InetAddress> localAddresses = Utils.GetLocalAddresses();
    int serverPort = rand.nextInt(10000) + 10000;
    IRCDiscovery disc = new IRCDiscovery(serverPort, rand);
    Node node = new Node(new Storage("b"));
    node.storage.AddPostAddedListener(new LANChat.ReceiveReporter());
    TCPServer server = new TCPServer(serverPort, disc, rand);
    Connections connections = new Connections(server, node, rand);
    Thread serverThread = new Thread(server);
    serverThread.start();
    org.nyan.dch.misc.Utils.StopOnShutdown(server);    
    
    System.out.println("Connecting to IRC...");
    for(int i = 0; i < 10 * 90; ++i)
    {
      if(disc.GetMyAddress() != null)
        break;
      
      Thread.sleep(100);
    }
    
    if(disc.GetMyAddress() == null)
    {
      System.out.println("Could not connect to IRC :(");      
      disc.Close();
      server.Close();
      return;
    }
    
    InetSocketAddress externalAddress = ((IPAddress)disc.GetMyAddress()).address;
    if(!localAddresses.contains(externalAddress.getAddress()) && disc.GetAcceptance().NeedToConfirmAcceptance())
    {
      System.out.println("We seem to be behind a NAT... Trying to use UPnP to map port " + externalAddress.getPort() + " at " + localAddresses.get(0).getHostAddress());
      Utils.MappingInfo mappingInfo = Utils.MapPort(externalAddress);
      if(mappingInfo == null)
        System.out.println("Could not map port. I will blindly hope that it's forwarded for now.");
      else
      {
        System.out.println("Port mapped.");
        org.nyan.dch.misc.Utils.StopOnShutdown(mappingInfo);        
      }
    }

    try
    {
      BufferedReader br;
      if(System.console() == null || System.console().reader() == null)
        br = new BufferedReader(new InputStreamReader(System.in));
      else
        br = new BufferedReader(System.console().reader());
      
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
