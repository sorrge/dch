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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import net.sbbi.upnp.services.ServiceStateVariable;
import net.sbbi.upnp.services.ServiceStateVariableTypes;

/**
 * Class to expose an JMX Mbean as an UPNP device service, this class shouldn't be used
 * directly
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class UPNPMBeanService {

  private final static Logger log = Logger.getLogger( UPNPMBeanService.class.getName() );
  
  private String serviceType;
  private String serviceId;
  private String serviceUUID;
  private String deviceUUID;
  private String deviceSCDP;
  
  private Map operationsStateVariables;
  private MBeanServer targetServer;
  private MBeanInfo mbeanInfo;
  private ObjectName mbeanName;
  private Class targetBeanClass;
  
  protected UPNPMBeanService( String deviceUUID, String vendorDomain, String serviceId, String serviceType, int serviceVersion,
                              MBeanInfo mbeanInfo, ObjectName mbeanName, MBeanServer targetServer ) throws IOException {
    this.deviceUUID = deviceUUID;
    if ( serviceId == null ) {
      serviceId = generateServiceId( mbeanName );
    }
    this.serviceUUID = deviceUUID + "/" + serviceId;
    this.serviceType = "urn:" + vendorDomain + ":service:" + serviceType + ":" + serviceVersion;
    this.serviceId = "urn:" + vendorDomain + ":serviceId:" + serviceId;
    deviceSCDP = getDeviceSSDP( mbeanInfo );
    try {
      targetBeanClass = Class.forName( mbeanInfo.getClassName() );
    } catch ( ClassNotFoundException ex ) {
      IOException ex2 = new IOException( "Unable to find target MBean class " + mbeanInfo.getClassName() );
      ex2.initCause( ex );
      throw ex2;
    }
    this.mbeanName = mbeanName;
    this.mbeanInfo = mbeanInfo;
    this.targetServer = targetServer;
  }
  
  protected String getServiceUUID() {
    return serviceUUID;
  }
  
  protected String getDeviceUUID() {
    return deviceUUID;
  }
  
  private String generateServiceId( ObjectName mbeanName ) {
    try {
      MessageDigest md5 = MessageDigest.getInstance( "MD5" );
      // the uuid is based on the device type, the internal id
      // and the host name
      md5.update( mbeanName.toString().getBytes() );
      StringBuffer hexString = new StringBuffer();
      byte[] digest = md5.digest();
      for (int i=0;i< digest.length; i++ ) {
        hexString.append( Integer.toHexString( 0xFF & digest[i] ) );
      }
      return hexString.toString().toUpperCase();
    } catch ( Exception ex ) {
      RuntimeException runTimeEx = new RuntimeException( "Unexpected error during MD5 hash creation, check your JRE" );
      runTimeEx.initCause( ex );
      throw runTimeEx;
    }
  }
  
  protected String getServiceInfo() {
    StringBuffer rtrVal = new StringBuffer();
    rtrVal.append( "<service>\r\n" );
    rtrVal.append( "<serviceType>" ).append( serviceType ).append( "</serviceType>\r\n" );
    rtrVal.append( "<serviceId>" ).append( serviceId ).append( "</serviceId>\r\n" );
    rtrVal.append( "<controlURL>" ).append( "/" ).append( serviceUUID ).append( "/control").append( "</controlURL>\r\n" );
    rtrVal.append( "<eventSubURL>" ).append( "/" ).append( serviceUUID ).append( "/events" ).append( "</eventSubURL>\r\n" );
    rtrVal.append( "<SCPDURL>" ).append( "/" ).append( serviceUUID ).append( "/scpd.xml" ).append( "</SCPDURL>\r\n" );
    rtrVal.append( "</service>\r\n" );
    return rtrVal.toString();
  }
  
  protected Map getOperationsStateVariables() {
    return operationsStateVariables;
  }
  
  protected String getDeviceSCDP() {
    return deviceSCDP;
  }

  protected String getServiceType() {
    return serviceType;
  }
  
  protected Class getTargetBeanClass() {
    return targetBeanClass;
  }
  
  protected ObjectName getObjectName() {
    return mbeanName;
  }
  
  protected Object getAttribute( String attributeName ) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
    return targetServer.getAttribute( mbeanName, attributeName );
  }
  
  protected Object invoke( String actionName, Object[] params, String[] signature ) throws ReflectionException, InstanceNotFoundException, MBeanException {
    return targetServer.invoke( mbeanName, actionName, params, signature );
  }
  
  private String getDeviceSSDP( MBeanInfo info ) throws IllegalArgumentException {
    
    MBeanOperationInfo[] ops = info.getOperations();
    MBeanAttributeInfo[] atts = info.getAttributes();
    
    if ( ( ops == null || ops.length == 0 ) && 
         ( atts == null || atts.length == 0 ) ) {
      throw new IllegalArgumentException( "MBean has no operation and no attribute and cannot be exposed as an UPNP device, provide at least one attribute" );
    }
    Set deployedActionNames = new HashSet();
    operationsStateVariables = new HashMap();
    StringBuffer rtrVal = new StringBuffer();
    rtrVal.append( "<?xml version=\"1.0\" ?>\r\n" );
    rtrVal.append( "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\r\n" );
    rtrVal.append( "<specVersion><major>1</major><minor>0</minor></specVersion>\r\n" );

    if ( ops != null && ops.length > 0 ) {
        rtrVal.append( "<actionList>\r\n" );
        for ( int i = 0; i < ops.length; i++ ) {
          MBeanOperationInfo op = ops[i];
          StringBuffer action = new StringBuffer();
          if ( deployedActionNames.contains( op.getName() ) ) {
            log.fine( "The " + op.getName() + " is allready deplyoed and cannot be reused, skipping operation deployment" );
            continue;
          }
          action.append( "<action>\r\n" );
          action.append( "<name>" );
          action.append( op.getName() );
          action.append( "</name>\r\n" );
          action.append( "<argumentList>\r\n" );
          // output argument
          action.append( "<argument>\r\n" );
          action.append( "<name>" );
          // TODO handle specific output vars
          String outVarName = op.getName() + "_out";
          String actionOutDataType = ServiceStateVariable.getUPNPDataTypeMapping( op.getReturnType() );
          if ( actionOutDataType == null ) actionOutDataType = ServiceStateVariableTypes.STRING;
          action.append( outVarName );
          action.append( "</name>\r\n" );
          action.append( "<direction>out</direction>\r\n" );
          action.append( "<relatedStateVariable>" );
          action.append( outVarName );
          action.append( "</relatedStateVariable>\r\n" );
          action.append( "</argument>\r\n" );
          
          // handle now for all input argument
          boolean nonPrimitiveInputType = false;
          boolean duplicatedInputVarname = false;
          Map operationsInputStateVariables = new HashMap();
          if ( op.getSignature() != null ) {
            for ( int z = 0; z < op.getSignature().length; z++ ) {
              MBeanParameterInfo param = op.getSignature()[z];
              // do some sanity checks
              String actionInDataType = ServiceStateVariable.getUPNPDataTypeMapping( param.getType() );
              if ( actionInDataType == null ) {
                nonPrimitiveInputType = true;
                log.fine( "The " + param.getType() + " type is not an UPNP compatible data type, use only primitives" );
                break;
              }
              String inVarName = param.getName();
              // check that if the name does allready exists it
              // has the same type
              String existing = (String)operationsStateVariables.get( inVarName );
              if ( existing != null && !existing.equals( actionInDataType ) ) {
                String msg =  "The operation " + op.getName() + " " + inVarName + 
                              " parameter already exists for another method with another data type (" + 
                              existing + ") either match the data type or change the parameter name" +
                              " in you MBeanParameterInfo object for this operation";
                duplicatedInputVarname = true;
                log.fine( msg );
                break;
              }
              action.append( "<argument>\r\n" );
              action.append( "<name>" );
              operationsInputStateVariables.put( inVarName, actionInDataType );
              action.append( inVarName );
              action.append( "</name>\r\n" );
              action.append( "<direction>in</direction>\r\n" );
              action.append( "<relatedStateVariable>" );
              action.append( inVarName );
              action.append( "</relatedStateVariable>\r\n" );
              action.append( "</argument>\r\n" );
            }
          }
          
          action.append( "</argumentList>\r\n" );
          action.append( "</action>\r\n" );
          // finally the action is only added to the UPNP SSDP if no problems have been detected
          // with the input parameters type and names.
          if ( !nonPrimitiveInputType && !duplicatedInputVarname ) {
            operationsStateVariables.putAll( operationsInputStateVariables );
            operationsStateVariables.put( outVarName, actionOutDataType );
            rtrVal.append( action.toString() );
            deployedActionNames.add( op.getName() );
          }
        }
      rtrVal.append( "</actionList>\r\n" );
    } else {
      rtrVal.append( "<actionList/>\r\n" );
    }
    
    // now append the operation created state vars
    rtrVal.append( "<serviceStateTable>\r\n" );
    
    for ( Iterator i = operationsStateVariables.keySet().iterator(); i.hasNext(); ) {
      String name = (String)i.next();
      String type = (String)operationsStateVariables.get( name );
      // TODO handle sendevents with mbean notifications ???
      // TODO handle defaultValue and allowedValueList values
      rtrVal.append( "<stateVariable sendEvents=\"no\">\r\n" );
      rtrVal.append( "<name>" );
      rtrVal.append( name );
      rtrVal.append( "</name>\r\n" );
      rtrVal.append( "<dataType>" );
      rtrVal.append( type );
      rtrVal.append( "</dataType>\r\n" );
      rtrVal.append( "</stateVariable>\r\n" );
    }
    
    if ( atts != null && atts.length > 0 ) {
      for ( int i = 0; i < atts.length; i++ ) {
        MBeanAttributeInfo att = atts[i];
        if ( att.isReadable() ) {
          rtrVal.append( "<stateVariable sendEvents=\"no\">\r\n" );
          rtrVal.append( "<name>" );
          rtrVal.append( att.getName() );
          rtrVal.append( "</name>\r\n" );
          rtrVal.append( "<dataType>" );
          // TODO check if this works
          String stateVarType = ServiceStateVariable.getUPNPDataTypeMapping( att.getType()  );
          if ( stateVarType == null ) stateVarType = ServiceStateVariableTypes.STRING;
          rtrVal.append( stateVarType );
          rtrVal.append( "</dataType>\r\n" );
          rtrVal.append( "</stateVariable>\r\n" );
        }
      }
    }
    rtrVal.append( "</serviceStateTable>\r\n" );
    rtrVal.append( "</scpd>" );
    return rtrVal.toString();
  }
  
  public MBeanInfo getMBeanInfo() {
    return mbeanInfo;
  }
  
}
