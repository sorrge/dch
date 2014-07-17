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

import java.util.HashMap;
import java.util.Map;
import net.sbbi.upnp.messages.ActionResponse;

/**
 * This immutable class represents a port mapping / forwarding on a router.
 * 
* @author chris
 * @version $Id: PortMapping.java 126 2013-08-04 15:18:04Z christoph $
 */
public class PortMapping implements Cloneable
{
  public static final String MAPPING_ENTRY_LEASE_DURATION = "NewLeaseDuration";
  public static final String MAPPING_ENTRY_ENABLED = "NewEnabled";
  public static final String MAPPING_ENTRY_REMOTE_HOST = "NewRemoteHost";
  public static final String MAPPING_ENTRY_INTERNAL_CLIENT = "NewInternalClient";
  public static final String MAPPING_ENTRY_PORT_MAPPING_DESCRIPTION = "NewPortMappingDescription";
  public static final String MAPPING_ENTRY_PROTOCOL = "NewProtocol";
  public static final String MAPPING_ENTRY_INTERNAL_PORT = "NewInternalPort";
  public static final String MAPPING_ENTRY_EXTERNAL_PORT = "NewExternalPort";
  private final int externalPort;
  private final Protocol protocol;
  private final int internalPort;
  private final String description;
  private final String internalClient;
  private final String remoteHost;
  private final boolean enabled;
  private final long leaseDuration;

  public PortMapping(final Protocol protocol, final String remoteHost,
          final int externalPort, final String internalClient,
          final int internalPort, final String description)
  {
    this(protocol, remoteHost, externalPort, internalClient, internalPort,
            description, true);
  }

  private PortMapping(final Protocol protocol, final String remoteHost,
          final int externalPort, final String internalClient,
          final int internalPort, final String description,
          final boolean enabled)
  {
    super();
    this.protocol = protocol;
    this.remoteHost = remoteHost;
    this.externalPort = externalPort;
    this.internalClient = internalClient;
    this.internalPort = internalPort;
    this.description = description;
    this.enabled = enabled;
    this.leaseDuration = -1;
  }

  private PortMapping(final ActionResponse response)
  {
    final Map<String, String> values = new HashMap<>();
    for (final Object argObj : response.getOutActionArgumentNames())
    {
      final String argName = (String) argObj;
      values.put(argName, response.getOutActionArgumentValue(argName));
    }
    externalPort = Integer
            .parseInt(values.get(MAPPING_ENTRY_EXTERNAL_PORT));
    internalPort = Integer
            .parseInt(values.get(MAPPING_ENTRY_INTERNAL_PORT));
    final String protocolString = values.get(MAPPING_ENTRY_PROTOCOL);
    protocol = (protocolString.equalsIgnoreCase("TCP") ? Protocol.TCP
            : Protocol.UDP);
    description = values.get(MAPPING_ENTRY_PORT_MAPPING_DESCRIPTION);
    internalClient = values.get(MAPPING_ENTRY_INTERNAL_CLIENT);
    remoteHost = values.get(MAPPING_ENTRY_REMOTE_HOST);
    final String enabledString = values.get(MAPPING_ENTRY_ENABLED);
    enabled = enabledString != null && enabledString.equals("1");
    leaseDuration = Long
            .parseLong(values.get(MAPPING_ENTRY_LEASE_DURATION));
  }

  public static PortMapping create(final ActionResponse response)
  {
    final PortMapping mapping = new PortMapping(response);
    return mapping;
  }

  /**
   * @return the leaseDuration
   */
  public long getLeaseDuration()
  {
    return leaseDuration;
  }

  public int getExternalPort()
  {
    return externalPort;
  }

  public Protocol getProtocol()
  {
    return protocol;
  }

  public int getInternalPort()
  {
    return internalPort;
  }

  public String getDescription()
  {
    return description;
  }

  public String getInternalClient()
  {
    return internalClient;
  }

  public String getRemoteHost()
  {
    return remoteHost;
  }

  public boolean isEnabled()
  {
    return enabled;
  }

  public String getCompleteDescription()
  {
    final StringBuilder b = new StringBuilder();
    b.append(protocol);
    b.append(" ");
    if (remoteHost != null)
    {
      b.append(remoteHost);
    }
    b.append(":");
    b.append(externalPort);
    b.append(" -> ");
    b.append(internalClient);
    b.append(":");
    b.append(internalPort);
    b.append(" ");
    b.append(enabled ? "enabled" : "not enabled");
    b.append(" ");
    b.append(description);
    return b.toString();
  }

  @Override
  public String toString()
  {
    return description;
  }

  @Override
  public Object clone()
  {
    final PortMapping clonedMapping = new PortMapping(protocol, remoteHost,
            externalPort, internalClient, internalPort, description,
            enabled);
    return clonedMapping;
  }
}
