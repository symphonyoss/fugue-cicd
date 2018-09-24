/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

class FuguePipelineTask extends JenkinsTask
{
//  private def     env_
//  private def     steps_
  private FuguePipeline pipeLine_
  
  public FuguePipelineTask(env, steps, FuguePipeline pipeLine)
  {
    super(env, steps)
//      steps_          = steps
//    pipeLine_       = pipeLine
  }
  
  public void execute()
  {
    super.execute()
    
    echo 'FuguePipelineTask.execute()'
    echo 'pipeLine_ = ' + pipeLine_.getClass()
    
  }
}
