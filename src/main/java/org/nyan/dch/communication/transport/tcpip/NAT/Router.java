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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sbbi.upnp.devices.UPNPRootDevice;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.UPNPResponseException;

/**
 *
 * @author chris
 */
public class Router
{

  private static final Logger logger = Logger.getLogger(Router.class.getName());
  private final String name;

  public String getName()
  {
    return name;
  }

  /**
   * Get the the ip of the local host.
   */
  public String getLocalHostAddress() throws Exception
  {
    logger.fine("Get IP of localhost");
    final InetAddress localHostIP = getLocalHostAddressFromSocket();
// We do not want an address like 127.0.0.1
    if (localHostIP.isLoopbackAddress())
    {
      throw new Exception("Only found a loopback address when retrieving IP of localhost");
    }

    return localHostIP.getHostAddress();
  }

  /**
   * Get the ip of the local host by connecting to the router and fetching the
   * ip from the socket. This only works when we are connected to the router and
   * know its internal upnp port.
   *   
* @return the ip of the local host.
   * @throws RouterException
   */
  private InetAddress getLocalHostAddressFromSocket() throws Exception
  {
    InetAddress localHostIP = null;
    try
    {
// In order to use the socket method to get the address, we have to
// be connected to the router.
      final int routerInternalPort = getInternalPort();
      logger.log(Level.FINE, "Got internal router port {0}", routerInternalPort);
// Check, if we got a correct port number
      if (routerInternalPort > 0)
      {
        logger.log(Level.FINE, "Creating socket to router: {0}:{1}...", new Object[]{getInternalHostName(), routerInternalPort});
        try (Socket socket = new Socket(getInternalHostName(), routerInternalPort))
        {
          localHostIP = socket.getLocalAddress();
        }
        catch (final UnknownHostException e)
        {
          throw new Exception("Could not create socked to "
                  + getInternalHostName() + ":" + routerInternalPort,
                  e);
        }
        logger.log(Level.FINE, "Got address {0} from socket.", localHostIP);
      }
      else
      {
        logger.log(Level.FINE, "Got invalid internal router port number {0}", routerInternalPort);
      }

// We are not connected to the router or got an invalid port number,
// so we have to use the traditional method.
      if (localHostIP == null)
      {
        logger.fine("Not connected to router or got invalid port number, can not use socket to determine the address of the localhost. "
                + "If no address is found, please connect to the router.");
        localHostIP = InetAddress.getLocalHost();
        logger.log(Level.FINE, "Got address {0} via InetAddress.getLocalHost().", localHostIP);
      }
    }
    catch (final IOException e)
    {
      throw new Exception("Could not get IP of localhost.", e);
    }

    return localHostIP;
  }
  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#toString()
   */

  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    sb.append(getName()).append(" (").append(getInternalHostName())
            .append(")");
    return sb.toString();
  }

  /**
   * The wrapped router device.
   */
  final private InternetGatewayDevice router;
  /**
   * The maximum number of port mappings that we will try to retrieve from the
   * router.
   */
  private final static int MAX_NUM_PORTMAPPINGS = 500;

  Router(final InternetGatewayDevice router)
  {
    this.name = router.getIGDRootDevice().getModelName();
    this.router = router;
  }

  public String getExternalIPAddress() throws Exception
  {
    logger.fine("Get external IP address...");
    String ipAddress;
    try
    {
      ipAddress = router.getExternalIPAddress();
    }
    catch (final UPNPResponseException e)
    {
      throw new Exception("Could not get external IP", e);
    }
    catch (final IOException e)
    {
      throw new Exception("Could not get external IP", e);
    }
    logger.log(Level.INFO, "Got external IP address {0} for router.", ipAddress);
    return ipAddress;
  }

  public String getInternalHostName()
  {
    logger.fine("Get internal IP address...");
    final URL presentationURL = router.getIGDRootDevice()
            .getPresentationURL();
    if (presentationURL == null)
    {
      logger.warning("Did not get presentation url");
      return null;
    }
    
    final String ipAddress = presentationURL.getHost();
    logger.info(ipAddress + "Got internal host name '" + "' for router.");
    return ipAddress;
  }

  public int getInternalPort()
  {
    logger.fine("Get internal port of router...");
    final URL presentationURL = router.getIGDRootDevice()
            .getPresentationURL();
// Presentation URL may be null in some situations.
    if (presentationURL != null)
    {
      final int presentationUrlPort = presentationURL.getPort();
// https://sourceforge.net/tracker/?func=detail&aid=3198378&group_id=213879&atid=1027466
// Some routers send an invalid presentationURL, in this case use
// URLBase.
      if (presentationUrlPort > 0)
      {
        logger.log(Level.FINE, "Got valid internal port {0} from presentation URL.", presentationUrlPort);
        return presentationUrlPort;
      }
      else
        logger.log(Level.FINE, "Got invalid port {0} from presentation url {1}", new Object[]{presentationUrlPort, presentationURL});
    }
    else
      logger.fine("Presentation url is null");
    
    final int urlBasePort = router.getIGDRootDevice().getURLBase().getPort();
    logger.log(Level.FINE, "Presentation URL is null or returns invalid port: using url base port {0}", urlBasePort);
    return urlBasePort;
  }

  public Collection<PortMapping> getPortMappings() throws Exception
  {
    return new PortMappingExtractor(router, MAX_NUM_PORTMAPPINGS)
            .getPortMappings();
  }

  public void logRouterInfo() throws Exception
  {
    final Map<String, String> info = new HashMap<>();
    final UPNPRootDevice rootDevice = router.getIGDRootDevice();
    info.put("friendlyName", rootDevice.getFriendlyName());
    info.put("manufacturer", rootDevice.getManufacturer());
    info.put("modelDescription", rootDevice.getModelDescription());
    info.put("modelName", rootDevice.getModelName());
    info.put("serialNumber", rootDevice.getSerialNumber());
    info.put("vendorFirmware", rootDevice.getVendorFirmware());
    info.put("modelNumber", rootDevice.getModelNumber());
    info.put("modelURL", rootDevice.getModelURL());
    info.put("manufacturerURL", rootDevice.getManufacturerURL()
            .toExternalForm());
    info.put("presentationURL",
            rootDevice.getPresentationURL() != null ? rootDevice
            .getPresentationURL().toExternalForm() : null);
    info.put("urlBase", rootDevice.getURLBase().toExternalForm());
    final SortedSet<String> sortedKeys = new TreeSet<>(info.keySet());
    for (final String key : sortedKeys)
    {
      final String value = info.get(key);
      logger.log(Level.INFO, "Router Info: {0} \t= {1}", new Object[]{key, value});
    }
    logger.log(Level.INFO, "def loc {0}", rootDevice.getDeviceDefLoc());
    logger.log(Level.INFO, "def loc data {0}", rootDevice.getDeviceDefLocData());
    logger.log(Level.INFO, "icons {0}", rootDevice.getDeviceIcons());
    logger.log(Level.INFO, "device type {0}", rootDevice.getDeviceType());
    logger.log(Level.INFO, "direct parent {0}", rootDevice.getDirectParent());
    logger.log(Level.INFO, "disc udn {0}", rootDevice.getDiscoveryUDN());
    logger.log(Level.INFO, "disc usn {0}", rootDevice.getDiscoveryUSN());
    logger.log(Level.INFO, "udn {0}", rootDevice.getUDN());
  }

  private boolean addPortMapping(final String description,
          final Protocol protocol, final String remoteHost,
          final int externalPort, final String internalClient,
          final int internalPort, final int leaseDuration)
          throws Exception
  {
    final String protocolString = protocol == Protocol.TCP ? "TCP" : "UDP";
    final String encodedDescription = description;
    try
    {
      final boolean success = router.addPortMapping(encodedDescription,
              null, internalPort, externalPort, internalClient,
              leaseDuration, protocolString);
      return success;
    }
    catch (final IOException e)
    {
      throw new Exception("Could not add port mapping: "
              + e.getMessage(), e);
    }
    catch (final UPNPResponseException e)
    {
      throw new Exception("Could not add port mapping: "
              + e.getMessage(), e);
    }
  }

  public void addPortMappings(final Collection<PortMapping> mappings)
          throws Exception
  {
    for (final PortMapping portMapping : mappings)
    {
      logger.info("Adding port mapping " + portMapping);
      addPortMapping(portMapping);
    }
  }

  public void addPortMapping(final PortMapping mapping)
          throws Exception
  {
    logger.info("Adding port mapping " + mapping.getCompleteDescription());
    addPortMapping(mapping.getDescription(), mapping.getProtocol(),
            mapping.getRemoteHost(), mapping.getExternalPort(),
            mapping.getInternalClient(), mapping.getInternalPort(), 0);
  }

  public void removeMapping(final PortMapping mapping) throws Exception
  {
    removePortMapping(mapping.getProtocol(), mapping.getRemoteHost(),
            mapping.getExternalPort());
  }

  public void removePortMapping(final Protocol protocol,
          final String remoteHost, final int externalPort)
          throws Exception
  {
    final String protocolString = (protocol.equals(Protocol.TCP) ? "TCP"
            : "UDP");
    try
    {
      router.deletePortMapping(remoteHost, externalPort, protocolString);
    }
    catch (final IOException e)
    {
      throw new Exception("Could not remove port mapping", e);
    }
    catch (final UPNPResponseException e)
    {
      throw new Exception("Could not remove port mapping", e);
    }
  }
}
