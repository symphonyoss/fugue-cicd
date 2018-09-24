/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

import org.jenkinsci.plugins.workflow.cps.DSL
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

class JenkinsTask implements Serializable
{
  protected EnvActionImpl     env_
  protected DSL               steps_
  
  public JenkinsTask(EnvActionImpl env, DSL steps)
  {
    env_            = env
    steps_          = steps
    
    def s = steps_.echo 'env_ = ' + env_.getClass()
    
    steps_.echo 's=' + s
  }
  
  public void echo(String message)
  {
    steps_."echo" message
  }
  
  public def sh(String script, boolean returnStdout = false, boolean returnStatus = false)
  {
    steps_."echo" 'TT2'
    return steps_."sh"(script: script, returnStdout: returnStdout, returnStatus: returnStatus)
  }
  
  public def sh(Map args)
  {
    steps_."echo" 'TT3'
    return steps_."sh"(args)
  }
  
  public void execute()
  {
    steps_.echo 'JenkinsTask.execute()'
    steps_.echo 'env_ = ' + env_.getClass()
    steps_.echo 'steps_ = ' + steps_.getClass()
    
    
    echo '2 env_ = ' + env_.getClass()
    echo '2 steps_ = ' + steps_.getClass()
  }
}
