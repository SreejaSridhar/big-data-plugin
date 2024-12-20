/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2024 by Hitachi Vantara, LLC : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2029-07-20
 ******************************************************************************/


package org.pentaho.big.data.kettle.plugins.hdfs.trans;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.pentaho.hadoop.shim.api.cluster.NamedCluster;
import org.pentaho.hadoop.shim.api.cluster.NamedClusterService;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.metastore.MetaStoreConst;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.steps.textfileoutput.TextFileField;
import org.pentaho.metastore.api.IMetaStore;
import org.pentaho.runtime.test.RuntimeTester;
import org.pentaho.runtime.test.action.RuntimeTestActionService;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Created by bryan on 11/23/15.
 */
public class HadoopFileOutputMetaTest {

  public static final String TEST_CLUSTER_NAME = "TEST-CLUSTER-NAME";
  public static final String SAMPLE_HADOOP_FILE_OUTPUT_STEP = "sample-hadoop-file-output-step.xml";
  public static final String ENTRY_TAG_NAME = "entry";
  public static final String EMBEDDED_XML = "embed";
  public static final String NAMED_CLUSTER_TAG = "NamedCluster";
  private static final Logger logger = Logger.getLogger( HadoopFileOutputMetaTest.class );
  // for message resolution
  private NamedClusterService namedClusterService;
  private RuntimeTestActionService runtimeTestActionService;
  private RuntimeTester runtimeTester;


  @Before
  public void setUp() throws Exception {
    namedClusterService = mock( NamedClusterService.class );
    runtimeTestActionService = mock( RuntimeTestActionService.class );
    runtimeTester = mock( RuntimeTester.class );
    MetaStoreConst.disableMetaStore = false;
  }

  @Test
  public void testProcessedUrl() {
    String sourceConfigurationName = "scName";
    String desiredUrl = "desiredUrl";
    String url = "url";
    HadoopFileOutputMeta hadoopFileOutputMeta = new HadoopFileOutputMeta( namedClusterService, runtimeTestActionService,
      runtimeTester );
    IMetaStore metaStore = mock( IMetaStore.class );
    assertTrue( null == hadoopFileOutputMeta.getProcessedUrl( metaStore, null ) );
    hadoopFileOutputMeta.setSourceConfigurationName( sourceConfigurationName );
    NamedCluster nc = mock( NamedCluster.class );
    when( namedClusterService.getNamedClusterByName( eq( sourceConfigurationName ), any()) )
      .thenReturn( null );
    assertEquals( url, hadoopFileOutputMeta.getProcessedUrl( metaStore, url ) );
    when( namedClusterService.getNamedClusterByName( eq( sourceConfigurationName ), any()) )
      .thenReturn( nc );
    when( nc.processURLsubstitution( eq( url ), any(), any()) )
      .thenReturn( desiredUrl );
    assertEquals( desiredUrl, hadoopFileOutputMeta.getProcessedUrl( metaStore, url ) );
  }

  @Test
  public void testProcessedUrlUsingEmbeddedCluster() {
    String desiredUrl = "desiredUrl";
    String url = "url";
    HadoopFileOutputMeta hadoopFileOutputMeta = new HadoopFileOutputMeta( namedClusterService, runtimeTestActionService,
      runtimeTester );
    NamedCluster nc = mock( NamedCluster.class );
    NamedCluster nc2 = mock( NamedCluster.class );
    MetaStoreConst.disableMetaStore = true;

    when( namedClusterService.getClusterTemplate() ).thenReturn( nc );
    when( nc.fromXmlForEmbed( any() ) ).thenReturn( nc2 );
    when( nc2.processURLsubstitution( eq( url ), any(), any() ) ).thenReturn( desiredUrl );
    assertEquals( desiredUrl, hadoopFileOutputMeta.getProcessedUrl( null, url ) );
  }

  /**
   * BACKLOG-7972 - Hadoop File Output: Hadoop Clusters dropdown doesn't preserve selected cluster after reopen a
   * transformation after changing signature of loadSource in , saveSource in HadoopFileOutputMeta wasn't called
   *
   * @throws Exception
   */
  @Test
  public void testSaveSourceCalledFromGetXml() throws Exception {
    HadoopFileOutputMeta hadoopFileOutputMeta = new HadoopFileOutputMeta( namedClusterService, runtimeTestActionService,
      runtimeTester );
    hadoopFileOutputMeta.setSourceConfigurationName( TEST_CLUSTER_NAME );
    //set required data for step - empty
    hadoopFileOutputMeta.setOutputFields( new TextFileField[] {} );
    //create spy to check whether saveSource now is called
    HadoopFileOutputMeta spy = Mockito.spy( hadoopFileOutputMeta );
    //getting from structure file node
    Document hadoopOutputMetaStep = getDocumentFromString( spy.getXML(), new SAXBuilder() );
    Element fileElement = getChildElementByTagName( hadoopOutputMetaStep.getRootElement(), "file" );
    //getting from file node cluster attribute value
    Element clusterNameElement = getChildElementByTagName( fileElement, HadoopFileInputMeta.SOURCE_CONFIGURATION_NAME );
    assertEquals( TEST_CLUSTER_NAME, clusterNameElement.getValue() );
    //check that saveSource is called from TextFileOutputMeta
    verify( spy, times( 1 ) ).saveSource( any( StringBuilder.class ), or( any( String.class ), isNull() ) );
  }

  public Node getChildElementByTagName( String fileName ) throws Exception {
    URL resource = getClass().getClassLoader().getResource( fileName );
    if ( resource == null ) {
      logger.error( "no file " + fileName + " found in resources" );
      throw new IllegalArgumentException( "no file " + fileName + " found in resources" );
    } else {
      return XMLHandler.getSubNode( XMLHandler.loadXMLFile( resource ), "entry" );
    }
  }

  public static Element getChildElementByTagName( Element element, String tagName ) {
    return (Element) element.getContent( new ElementFilter( tagName ) ).get( 0 );
  }

  @Test
  public void testLoadSourceCalledFromReadData() throws Exception {
    HadoopFileOutputMeta hadoopFileOutputMeta = new HadoopFileOutputMeta( namedClusterService, runtimeTestActionService,
      runtimeTester );
    hadoopFileOutputMeta.setSourceConfigurationName( TEST_CLUSTER_NAME );
    //set required data for step - empty
    hadoopFileOutputMeta.setOutputFields( new TextFileField[] {} );
    HadoopFileOutputMeta spy = Mockito.spy( hadoopFileOutputMeta );
    Node node = getChildElementByTagName( SAMPLE_HADOOP_FILE_OUTPUT_STEP );
    //create spy to check whether saveSource now is called from readData
    spy.readData( node );
    assertEquals( TEST_CLUSTER_NAME, hadoopFileOutputMeta.getSourceConfigurationName() );
    verify( spy, times( 1 ) ).loadSource( any( Node.class ), or( any( IMetaStore.class ), isNull() ) );
  }

  @Test
  public void testLoadSourceRepForUrlRefresh() throws Exception {
    final String URL_FROM_CLUSTER = "urlFromCluster";
    IMetaStore mockMetaStore = mock( IMetaStore.class );
    NamedCluster mockNamedCluster = mock( NamedCluster.class );
    when( mockNamedCluster.processURLsubstitution( any(), eq( mockMetaStore ), any() ) ).thenReturn( URL_FROM_CLUSTER );
    when( namedClusterService.getNamedClusterByName( TEST_CLUSTER_NAME, mockMetaStore ) )
      .thenReturn( mockNamedCluster );
    Repository mockRep = mock( Repository.class );
    when( mockRep.getStepAttributeString( any(), eq( "source_configuration_name" ) ) ).thenReturn(
      TEST_CLUSTER_NAME );
    HadoopFileOutputMeta hadoopFileOutputMeta =
      new HadoopFileOutputMeta( namedClusterService, runtimeTestActionService, runtimeTester );
    hadoopFileOutputMeta.setSourceConfigurationName( TEST_CLUSTER_NAME );
    when( mockRep.getStepAttributeString( any(), eq( "file_name" ) ) ).thenReturn( "Bad Url In Repo" );

    assertEquals( URL_FROM_CLUSTER, hadoopFileOutputMeta.loadSourceRep( mockRep, null, mockMetaStore ) );
  }

  @Test
  public void testSaveSourceCalledFromGetXmlWithEmbeddedCluster() throws Exception {
    HadoopFileOutputMeta hadoopFileOutputMeta =
      new HadoopFileOutputMeta( namedClusterService, runtimeTestActionService, runtimeTester );
    hadoopFileOutputMeta.setSourceConfigurationName( TEST_CLUSTER_NAME );
    // set required data for step - empty
    hadoopFileOutputMeta.setOutputFields( new TextFileField[] {} );
    // create spy to check whether saveSource now is called
    HadoopFileOutputMeta spy = Mockito.spy( hadoopFileOutputMeta );
    // getting from structure file node
    NamedCluster mockNamedCluster = mock( NamedCluster.class );
    when( namedClusterService.getNamedClusterByName( eq( TEST_CLUSTER_NAME ), any() ) ).thenReturn( mockNamedCluster );
    when( mockNamedCluster.toXmlForEmbed( NAMED_CLUSTER_TAG ) ).thenReturn(
      "<" + NAMED_CLUSTER_TAG + ">" + EMBEDDED_XML + "</" + NAMED_CLUSTER_TAG + ">" );

    Document hadoopOutputMetaStep = getDocumentFromString( spy.getXML(), new SAXBuilder() );
    Element clusterElement = getChildElementByTagName( hadoopOutputMetaStep.getRootElement(), NAMED_CLUSTER_TAG );
    // getting from file node cluster attribute value
    assertEquals( EMBEDDED_XML, clusterElement.getValue() );
    // check that saveSource is called from TextFileOutputMeta
    verify( spy, times( 1 ) ).saveSource( any( StringBuilder.class ), or( any( String.class ), isNull() ) );
  }


  public static Document getDocumentFromString( String xmlStep, SAXBuilder jdomBuilder )
    throws JDOMException, IOException {
    String xml = XMLHandler.openTag( ENTRY_TAG_NAME ) + xmlStep + XMLHandler.closeTag( ENTRY_TAG_NAME );
    return jdomBuilder.build( new ByteArrayInputStream( xml.getBytes() ) );
  }
}
