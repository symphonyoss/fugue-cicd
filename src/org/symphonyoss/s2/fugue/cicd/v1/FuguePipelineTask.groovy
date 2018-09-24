/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

class FuguePipelineTask extends JenkinsTask
{
  private FuguePipeline pipeLine_
  
  public FuguePipelineTask(steps, FuguePipeline pipeLine)
  {
    super(steps)
    pipeLine_       = pipeLine
  }
  
  public void execute()
  {
    steps_.echo 'FuguePipeline.execute()'
    steps_.echo 'pipeLine_ = ' + pipeLine_.getClass()
    
  }
}
