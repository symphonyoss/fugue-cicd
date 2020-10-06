/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v4

class RunTask
{
  private def     steps_
  private FuguePipeline pipeLine_
  
  public RunTask(steps, FuguePipeline pipeLine)
  {
      steps_          = pipeLine.steps
      pipeLine_       = pipeLine
  }
  
  public void execute()
  {
    steps_.echo 'RunTask.execute()'
    steps_.echo 'steps_ = ' + steps_.getClass()
    
  }
}
