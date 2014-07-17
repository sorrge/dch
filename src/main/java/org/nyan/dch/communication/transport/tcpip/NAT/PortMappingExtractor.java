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
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.ActionResponse;
import net.sbbi.upnp.messages.UPNPResponseException;

/**
 * This class fetches all {@link PortMapping} from an
 * {@link InternetGatewayDevice}.
 * 
* @author chris
 */
class PortMappingExtractor
{
  private static final Logger logger = Logger.getLogger(PortMappingExtractor.class.getName());
  private final InternetGatewayDevice router;
  private final Collection<PortMapping> mappings;
  private boolean moreEntries;
  private int currentMappingNumber;
  private int nullPortMappings;
  /**
   * The maximum number of port mappings that we will try to retrieve from the
   * router.
   */
  private final int maxNumPortMappings;

  PortMappingExtractor(final InternetGatewayDevice router,
          final int maxNumPortMappings)
  {
    this.router = router;
    this.maxNumPortMappings = maxNumPortMappings;
    this.mappings = new LinkedList<>();
    this.moreEntries = true;
    this.currentMappingNumber = 0;
    this.nullPortMappings = 0;
  }

  public Collection<PortMapping> getPortMappings() throws Exception
  {
    try
    {
      /*
       * This is a little trick to get all port mappings. There is a
       * method that gets the number of available port mappings
       * (getNatMappingsCount()), but it seems, that this method just
       * tries to get all port mappings and checks, if an error is
       * returned.
       *
       * In order to speed this up, we will do the same here, but stop,
       * when the first exception is thrown.
       */
      while (morePortMappingsAvailable())
      {
        logger.log(Level.FINE, "Getting port mapping with entry number {0}...", currentMappingNumber);
        try
        {
          final ActionResponse response = router
                  .getGenericPortMappingEntry(currentMappingNumber);
          addResponse(response);
        }
        catch (final UPNPResponseException e)
        {
          handleUPNPResponseException(e);
        }
        currentMappingNumber++;
      }
      checkMaxNumPortMappingsReached();
    }
    catch (final IOException e)
    {
      throw new Exception("Could not get NAT mappings: "
              + e.getMessage(), e);
    }
    
    logger.log(Level.INFO, "Found {0} mappings, {1} mappings returned as null.", new Object[]{mappings.size(), nullPortMappings});
    return mappings;
  }

  /**
   * Check, if the max number of entries is reached and print a warning message.
   */
  private void checkMaxNumPortMappingsReached()
  {
    if (currentMappingNumber == maxNumPortMappings)
      logger.log(Level.WARNING, "Reached max number of port mappings to get ({0}). Perhaps not all port mappings where retrieved. Try to increase SBBIRouter.MAX_NUM_PORTMAPPINGS.", maxNumPortMappings);
  }

  private boolean morePortMappingsAvailable()
  {
    return moreEntries && currentMappingNumber < maxNumPortMappings;
  }

  private void addResponse(final ActionResponse response)
  {
// Create a port mapping for the response.
    if (response != null)
    {
      final PortMapping newMapping = PortMapping.create(response);
      logger.log(Level.FINEST, "Got port mapping #{0}: {1}", new Object[]{currentMappingNumber, newMapping.getCompleteDescription()});
      mappings.add(newMapping);
    }
    else
    {
      nullPortMappings++;
      logger.log(Level.FINEST, "Got a null port mapping for number {0} ({1} so far).", new Object[]{currentMappingNumber, nullPortMappings});
    }
  }

  private void handleUPNPResponseException(final UPNPResponseException e)
  {
    if (isNoMoreMappingsException(e))
    {
      moreEntries = false;
      logger.log(Level.FINE, "Got no port mapping for entry number {0} (error code: {1}, error description: {2}). Stop getting more entries.", new Object[]{currentMappingNumber, e.getDetailErrorCode(), e.getDetailErrorDescription()});
    }
    else
    {
      moreEntries = false;
      logger.log(Level.SEVERE, "Got exception when fetching port mapping for entry number {0}. Stop getting more entries.", + currentMappingNumber);
    }
  }

  /**
   * This method checks, if the error code of the given exception means, that no
   * more mappings are available.
   * <p>
   * The following error codes are recognized:
   * <ul>
   * <li>SpecifiedArrayIndexInvalid: 713</li>
   * <li>NoSuchEntryInArray: 714</li>
   * <li>Invalid Args: 402 (e.g. for DD-WRT, TP-LINK TL-R460 firmware 4.7.6
   * Build 100714 Rel.63134n)</li>
   * <li>Other errors, e.g. "The reference to entity "T" must end with the ';'
   * delimiter" or "Content is not allowed in prolog": 899 (e.g. ActionTec
   * MI424-WR, Thomson TWG850-4U)</li>
   * </ul>
   * See bug reports
   * <ul>
   * <li><a href=
   * "https://sourceforge.net/tracker/index.php?func=detail&aid=1939749&group_id=213879&atid=1027466"
   * >https://sourceforge.net/tracker/index.php?func=detail&aid=
   * 1939749&group_id=213879&atid=1027466</a></li>
   * <li><a href="http://www.sbbi.net/forum/viewtopic.php?p=394">http://www.sbbi
   * .net/forum/viewtopic.php?p=394</a></li>
   * <li><a href=
   * "http://sourceforge.net/tracker/?func=detail&atid=1027466&aid=3325388&group_id=213879"
   * >http://sourceforge.net/tracker/?func=detail&atid=1027466&aid=3325388&
   * group_id=213879</a></li>
   * <a href=
   * "https://sourceforge.net/tracker2/?func=detail&aid=2540478&group_id=213879&atid=1027466"
   * >https://sourceforge.net/tracker2/?func=detail&aid=2540478&group_id=
   * 213879&atid=1027466</a></li>
   * </ul>
   *   
* @param e the exception to check
   * @return <code>true</code>, if the given exception means, that no more port
   * mappings are available, else <code>false</code>.
   */
  private boolean isNoMoreMappingsException(final UPNPResponseException e)
  {
    final int errorCode = e.getDetailErrorCode();
    switch (errorCode)
    {
      case 713:
      case 714:
      case 402:
      case 899:
        return true;
      default:
        return false;
    }
  }
}
