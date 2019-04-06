/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v3

class RunInitContainer extends RunTask
{
  private Station tenantStage_
  private String tenant_
  
  public RunInitContainer(FuguePipeline pipeLine, Station tenantStage, String tenant)
  {
      super(pipeLine)
      
      tenantStage_ = tenantStage
      tenant_ = tenant
  }
  
  public void execute()
  {
    steps_.echo 'RunInitContainer.execute()'
    steps_.echo 'tenantStage_ = ' + tenantStage_
    steps_.echo 'tenant_ = ' + tenant_
    
  }
}
