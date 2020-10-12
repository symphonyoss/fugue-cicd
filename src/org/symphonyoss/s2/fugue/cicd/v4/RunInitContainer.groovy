/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v4

class RunInitContainer extends RunTask
{
  private Station station_
  private String podName_
  
  public RunInitContainer(FuguePipeline pipeLine, Station station, String podName)
  {
      super(pipeLine)
      
      station_ = station
      podName_ = podName
  }
  
  public void execute()
  {
    steps_.echo 'RunInitContainer.execute()'
    steps_.echo 'station_ = ' + station_
    steps_.echo 'podName_ = ' + podName_
    
  }
}
