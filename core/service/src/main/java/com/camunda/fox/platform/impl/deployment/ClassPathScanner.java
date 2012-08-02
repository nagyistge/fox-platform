/**
 * Copyright (C) 2011, 2012 camunda services GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.camunda.fox.platform.impl.deployment;

import static com.camunda.fox.platform.impl.deployment.spi.ProcessArchiveScanner.ScanningUtil.isDeployable;
import static com.camunda.fox.platform.impl.deployment.spi.ProcessArchiveScanner.ScanningUtil.isDiagramForProcess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.activiti.engine.impl.util.IoUtil;

import com.camunda.fox.platform.FoxPlatformException;
import com.camunda.fox.platform.impl.deployment.spi.ProcessArchiveScanner;
import com.camunda.fox.platform.spi.ProcessArchive;

/**
 * <p>Scans for bpmn20.xml files in the classpath of the given classloader.</p>
 *  
 * <p>Scans all branches of the classpath containing a META-INF/processes.xml 
 * file </p>
 * 
 * @author Daniel Meyer
 * @author Falko Menge
 */
public class ClassPathScanner implements ProcessArchiveScanner {

  private static Logger log = Logger.getLogger(ClassPathScanner.class.getName());
  
  @Override
  public Map<String, byte[]> findResources(ProcessArchive processArchive) {    
    
    final Map<String, byte[]> resourceMap = new HashMap<String, byte[]>();

    final ClassLoader classLoader = processArchive.getClassLoader();
    final URL metaFileUrl = (URL) processArchive.getProperties().get(ProcessArchive.PROP_META_FILE_URL);
    final String paResourceRootPath = (String) processArchive.getProperties().get(ProcessArchive.PROP_RESOURCE_ROOT_PATH);
    
    URL paResourceRoot = null;
    String strippedPaResourceRootPath = null;
    boolean isPaLocal;
    
    if(paResourceRootPath != null && !paResourceRootPath.startsWith("pa:")) {
      strippedPaResourceRootPath = paResourceRootPath.replace("classpath:", "");
      strippedPaResourceRootPath = strippedPaResourceRootPath.endsWith("/") ? strippedPaResourceRootPath : strippedPaResourceRootPath +"/";
      paResourceRoot = classLoader.getResource(strippedPaResourceRootPath);
      isPaLocal = false;
    } else {
      if(paResourceRootPath != null) {
        strippedPaResourceRootPath = paResourceRootPath.replace("pa:", "");        
        strippedPaResourceRootPath = strippedPaResourceRootPath.endsWith("/") ? strippedPaResourceRootPath : strippedPaResourceRootPath +"/";
      }
      paResourceRoot = metaFileUrl;
      isPaLocal = true;
    }
    
    if(paResourceRoot != null) {
      
      Set<String> discoveredProcesses = scanClassPath(paResourceRoot, strippedPaResourceRootPath, isPaLocal);         
      
      for (String resourceName : discoveredProcesses) {            
        InputStream resourceAsStream = classLoader.getResourceAsStream(resourceName);
        try {
          if(resourceAsStream == null) {
            log.warning("Could not load deployable resource: "+resourceName+ " using classloader "+ classLoader);
          } else {
            byte[] bytes = IoUtil.readInputStream(resourceAsStream, resourceName);      
            resourceMap.put(resourceName, bytes);          
          }
        } finally {
          IoUtil.closeSilently(resourceAsStream);
        }
      }
      
    } else {
      log.warning("Not scanning process archive classloader '"+processArchive.getName()+"': property  '"+ProcessArchive.PROP_META_FILE_URL+"' not set.");
    }
    
    return resourceMap;
  }
  
  protected Set<String> scanClassPath(URL metaFileUrl, String paResourceRootPath, boolean isPaLocal) {
    Set<String> discoveredProcesses = new HashSet<String>();

    String urlPath = metaFileUrl.toExternalForm();
    
    if(isPaLocal) {
  
      if (urlPath.startsWith("file:") || urlPath.startsWith("jar:")) {
        urlPath = metaFileUrl.getPath();
        int withinArchive = urlPath.indexOf('!');
        if (withinArchive != -1) {
          urlPath = urlPath.substring(0, withinArchive);
        } else {
          File file = new File(urlPath);
          urlPath = file.getParentFile().getParent();
        }
      }
      
    } else {
      
      if (urlPath.startsWith("file:") || urlPath.startsWith("jar:")) {
        urlPath = metaFileUrl.getPath();
        int withinArchive = urlPath.indexOf('!');
        if (withinArchive != -1) {
          urlPath = urlPath.substring(0, withinArchive);
        }
      }
      
    }

    try {
      urlPath = URLDecoder.decode(urlPath, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new FoxPlatformException("Could not decode pathname using utf-8 decoder.", e);
    }

    log.log(Level.FINEST, "Rootpath is {0}", urlPath);

    handlePaths(urlPath, discoveredProcesses, paResourceRootPath);

    return discoveredProcesses;
  }

  protected void handlePaths(String urlPath, Set<String> discoveredProceses, String paResourceRootPath) {
    if (urlPath.startsWith("file:")) {
      urlPath = urlPath.substring(5);
    }
    if (urlPath.indexOf('!') > 0) {
      urlPath = urlPath.substring(0, urlPath.indexOf('!'));
    }

    File file = new File(urlPath);
    if (file.isDirectory()) {
      String path = file.getPath();
      String rootPath = path.endsWith(File.separator) ? path : path+File.separator;
      handleDirectory(file, rootPath, discoveredProceses, paResourceRootPath);
    } else {
      handleArchive(file, discoveredProceses, paResourceRootPath);
    }
  }

  protected void handleArchive(File file, Set<String> discoveredProceses, String paResourceRootPath) {    
    try {
      ZipFile zipFile = new ZipFile(file);
      Enumeration< ? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = (ZipEntry) entries.nextElement();
        String processFileName = zipEntry.getName();        
        if (isDeployable(processFileName) && isBelowPath(processFileName, paResourceRootPath)) {
          addResource(processFileName, discoveredProceses);
          // find diagram(s) for process
          Enumeration< ? extends ZipEntry> entries2 = zipFile.entries();
          while (entries2.hasMoreElements()) {
            ZipEntry zipEntry2 = (ZipEntry) entries2.nextElement();
            String diagramFileName = zipEntry2.getName();
            if (isDiagramForProcess(diagramFileName, processFileName)) {
              addResource(diagramFileName, discoveredProceses);
            }
          }
        }
      }
      zipFile.close();
    } catch (IOException e) {
      throw new FoxPlatformException("IOException while scanning archive '"+file+"'.", e);
    }
  }

  protected boolean isBelowPath(String processFileName, String paResourceRootPath) {
    if(paResourceRootPath == null || paResourceRootPath.length() ==0 ) {
      return true;
    } else {
      return processFileName.startsWith(paResourceRootPath);
    }    
  }

  protected void handleDirectory(File directory, String rootPath, Set<String> discoveredProcesses, String paResourceRootPath) {
    File[] paths = directory.listFiles();
    
    String currentPathSegment = paResourceRootPath;
    if (paResourceRootPath != null && paResourceRootPath.length() > 0) {
      if (paResourceRootPath.indexOf('/') > 0) {
        currentPathSegment = paResourceRootPath.substring(0, paResourceRootPath.indexOf('/'));
        paResourceRootPath = paResourceRootPath.substring(paResourceRootPath.indexOf('/') + 1, paResourceRootPath.length());
      } else {
        paResourceRootPath = null;
      }
    }
    
    for (File path : paths) {
      
      if(currentPathSegment != null && currentPathSegment.length()>0) {    
        
        if(path.isDirectory()) {
          // only descend into directory, if below resource root:
          if(path.getName().equals(currentPathSegment)) {
            handleDirectory(path, rootPath, discoveredProcesses, paResourceRootPath);
          }
        }
        
      } else { // at resource root or below -> continue scanning 
        String processFileName = path.getPath();
        if (!path.isDirectory() && isDeployable(processFileName)) {
          addResource(processFileName, rootPath, discoveredProcesses);
          // find diagram(s) for process
          for (File file : paths) {
            String diagramFileName = file.getPath();
            if (!path.isDirectory() && isDiagramForProcess(diagramFileName, processFileName)) {
              addResource(diagramFileName, rootPath, discoveredProcesses);
            }
          }
        } else if (path.isDirectory()) {
          handleDirectory(path, rootPath, discoveredProcesses, paResourceRootPath);
        }
      }
    }
  }

  private void addResource(String fileName, String rootPath, Set<String> discoveredProcessResources) {
    String nameRelative = fileName.substring(rootPath.length());
    addResource(nameRelative, discoveredProcessResources);
  }

  private void addResource(String resourceName, Set<String> discoveredProcessResources) {
    discoveredProcessResources.add(resourceName);
    log.log(Level.FINEST, "discovered process resource {0}", resourceName);
  }

}
