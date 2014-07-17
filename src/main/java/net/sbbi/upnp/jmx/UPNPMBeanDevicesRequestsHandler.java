/*
 * ============================================================================
 *                 The Apache Software License, Version 1.1
 * ============================================================================
 *
 * Copyright (C) 2002 The Apache Software Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modifica-
 * tion, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must  retain the above copyright  notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following  acknowledgment: "This product includes software
 *    developed by SuperBonBon Industries (http://www.sbbi.net/)."
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "UPNPLib" and "SuperBonBon Industries" must not be
 *    used to endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    info@sbbi.net.
 *
 * 5. Products  derived from this software may not be called 
 *    "SuperBonBon Industries", nor may "SBBI" appear in their name, 
 *    without prior written permission of SuperBonBon Industries.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED. IN NO EVENT SHALL THE
 * APACHE SOFTWARE FOUNDATION OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT,INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLU-
 * DING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software  consists of voluntary contributions made by many individuals
 * on behalf of SuperBonBon Industries. For more information on 
 * SuperBonBon Industries, please see <http://www.sbbi.net/>.
 */
package net.sbbi.upnp.jmx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * Class to handle HTTP UPNP requests on UPNPMBeanDevices
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class UPNPMBeanDevicesRequestsHandler implements Runnable {
  
  private final static Logger log = Logger.getLogger( UPNPMBeanDevicesRequestsHandler.class.getName() );
  private final static int MAX_HTTP_WORKERS = 10;
  
  private final static Map instances = new HashMap();
  
  private Set handledDevices = new HashSet();
  private Set httpWorkers = new HashSet();
  private ServerSocket srv;
  private boolean isRunning = false;
  
  private InetSocketAddress bindAddress;
  
  public final static UPNPMBeanDevicesRequestsHandler getInstance( InetSocketAddress bindAddress ) {
    String key = bindAddress.toString();
    synchronized( instances ) {
      UPNPMBeanDevicesRequestsHandler handler = (UPNPMBeanDevicesRequestsHandler)instances.get( key );
      if ( handler == null ) {
        handler = new UPNPMBeanDevicesRequestsHandler( bindAddress );
        instances.put( key, handler );
      }
      return handler;
    }
  }
  
  private UPNPMBeanDevicesRequestsHandler( InetSocketAddress bindAddress ) {
    this.bindAddress = bindAddress;
  }
  
  protected void addUPNPMBeanDevice( UPNPMBeanDevice rootDevice ) {
    synchronized( handledDevices ) {
      for ( Iterator i = handledDevices.iterator(); i.hasNext(); ) {
        UPNPMBeanDevice registred = (UPNPMBeanDevice)i.next();
        if ( registred.getDeviceType().equals( rootDevice.getDeviceType() ) && 
             registred.getUuid().equals( rootDevice.getUuid() ) ) {
          // API Use error
          throw new RuntimeException( "An UPNPMBeanDevice object of type " + rootDevice.getDeviceType() + 
                                      " with uuid " + rootDevice.getUuid() +
                                      " is already registred within this class, use a different UPNPMBeanDevice internalId" );
        }
      }
      if ( handledDevices.size() == 0 ) {
        Thread runner = new Thread( this, "UPNPMBeanDevicesRequestsHandler " + bindAddress.toString() );
        runner.setDaemon( true );
        runner.start();
      }
      handledDevices.add( rootDevice );
      // adding the child devices
      for ( Iterator i = rootDevice.getUPNPMBeanChildrens().iterator(); i.hasNext(); ) {
        handledDevices.add( i.next() );
      }
    }
  }
  
  protected void removeUPNPMBeanDevice( UPNPMBeanDevice rootDevice ) {
    synchronized( handledDevices ) {
      if ( handledDevices.contains( rootDevice ) ) {
        handledDevices.remove( rootDevice );
        // removing the child devices
        for ( Iterator i = rootDevice.getUPNPMBeanChildrens().iterator(); i.hasNext(); ) {
          handledDevices.remove( i.next() );
        }
        if ( handledDevices.size() == 0 ) {
          try {
            isRunning = false;
            srv.close();
          } catch ( IOException ex ) {
            // do not care
          }
        }
      }
    }
  }
  
  protected void notifyWorkerThreadEnd( HttpWorker worker ) {
    synchronized( httpWorkers ) {
      httpWorkers.remove( worker );
    }
  }
  
  public void run() {
    try {
      srv = new ServerSocket( bindAddress.getPort(), 200, bindAddress.getAddress() );
    } catch ( IOException ex ) {
      log.severe( "Error during server socket creation, thread cannot start" );
      return;
    }
    isRunning = true;
    while ( isRunning ) {
      try {
        Socket skt = srv.accept();
        skt.setSoTimeout( 30000 );
        HttpWorker worker = new HttpWorker(skt, log, handledDevices, this );
        while ( httpWorkers.size() >= MAX_HTTP_WORKERS ) {
          try {
            Thread.sleep( 100 );
          } catch ( InterruptedException ex ) {
            // ignore
          }
        }
        Thread workerThread = new Thread( worker, "UPNPMBeanDevicesRequestsHandler Http Worker " + httpWorkers.size() );
        workerThread.start();
        synchronized( httpWorkers ) {
          httpWorkers.add( worker );
        }
      } catch ( IOException ex ) {
        if ( isRunning ) {
          log.severe( "Error during client socket creation" );
        }
      }
    }
  }
  
  private class HttpWorker implements Runnable {
    
    private Socket client;
    private Logger logger;
    private Set devices;
    private UPNPMBeanDevicesRequestsHandler handler;
    
    public HttpWorker( Socket client, Logger log, Set handledDevices, UPNPMBeanDevicesRequestsHandler handler ) {
      this.client = client;
      this.logger = log;
      this.devices = handledDevices;
      this.handler = handler;
    }
    
    public void run() {
      try {
        byte[] buffer = new byte[256];
        InputStream in = client.getInputStream();
        StringBuffer request = new StringBuffer();
        int readen = 0;
        String firstReadenData = null;
        while ( readen != -1 ) {
          readen = in.read( buffer );
          String data = new String( buffer, 0, readen );
          if ( firstReadenData == null ) {
            firstReadenData = data.toUpperCase();
          }
          request.append( data );
          // either a simple get request
          String rawRequest = request.toString();
          if ( rawRequest.endsWith( "\r\n\r\n" ) && firstReadenData.startsWith( "GET" ) ) {
            readen = -1;
          } else if ( rawRequest.indexOf( "</s:Envelope>" ) != -1 ) {
            // or a post request with content that should end with </s:Envelope>
            readen = -1;
          }
        }
        
        OutputStream out = client.getOutputStream();
        String req = request.toString().trim();
        logger.log( Level.FINE, "Received message:\n{0}", req);
        String toWrite = null;
        if ( req.length() > 0 ) {
          HttpRequest httpReq = new HttpRequest( req );
          if ( httpReq.getHttpCommand() != null && httpReq.getHttpCommandArg() != null && 
               httpReq.getHttpCommandArg().trim().length() > 0 ) {
            String cmd = httpReq.getHttpCommand();
            HttpRequestHandler handler = null;
            if ( cmd.equals( "GET" ) ) {
              handler = HttpGetRequest.getInstance();
              // TODO implement M-POST
            } else if ( cmd.equals( "POST" ) ) {
              handler = HttpPostRequest.getInstance();
            } else if ( cmd.equals( "SUBSCRIBE" ) ) {
              handler = HttpSubscriptionRequest.getInstance();
            } else if ( cmd.equals( "UNSUBSCRIBE" ) ) {
              handler = HttpSubscriptionRequest.getInstance();
            }
            if ( handler != null ) {
              toWrite = handler.service( devices, httpReq );
            }
          }
        }
        if ( toWrite == null ) {
          String content = "<html><head><title>Not found</title></head><body>The requested ressource cannot be found</body></html>";
          StringBuffer rtr = new StringBuffer();
          rtr.append( "HTTP/1.1 404 Not Found\r\n" );
          rtr.append( "CONTENT-LENGTH: " ).append( content.length() ).append( "\r\n" );
          rtr.append( "CONTENT-TYPE: text/html\r\n\r\n" );
          rtr.append( content );
          toWrite = rtr.toString();
        }
        logger.log( Level.FINE, "Sending response :\n{0}", toWrite);
        out.write( toWrite.getBytes() );
        out.flush();
        out.close();
        in.close();
        client.close();
      } catch ( IOException ex ) {
        logger.severe("IO Exception occured during client serving" );
      } catch ( Throwable t ) {
        logger.severe("Unexpected Exception occured during client serving" );
      } finally {
        handler.notifyWorkerThreadEnd( this );
      }
    }
  }
}
