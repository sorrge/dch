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

package org.nyan.dch.communication.transport.tcpip.NAT;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nyan.dch.misc.IStoppable;

/**
 *
 * @author sorrge
 */
public class Utils
{
  public static class MappingInfo implements IStoppable
  {
    final PortMapping mapping;
    final Router router;

    public MappingInfo(PortMapping mapping, Router router)
    {
      this.mapping = mapping;
      this.router = router;
    }

    @Override
    public void Close()
    {
      try
      {
        router.removeMapping(mapping);
      }
      catch (Exception ex)
      {
      }    
    }
  }
  
  private static final Logger log = Logger.getLogger(Utils.class.getName());
  
  public static List<InetAddress> GetLocalAddresses()
  {
    ArrayList<InetAddress> res = new ArrayList<>();
    Enumeration<NetworkInterface> interfaces;
    try
    {
      interfaces = NetworkInterface.getNetworkInterfaces();
    }
    catch (SocketException ex)
    {
      return res;
    }
    
    while (interfaces.hasMoreElements()) 
    {
      NetworkInterface iface = interfaces.nextElement();
      try
      {
        // filters out 127.0.0.1 and inactive interfaces
        if (iface.isLoopback() || !iface.isUp())
          continue;
      }
      catch (SocketException ex)
      {
        continue;
      }

      Enumeration<InetAddress> addresses = iface.getInetAddresses();
      while(addresses.hasMoreElements())
      {
        InetAddress addr = addresses.nextElement();
        res.add(addr);
        log.log(Level.INFO, "A local address found: {0}", addr);
      }
    }
        
    return res;
  }
  
  public static MappingInfo MapPort(InetSocketAddress externalAddress)
  {
    RouterFactory rf = new RouterFactory();
    Router goodRouter = null;
    List<Router> routers = null;
    try
    {
      routers = rf.findRouters();
    }
    catch (Exception ex)
    {
    }

    if(routers == null || routers.isEmpty())
      log.warning("Could not find a UPnP router");
    else
    {
      for(Router r : routers)
        try
        {
          if(InetAddress.getByName(r.getExternalIPAddress()).equals(externalAddress.getAddress()))
          {
            goodRouter = r;
            break;
          }
        }
        catch (Exception ex)
        {
        }

      if(goodRouter == null)
        log.log(Level.WARNING, "Could not find a UPnP router with IP {0}", externalAddress.getAddress());
      else
      {
        try
        {
          PortMapping mapping = new PortMapping(Protocol.TCP, goodRouter.getExternalIPAddress(), externalAddress.getPort(), goodRouter.getLocalHostAddress(),
                  externalAddress.getPort(), "dch Node");
          goodRouter.addPortMapping(mapping);
          return new MappingInfo(mapping, goodRouter);
        }
        catch (Exception ex)
        {
          log.log(Level.WARNING, "Could not map port {0}: {1}", new Object[] { externalAddress.getPort(), ex.getMessage() });
        }
      }
    }
    
    return null;
  }
}
