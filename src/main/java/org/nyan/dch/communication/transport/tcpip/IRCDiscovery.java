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

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.nyan.dch.communication.AcceptanceChecker;
import org.nyan.dch.communication.IAcceptance;
import org.nyan.dch.communication.IAddress;
import org.nyan.dch.communication.IDiscovery;
import org.nyan.dch.communication.IDiscoveryListener;
import org.nyan.dch.misc.AddressFormatException;
import org.nyan.dch.misc.Base58;
import org.nyan.dch.misc.IStoppable;
import org.nyan.dch.misc.RandomSet;


/**
 * IRCDiscovery provides a way to find network peers by joining a pre-agreed rendezvous point on an IRC network.
 * @author sorrge
 */
public class IRCDiscovery implements IDiscovery, Runnable, IStoppable
{
  public static final String Server = "us.undernet.org", Channel = "dch";
  public static final int ServerPort = 6667;
  
  private static class IRCMessage
  {
    static final Pattern Space = Pattern.compile(" ");
    
    final String prefix;
    final IRCCommands command;
    final String[] parameters;

    public IRCMessage(String line)
    {
      String[] parts = Space.split(line);
      int i = 0;
      if(parts[i].charAt(0) == ':')
        prefix = parts[i++].substring(1);
      else
        prefix = null;
      
      command = IRCCommands.FromCode(parts[i++]);
      ArrayList<String> params = new ArrayList<>(parts.length - i);
      for(; i <  Math.min(parts.length, 14) && parts[i].charAt(0) != ':'; ++i)
        params.add(parts[i]);
      
      if(i < parts.length)
      {
        String trailing = parts[i].charAt(0) == ':' ? parts[i].substring(1) : parts[i];
        for(++i; i < parts.length; ++i)
          trailing += " " + parts[i];
        
        params.add(trailing);
      }
      
      parameters = new String[params.size()];
      params.toArray(parameters);
    }
  }
  
  private static enum IRCCommands
  {
    Ping("PING"), Pong("PONG"), Nick("NICK"), User("USER"), Join("JOIN"), Quit("QUIT"), Who("WHO"),
    RplMyInfo("004"), RplNamReply("353"), RplWhoReply("352");
    
    public final String code;
    private static final HashMap<String, IRCCommands> codeMap = new HashMap<>();

    private IRCCommands(String code)
    {
      this.code = code;
    }
    
    static
    {
      for (IRCCommands type : IRCCommands.values()) 
        codeMap.put(type.code, type);
    }
    
    static IRCCommands FromCode(String code)
    {
      return codeMap.get(code.toUpperCase());
    }    
  }

  private final static Logger log = Logger.getLogger(IRCDiscovery.class.getName());
  private BufferedWriter writer;
  private BufferedReader reader;
  private Socket connection;
  private final Random rand;
  private Thread thread;
  private String myNick = null;
  private IPAddress myAddress = null;
  private final HashSet<String> checkedNicks = new HashSet<>();
  private final RandomSet<String> uncheckedNicks = new RandomSet<>();
  private final HashSet<IPAddress> sentAddresses = new HashSet<>(), unsentAddresses = new HashSet<>();
  private final HashMap<String, IPAddress> resolvedNicks = new HashMap<>();
  private IDiscoveryListener listener;
  private boolean discovering = false;
  private final int selectedPort;
  private final AcceptanceChecker acceptance = new AcceptanceChecker();
  private boolean portIsValid;

  public IRCDiscovery(int serverPort, Random rand) throws IOException
  {
    this.rand = rand;
    selectedPort = serverPort;
    SetPort(selectedPort);
    Connect();
  }

  private void Connect() throws IOException
  {
    InetAddress[] ips = InetAddress.getAllByName(Server);
    Collections.shuffle(Arrays.asList(ips), rand);
    for (InetAddress ip : ips)
    {
      try
      {
        connection = new Socket(ip, ServerPort);
        connection.setSoTimeout(1000);
        writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
        reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        Send(IRCCommands.Nick, myNick);
        char[] userName = new char[7];
        for (int i = 0; i < userName.length; ++i)
          userName[i] = (char) ('a' + rand.nextInt(26));
        
        Send(IRCCommands.User, String.valueOf(userName), "8", "*", String.valueOf(userName));
        thread = new Thread(this, "IRCDiscovery");
        thread.start();
        return;
      }
      catch (IOException ex)
      {
        if(connection != null)
          try { connection.close(); } catch (IOException exx) {}        
      }
    }
    
    throw new IOException("Could not connect to IRC");
  }
  
  private void Send(IRCCommands command, String... arguments) throws IOException 
  {
    String commandString = command.code;
    for(int i = 0; i < arguments.length - 1; ++i)
      commandString += " " + arguments[i];
    
    if(arguments.length > 0)
      commandString += " :" + arguments[arguments.length - 1];
    
    log.log(Level.FINE, "<{0}", commandString);    
    writer.write(commandString + "\r\n");
    writer.flush();
  }
  
  public final void SetPort(int port) throws IOException
  {
    byte[] portBytesWithCookieAndChecksum = new byte[5];
    rand.nextBytes(portBytesWithCookieAndChecksum);
    portBytesWithCookieAndChecksum[0] = (byte)(port & 0xff);
    portBytesWithCookieAndChecksum[1] = (byte)((port >> 8) & 0xff);
    portBytesWithCookieAndChecksum[4] = Checksum(portBytesWithCookieAndChecksum);
    
    synchronized(uncheckedNicks)
    {
      boolean checked = checkedNicks.remove(myNick);
      uncheckedNicks.remove(myNick);
      myNick = "d" + Base58.encode(portBytesWithCookieAndChecksum);
      (checked ? checkedNicks : uncheckedNicks).add(myNick);
    }
    
    if(myAddress != null)
      myAddress = new IPAddress(myAddress.address.getAddress(), port);
    
    if(writer != null)
      Send(IRCCommands.Nick, myNick);
    
    portIsValid = port > 127 && port < 65536;
  }
  
  private static byte Checksum(byte[] bytes)
  {
    byte checkSum = 0;
    for(int i = 0; i < 4; ++i)
      checkSum = (byte)(checkSum ^ bytes[i]);
    
    return checkSum;
  }
  
  private static int DecodeNick(String nick)
  {
    if(nick.charAt(0) != 'd')
      return -1;
    
    try
    {
      byte[] bytes = Base58.decode(nick.substring(1));
      if(bytes.length != 5)
        return -1;
      
      byte checkSum = Checksum(bytes);
      if(checkSum != bytes[4])
        return -1;
      
      int port = (((int)bytes[0]) & 0xff) | ((((int)bytes[1]) & 0xff) << 8);
      if(port < 128 || port > 65535)
        return -1;
      
      return port;
    }
    catch(AddressFormatException ex)
    {
      return -1;
    }
  }
  
  @Override
  public void BeginDiscovery()
  {
    discovering = true;
  }

  @Override
  public void EndDiscovery()
  {
    discovering = false;
  }

  @Override
  public void SetDiscoveryListener(IDiscoveryListener listener)
  {
    this.listener = listener;
  }

  @Override
  public void Close()
  {
    try 
    {
      if (connection != null)
      {
        Send(IRCCommands.Quit);
        connection.close();
      }
    } 
    catch (IOException ex) 
    {
    }
    
    acceptance.Close();
  }

  @Override
  public void Restart() throws IOException
  {
    if(!IsRunning())
      Connect();
  }

  @Override
  public IAddress GetMyAddress()
  {
    return myAddress;
  }

  @Override
  public void run()
  {
    boolean joinedChannel = false;
    Date lastPing = new Date();

    while (true)
      try
      {        
        String line = reader.readLine();
        if(line == null)
          break;
        
        log.log(Level.FINE, ">{0}", line);
        IRCMessage message = new IRCMessage(line);
        lastPing = new Date();

        if(message.command != null)
          switch(message.command)
          {
          case Ping:
            Send(IRCCommands.Pong, message.parameters);          
            break;
          case Join:
            String[] added = ParseUserSource(message.prefix);
            if(added[0] != null)
            {
              if(added[1] != null)
                RegisterNick(added[0], InetAddress.getByName(added[1]));
              else
                RegisterNick(added[0], null);
            }
            
            break;
          case Quit:
            String[] removed = ParseUserSource(message.prefix);
            if(removed[0] != null)
              UnregisterNick(removed[0]);
            
            break;
          case RplMyInfo:
            if(!joinedChannel)
            {
              Send(IRCCommands.Join, "#" + Channel);
              joinedChannel = true;          
            }   
            
            break;
          case RplNamReply:
            for(String nick : IRCMessage.Space.split(message.parameters[3]))
            {
              String n = nick.charAt(0) == '@' || nick.charAt(0) == '+' ? nick.substring(1) : nick;
              RegisterNick(n, null);
            }      
            
            break;
          case RplWhoReply:
            RegisterNick(message.parameters[5], InetAddress.getByName(message.parameters[3]));
            break;
          case Nick:
            String[] changed = ParseUserSource(message.prefix);
            if(changed[0] != null)
              UnregisterNick(changed[0]);
            
            if(changed[1] != null)
              RegisterNick(message.parameters[0], InetAddress.getByName(changed[1]));
            else
              RegisterNick(message.parameters[0], null);
            
            break;
          default:
            break;
          }
        
        if(discovering)
          CheckNicks();
        
        if(!acceptance.NeedToConfirmAcceptance())
          if(acceptance.CanAcceptConnections() && !portIsValid)
            SetPort(selectedPort);
          else if(!acceptance.CanAcceptConnections() && portIsValid)
            SetPort(0);
      }
      catch(SocketTimeoutException ste)
      {
        Date now = new Date();
        if(now.getTime() - lastPing.getTime() > 60 * 1000)
        {
          try { Send(IRCCommands.Ping, "xxx"); } catch (IOException ex) {}
          lastPing = now;
        }
      }
      catch (IOException ex)
      {
        log.log(Level.WARNING, "Error during Irc communication: {0}", ex.getMessage());
        if(connection.isClosed())
          break;
      }

    log.info("Irc connection closed");
  }

  private void UnregisterNick(String nick)
  {
    synchronized(uncheckedNicks)
    {
      log.log(Level.FINE, "Removed nick: {0}", nick);
      uncheckedNicks.remove(nick);
      checkedNicks.remove(nick);
      IPAddress addr = resolvedNicks.remove(nick);
      if(addr != null)
        unsentAddresses.remove(addr);
    }
  }
  
  private static String[] ParseUserSource(String src)
  {
    String[] res = new String[2];
    int nickLength = src.indexOf('!');
    if(nickLength != -1)
    {
      res[0] = src.substring(0, nickLength);
      int hostPos = src.indexOf('@', nickLength + 1);
      if(hostPos != -1)
        res[1] = src.substring(hostPos + 1);
    }
    
    return res;
  }
  
  public boolean IsRunning()
  {
    return thread != null && thread.isAlive();
  }
  
  private void RegisterNick(String nick, InetAddress address)
  {
    synchronized(uncheckedNicks)
    {
      if(checkedNicks.contains(nick) || uncheckedNicks.contains(nick) && address == null)
        return;

      uncheckedNicks.remove(nick);
      int port = DecodeNick(nick);
      if(port == -1)
        checkedNicks.add(nick);
      else if(address == null)
        uncheckedNicks.add(nick);
      else
      {
        checkedNicks.add(nick);
        IPAddress addr = new IPAddress(address, port);
        resolvedNicks.put(nick, addr);
        log.log(Level.FINE, "Nick {0}: {1}", new Object[]{nick, addr});      
        if(nick.equals(myNick))
          myAddress = addr;
        else
        {
          if(discovering && listener != null)
          {
            if(!sentAddresses.contains(addr))
            {
              listener.AddAddress(addr);
              sentAddresses.add(addr);
            }

            unsentAddresses.remove(addr);
          }
          else if(!sentAddresses.contains(addr))
            unsentAddresses.add(addr);
        }
      }
    }
  }
  
  private void CheckNicks() throws IOException
  {
    if(listener == null)
      return;
    
    synchronized(uncheckedNicks)
    {
      if(!unsentAddresses.isEmpty())
      {
        for(IPAddress addr : unsentAddresses)
          listener.AddAddress(addr);

        sentAddresses.addAll(unsentAddresses);
        unsentAddresses.clear();
      }
      else
        for(int i = 0; i < 10 && !uncheckedNicks.isEmpty(); ++i)
        {
          String toCheck = uncheckedNicks.pollRandom(rand);
          Send(IRCCommands.Who, toCheck);
        }
    }
  }
  
  @Override
  public IAcceptance GetAcceptance()
  {
    return acceptance;
  }  
}
