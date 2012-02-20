/*
 *  soapUI, copyright (C) 2004-2012 smartbear.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.w3c.dom.Node;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.ModelItemConfig;
import com.eviware.soapui.config.ProjectConfig;
import com.eviware.soapui.config.RegexConfig;
import com.eviware.soapui.config.RestParametersConfig;
import com.eviware.soapui.config.SearchPatternsDocumentConfig;
import com.eviware.soapui.impl.rest.support.RestParamProperty;
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder;
import com.eviware.soapui.impl.rest.support.XmlBeansRestParamsTestPropertyHolder;
import com.eviware.soapui.impl.support.AbstractHttpRequest;
import com.eviware.soapui.impl.wsdl.AbstractWsdlModelItem;
import com.eviware.soapui.impl.wsdl.MutableTestPropertyHolder;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.propertyexpansion.PropertyExpander;
import com.eviware.soapui.model.security.SecurityCheckedParameter;
import com.eviware.soapui.model.support.ModelSupport;
import com.eviware.soapui.model.support.SettingsTestPropertyHolder;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.security.assertion.SensitiveInfoExposureAssertion;
import com.eviware.soapui.security.panels.ProjectSensitiveInformationPanel;
import com.eviware.soapui.security.scan.GroovySecurityScan;
import com.eviware.soapui.settings.GlobalPropertySettings;
import com.eviware.soapui.support.types.StringToStringMap;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.eviware.soapui.support.xml.XmlObjectTreeModel;
import com.eviware.soapui.support.xml.XmlUtils;

public class SecurityScanUtil
{
	private static SettingsTestPropertyHolder globalSensitiveInformationExposureTokens;

	public static Map<String, String> globalEntriesList()
	{
		Map<String, TestProperty> map = getGlobalSensitiveInformationExposureTokens().getProperties();

		StringToStringMap result = new StringToStringMap();

		for( String key : map.keySet() )
			result.put( key, map.get( key ).getValue() );

		return result;
	}

	public static String contains( SubmitContext context, String content, String token, boolean useRegEx )
	{
		if( token == null )
			token = "";
		String replToken = PropertyExpander.expandProperties( context, token );
		String result = null;

		if( replToken.length() > 0 )
		{
			if( useRegEx )
			{
				boolean grouped = false;
				String orgToken = token;

				if( token.startsWith( "(?s).*" ) && token.endsWith( ".*" ) )
				{
					token = "(?s)((.*)(" + token.substring( 6, token.length() - 2 ) + ")(.*))";
					grouped = true;
				}

				Pattern pattern = Pattern.compile( token );
				Matcher matcher = pattern.matcher( content );

				if( matcher.matches() )
				{
					if( grouped && matcher.groupCount() > 2 )
						result = content.substring( matcher.start( 3 ), matcher.end( 3 ) );
					else
						result = content.substring( matcher.start(), matcher.end() );
				}
			}
			else
			{
				if( content.toUpperCase().indexOf( replToken.toUpperCase() ) >= 0 )
					result = replToken;
			}
		}

		return result;
	}

	public static RestParamsPropertyHolder getSoapRequestParams( AbstractHttpRequest<?> request )
	{
		XmlBeansRestParamsTestPropertyHolder holder = new XmlBeansRestParamsTestPropertyHolder( request,
				RestParametersConfig.Factory.newInstance() );
		try
		{
			// XmlObject requestXml = XmlObject.Factory.parse(
			// request.getRequestContent(), new XmlOptions()
			// .setLoadStripWhitespace().setLoadStripComments() );
			XmlObject requestXml = XmlUtils.createXmlObject( request.getRequestContent(), new XmlOptions()
					.setLoadStripWhitespace().setLoadStripComments() );
			Node[] nodes = XmlUtils.selectDomNodes( requestXml, "//text()" );

			for( Node node : nodes )
			{
				String xpath = XmlUtils.createXPath( node.getParentNode() );
				RestParamProperty property = holder.addProperty( node.getParentNode().getNodeName() );
				property.setValue( node.getNodeValue() );
				property.setPath( xpath );
			}
		}
		catch( XmlException e )
		{
			SoapUI.logError( e );
		}
		return holder;
	}

	@SuppressWarnings( "unchecked" )
	public static Map<String, String> projectEntriesList( SensitiveInfoExposureAssertion sensitiveInfoExposureAssertion )
	{
		Project project = ModelSupport.getModelItemProject( sensitiveInfoExposureAssertion );
		AbstractWsdlModelItem<ModelItemConfig> modelItem = ( AbstractWsdlModelItem<ModelItemConfig> )project
				.getModelItem();
		XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( ( ( ProjectConfig )modelItem.getConfig() )
				.getSensitiveInformation() );
		String[] strngArray = reader.readStrings( ProjectSensitiveInformationPanel.PROJECT_SPECIFIC_EXPOSURE_LIST );
		if( strngArray != null )
		{
			Map<String, String> map = new HashMap<String, String>();

			for( String str : strngArray )
			{
				String[] tokens = str.split( "###" );
				if( tokens.length == 2 )
				{
					map.put( tokens[0], tokens[1] );
				}
				else
				{
					map.put( tokens[0], "" );
				}
			}
			return map;
		}
		else
		{
			return new HashMap<String, String>();
		}
	}

	public static XmlObjectTreeModel getXmlObjectTreeModel( TestStep testStep, SecurityCheckedParameter scp )
	{
		try
		{
			TestProperty tp = testStep.getProperty( scp.getName() );
			if( tp.getSchemaType() != null && XmlUtils.seemsToBeXml( tp.getValue() ) )
			{
				// return new XmlObjectTreeModel(
				// tp.getSchemaType().getTypeSystem(), XmlObject.Factory.parse(
				// tp.getValue() ) );
				return new XmlObjectTreeModel( tp.getSchemaType().getTypeSystem(), XmlUtils.createXmlObject( tp.getValue() ) );
			}
		}
		catch( Exception e )
		{
			SoapUI.logError( e );
		}
		return null;
	}

	private synchronized static void initGlobalSecuritySettings()
	{
		globalSensitiveInformationExposureTokens = new SettingsTestPropertyHolder( SoapUI.getSettings(), null,
				GlobalPropertySettings.SECURITY_CHECKS_PROPERTIES );

		String propFile = System.getProperty( "soapui.security.exposure.tokens" );
		if( StringUtils.hasContent( propFile ) )
			globalSensitiveInformationExposureTokens.addPropertiesFromFile( propFile );

		try
		{
			SearchPatternsDocumentConfig doc = SearchPatternsDocumentConfig.Factory.parse( SoapUI.class
					.getResourceAsStream( "/com/eviware/soapui/resources/security/SensitiveInfo.xml" ) );

			for( RegexConfig regex : doc.getSearchPatterns().getRegexList() )
			{
				String description = regex.getDescription();
				for( String pattern : regex.getPatternList() )
				{
					globalSensitiveInformationExposureTokens.setPropertyValue( "~(?s).*" + pattern + ".*", "["
							+ regex.getName() + "] " + description );
				}
			}
		}
		catch( Exception e )
		{
			SoapUI.logError( e );
		}
	}

	public static void saveGlobalSecuritySettings()
	{
		if( globalSensitiveInformationExposureTokens != null )
		{
			globalSensitiveInformationExposureTokens.saveSecurityTo( SoapUI.getSettings() );
		}
	}

	public static MutableTestPropertyHolder getGlobalSensitiveInformationExposureTokens()
	{
		if( globalSensitiveInformationExposureTokens == null )
		{
			initGlobalSecuritySettings();
		}

		return globalSensitiveInformationExposureTokens;
	}

	/**
	 * checks if scan is applicable for provided testStep
	 * 
	 * @param testStep
	 * @param scanName
	 * @return
	 */
	public static boolean scanIsApplicableForTestStep( TestStep testStep, String scanName )
	{
		List<String> list = Arrays.asList( SoapUI.getSoapUICore().getSecurityScanRegistry()
				.getAvailableSecurityScansNames( testStep ) );
		return list.contains( scanName );
	}

	/**
	 * @param excludeCustomScript
	 * @return list of security scan names from SecurityScanRegistry optionally excluding Custom Script scan
	 */
	public static List<String> getAllSecurityScanNames( boolean excludeCustomScript )
	{
		if( excludeCustomScript )
		{
			List<String> newList = new ArrayList<String>();
			for( String name : SoapUI.getSoapUICore().getSecurityScanRegistry().getAvailableSecurityScansNames() )
			{
				if( name.equals( GroovySecurityScan.NAME ) )
					continue;

				newList.add( name );
			}
			return newList;
		}
		else
		{
			return Arrays.asList( SoapUI.getSoapUICore().getSecurityScanRegistry().getAvailableSecurityScansNames() );
		}
	}
}
