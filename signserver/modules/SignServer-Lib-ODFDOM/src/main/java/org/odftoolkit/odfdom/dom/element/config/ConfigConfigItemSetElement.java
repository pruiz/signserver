/************************************************************************
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 *
 * Use is subject to license terms.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. You can also
 * obtain a copy of the License at http://odftoolkit.org/docs/license.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ************************************************************************/

/*
 * This file is automatically generated.
 * Don't edit manually.
 */    

package org.odftoolkit.odfdom.dom.element.config;

import org.odftoolkit.odfdom.OdfName;
import org.odftoolkit.odfdom.OdfNamespace;
import org.odftoolkit.odfdom.OdfFileDom;
import org.odftoolkit.odfdom.dom.OdfNamespaceNames;
import org.odftoolkit.odfdom.OdfElement;
import org.odftoolkit.odfdom.dom.attribute.config.ConfigNameAttribute;


/**
 * DOM implementation of OpenDocument element  {@odf.element config:config-item-set}.
 *
 */
public abstract class ConfigConfigItemSetElement extends OdfElement
{        
    public static final OdfName ELEMENT_NAME = OdfName.get( OdfNamespace.get(OdfNamespaceNames.CONFIG), "config-item-set" );


	/**
	 * Create the instance of <code>ConfigConfigItemSetElement</code> 
	 *
	 * @param  ownerDoc     The type is <code>OdfFileDom</code>
	 */
	public ConfigConfigItemSetElement( OdfFileDom ownerDoc )
	{
		super( ownerDoc, ELEMENT_NAME	);
	}

	/**
	 * Get the element name 
	 *
	 * @return  return   <code>OdfName</code> the name of element {@odf.element config:config-item-set}.
	 */
	public OdfName getOdfName()
	{
		return ELEMENT_NAME;
	}

	/**
	 * Initialization of the mandatory attributes of {@link  ConfigConfigItemSetElement}
	 *
     * @param configNameAttributeValue  The mandatory attribute {@odf.attribute  config:name}"
     *
	 */
	public void init(String configNameAttributeValue)
	{
		setConfigNameAttribute( configNameAttributeValue );
	}

	/**
	 * Receives the value of the ODFDOM attribute representation <code>ConfigNameAttribute</code> , See {@odf.attribute config:name}
	 *
	 * @return - the <code>String</code> , the value or <code>null</code>, if the attribute is not set and no default value defined.
	 */
	public String getConfigNameAttribute()
	{
		ConfigNameAttribute attr = (ConfigNameAttribute) getOdfAttribute( OdfName.get( OdfNamespace.get(OdfNamespaceNames.CONFIG), "name" ) );
		if( attr != null ){
			return String.valueOf( attr.getValue() );
		}
		return null;
	}
		 
	/**
	 * Sets the value of ODFDOM attribute representation <code>ConfigNameAttribute</code> , See {@odf.attribute config:name}
	 *
	 * @param configNameValue   The type is <code>String</code>
	 */
	public void setConfigNameAttribute( String configNameValue )
	{
		ConfigNameAttribute attr =  new ConfigNameAttribute( (OdfFileDom)this.ownerDocument );
		setOdfAttribute( attr );
		attr.setValue( configNameValue );
	}

	/**
	 * Create child element {@odf.element config:config-item}.
	 *
     * @param configNameAttributeValue  the <code>String</code> value of <code>ConfigNameAttribute</code>, see {@odf.attribute  config:name} at specification
	 * @param configTypeAttributeValue  the <code>String</code> value of <code>ConfigTypeAttribute</code>, see {@odf.attribute  config:type} at specification
	 * @return   return  the element {@odf.element config:config-item}
	 * DifferentQName 
	 */
    
	public ConfigConfigItemElement newConfigConfigItemElement(String configNameAttributeValue, String configTypeAttributeValue)
	{
		ConfigConfigItemElement  configConfigItem = ((OdfFileDom)this.ownerDocument).newOdfElement(ConfigConfigItemElement.class);
		configConfigItem.setConfigNameAttribute( configNameAttributeValue );
		configConfigItem.setConfigTypeAttribute( configTypeAttributeValue );
		this.appendChild( configConfigItem);
		return  configConfigItem;      
	}
    
	/**
	 * Create child element {@odf.element config:config-item-set}.
	 *
     * @param configNameAttributeValue  the <code>String</code> value of <code>ConfigNameAttribute</code>, see {@odf.attribute  config:name} at specification
	 * @return   return  the element {@odf.element config:config-item-set}
	 * DifferentQName 
	 */
    
	public ConfigConfigItemSetElement newConfigConfigItemSetElement(String configNameAttributeValue)
	{
		ConfigConfigItemSetElement  configConfigItemSet = ((OdfFileDom)this.ownerDocument).newOdfElement(ConfigConfigItemSetElement.class);
		configConfigItemSet.setConfigNameAttribute( configNameAttributeValue );
		this.appendChild( configConfigItemSet);
		return  configConfigItemSet;      
	}
    
	/**
	 * Create child element {@odf.element config:config-item-map-named}.
	 *
     * @param configNameAttributeValue  the <code>String</code> value of <code>ConfigNameAttribute</code>, see {@odf.attribute  config:name} at specification
	 * @return   return  the element {@odf.element config:config-item-map-named}
	 * DifferentQName 
	 */
    
	public ConfigConfigItemMapNamedElement newConfigConfigItemMapNamedElement(String configNameAttributeValue)
	{
		ConfigConfigItemMapNamedElement  configConfigItemMapNamed = ((OdfFileDom)this.ownerDocument).newOdfElement(ConfigConfigItemMapNamedElement.class);
		configConfigItemMapNamed.setConfigNameAttribute( configNameAttributeValue );
		this.appendChild( configConfigItemMapNamed);
		return  configConfigItemMapNamed;      
	}
    
	/**
	 * Create child element {@odf.element config:config-item-map-indexed}.
	 *
     * @param configNameAttributeValue  the <code>String</code> value of <code>ConfigNameAttribute</code>, see {@odf.attribute  config:name} at specification
	 * @return   return  the element {@odf.element config:config-item-map-indexed}
	 * DifferentQName 
	 */
    
	public ConfigConfigItemMapIndexedElement newConfigConfigItemMapIndexedElement(String configNameAttributeValue)
	{
		ConfigConfigItemMapIndexedElement  configConfigItemMapIndexed = ((OdfFileDom)this.ownerDocument).newOdfElement(ConfigConfigItemMapIndexedElement.class);
		configConfigItemMapIndexed.setConfigNameAttribute( configNameAttributeValue );
		this.appendChild( configConfigItemMapIndexed);
		return  configConfigItemMapIndexed;      
	}
    
}
