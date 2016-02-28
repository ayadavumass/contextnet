/*
 *
 *  Copyright (c) 2015 University of Massachusetts
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you
 *  may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  Initial developer(s): ayadav
 *
 */
package edu.umass.cs.contextservice.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import edu.umass.cs.contextservice.logging.ContextServiceLogger;

/**
 * Parses a properties file to get all info needed to run CS on hosts.
 * @author ayadav
 */
public class CSConfigFileLoader 
{
  /**
   * Creates an instance of InstallConfig.
   * 
   * @param filename
   */
  public CSConfigFileLoader(String filename) {
    try {
      loadPropertiesFile(filename);
    } catch (IOException e) {
      ContextServiceLogger.getLogger().severe("Problem loading installer config file: " + e);
    }
  }

  private void loadPropertiesFile(String filename) throws IOException 
  {
	Properties properties = new Properties();

    File f = new File(filename);
    if (f.exists() == false) {
      throw new FileNotFoundException("CS config file not found:" + filename);
    }
    InputStream input = new FileInputStream(filename);
    properties.load(input);
    
    ContextServiceConfig.modelRho = Double.parseDouble(
    		properties.getProperty(ContextServiceConfig.modelRhoString, ContextServiceConfig.modelRho+"") );
    
    ContextServiceConfig.modelCsByC = Double.parseDouble(
    		properties.getProperty(ContextServiceConfig.modelCsByCString, ContextServiceConfig.modelCsByC+"") );
    
    ContextServiceConfig.modelCuByC = Double.parseDouble(
    		properties.getProperty(ContextServiceConfig.modelCuByCString, ContextServiceConfig.modelCuByC+"") );
    
    ContextServiceConfig.modelAavg = Double.parseDouble(
    		properties.getProperty(ContextServiceConfig.modelAavgString, ContextServiceConfig.modelAavg+"") );
    
    ContextServiceConfig.TRIGGER_ENABLED = Boolean.parseBoolean(
    		properties.getProperty(ContextServiceConfig.triggerEnableString, "false") );
  }
  
  /**
   * The main routine. For testing only.
   * 
   * @param args
   */
  public static void main(String[] args) {
    //String filename = GNS.WESTY_GNS_DIR_PATH + "/conf/ec2_small/installer_config";
    //InstallConfig config = new InstallConfig(filename);
    //System.out.println(config.toString());
  }
}