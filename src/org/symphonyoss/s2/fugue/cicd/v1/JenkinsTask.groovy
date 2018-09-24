/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

import org.jenkinsci.plugins.workflow.cps.DSL

class JenkinsTask
{
  private def     env_
  private DSL     steps_
  
  public JenkinsTask(env, steps)
  {
    env_            = env
    steps_          = steps
    
   
  }
  
  public void echo(String message)
  {
    steps_."echo" message
  }
  
  public void execute()
  {
    steps_.echo 'JenkinsTask.execute()'
//    steps_.echo 'env_ = ' + env_.getClass()
    steps_.echo 'steps_ = ' + steps_.getClass()
    
    
//    echo '2 env_ = ' + env_.getClass()
    echo '2 steps_ = ' + steps_.getClass()
  }
}
