/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

class JenkinsTask
{
  private def     steps_
  
  public JenkinsTask(steps)
  {
      steps_          = steps
  }
  
  public void execute()
  {
    steps_.echo 'JenkinsTask.execute()'
    steps_.echo 'steps_ = ' + steps_.getClass()
    
  }
}
