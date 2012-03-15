package com.camunda.fox.platform.subsystem.impl.platform;

import javax.sql.DataSource;
import javax.transaction.TransactionManager;

import org.jboss.as.connector.subsystems.datasources.DataSourceReferenceFactoryService;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import com.camunda.fox.platform.impl.configuration.JtaCmpeProcessEngineConfiguration;
import com.camunda.fox.platform.impl.service.ProcessEngineController;
import com.camunda.fox.platform.spi.ProcessEngineConfiguration;
import com.camunda.fox.platform.subsystem.impl.util.Tccl;
import com.camunda.fox.platform.subsystem.impl.util.Tccl.Operation;

/**
 * @author Daniel Meyer
 */
public class ProcessEngineControllerService extends ProcessEngineController implements Service<ProcessEngineControllerService> {
    
  // Injecting these values makes the MSC aware of our dependencies on these resources.
  // This ensures that they are available when this service is started
  private final InjectedValue<TransactionManager> transactionManagerInjector = new InjectedValue<TransactionManager>();
  private final InjectedValue<DataSourceReferenceFactoryService> datasourceBinderServiceInjector = new InjectedValue<DataSourceReferenceFactoryService>();
  private final InjectedValue<ContainerPlatformService> containerPlatformServiceInjector = new InjectedValue<ContainerPlatformService>();
  
  public ProcessEngineControllerService(ProcessEngineConfiguration processEngineConfiguration) {
    super(processEngineConfiguration);
  }
  
  public static ServiceName createServiceName(String engineName) {
    return ServiceName.of("foxPlatform", "processEngineController", engineName);
  }

  public ProcessEngineControllerService getValue() throws IllegalStateException, IllegalArgumentException {
    return this;
  }
  
  public void start(StartContext context) throws StartException {
    
    processEngineRegistry = containerPlatformServiceInjector.getValue().getProcessEngineRegistry();
    
    // setting the TCCL to the Classloader of this module.
    // this exploits a hack in MyBatis allowing it to use the TCCL to load the 
    // mapping files from the process engine module
    Tccl.runUnderClassloader(new Operation<Void>() {
      public Void run() {
        start();
        return null;
      }
    }, getClass().getClassLoader());   
  }
  
  protected void initProcessEngineConfiguration() {
    super.initProcessEngineConfiguration();
    JtaCmpeProcessEngineConfiguration processEngineConfiguration = (JtaCmpeProcessEngineConfiguration) this.processEngineConfiguration;
    // use the injected datasource
    processEngineConfiguration.setDataSource((DataSource) datasourceBinderServiceInjector.getValue().getReference().getInstance());
    // use the injected transaction manager
    processEngineConfiguration.setTransactionManager(transactionManagerInjector.getValue());    
  }

  @Override
  public void stop(StopContext context) {
    super.stop();
  }

  public Injector<TransactionManager> getTransactionManagerInjector() {
    return transactionManagerInjector;
  }

  public Injector<DataSourceReferenceFactoryService> getDatasourceBinderServiceInjector() {
    return datasourceBinderServiceInjector;
  }
    
  public InjectedValue<ContainerPlatformService> getContainerPlatformServiceInjector() {
    return containerPlatformServiceInjector;
  }

}